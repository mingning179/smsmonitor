package com.nothing.sms.monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nothing.sms.monitor.MainActivity
import com.nothing.sms.monitor.db.SMSDatabase
import com.nothing.sms.monitor.push.PushService
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 短信处理服务
 * 负责短信的存储和推送管理
 */
class SMSProcessingService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sms_monitor_channel"
        private const val CHANNEL_NAME = "短信监控服务"
        private const val CHANNEL_DESCRIPTION = "保持短信监控服务运行"

        // 重试检查间隔（分钟）
        private const val DEFAULT_RETRY_CHECK_INTERVAL = 1L

        // 用于Intent传递的推送记录ID
        const val EXTRA_PUSH_RECORD_ID = "push_record_id"

        // 广播Action定义
        const val ACTION_RETRY_PUSH = "com.nothing.sms.monitor.ACTION_RETRY_PUSH"
        const val ACTION_PROCESS_PENDING = "com.nothing.sms.monitor.ACTION_PROCESS_PENDING"
    }

    private lateinit var settingsService: SettingsService
    private lateinit var smsDatabase: SMSDatabase
    private lateinit var pushServiceManager: PushServiceManager
    private lateinit var serviceScope: CoroutineScope

    private var statusReportJob: Job? = null
    private var retryJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // 创建通知渠道
        createNotificationChannel()

        // 开启前台服务
        startForeground(NOTIFICATION_ID, createNotification("服务启动中..."))

        // 初始化服务
        settingsService = SettingsService.getInstance(this)
        smsDatabase = SMSDatabase(this)
        pushServiceManager = PushServiceManager.getInstance(this)

        // 创建协程上下文
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "服务协程异常")
        }
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

        // 启动状态报告协程
        startStatusReportJob()

        // 启动失败推送重试协程
        startRetryJob()

        // 处理超过最大重试次数的记录
        handleMaxRetriesRecords()

        Timber.i("短信处理服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理Intent
        intent?.let {
            when (it.action) {
                ACTION_RETRY_PUSH -> {
                    val recordId = it.getLongExtra(EXTRA_PUSH_RECORD_ID, -1)
                    if (recordId > 0) {
                        retryPushRecord(recordId)
                    }
                }

                ACTION_PROCESS_PENDING -> {
                    // 处理所有待处理的消息
                    processPendingMessages()
                }

                else -> {
                    // 获取传入的短信信息
                    val sender = it.getStringExtra("sender")
                    val body = it.getStringExtra("body")
                    val timestamp = it.getLongExtra("timestamp", System.currentTimeMillis())
                    val subscriptionId = it.getIntExtra("subscriptionId", 0)  // 获取订阅ID，默认为0

                    if (!sender.isNullOrEmpty() && !body.isNullOrEmpty()) {
                        serviceScope.launch {
                            processIncomingSMS(sender, body, timestamp, subscriptionId)
                        }
                    }
                }
            }
        }

        // 如果服务被杀死，自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private suspend fun processIncomingSMS(
        sender: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int
    ) {
        try {
            // 保存到数据库，获取SMS ID
            val smsId = smsDatabase.saveSMS(sender, body, timestamp, subscriptionId)

            // 获取启用的推送服务
            val enabledServices = pushServiceManager.getEnabledServices()

            if (enabledServices.isEmpty()) {
                // 如果没有启用的推送服务，将短信状态直接设为SUCCESS
                // 这样避免消息一直显示为待处理状态
                smsDatabase.updateStatus(smsId, SMSDatabase.STATUS_SUCCESS)
                Timber.i("没有启用的推送服务，短信已保存但不会推送")
            } else {
                // 推送到所有已启用的推送服务
                pushSMSToServices(smsId, sender, body, timestamp, subscriptionId)
            }

            // 清理旧记录
            withContext(Dispatchers.IO) {
                smsDatabase.cleanupOldRecords()
            }

            updateNotification("短信处理完成")

        } catch (e: Exception) {
            Timber.e(e, "处理短信时出错")
        }
    }

    /**
     * 推送短信内容到所有已启用的推送服务
     */
    private suspend fun pushSMSToServices(
        smsId: Long,
        sender: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int
    ) {
        val enabledServices = pushServiceManager.getEnabledServices()

        if (enabledServices.isEmpty()) {
            Timber.w("没有已启用的推送服务")
            // 如果没有启用的推送服务，将短信状态直接设为SUCCESS
            smsDatabase.updateStatus(smsId, SMSDatabase.STATUS_SUCCESS)
            return
        }

        // 获取已有的所有推送服务类型（无论状态如何），避免重复推送
        val existingServiceTypes = smsDatabase.getAllServiceTypesBySmsId(smsId)
        Timber.d("短信已有的推送服务类型: $existingServiceTypes")

        // 仅推送到未创建记录的服务
        for (service in enabledServices) {
            // 跳过已经有推送记录的服务
            if (service.serviceType in existingServiceTypes) {
                Timber.d("服务 ${service.serviceName} 已有推送记录，跳过")
                continue
            }

            pushToService(smsId, service, sender, body, timestamp, subscriptionId)
        }
    }

    /**
     * 推送到单个服务并记录结果
     */
    private suspend fun pushToService(
        smsId: Long,
        service: PushService,
        sender: String,
        content: String,
        timestamp: Long,
        subscriptionId: Int
    ) {
        try {
            Timber.d("正在推送到 ${service.serviceName}")

            val result = service.pushSMS(sender, content, timestamp, subscriptionId)

            result.fold(
                onSuccess = {
                    // 推送成功，记录结果
                    smsDatabase.addPushRecord(
                        smsId = smsId,
                        service = service,
                        status = SMSDatabase.STATUS_SUCCESS
                    )
                    Timber.d("推送到 ${service.serviceName} 成功")
                },
                onFailure = { e ->
                    // 推送失败，记录错误
                    smsDatabase.addPushRecord(
                        smsId = smsId,
                        service = service,
                        status = SMSDatabase.STATUS_FAILED,
                        errorMessage = e.message ?: "未知错误"
                    )
                    Timber.e(e, "推送到 ${service.serviceName} 失败")
                }
            )
        } catch (e: Exception) {
            // 发生异常，记录错误
            smsDatabase.addPushRecord(
                smsId = smsId,
                service = service,
                status = SMSDatabase.STATUS_FAILED,
                errorMessage = e.message ?: "未知错误"
            )
            Timber.e(e, "推送到 ${service.serviceName} 时发生异常: ${e.message}")
        }
    }

    /**
     * 重试单个推送记录
     */
    private fun retryPushRecord(recordId: Long) {
        serviceScope.launch {
            try {
                // 获取推送记录
                val record = smsDatabase.getPushRecordById(recordId) ?: return@launch

                // 检查是否可以重试
                if (!record.canRetry) {
                    Timber.w("推送记录 #${record.id} 无法重试：已达到最大重试次数或状态不允许")

                    // 超过最大重试次数后，将推送记录标记为失败
                    smsDatabase.updatePushRecordStatus(
                        recordId = record.id,
                        status = SMSDatabase.STATUS_FAILED,
                        errorMessage = "已达到最大重试次数"
                    )

                    Toast.makeText(
                        applicationContext,
                        "推送记录 #${record.id} 重试失败，已达到最大重试次数",
                        Toast.LENGTH_LONG
                    ).show()

                    return@launch
                }

                // 获取对应短信
                val sms = smsDatabase.getSMSById(record.smsId) ?: return@launch

                // 获取推送服务
                val service = pushServiceManager.getService(record.serviceType) ?: return@launch

                // 检查该服务是否已成功推送过（可能在多线程环境下，状态已更新）
                val successfulServices = smsDatabase.getSuccessfulServiceTypes(record.smsId)
                if (service.serviceType in successfulServices) {
                    Timber.d("服务 ${service.serviceName} 已成功推送，跳过重试")
                    return@launch
                }

                // 增加重试次数
                val currentRetry = smsDatabase.incrementRetryCount(recordId)

                Timber.i("正在重试推送记录 #${record.id}，SMS ID: ${record.smsId}，服务: ${record.serviceName}，重试次数: $currentRetry")

                // 重新推送
                val result = service.pushSMS(sms.sender, sms.content, sms.timestamp, sms.subscriptionId)

                result.fold(
                    onSuccess = {
                        // 推送成功，更新状态
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = SMSDatabase.STATUS_SUCCESS
                        )
                        Timber.d("重试推送到 ${service.serviceName} 成功")
                    },
                    onFailure = { e ->
                        // 判断是否是最后一次重试
                        val finalStatus = if (currentRetry >= SMSDatabase.MAX_RETRY_COUNT) {
                            SMSDatabase.STATUS_FAILED
                        } else {
                            SMSDatabase.STATUS_FAILED // 仍然是失败状态，但允许后续重试
                        }

                        // 推送失败，更新错误信息
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = finalStatus,
                            errorMessage = e.message ?: "未知错误"
                        )
                        Timber.e(
                            e,
                            "重试推送到 ${service.serviceName} 失败，短信ID: ${record.smsId}"
                        )

                        // 如果这是最后一次重试，显示Toast通知
                        if (currentRetry >= SMSDatabase.MAX_RETRY_COUNT) {
                            Toast.makeText(
                                applicationContext,
                                "推送记录 #${record.id} 重试失败，已达到最大重试次数",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "重试推送记录时出错")
            }
        }
    }

    /**
     * 启动定期状态报告任务
     */
    private fun startStatusReportJob() {
        statusReportJob?.cancel()
        statusReportJob = serviceScope.launch {
            while (true) {
                if (!isActive) break
                try {
                    val interval = settingsService.getStatusReportInterval()
                    val stats = smsDatabase.getStats()
                    updateNotification(buildStatsMessage(stats))

                    // 延迟到下一次报告
                    delay(TimeUnit.MINUTES.toMillis(interval))
                } catch (e: Exception) {
                    Timber.e(e, "状态报告任务异常")
                    delay(TimeUnit.MINUTES.toMillis(DEFAULT_RETRY_CHECK_INTERVAL))
                }
            }
        }
    }

    private fun startRetryJob() {
        retryJob?.cancel()
        retryJob = serviceScope.launch {
            while (true) {
                if (!isActive) break
                try {
                    // 1. 处理可重试的推送记录
                    processRetryableRecords()

                    // 2. 处理所有待处理但已达到最大重试次数的推送记录
                    markMaxRetryRecordsAsFailed()

                    // 每隔一段时间检查一次
                    delay(TimeUnit.MINUTES.toMillis(DEFAULT_RETRY_CHECK_INTERVAL))
                } catch (e: Exception) {
                    Timber.e(e, "重试任务异常")
                    delay(TimeUnit.MINUTES.toMillis(DEFAULT_RETRY_CHECK_INTERVAL))
                }
            }
        }
    }

    /**
     * 处理可重试的推送记录
     */
    private suspend fun processRetryableRecords() = coroutineScope {
        // 获取可重试的推送记录
        val retryableRecords = smsDatabase.getRetryablePushRecords()
        Timber.d("发现 ${retryableRecords.size} 条可重试的推送记录")

        for (record in retryableRecords) {
            if (!isActive) break

            // 获取SMS和服务
            val sms = smsDatabase.getSMSById(record.smsId) ?: continue
            val service = pushServiceManager.getService(record.serviceType) ?: continue

            // 检查该服务是否已成功推送过（可能在多线程环境下，状态已更新）
            val successfulServices = smsDatabase.getSuccessfulServiceTypes(record.smsId)
            if (service.serviceType in successfulServices) {
                Timber.d("服务 ${service.serviceName} 已成功推送，跳过重试")
                continue
            }

            // 增加重试次数
            val currentRetry = smsDatabase.incrementRetryCount(record.id)
            Timber.i("自动重试推送记录 #${record.id}，服务: ${record.serviceName}，重试次数: $currentRetry")

            try {
                // 执行推送
                val result = service.pushSMS(sms.sender, sms.content, sms.timestamp, sms.subscriptionId)

                result.fold(
                    onSuccess = {
                        // 推送成功，更新状态
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = SMSDatabase.STATUS_SUCCESS
                        )
                        Timber.d("自动重试推送到 ${service.serviceName} 成功")
                    },
                    onFailure = { e ->
                        // 推送失败，更新错误信息
                        // 判断是否是最后一次重试
                        val finalStatus = if (currentRetry >= SMSDatabase.MAX_RETRY_COUNT) {
                            SMSDatabase.STATUS_FAILED
                        } else {
                            SMSDatabase.STATUS_FAILED // 仍然是失败状态，但允许后续重试
                        }

                        // 更新状态
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = finalStatus,
                            errorMessage = e.message ?: "未知错误"
                        )
                        Timber.e(e, "自动重试推送到 ${service.serviceName} 失败: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "处理重试记录时出错")
            }

            // 避免过快处理，每条消息之间稍作延迟
            delay(1000)
        }
    }

    /**
     * 将所有已达到最大重试次数的推送记录标记为失败
     */
    private fun markMaxRetryRecordsAsFailed() {
        serviceScope.launch {
            try {
                // 查找所有待处理但已达到最大重试次数的推送记录
                val pendingWithMaxRetries = smsDatabase.getPendingRecordsWithMaxRetry()
                if (pendingWithMaxRetries.isNotEmpty()) {
                    Timber.i("发现 ${pendingWithMaxRetries.size} 条已达到最大重试次数的待处理记录")

                    for (record in pendingWithMaxRetries) {
                        if (!isActive) break

                        // 更新为失败状态
                        Timber.w("推送记录 #${record.id} 处于待处理状态但已达到最大重试次数，标记为失败")
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = SMSDatabase.STATUS_FAILED,
                            errorMessage = "已达到最大重试次数"
                        )
                    }

                    Toast.makeText(
                        applicationContext,
                        "已将 ${pendingWithMaxRetries.size} 条超过重试次数的记录标记为失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "处理最大重试次数记录时出错")
            }
        }
    }

    /**
     * 处理所有待处理的短信消息
     */
    private fun processPendingMessages() {
        serviceScope.launch {
            try {
                val pendingMessages = smsDatabase.getPendingMessages()
                Timber.i("发现 ${pendingMessages.size} 条待处理短信")

                // 检查是否有启用的推送服务
                val enabledServices = pushServiceManager.getEnabledServices()
                if (enabledServices.isEmpty()) {
                    // 如果没有启用的推送服务，将所有待处理短信标记为成功
                    pendingMessages.forEach { sms ->
                        if (!isActive) return@forEach
                        smsDatabase.updateStatus(sms.id, SMSDatabase.STATUS_SUCCESS)
                    }
                    Timber.i("没有启用的推送服务，所有待处理短信已标记为完成")

                    Toast.makeText(
                        applicationContext,
                        "没有启用的推送服务，${pendingMessages.size} 条待处理短信已标记为完成",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                // 逐个处理待处理的短信
                var processedCount = 0
                var skippedCount = 0
                for (sms in pendingMessages) {
                    if (!isActive) break

                    // 获取所有已有推送记录的服务类型（无论状态如何）
                    val existingServiceTypes = smsDatabase.getAllServiceTypesBySmsId(sms.id)

                    // 检查是否所有服务都已经有记录
                    val allServicesHaveRecords =
                        enabledServices.all { it.serviceType in existingServiceTypes }

                    if (allServicesHaveRecords) {
                        Timber.d("短信 ${sms.id} 已对所有启用的服务创建了推送记录，跳过创建新记录")

                        // 这里可以尝试重试失败的记录
                        val failedRecords = smsDatabase.getPushRecordsBySmsId(sms.id)
                            .filter { it.canRetry }

                        if (failedRecords.isNotEmpty()) {
                            Timber.d("短信 ${sms.id} 有 ${failedRecords.size} 条失败的推送记录，将尝试重试")
                            for (record in failedRecords) {
                                if (!isActive) break
                                retryPushRecord(record.id)
                                delay(500)
                            }
                            processedCount++
                        } else {
                            skippedCount++
                        }
                        continue
                    }

                    // 筛选未创建推送记录的服务
                    val servicesToPush =
                        enabledServices.filter { it.serviceType !in existingServiceTypes }

                    Timber.d("处理待处理短信: ${sms.id}，需推送到 ${servicesToPush.size} 个服务")
                    processedCount++

                    // 推送到所有未创建记录的已启用服务
                    for (service in servicesToPush) {
                        if (!isActive) break

                        pushToService(
                            sms.id,
                            service,
                            sms.sender,
                            sms.content,
                            sms.timestamp,
                            sms.subscriptionId
                        )
                        // 避免过快处理，每个服务之间稍作延迟
                        delay(500)
                    }

                    // 每条短信处理后稍作延迟
                    delay(1000)
                }

                // 处理完后，将所有超过最大重试次数的待处理记录标记为失败
                if (isActive) {
                    markMaxRetryRecordsAsFailed()
                }

                // 显示处理结果
                if (processedCount > 0) {
                    val message = if (skippedCount > 0) {
                        "已处理 $processedCount 条短信，跳过 $skippedCount 条（已有记录）"
                    } else {
                        "已处理 $processedCount 条待处理短信"
                    }

                    Toast.makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (skippedCount > 0) {
                    Toast.makeText(
                        applicationContext,
                        "跳过 $skippedCount 条短信（已有记录）",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (pendingMessages.isNotEmpty()) {
                    Toast.makeText(
                        applicationContext,
                        "所有短信均已处理或无需处理",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "处理待处理消息时出错")
                Toast.makeText(
                    applicationContext,
                    "处理消息时出错: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * 建立统计信息消息（增加部分成功状态）
     */
    private fun buildStatsMessage(stats: SMSDatabase.SMSStats): String {
        return "总计: ${stats.total} | 成功: ${stats.success} | 失败: ${stats.failed} | 待处理: ${stats.pending} | 部分成功: ${stats.partialSuccess}"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("短信监控服务")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    /**
     * 处理所有已达到最大重试次数的记录
     */
    private fun handleMaxRetriesRecords() {
        serviceScope.launch {
            try {
                // 查找所有待处理但已达到最大重试次数的推送记录
                val pendingWithMaxRetries = smsDatabase.getPendingRecordsWithMaxRetry()
                if (pendingWithMaxRetries.isNotEmpty()) {
                    Timber.i("发现 ${pendingWithMaxRetries.size} 条已达到最大重试次数的待处理记录")

                    for (record in pendingWithMaxRetries) {
                        if (!isActive) break

                        // 更新为失败状态
                        Timber.w("推送记录 #${record.id} 处于待处理状态但已达到最大重试次数，标记为失败")
                        smsDatabase.updatePushRecordStatus(
                            recordId = record.id,
                            status = SMSDatabase.STATUS_FAILED,
                            errorMessage = "已达到最大重试次数"
                        )
                    }

                    Toast.makeText(
                        applicationContext,
                        "已将 ${pendingWithMaxRetries.size} 条超过重试次数的记录标记为失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "处理最大重试次数记录时出错")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 取消所有协程
        serviceScope.cancel()

        Timber.i("短信处理服务已销毁")
    }
}