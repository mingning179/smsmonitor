package com.nothing.sms.monitor.push

import android.content.Context
import com.nothing.sms.monitor.db.SMSRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * API推送服务实现类
 * 负责将短信内容推送到配置的API服务器
 */
class ApiPushService(
    context: Context,
    private val settingsService: SettingsService = SettingsService.getInstance(context)
) : BasePushService(context), BindingService {

    companion object {
        const val KEY_API_URL = "api_url"
        const val KEY_ENABLED = "enabled"
        const val DEFAULT_API_URL = "http://localhost:8080/api"
        const val DEFAULT_TIMEOUT = 30L // 默认超时时间（秒）
        const val MEDIA_TYPE = "application/json; charset=utf-8"
    }

    private val smsRepository by lazy { SMSRepository(context) }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)  // 启用连接失败重试
            .build()
    }

    init {
        // 确保服务默认启用
        if (!getBoolean(KEY_ENABLED, false)) {
            setEnabled(true)
        }
    }

    override val serviceType: String = "api"

    override val serviceName: String = "API推送"

    override suspend fun pushSMS(
        sender: String,
        content: String,
        timestamp: Long,
        subscriptionId: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 检查服务是否启用
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("API推送未启用"))
            }

            val apiUrl = "${getString(KEY_API_URL, DEFAULT_API_URL)}/report-sms"

            if (apiUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API URL未配置"))
            }

            // 准备数据
            val deviceInfo = getDeviceInfo()
            val smsData = SMSData(
                sender = sender,
                content = content,
                timestamp = timestamp,
                deviceInfo = deviceInfo,
                deviceId = settingsService.getDeviceId(),
                subscriptionId = subscriptionId
            )

            // 构建请求体
            val jsonBody = smsData.toJson()
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE.toMediaType())

            // 构建请求
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .header("Content-Type", MEDIA_TYPE)
                .header("User-Agent", "SMSMonitor/${getAppVersion()}")
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Timber.d("API推送成功: $responseBody")

                    // 同时也上报当前状态
                    reportStatus()

                    Result.success(true)
                } else {
                    val error = "API推送失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "API推送出错")
            Result.failure(e)
        }
    }

    /**
     * 上报状态到服务器
     */
    suspend fun reportStatus(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("API推送未启用"))
            }

            val apiUrl = "${getString(KEY_API_URL, DEFAULT_API_URL)}/report-status"

            if (apiUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API URL未配置"))
            }

            // 获取统计数据
            val stats = smsRepository.getStats()

            // 构建状态数据
            val statusData = StatusData(
                deviceId = settingsService.getDeviceId(),
                totalSMS = stats.total,
                successSMS = stats.success,
                failedSMS = stats.failed,
                pendingSMS = stats.pending,
                timestamp = System.currentTimeMillis(),
                deviceInfo = getDeviceInfo()
            )

            // 构建请求体
            val jsonBody = statusData.toJson()
            val requestBody = jsonBody.toRequestBody(MEDIA_TYPE.toMediaType())

            // 构建请求
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .header("Content-Type", MEDIA_TYPE)
                .header("User-Agent", "SMSMonitor/${getAppVersion()}")
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Timber.d("状态上报成功: $responseBody")
                    Result.success(true)
                } else {
                    val error = "状态上报失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "状态上报出错")
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = getString(KEY_API_URL, DEFAULT_API_URL)

            if (apiUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API URL未配置"))
            }

            // 简单的连接测试
            val request = Request.Builder()
                .url(apiUrl)
                .build()

            client.newCall(request).execute().use {
                // 只要能连接上就返回成功，不管服务器返回什么状态码
                Result.success(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "API连接测试失败")
            Result.failure(e)
        }
    }

    override fun getConfigItems(): List<PushService.ConfigItem> {
        return listOf(
            PushService.ConfigItem(
                key = "enabled",
                label = "启用API推送",
                value = isEnabled.toString(),
                type = PushService.ConfigType.BOOLEAN
            ),
            PushService.ConfigItem(
                key = KEY_API_URL,
                label = "API服务器地址",
                value = getString(KEY_API_URL, DEFAULT_API_URL),
                hint = "例如: $DEFAULT_API_URL"
            )
        )
    }

    override fun applyConfigs(configs: Map<String, String>) {
        configs[KEY_API_URL]?.let { saveString(KEY_API_URL, it) }
    }

    /**
     * 获取应用版本号
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            Timber.w(e, "获取应用版本信息失败")
            "unknown"
        }
    }

    /**
     * 短信数据类
     */
    data class SMSData(
        val sender: String,        // 发送者
        val content: String,       // 短信内容
        val timestamp: Long,       // 接收时间
        val deviceInfo: String,    // 设备信息
        val deviceId: String,      // 设备唯一ID
        val subscriptionId: Int     // 订阅ID
    ) {
        fun toJson(): String {
            return """
                {
                    "sender": "$sender",
                    "content": "$content",
                    "timestamp": $timestamp,
                    "deviceInfo": "$deviceInfo",
                    "deviceId": "$deviceId",
                    "subscriptionId": $subscriptionId
                }
            """.trimIndent()
        }
    }

    /**
     * 状态数据类
     */
    data class StatusData(
        val deviceId: String,      // 设备唯一ID
        val totalSMS: Int,         // 总短信数
        val successSMS: Int,       // 成功发送数
        val failedSMS: Int,        // 发送失败数
        val pendingSMS: Int,       // 待处理数
        val timestamp: Long,       // 上报时间
        val deviceInfo: String     // 设备信息
    ) {
        fun toJson(): String {
            return """
                {
                    "deviceId": "$deviceId",
                    "totalSMS": $totalSMS,
                    "successSMS": $successSMS,
                    "failedSMS": $failedSMS,
                    "pendingSMS": $pendingSMS,
                    "timestamp": $timestamp,
                    "deviceInfo": "$deviceInfo"
                }
            """.trimIndent()
        }
    }

    /**
     * 发送验证码
     */
    override suspend fun sendVerificationCode(
        phoneNumber: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 检查服务是否启用
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("API推送未启用"))
            }

            val apiUrl = "${getString(KEY_API_URL, DEFAULT_API_URL)}/send-code"

            // 准备请求数据
            val requestData = JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("deviceId", settingsService.getDeviceId())
                put("deviceInfo", getDeviceInfo())
            }

            // 构建请求
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestData.toString().toRequestBody(MEDIA_TYPE.toMediaType()))
                .header("Content-Type", MEDIA_TYPE)
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    if (success) {
                        Timber.d("验证码发送成功")
                        Result.success(true)
                    } else {
                        val message = jsonResponse.optString("message", "未知错误")
                        Timber.e("验证码发送失败: $message")
                        Result.failure(Exception(message))
                    }
                } else {
                    val error = "验证码发送失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "发送验证码时出错")
            Result.failure(e)
        }
    }

    /**
     * 验证并绑定
     */
    override suspend fun verifyAndBind(
        phoneNumber: String,
        code: String,
        subscriptionId: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 检查服务是否启用
            if (!isEnabled) {
                return@withContext Result.failure(IllegalStateException("API推送未启用"))
            }

            val apiUrl = "${getString(KEY_API_URL, DEFAULT_API_URL)}/verify-bind"

            // 准备请求数据
            val requestData = JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("code", code)
                put("subscriptionId", subscriptionId)
                put("deviceId", settingsService.getDeviceId())
                put("deviceInfo", getDeviceInfo())
            }

            // 构建请求
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestData.toString().toRequestBody(MEDIA_TYPE.toMediaType()))
                .header("Content-Type", MEDIA_TYPE)
                .build()

            // 发送请求
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    if (success) {
                        // 验证成功，保存绑定信息
                        settingsService.saveBinding(
                            SettingsService.BindingInfo(
                                phoneNumber = phoneNumber,
                                deviceId = settingsService.getDeviceId(),
                                subscriptionId = subscriptionId,
                                bindTime = System.currentTimeMillis(),
                                lastVerifyTime = System.currentTimeMillis(),
                                verifyCount = 0
                            )
                        )
                        Timber.d("手机号绑定成功，确认的订阅ID: $subscriptionId")
                        Result.success(true)
                    } else {
                        val message = jsonResponse.optString("message", "未知错误")
                        Timber.e("手机号绑定失败: $message")
                        Result.failure(Exception(message))
                    }
                } else {
                    val error = "手机号绑定失败: ${response.code}, $responseBody"
                    Timber.e(error)
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "验证绑定时出错")
            Result.failure(e)
        }
    }
} 