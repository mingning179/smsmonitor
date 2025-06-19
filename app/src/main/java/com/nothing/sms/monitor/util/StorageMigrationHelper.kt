package com.nothing.sms.monitor.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import timber.log.Timber

/**
 * 存储迁移助手
 * 用于将SharedPreferences数据从凭据加密存储迁移到设备加密存储
 */
object StorageMigrationHelper {
    
    /**
     * 迁移SharedPreferences数据
     * @param context 应用上下文
     * @param prefName SharedPreferences名称
     */
    fun migrateSharedPreferences(context: Context, prefName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Android N以下版本不需要迁移
            return
        }
        
        try {
            // 获取设备加密存储上下文
            val deviceContext = context.createDeviceProtectedStorageContext()
            
            // 检查设备加密存储中是否已有数据
            val devicePrefs = deviceContext.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            if (!devicePrefs.all.isEmpty()) {
                // 已有数据，不需要迁移
                Timber.d("$prefName 已存在于设备加密存储中，跳过迁移")
                return
            }
            
            // 尝试从凭据加密存储读取数据
            val credentialPrefs = try {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                // 无法访问凭据加密存储（用户未解锁），跳过迁移
                Timber.d("无法访问凭据加密存储，跳过迁移: $prefName")
                return
            }
            
            // 获取所有数据
            val allData = credentialPrefs.all
            if (allData.isEmpty()) {
                // 没有需要迁移的数据
                Timber.d("$prefName 在凭据加密存储中没有数据")
                return
            }
            
            // 迁移数据到设备加密存储
            val editor = devicePrefs.edit()
            for ((key, value) in allData) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as Set<String>)
                    }
                }
            }
            editor.apply()
            
            // 清除凭据加密存储中的数据
            credentialPrefs.edit().clear().apply()
            
            Timber.d("成功迁移 $prefName 从凭据加密存储到设备加密存储，共 ${allData.size} 条数据")
        } catch (e: Exception) {
            Timber.e(e, "迁移 $prefName 时发生错误")
        }
    }
    
    /**
     * 批量迁移多个SharedPreferences
     */
    fun migrateAllPreferences(context: Context, prefNames: List<String>) {
        for (prefName in prefNames) {
            migrateSharedPreferences(context, prefName)
        }
    }
} 