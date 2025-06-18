package com.nothing.sms.monitor.push

import android.content.Context
import timber.log.Timber

/**
 * 推送服务管理器
 * 负责管理、注册和使用不同的推送服务
 */
class PushServiceManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: PushServiceManager? = null

        fun getInstance(context: Context): PushServiceManager {
            return instance ?: synchronized(this) {
                instance ?: PushServiceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 已注册的推送服务列表
    private val pushServices = mutableMapOf<String, PushService>()

    // 常用服务的引用
    private var _apiPushService: ApiPushService? = null
    private var _settingsService: SettingsService? = null

    // 公开的API获取服务实例
    val apiPushService: ApiPushService
        get() = _apiPushService ?: (getService("api") as? ApiPushService)
            ?.also { _apiPushService = it }
        ?: throw IllegalStateException("ApiPushService未注册")

    val settingsService: SettingsService
        get() = _settingsService ?: SettingsService.getInstance(context)
            .also { _settingsService = it }

    init {
        // 注册内置的推送服务
        registerBuiltInServices()
    }

    /**
     * 注册内置的推送服务
     */
    private fun registerBuiltInServices() {
        // 预先创建SettingsService实例
        _settingsService = SettingsService.getInstance(context)

        // 注册API推送服务
        val apiService = ApiPushService(context, _settingsService!!)
        registerPushService(apiService)
        // 保存实例引用
        _apiPushService = apiService

        // 注册钉钉推送服务（也默认启用）
        val dingTalkService = DingTalkPushService(context, _settingsService!!)
        registerPushService(dingTalkService)

        // 这里可以注册更多内置的推送服务
        // registerPushService(TelegramPushService(context))
        // registerPushService(WeChatPushService(context))
    }

    /**
     * 注册推送服务
     */
    fun registerPushService(service: PushService) {
        pushServices[service.serviceType] = service
        Timber.d("已注册推送服务: ${service.serviceName} (${service.serviceType})")
    }

    /**
     * 获取已注册的所有推送服务
     */
    fun getAllServices(): List<PushService> {
        return pushServices.values.toList()
    }

    /**
     * 获取已启用的推送服务
     */
    fun getEnabledServices(): List<PushService> {
        return pushServices.values.filter { it.isEnabled }
    }

    /**
     * 获取指定类型的推送服务
     */
    fun getService(serviceType: String): PushService? {
        return pushServices[serviceType]
    }
}