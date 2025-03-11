package com.nothing.sms.monitor.model

import com.nothing.sms.monitor.db.entity.SMSEntity

/**
 * 短信数据模型
 * 用于业务层操作，不包含数据库特定的注解
 */
data class SMS(
    val id: Long,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val status: Int,
    val subscriptionId: Int = 0
) {
    /**
     * 转换为数据库实体
     */
    fun toEntity(): SMSEntity {
        return SMSEntity(
            id = this.id,
            sender = this.sender,
            content = this.content,
            timestamp = this.timestamp,
            status = this.status,
            subscriptionId = this.subscriptionId
        )
    }
}

/**
 * 扩展函数：将实体转换为模型
 */
fun SMSEntity.toModel(): SMS {
    return SMS(
        id = this.id,
        sender = this.sender,
        content = this.content,
        timestamp = this.timestamp,
        status = this.status,
        subscriptionId = this.subscriptionId
    )
} 