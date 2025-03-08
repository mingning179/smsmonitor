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
    
    init {
        // 注册内置的推送服务
        registerBuiltInServices()
    }
    
    /**
     * 注册内置的推送服务
     */
    private fun registerBuiltInServices() {
        // 注册API推送服务（默认启用）
        val apiService = ApiPushService(context)
        // 默认启用API推送
        apiService.saveConfigs(mapOf("enabled" to "true"))
        registerPushService(apiService)
        
        // 注册钉钉推送服务（也默认启用）
        val dingTalkService = DingTalkPushService(context)
        dingTalkService.saveConfigs(mapOf("enabled" to "false"))
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
    
    /**
     * 推送消息到所有已启用的服务
     */
    suspend fun pushToAll(sender: String, content: String, timestamp: Long) {
        // 获取已启用的服务
        val enabledServices = getEnabledServices()
        
        // 没有启用的服务，直接返回
        if (enabledServices.isEmpty()) {
            Timber.d("没有启用的推送服务")
            return
        }
        
        // 向每个启用的服务推送消息
        for (service in enabledServices) {
            try {
                Timber.d("正在推送到 ${service.serviceName}")
                service.pushSMS(sender, content, timestamp)
                    .onSuccess { 
                        Timber.d("推送到 ${service.serviceName} 成功")
                    }
                    .onFailure { e ->
                        Timber.e(e, "推送到 ${service.serviceName} 失败")
                    }
            } catch (e: Exception) {
                Timber.e(e, "推送到 ${service.serviceName} 时出错")
            }
        }
    }
} 