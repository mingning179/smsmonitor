package com.nothing.sms.monitor.receiver

import android.content.Context
import com.nothing.sms.monitor.push.SettingsService
import timber.log.Timber

/**
 * 短信过滤器
 * 负责根据配置的关键字过滤短信
 */
class SMSFilter(context: Context) {
    
    private val settingsService = SettingsService.getInstance(context)
    
    /**
     * 判断短信是否符合过滤条件
     * @param content 短信内容
     * @return 是否匹配关键字
     */
    fun matchesFilter(content: String): Boolean {
        if (content.isBlank()) {
            return false
        }
        
        val keywords = settingsService.getKeywords()
        
        // 如果没有配置关键字，默认所有短信都符合条件
        if (keywords.isEmpty()) {
            return true
        }
        
        val matches = keywords.any { keyword ->
            content.contains(keyword, ignoreCase = true)
        }
        
        Timber.d("短信过滤结果 - 内容: $content, 匹配: $matches")
        return matches
    }
    
    /**
     * 检查短信是否来自可信发送者
     * @param sender 发送者号码
     * @return 是否为可信发送者
     */
    fun isTrustedSender(sender: String): Boolean {
        //TODO 可以自行实现
        // 移除所有非数字字符
        val normalizedSender = sender.replace("[^0-9]".toRegex(), "")
        
        // 检查是否为服务号（3-6位数字）
        if (normalizedSender.matches("^\\d{3,6}$".toRegex())) {
            return true
        }
        
        // 检查是否为正常手机号（11位数字）
        if (normalizedSender.matches("^1[3-9]\\d{9}$".toRegex())) {
            return true
        }
        
        return false
    }
} 