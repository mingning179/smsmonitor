package com.nothing.sms.monitor.model

import com.nothing.sms.monitor.db.SMSConstants
import com.nothing.sms.monitor.db.entity.PushRecordEntity

/**
 * 推送记录数据模型
 * 用于业务层操作，不包含数据库特定的注解
 */
data class PushRecord(
    val id: Long,
    val smsId: Long,
    val serviceType: String,
    val serviceName: String,
    val status: Int,
    val errorMessage: String?,
    val pushTimestamp: Long,
    val retryCount: Int
) {
    /**
     * 计算属性，判断记录是否可以重试
     */
    val canRetry: Boolean
        get() = status == SMSConstants.STATUS_FAILED && retryCount < SMSConstants.MAX_RETRY_COUNT

    /**
     * 转换为数据库实体
     */
    fun toEntity(): PushRecordEntity {
        return PushRecordEntity(
            id = this.id,
            smsId = this.smsId,
            serviceType = this.serviceType,
            serviceName = this.serviceName,
            status = this.status,
            errorMessage = this.errorMessage,
            pushTimestamp = this.pushTimestamp,
            retryCount = this.retryCount
        )
    }
}

/**
 * 扩展函数：将实体转换为模型
 */
fun PushRecordEntity.toModel(): PushRecord {
    return PushRecord(
        id = this.id,
        smsId = this.smsId,
        serviceType = this.serviceType,
        serviceName = this.serviceName,
        status = this.status,
        errorMessage = this.errorMessage,
        pushTimestamp = this.pushTimestamp,
        retryCount = this.retryCount
    )
} 