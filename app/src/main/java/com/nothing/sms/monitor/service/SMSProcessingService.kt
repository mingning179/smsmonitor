package com.nothing.sms.monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nothing.sms.monitor.MainActivity
import com.nothing.sms.monitor.db.SMSDatabase
import com.nothing.sms.monitor.push.ApiPushService
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 短信处理服务
 * 负责短信的存储和管理
 */
class SMSProcessingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_monitor_channel"
        private const val CHANNEL_NAME = "短信监控服务"
        private const val CHANNEL_DESCRIPTION = "保持短信监控服务运行"
        
        // 状态常量，从AppConfig迁移到这里
        const val STATUS_PENDING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_FAILED = 2
        
        // 重试检查间隔（分钟）
        private const val DEFAULT_RETRY_CHECK_INTERVAL = 5L
    }

    private lateinit var settingsService: SettingsService
    private lateinit var smsDatabase: SMSDatabase
    private lateinit var pushServiceManager: PushServiceManager
    
    private val serviceScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + 
        CoroutineExceptionHandler { _, e -> 
            Timber.e(e, "协程异常")
        }
    )
    
    private var retryJob: Job? = null
    private var statusReportJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        startForeground()
        startRetryJob()
        startStatusReportJob()
    }

    private fun initializeComponents() {
        settingsService = SettingsService.getInstance(this)
        smsDatabase = SMSDatabase(this)
        pushServiceManager = PushServiceManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("sender") == true && intent.hasExtra("body")) {
            val sender = intent.getStringExtra("sender") ?: "未知"
            val body = intent.getStringExtra("body") ?: ""
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

            serviceScope.launch {
                processIncomingSMS(sender, body, timestamp)
            }
        }
        
        return START_STICKY
    }

    private suspend fun processIncomingSMS(sender: String, body: String, timestamp: Long) {
        try {
            // 保存到数据库
            val id = smsDatabase.insertSMS(sender, body, timestamp)
            
            // 推送到所有已启用的推送服务
            pushSMSToServices(sender, body, timestamp)
            
            // 清理旧记录
            withContext(Dispatchers.IO) {
                smsDatabase.cleanupOldRecords()
            }
            
            // 更新短信状态为成功（当所有推送服务都完成后）
            smsDatabase.updateStatus(id, STATUS_SUCCESS)
            updateNotification("短信处理成功")
            
        } catch (e: Exception) {
            Timber.e(e, "处理短信时出错")
        }
    }
    
    /**
     * 推送短信内容到所有已启用的推送服务
     */
    private suspend fun pushSMSToServices(sender: String, body: String, timestamp: Long) {
        try {
            // 使用推送服务管理器推送到所有启用的服务
            pushServiceManager.pushToAll(sender, body, timestamp)
        } catch (e: Exception) {
            Timber.e(e, "推送短信时出错")
        }
    }
    
    private fun startRetryJob() {
        retryJob?.cancel()
        retryJob = serviceScope.launch {
            while (isActive) {
                try {
                    val stats = smsDatabase.getStats()
                    updateNotification(buildStatsMessage(stats))
                    
                    // 获取需要重试的消息
                    val pendingMessages = smsDatabase.getPendingMessages()
                    for (message in pendingMessages) {
                        if (!isActive) break
                        
                        // 重试推送
                        pushSMSToServices(
                            message.sender,
                            message.content,
                            message.timestamp
                        )
                        
                        // 更新状态
                        smsDatabase.updateStatus(message.id, STATUS_SUCCESS)
                        
                        // 避免过快重试，每条消息之间稍作延迟
                        delay(1000)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "重试任务出错")
                }
                
                // 等待指定时间后再次检查
                delay(TimeUnit.MINUTES.toMillis(DEFAULT_RETRY_CHECK_INTERVAL))
            }
        }
    }

    private fun startForeground() {
        createNotificationChannel()
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(CHANNEL_NAME)
            .setContentText("服务运行中...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updateNotification(message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(CHANNEL_NAME)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun buildStatsMessage(stats: SMSDatabase.SMSStats): String {
        return "总计: ${stats.total} | " +
               "成功: ${stats.success} | " +
               "失败: ${stats.failed} | " +
               "待处理: ${stats.pending}"
    }

    /**
     * 启动状态上报任务
     */
    private fun startStatusReportJob() {
        statusReportJob?.cancel()
        statusReportJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 获取当前统计信息，记录到日志
                    val stats = smsDatabase.getStats()
                    Timber.d("短信统计: 总计=${stats.total}, 成功=${stats.success}, 失败=${stats.failed}, 待处理=${stats.pending}")
                    
                    // 尝试上报状态到API服务器
                    val apiService = pushServiceManager.getService("api") as? ApiPushService
                    apiService?.let {
                        if (it.isEnabled) {
                            it.reportStatus()
                                .onSuccess { Timber.d("状态上报成功") }
                                .onFailure { e -> Timber.e(e, "状态上报失败") }
                        }
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "状态上报任务出错")
                }
                
                // 使用SettingsService获取状态上报间隔
                delay(TimeUnit.MINUTES.toMillis(settingsService.getStatusReportInterval()))
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        retryJob?.cancel()
        statusReportJob?.cancel()
        serviceScope.cancel()
    }
}