package com.nothing.sms.monitor.receiver

import android.content.Context
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService

/**
 * 短信过滤器
 * 负责根据配置的关键字过滤短信
 */
class SMSFilter(context: Context) {

    private val pushServiceManager = PushServiceManager.getInstance(context)
    private val settingsService = pushServiceManager.settingsService

    /**
     * 判断短信是否符合过滤条件
     * @param content 短信内容
     * @return 是否匹配关键字
     */
    fun matchesFilter(content: String): Boolean {
        if (content.isBlank()) {
            return false
        }

        // 根据监控模式判断
        return when (settingsService.getMonitorMode()) {
            SettingsService.MONITOR_MODE_ALL -> true  // 监控所有短信模式
            SettingsService.MONITOR_MODE_KEYWORDS -> {
                val keywords = settingsService.getKeywords()
                // 如果没有配置关键字，使用默认关键字列表
                if (keywords.isEmpty()) {
                    settingsService.resetToDefaultKeywords()
                    val defaultKeywords = settingsService.getKeywords()
                    defaultKeywords.any { keyword ->
                        content.contains(keyword, ignoreCase = true)
                    }
                } else {
                    keywords.any { keyword ->
                        content.contains(keyword, ignoreCase = true)
                    }
                }
            }
            else -> false  // 未知模式，默认不匹配
        }
    }

    /**
     * 检查短信是否来自可信发送者
     * @param sender 发送者号码
     * @return 是否为可信发送者
     */
    fun isTrustedSender(sender: String): Boolean {
        //TODO 可以自行实现
        return sender.isNotBlank()
    }
} 