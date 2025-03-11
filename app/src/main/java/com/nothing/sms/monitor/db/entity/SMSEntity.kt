package com.nothing.sms.monitor.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nothing.sms.monitor.db.SMSConstants

/**
 * 短信实体类
 * 对应数据库中的sms表
 */
@Entity(tableName = "sms")
data class SMSEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val status: Int = SMSConstants.STATUS_PENDING,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_retry")
    val lastRetry: Long = 0,
    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Int = 0
) 