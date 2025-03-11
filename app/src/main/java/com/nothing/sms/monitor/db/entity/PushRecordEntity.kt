package com.nothing.sms.monitor.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nothing.sms.monitor.db.SMSConstants

/**
 * 推送记录实体类
 * 对应数据库中的push_records表
 */
@Entity(
    tableName = "push_records",
    foreignKeys = [
        ForeignKey(
            entity = SMSEntity::class,
            parentColumns = ["_id"],
            childColumns = ["sms_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sms_id"),
        Index("service_type"),
        Index("status")
    ]
)
data class PushRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sms_id")
    val smsId: Long,
    @ColumnInfo(name = "service_type")
    val serviceType: String,
    @ColumnInfo(name = "service_name")
    val serviceName: String,
    val status: Int = SMSConstants.STATUS_PENDING,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "push_timestamp")
    val pushTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
) {
    // 计算属性，判断记录是否可以重试
    val canRetry: Boolean
        get() = status == SMSConstants.STATUS_FAILED && retryCount < SMSConstants.MAX_RETRY_COUNT
} 