package com.nothing.sms.monitor.push

import android.content.Context
import com.nothing.sms.monitor.db.SMSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * API推送服务实现类
 * 负责将短信内容推送到配置的API服务器
 */
class ApiPushService(context: Context) : BasePushService(context) {

    companion object {
        private const val KEY_API_URL = "api_url"
        private const val KEY_ENABLED = "enabled"
        private const val DEFAULT_API_URL = "http://192.168.10.38:8888"
        private const val DEFAULT_TIMEOUT = 30L // 默认超时时间（秒）
        private const val MEDIA_TYPE = "application/json; charset=utf-8"
    }

    private val smsDatabase by lazy { SMSDatabase(context) }
    private val settingsService by lazy { SettingsService.getInstance(context) }

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

    override val serviceName: String = "API服务器"

    override suspend fun pushSMS(
        sender: String,
        content: String,
        timestamp: Long
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
                deviceId = settingsService.getDeviceId()
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
            val stats = smsDatabase.getStats()

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
                hint = "例如: http://192.168.1.100:8080"
            )
        )
    }

    override protected fun applyConfigs(configs: Map<String, String>) {
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
        val deviceId: String       // 设备唯一ID
    ) {
        fun toJson(): String {
            return """
                {
                    "sender": "$sender",
                    "content": "$content",
                    "timestamp": $timestamp,
                    "deviceInfo": "$deviceInfo",
                    "deviceId": "$deviceId"
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
} 