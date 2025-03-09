package com.nothing.sms.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nothing.sms.monitor.service.SMSProcessingService
import timber.log.Timber

/**
 * 开机自启动接收器，用于在设备启动时启动短信处理服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.REBOOT"
        ) {

            Timber.d("系统启动，开始短信服务")

            // 启动主服务
            val serviceIntent = Intent(context, SMSProcessingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}