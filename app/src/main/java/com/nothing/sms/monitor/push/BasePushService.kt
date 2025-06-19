package com.nothing.sms.monitor.push

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import timber.log.Timber

/**
 * 推送服务抽象基类
 * 提供推送服务的通用功能和方法
 */
abstract class BasePushService(protected val context: Context) : PushService {

    companion object {
        private const val PREF_PUSH_SERVICES = "push_services_config"
        private const val KEY_ENABLED_SUFFIX = "_enabled"
    }

    /**
     * 获取存储配置的SharedPreferences
     */
    protected val prefs: SharedPreferences by lazy {
        // 使用设备加密存储，以便在用户未解锁时也能访问
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        storageContext.getSharedPreferences(PREF_PUSH_SERVICES, Context.MODE_PRIVATE)
    }

    /**
     * 生成配置项的键名
     * 格式：服务类型_配置键
     */
    protected fun configKey(key: String): String = "${serviceType}_$key"

    /**
     * 服务是否启用的键名
     */
    protected val enabledKey: String
        get() = configKey(KEY_ENABLED_SUFFIX)

    /**
     * 服务是否已启用
     */
    override val isEnabled: Boolean
        get() = prefs.getBoolean(enabledKey, false)

    /**
     * 设置服务是否启用
     */
    protected fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(enabledKey, enabled).apply()
    }

    /**
     * 获取字符串配置项
     */
    protected fun getString(key: String, default: String = ""): String {
        return prefs.getString(configKey(key), default) ?: default
    }

    /**
     * 保存字符串配置项
     */
    protected fun saveString(key: String, value: String) {
        prefs.edit().putString(configKey(key), value).apply()
    }

    /**
     * 获取布尔配置项
     */
    protected fun getBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(configKey(key), default)
    }

    /**
     * 保存布尔配置项
     */
    protected fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(configKey(key), value).apply()
    }

    /**
     * 保存配置项
     */
    override fun saveConfigs(configs: Map<String, String>): Boolean {
        try {
            // 处理启用状态
            configs["enabled"]?.let {
                setEnabled(it.toBoolean())
            }

            // 应用配置
            applyConfigs(configs)
            return true
        } catch (e: Exception) {
            Timber.e(e, "保存推送服务配置失败: $serviceType")
            return false
        }
    }

    /**
     * 子类应实现的配置应用方法
     */
    protected abstract fun applyConfigs(configs: Map<String, String>)

    /**
     * 获取设备信息
     */
    protected fun getDeviceInfo(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }

    /**
     * 格式化时间
     */
    protected fun formatTime(timestamp: Long): String {
        val dateFormat =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
} 