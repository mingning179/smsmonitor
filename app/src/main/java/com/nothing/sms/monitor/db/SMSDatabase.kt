package com.nothing.sms.monitor.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nothing.sms.monitor.service.SMSProcessingService
import java.util.concurrent.TimeUnit

/**
 * 短信数据库管理类
 * 负责短信记录的存储和管理
 */
class SMSDatabase(context: Context) : SQLiteOpenHelper(
    context, 
    DATABASE_NAME,
    null, 
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "sms_monitor.db"
        const val DATABASE_VERSION = 1
        
        // 表名和列名定义
        const val TABLE_SMS = "sms"
        const val COLUMN_ID = "_id"
        const val COLUMN_SENDER = "sender"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_STATUS = "status"
        const val COLUMN_RETRY_COUNT = "retry_count"
        const val COLUMN_LAST_RETRY = "last_retry"
        
        // 消息保留天数
        private const val DEFAULT_RETENTION_DAYS = 7L
        
        // 最大重试次数
        private const val MAX_RETRY_COUNT = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_SMS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SENDER TEXT NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS INTEGER NOT NULL DEFAULT ${SMSProcessingService.STATUS_PENDING},
                $COLUMN_RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_RETRY INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        // 创建索引以优化查询性能
        db.execSQL("CREATE INDEX idx_status ON $TABLE_SMS ($COLUMN_STATUS)")
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_SMS ($COLUMN_TIMESTAMP)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 简单的升级策略：删除旧表，创建新表
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SMS")
        onCreate(db)
    }
    
    /**
     * 插入新短信记录
     */
    fun insertSMS(sender: String, content: String, timestamp: Long): Long {
        val values = ContentValues().apply {
            put(COLUMN_SENDER, sender)
            put(COLUMN_CONTENT, content)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_STATUS, SMSProcessingService.STATUS_PENDING)
            put(COLUMN_RETRY_COUNT, 0)
            put(COLUMN_LAST_RETRY, 0)
        }
        
        return writableDatabase.insert(TABLE_SMS, null, values)
    }
    
    /**
     * 获取需要重试的消息
     */
    fun getPendingMessages(): List<SMSData> {
        val messages = mutableListOf<SMSData>()
        val selection = "$COLUMN_STATUS IN (${SMSProcessingService.STATUS_PENDING}, ${SMSProcessingService.STATUS_FAILED}) " +
                        "AND $COLUMN_RETRY_COUNT < $MAX_RETRY_COUNT"
        
        readableDatabase.query(
            TABLE_SMS,
            null,
            selection,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val retryCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RETRY_COUNT))
                
                messages.add(SMSData(id, sender, content, timestamp, retryCount))
                
                // 增加重试次数
                updateRetryCount(id, retryCount + 1)
            }
        }
        
        return messages
    }
    
    /**
     * 更新重试次数
     */
    fun updateRetryCount(id: Long, retryCount: Int) {
        val values = ContentValues().apply {
            put(COLUMN_RETRY_COUNT, retryCount)
            put(COLUMN_LAST_RETRY, System.currentTimeMillis())
        }
        
        writableDatabase.update(
            TABLE_SMS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }
    
    /**
     * 更新消息状态
     */
    fun updateStatus(id: Long, status: Int) {
        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
        }
        
        writableDatabase.update(
            TABLE_SMS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }
    
    /**
     * 清理旧记录（默认保留7天）
     */
    fun cleanupOldRecords() {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS)
        
        writableDatabase.delete(
            TABLE_SMS,
            "$COLUMN_STATUS = ? AND $COLUMN_TIMESTAMP < ?",
            arrayOf(SMSProcessingService.STATUS_SUCCESS.toString(), cutoffTime.toString())
        )
    }
    
    /**
     * 获取短信统计信息
     */
    fun getStats(): SMSStats {
        val stats = SMSStats()
        
        val query = """
            SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN $COLUMN_STATUS = ${SMSProcessingService.STATUS_SUCCESS} THEN 1 ELSE 0 END) as success,
            SUM(CASE WHEN $COLUMN_STATUS = ${SMSProcessingService.STATUS_FAILED} THEN 1 ELSE 0 END) as failed,
            SUM(CASE WHEN $COLUMN_STATUS IN (${SMSProcessingService.STATUS_PENDING}) THEN 1 ELSE 0 END) as pending
            FROM $TABLE_SMS
        """.trimIndent()
        
        readableDatabase.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) {
                stats.total = cursor.getInt(cursor.getColumnIndexOrThrow("total"))
                stats.success = cursor.getInt(cursor.getColumnIndexOrThrow("success"))
                stats.failed = cursor.getInt(cursor.getColumnIndexOrThrow("failed"))
                stats.pending = cursor.getInt(cursor.getColumnIndexOrThrow("pending"))
            }
        }
        
        return stats
    }
    
    /**
     * 短信数据类，用于传递短信信息
     */
    data class SMSData(
        val id: Long,
        val sender: String,
        val content: String,
        val timestamp: Long,
        val retryCount: Int
    )
    
    /**
     * 短信统计数据类
     */
    data class SMSStats(
        var total: Int = 0,
        var success: Int = 0,
        var failed: Int = 0,
        var pending: Int = 0
    )
}