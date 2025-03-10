package com.nothing.sms.monitor.push

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * 通用设置服务类
 * 用于管理应用程序通用配置
 */
class SettingsService private constructor(private val appContext: Context) {

    companion object {
        private const val PREF_NAME = "settings_service_prefs"
        private const val KEY_STATUS_REPORT_INTERVAL = "status_report_interval"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_DEVICE_ID = "device_id"
        private const val DEFAULT_STATUS_REPORT_INTERVAL = 5L

        // 绑定信息相关常量
        private const val KEY_BINDINGS = "bindings"
        private const val MAX_BINDINGS_PER_DEVICE = 4  // 每个设备最大绑定数量

        /**
         * 获取SettingsService实例
         * 注意：应只通过PushServiceManager获取，避免静态Context引用
         */
        fun getInstance(context: Context): SettingsService {
            return SettingsService(context.applicationContext)
        }
    }

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 绑定信息数据类
     */
    data class BindingInfo(
        val phoneNumber: String,      // 绑定手机号
        val deviceId: String,         // 设备唯一标识
        val subscriptionId: Int,      // SIM卡订阅ID
        val bindTime: Long,           // 绑定时间
        val lastVerifyTime: Long,     // 最后验证时间
        val verifyCount: Int          // 验证次数，用于限制频率
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("phoneNumber", phoneNumber)
                put("deviceId", deviceId)
                put("subscriptionId", subscriptionId)
                put("bindTime", bindTime)
                put("lastVerifyTime", lastVerifyTime)
                put("verifyCount", verifyCount)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): BindingInfo {
                return BindingInfo(
                    phoneNumber = json.getString("phoneNumber"),
                    deviceId = json.getString("deviceId"),
                    subscriptionId = json.getInt("subscriptionId"),
                    bindTime = json.getLong("bindTime"),
                    lastVerifyTime = json.getLong("lastVerifyTime"),
                    verifyCount = json.getInt("verifyCount")
                )
            }
        }
    }

    /**
     * 保存绑定信息
     */
    fun saveBinding(bindingInfo: BindingInfo) {
        val bindings = getBindings().toMutableList()

        // 检查是否已达到最大绑定数量
        if (bindings.size >= MAX_BINDINGS_PER_DEVICE && !bindings.any { it.subscriptionId == bindingInfo.subscriptionId }) {
            throw IllegalStateException("已达到最大绑定数量限制: $MAX_BINDINGS_PER_DEVICE")
        }

        // 更新或添加绑定信息
        val index = bindings.indexOfFirst { it.subscriptionId == bindingInfo.subscriptionId }
        if (index != -1) {
            bindings[index] = bindingInfo
        } else {
            bindings.add(bindingInfo)
        }

        // 保存所有绑定信息
        saveBindings(bindings)
        Timber.d("已保存绑定信息: $bindingInfo")
    }

    /**
     * 获取所有绑定信息
     */
    fun getBindings(): List<BindingInfo> {
        val bindingsJson = prefs.getString(KEY_BINDINGS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(bindingsJson)
            List(jsonArray.length()) { i ->
                BindingInfo.fromJson(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            Timber.e(e, "解析绑定信息失败")
            emptyList()
        }
    }

    /**
     * 获取指定订阅ID的绑定信息
     */
    fun getBindingBySubscriptionId(subscriptionId: Int): BindingInfo? {
        return getBindings().find { it.subscriptionId == subscriptionId }
    }

    /**
     * 移除指定订阅ID的绑定信息
     */
    fun removeBindingBySubscriptionId(subscriptionId: Int) {
        val bindings = getBindings().filterNot { it.subscriptionId == subscriptionId }
        saveBindings(bindings)
        Timber.d("已移除订阅ID $subscriptionId 的绑定信息")
    }

    /**
     * 保存所有绑定信息
     */
    private fun saveBindings(bindings: List<BindingInfo>) {
        val jsonArray = JSONArray()
        bindings.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString(KEY_BINDINGS, jsonArray.toString()).apply()
    }

    /**
     * 获取设备ID
     * 如果ID不存在，基于系统唯一标识自动生成
     */
    fun getDeviceId(): String {
        val id = prefs.getString(KEY_DEVICE_ID, "")
        if (id.isNullOrBlank()) {
            // 基于系统唯一标识生成设备ID
            val systemId = getSystemUniqueId()
            val deviceId = "SMS_$systemId"
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            return deviceId
        }
        return id
    }

    /**
     * 获取系统唯一标识符
     */
    private fun getSystemUniqueId(): String {
        // 获取Android ID作为基础
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)

        // 如果Android ID无效或为null，使用备用标识符
        if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") { // 9774d56d682e549c是模拟器常见ID
            // 创建设备特征指纹，结合可用的系统信息
            val deviceInfo = StringBuilder()
                .append(android.os.Build.BOARD)
                .append(android.os.Build.BRAND)
                .append(android.os.Build.DEVICE)
                .append(android.os.Build.HARDWARE)
                .append(android.os.Build.MANUFACTURER)
                .append(android.os.Build.MODEL)
                .append(android.os.Build.PRODUCT)
                .toString()

            // 对设备信息进行哈希处理
            return deviceInfo.hashCode().toString().replace("-", "")
        }

        return androidId
    }

    /**
     * 获取状态上报间隔（分钟）
     */
    fun getStatusReportInterval(): Long {
        return prefs.getLong(KEY_STATUS_REPORT_INTERVAL, DEFAULT_STATUS_REPORT_INTERVAL)
    }

    /**
     * 保存状态上报间隔（分钟）
     */
    fun saveStatusReportInterval(interval: Long) {
        prefs.edit().putLong(KEY_STATUS_REPORT_INTERVAL, interval).apply()
    }

    /**
     * 获取关键字列表
     */
    fun getKeywords(): List<String> {
        val keywordsString = prefs.getString(KEY_KEYWORDS, "") ?: ""
        return if (keywordsString.isBlank()) {
            emptyList()
        } else {
            keywordsString.split(",").map { it.trim() }
        }
    }

    /**
     * 保存关键字列表
     */
    fun saveKeywords(keywords: List<String>) {
        val keywordsString = keywords.joinToString(",")
        prefs.edit().putString(KEY_KEYWORDS, keywordsString).apply()
    }

    /**
     * 添加关键字
     */
    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return

        val currentKeywords = getKeywords().toMutableList()
        if (!currentKeywords.contains(keyword)) {
            currentKeywords.add(keyword)
            saveKeywords(currentKeywords)
        }
    }

    /**
     * 删除关键字
     */
    fun removeKeyword(keyword: String) {
        val currentKeywords = getKeywords().toMutableList()
        if (currentKeywords.remove(keyword)) {
            saveKeywords(currentKeywords)
        }
    }
}