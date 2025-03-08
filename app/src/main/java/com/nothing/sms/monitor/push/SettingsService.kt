package com.nothing.sms.monitor.push

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap

/**
 * 通用设置服务类
 * 用于管理应用程序通用配置
 */
class SettingsService private constructor(context: Context) {
    
    companion object {
        private const val PREF_NAME = "settings_service_prefs"
        private const val KEY_STATUS_REPORT_INTERVAL = "status_report_interval"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_DEVICE_ID = "device_id"
        private const val DEFAULT_STATUS_REPORT_INTERVAL = 5L
        
        private val instances = ConcurrentHashMap<String, SettingsService>()
        
        /**
         * 获取SettingsService实例
         */
        fun getInstance(context: Context): SettingsService {
            val appContext = context.applicationContext
            return instances.computeIfAbsent(PREF_NAME) { 
                SettingsService(appContext) 
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    
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
} 