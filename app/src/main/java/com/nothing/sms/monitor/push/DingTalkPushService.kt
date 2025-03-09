package com.nothing.sms.monitor.push

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 钉钉推送服务实现类
 */
class DingTalkPushService(context: Context) : BasePushService(context) {

    companion object {
        // 配置项键名
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_SECRET = "secret"
        private const val KEY_MESSAGE_TEMPLATE = "message_template"

        // 默认消息模板
        private const val DEFAULT_MESSAGE_TEMPLATE = """
        【短信监控】
        
        发送者: {sender}
        时间: {time}
        内容: {content}
        
        设备信息: {device}
        设备ID: {device_id}
        """
    }

    // OkHttpClient实例
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val settingsService by lazy { SettingsService.getInstance(context) }

    init {
        // 确保消息模板存在
        if (getString(KEY_MESSAGE_TEMPLATE).isBlank()) {
            saveString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE)
        }
    }

    override val serviceType: String = "dingtalk"

    override val serviceName: String = "钉钉机器人"

    override suspend fun pushSMS(
        sender: String,
        content: String,
        timestamp: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 检查是否启用
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("钉钉推送未启用"))
            }

            val webhookUrl = getString(KEY_WEBHOOK_URL)
            val secret = getString(KEY_SECRET)

            if (webhookUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("钉钉Webhook URL未配置"))
            }

            // 获取最终URL（可能需要签名）
            val finalUrl = if (secret.isNotBlank()) {
                signUrl(webhookUrl, secret)
            } else {
                webhookUrl
            }

            // 生成消息内容
            val messageTemplate = getString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE)
            val message = formatMessage(messageTemplate, sender, content, timestamp)

            // 构建请求体
            val jsonBody = """
                {
                    "msgtype": "text",
                    "text": {
                        "content": ${message.toJson()}
                    }
                }
            """.trimIndent()

            val requestBody =
                jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            // 构建请求
            val request = Request.Builder()
                .url(finalUrl)
                .post(requestBody)
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.contains("\"errcode\":0")) {
                    Timber.d("钉钉推送成功: $responseBody")
                    Result.success(true)
                } else {
                    val error = "钉钉推送失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "钉钉推送出错")
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val webhookUrl = getString(KEY_WEBHOOK_URL)
            val secret = getString(KEY_SECRET)

            if (webhookUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("钉钉Webhook URL未配置"))
            }

            // 获取最终URL
            val finalUrl = if (secret.isNotBlank()) {
                signUrl(webhookUrl, secret)
            } else {
                webhookUrl
            }

            // 测试消息内容
            val jsonBody = """
                {
                    "msgtype": "text",
                    "text": {
                        "content": "【测试消息】短信监控服务连接测试\n设备: ${getDeviceInfo()}\n设备ID: ${settingsService.getDeviceId()}"
                    }
                }
            """.trimIndent()

            val requestBody =
                jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            // 构建请求
            val request = Request.Builder()
                .url(finalUrl)
                .post(requestBody)
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.contains("\"errcode\":0")) {
                    Timber.d("钉钉测试连接成功: $responseBody")
                    Result.success(true)
                } else {
                    val error = "钉钉测试连接失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "钉钉测试连接出错")
            Result.failure(e)
        }
    }

    override fun getConfigItems(): List<PushService.ConfigItem> {
        return listOf(
            PushService.ConfigItem(
                key = "enabled",
                label = "启用钉钉推送",
                value = isEnabled.toString(),
                type = PushService.ConfigType.BOOLEAN
            ),
            PushService.ConfigItem(
                key = KEY_WEBHOOK_URL,
                label = "钉钉Webhook URL",
                value = getString(KEY_WEBHOOK_URL),
                hint = "例如: https://oapi.dingtalk.com/robot/send?access_token=xxx"
            ),
            PushService.ConfigItem(
                key = KEY_SECRET,
                label = "钉钉安全密钥",
                value = getString(KEY_SECRET),
                hint = "可选，用于签名"
            ),
            PushService.ConfigItem(
                key = KEY_MESSAGE_TEMPLATE,
                label = "消息模板",
                value = getString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE),
                type = PushService.ConfigType.TEXTAREA,
                hint = "支持的变量: {sender}, {time}, {content}, {device}, {device_id}"
            )
        )
    }

    /**
     * 实现父类的抽象方法，用于保存配置
     */
    override protected fun applyConfigs(configs: Map<String, String>) {
        configs[KEY_WEBHOOK_URL]?.let { saveString(KEY_WEBHOOK_URL, it) }
        configs[KEY_SECRET]?.let { saveString(KEY_SECRET, it) }
        configs[KEY_MESSAGE_TEMPLATE]?.let {
            if (it.isBlank()) {
                saveString(KEY_MESSAGE_TEMPLATE, DEFAULT_MESSAGE_TEMPLATE)
            } else {
                saveString(KEY_MESSAGE_TEMPLATE, it)
            }
        }
    }

    /**
     * 为URL添加签名
     */
    private fun signUrl(url: String, secret: String): String {
        val timestamp = System.currentTimeMillis()
        val stringToSign = "$timestamp\n$secret"
        val sign = generateHmacSha256(stringToSign, secret)
        val urlEncodedSign = java.net.URLEncoder.encode(sign, "UTF-8")

        return if (url.contains("?")) {
            "$url&timestamp=$timestamp&sign=$urlEncodedSign"
        } else {
            "$url?timestamp=$timestamp&sign=$urlEncodedSign"
        }
    }

    /**
     * 生成HMAC-SHA256签名
     */
    private fun generateHmacSha256(data: String, key: String): String {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val dataBytes = data.toByteArray(Charsets.UTF_8)

        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256")
        mac.init(secretKeySpec)

        val hash = mac.doFinal(dataBytes)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * 格式化消息，替换变量
     */
    private fun formatMessage(
        template: String,
        sender: String,
        content: String,
        timestamp: Long
    ): String {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

        return template
            .replace("{sender}", sender)
            .replace("{time}", time)
            .replace("{content}", content)
            .replace("{device}", getDeviceInfo())
            .replace("{device_id}", settingsService.getDeviceId())
    }

    /**
     * 将字符串转换为JSON格式（处理转义字符）
     */
    private fun String.toJson(): String {
        return "\"" + this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}