package com.nothing.sms.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.nothing.sms.monitor.service.SMSProcessingService
import timber.log.Timber

/**
 * 短信接收器
 * 负责接收和预处理短信
 */
class SMSReceiver : BroadcastReceiver() {
    companion object {
        // 常量定义
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_SUBSCRIPTION_ID = 0
        private const val SUBSCRIPTION_EXTRA = "subscription"
    }



    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) {
                Timber.w("收到空短信")
                return
            }

            // 获取发送者和时间戳
            val firstMessage = messages[0]
            val sender = firstMessage.originatingAddress ?: "未知"
            val timestamp = firstMessage.timestampMillis

            // 获取订阅ID信息
            val subscriptionId = try {
                getSubscriptionIdFromIntent(intent)
            } catch (e: Exception) {
                Timber.e(e, "获取订阅ID信息失败，使用默认值")
                DEFAULT_SUBSCRIPTION_ID
            }

            // 合并短信内容（处理长短信）
            val fullMessageBody = messages.joinToString("") { it.messageBody ?: "" }

            // 过滤短信
            val smsFilter = SMSFilter(context)

            // 检查发送者是否可信
            if (!smsFilter.isTrustedSender(sender)) {
                Timber.d("忽略不可信发送者的短信: $sender")
                return
            }

            // 检查是否匹配关键字
            if (!smsFilter.matchesFilter(fullMessageBody)) {
                Timber.d("短信内容不匹配关键字")
                return
            }

            Timber.i("收到符合条件的短信 - 发送者: $sender")

            // 发送到服务处理
            val serviceIntent = Intent(context, SMSProcessingService::class.java).apply {
                putExtra("sender", sender)
                putExtra("body", fullMessageBody)
                putExtra("timestamp", timestamp)
                putExtra("subscriptionId", subscriptionId)  // 添加订阅ID信息
            }

            // 在 Android 8.0 及以上版本，需要使用 startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            Timber.e(e, "处理短信时出错")
        }
    }

    /**
     * 从Intent中获取订阅ID
     * 不同Android版本实现方式可能不同
     */
    private fun getSubscriptionIdFromIntent(intent: Intent): Int {
        // 尝试从Intent的Extra数据中获取Subscription ID
        val subscriptionId = intent.getIntExtra(SUBSCRIPTION_EXTRA, INVALID_SUBSCRIPTION_ID)
        return if (subscriptionId != INVALID_SUBSCRIPTION_ID) {
            subscriptionId
        } else DEFAULT_SUBSCRIPTION_ID
    }
}