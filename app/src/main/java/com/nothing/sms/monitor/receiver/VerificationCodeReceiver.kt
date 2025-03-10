package com.nothing.sms.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.regex.Pattern

/**
 * 验证码接收器
 * 用于自动识别短信中的验证码
 */
class VerificationCodeReceiver : BroadcastReceiver() {

    companion object {
        // 验证码识别正则表达式
        private val CODE_PATTERN = Pattern.compile("(\\d{4,6})")

        // 最后接收到的验证码
        private val _lastReceivedCode = MutableStateFlow<VerificationCodeData?>(null)
        val lastReceivedCode = _lastReceivedCode.asStateFlow()

        // 清除最后的验证码
        fun clearLastCode() {
            _lastReceivedCode.value = null
        }

        // 常量定义
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_SUBSCRIPTION_ID = 0
        private const val SUBSCRIPTION_EXTRA = "subscription"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            // 解析短信内容
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return

            // 拼接完整短信内容
            val smsContent = messages.joinToString("") { it.messageBody }
            val sender = messages[0].originatingAddress ?: ""

            // 获取订阅ID信息
            val subscriptionId = try {
                getSubscriptionIdFromIntent(intent)
            } catch (e: Exception) {
                Timber.e(e, "获取订阅ID信息失败，使用默认值")
                DEFAULT_SUBSCRIPTION_ID
            }

            Timber.d("收到短信，内容: $smsContent, 发送者: $sender, 订阅ID: $subscriptionId")

            // 尝试从短信中提取验证码
            extractVerificationCode(smsContent)?.let { code ->
                Timber.d("识别到验证码: $code")
                _lastReceivedCode.value = VerificationCodeData(
                    code = code,
                    subscriptionId = subscriptionId,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "解析短信失败")
        }
    }

    /**
     * 从短信内容中提取验证码
     */
    private fun extractVerificationCode(content: String): String? {
        val matcher = CODE_PATTERN.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
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

/**
 * 验证码数据
 */
data class VerificationCodeData(
    val code: String,       // 验证码
    val subscriptionId: Int, // 订阅ID
    val timestamp: Long     // 接收时间戳
) 