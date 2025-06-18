package com.nothing.sms.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.nothing.sms.monitor.service.SMSProcessingService
import timber.log.Timber

/**
 * 开机自启动接收器，用于在设备启动时启动短信处理服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val STARTUP_DELAY_MS = 3000L // 延迟3秒启动，等待系统稳定
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.d("收到广播: $action")

        // 检查是否是开机相关的广播
        if (isBootAction(action)) {
            Timber.i("系统启动完成，准备启动短信监控服务")
            
            // 延迟启动服务，等待系统完全启动
            Handler(Looper.getMainLooper()).postDelayed({
                startSMSService(context)
            }, STARTUP_DELAY_MS)
        }
    }

    /**
     * 检查是否是开机相关的广播
     */
    private fun isBootAction(action: String?): Boolean {
        return when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT" -> true
            else -> false
        }
    }

    /**
     * 启动短信监控服务
     */
    private fun startSMSService(context: Context) {
        try {
            val serviceIntent = Intent(context, SMSProcessingService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用前台服务
                context.startForegroundService(serviceIntent)
            } else {
                // Android 8.0以下使用普通服务
                context.startService(serviceIntent)
            }
            
            Timber.i("短信监控服务启动成功")
        } catch (e: Exception) {
            Timber.e(e, "启动短信监控服务失败")
            
            // 如果直接启动失败，尝试通过Activity启动
            try {
                val mainIntent = Intent(context, com.nothing.sms.monitor.MainActivity::class.java)
                mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                mainIntent.putExtra("auto_start_service", true)
                context.startActivity(mainIntent)
                Timber.i("通过MainActivity启动服务")
            } catch (activityException: Exception) {
                Timber.e(activityException, "通过MainActivity启动服务也失败")
            }
        }
    }
}