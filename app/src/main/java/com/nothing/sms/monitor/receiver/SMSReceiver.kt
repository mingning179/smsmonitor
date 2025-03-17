package com.nothing.sms.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.regex.Pattern

/**
 * 短信接收器
 * 负责接收和预处理短信，并提取验证码
 */
class SMSReceiver : BroadcastReceiver() {
    companion object {
        // 常量定义
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_SUBSCRIPTION_ID = 0
        private const val SUBSCRIPTION_EXTRA = "subscription"
        
        // 验证码识别正则表达式
        private val CODE_PATTERN = Pattern.compile("验证码[：:](\\d{4,8})")

        // 公司名称列表
        private val COMPANY_NAMES = listOf(
            "乐尔凌人工智能科技",
            "财小桃",
            "用于验证手机号",
        )

        // 最后接收到的验证码
        private val _lastReceivedCode = MutableStateFlow<VerificationCodeData?>(null)
        val lastReceivedCode = _lastReceivedCode.asStateFlow()

        // 清除最后的验证码
        fun clearLastCode() {
            _lastReceivedCode.value = null
        }
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

            // 注意: 这个部分这里是为了绑定手机号的时候自动获取验证码
            extractVerificationCode(fullMessageBody)?.let { code ->
                Timber.d("识别到验证码: $code")
                _lastReceivedCode.value = VerificationCodeData(
                    code = code,
                    subscriptionId = subscriptionId,
                    timestamp = System.currentTimeMillis()
                )
            }

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
     * 从短信内容中提取验证码
     */
    private fun extractVerificationCode(content: String): String? {
        val matcher = CODE_PATTERN.matcher(content)
        return if (matcher.find()) {
            val code = matcher.group(1)
            // 验证：确保短信中包含任一公司名称或用途说明
            if (COMPANY_NAMES.any { companyName -> content.contains(companyName) }) {
                code
            } else null
        } else null
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