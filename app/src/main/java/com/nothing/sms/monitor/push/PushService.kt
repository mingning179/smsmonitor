package com.nothing.sms.monitor.push

/**
 * 推送服务接口
 * 定义不同推送服务的通用方法
 */
interface PushService {
    /**
     * 服务类型标识
     */
    val serviceType: String
    
    /**
     * 服务名称（显示用）
     */
    val serviceName: String
    
    /**
     * 服务是否已启用
     */
    val isEnabled: Boolean
    
    /**
     * 推送短信内容
     * 
     * @param sender 发送者
     * @param content 短信内容
     * @param timestamp 接收时间戳
     * @return 推送结果
     */
    suspend fun pushSMS(sender: String, content: String, timestamp: Long): Result<Boolean>
    
    /**
     * 测试推送服务连接
     * @return 测试结果
     */
    suspend fun testConnection(): Result<Boolean>
    
    /**
     * 获取配置项列表（用于UI显示）
     * @return 配置项列表
     */
    fun getConfigItems(): List<ConfigItem>
    
    /**
     * 保存配置项
     * @param configs 配置项键值对
     */
    fun saveConfigs(configs: Map<String, String>): Boolean
    
    /**
     * 配置项类
     * 用于UI展示和配置管理
     */
    data class ConfigItem(
        val key: String,                // 配置键
        val label: String,              // 显示名称
        val value: String,              // 当前值
        val type: ConfigType = ConfigType.TEXT,  // 配置类型
        val isRequired: Boolean = true, // 是否必填
        val hint: String = ""           // 输入提示
    )
    
    /**
     * 配置项类型
     */
    enum class ConfigType {
        TEXT,       // 文本输入
        PASSWORD,   // 密码输入（隐藏）
        BOOLEAN,    // 布尔值（开关）
        TEXTAREA    // 多行文本
    }
} 