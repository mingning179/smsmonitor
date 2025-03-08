package com.nothing.sms.monitor.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.nothing.sms.monitor.push.PushService
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 短信数据库管理类
 * 负责短信记录的存储和管理
 *
 * 注意：数据库连接管理
 * 1. SQLiteOpenHelper已内置了数据库连接池管理，会自动缓存和重用连接
 * 2. 不应手动关闭getReadableDatabase()和getWritableDatabase()获取的数据库对象
 * 3. 每个方法内使用try-finally结构确保正确资源管理，但不主动关闭数据库连接
 * 4. 只需关闭查询返回的Cursor对象
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
        
        // 推送记录表
        const val TABLE_PUSH_RECORDS = "push_records"
        const val COLUMN_RECORD_ID = "id"
        const val COLUMN_SMS_ID = "sms_id"
        const val COLUMN_SERVICE_TYPE = "service_type"
        const val COLUMN_SERVICE_NAME = "service_name"
        const val COLUMN_PUSH_STATUS = "status"
        const val COLUMN_ERROR_MESSAGE = "error_message"
        const val COLUMN_PUSH_TIMESTAMP = "push_timestamp"
        
        // 消息保留天数
        private const val DEFAULT_RETENTION_DAYS = 7L
        
        // 最大重试次数
        private const val MAX_RETRY_COUNT = 3
        
        // 状态常量
        const val STATUS_PENDING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_FAILED = 2
        const val STATUS_PARTIAL_SUCCESS = 3  // 新增状态：部分成功部分失败且已达到最大重试次数
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_SMS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SENDER TEXT NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS INTEGER NOT NULL DEFAULT ${STATUS_PENDING},
                $COLUMN_RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_RETRY INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        // 创建索引以优化查询性能
        db.execSQL("CREATE INDEX idx_status ON $TABLE_SMS ($COLUMN_STATUS)")
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_SMS ($COLUMN_TIMESTAMP)")
        
        // 创建推送记录表
        val createPushRecordsTable = """
            CREATE TABLE $TABLE_PUSH_RECORDS (
                $COLUMN_RECORD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SMS_ID INTEGER,
                $COLUMN_SERVICE_TYPE TEXT,
                $COLUMN_SERVICE_NAME TEXT,
                $COLUMN_PUSH_STATUS INTEGER DEFAULT 0,
                $COLUMN_ERROR_MESSAGE TEXT,
                $COLUMN_PUSH_TIMESTAMP INTEGER,
                $COLUMN_RETRY_COUNT INTEGER DEFAULT 0,
                FOREIGN KEY ($COLUMN_SMS_ID) REFERENCES $TABLE_SMS($COLUMN_ID)
            )
        """.trimIndent()
        
        db.execSQL(createPushRecordsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 简单的升级策略：删除旧表，创建新表
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PUSH_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SMS")
        onCreate(db)
    }
    
    /**
     * 获取需要重试的消息
     */
    fun getPendingMessages(): List<SMS> {
        val messages = mutableListOf<SMS>()
        val db = this.readableDatabase
        
        try {
            val selection = "$COLUMN_STATUS IN (${STATUS_PENDING}, ${STATUS_FAILED}) " +
                          "AND $COLUMN_RETRY_COUNT < $MAX_RETRY_COUNT"
            
            val cursor = db.query(
                TABLE_SMS,
                null,
                selection,
                null,
                null,
                null,
                "$COLUMN_TIMESTAMP ASC"
            )
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
                val retryCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RETRY_COUNT))
                
                messages.add(SMS(id, sender, content, timestamp, status))
                
                // 增加重试次数
                updateRetryCount(id, retryCount + 1)
            }
            
            cursor.close()
            return messages
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 更新重试次数
     */
    fun updateRetryCount(id: Long, retryCount: Int) {
        val db = this.writableDatabase
        
        try {
        val values = ContentValues().apply {
            put(COLUMN_RETRY_COUNT, retryCount)
            put(COLUMN_LAST_RETRY, System.currentTimeMillis())
        }
        
            db.update(
                TABLE_SMS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(id.toString())
            )
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 更新消息状态
     */
    fun updateStatus(id: Long, status: Int) {
        val db = this.writableDatabase
        
        try {
            val values = ContentValues().apply {
                put(COLUMN_STATUS, status)
            }
            
            db.update(
                TABLE_SMS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(id.toString())
            )
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 清理旧记录（默认保留7天）
     */
    fun cleanupOldRecords() {
        val db = this.writableDatabase
        
        try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DEFAULT_RETENTION_DAYS)
            
            db.delete(
            TABLE_SMS,
            "$COLUMN_STATUS = ? AND $COLUMN_TIMESTAMP < ?",
                arrayOf(STATUS_SUCCESS.toString(), cutoffTime.toString())
        )
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 获取短信统计信息
     */
    fun getStats(): SMSStats {
        val stats = SMSStats()
        
        try {
        val query = """
            SELECT 
                COUNT(*) as total,
                    SUM(CASE WHEN $COLUMN_STATUS = ${STATUS_SUCCESS} THEN 1 ELSE 0 END) as success,
                    SUM(CASE WHEN $COLUMN_STATUS = ${STATUS_FAILED} THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN $COLUMN_STATUS = ${STATUS_PENDING} THEN 1 ELSE 0 END) as pending,
                    SUM(CASE WHEN $COLUMN_STATUS = ${STATUS_PARTIAL_SUCCESS} THEN 1 ELSE 0 END) as partial
            FROM $TABLE_SMS
        """.trimIndent()
            
            readableDatabase.rawQuery(query, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    stats.total = cursor.getInt(cursor.getColumnIndexOrThrow("total"))
                    stats.success = cursor.getInt(cursor.getColumnIndexOrThrow("success"))
                    stats.failed = cursor.getInt(cursor.getColumnIndexOrThrow("failed"))
                    stats.pending = cursor.getInt(cursor.getColumnIndexOrThrow("pending"))
                    stats.partialSuccess = cursor.getInt(cursor.getColumnIndexOrThrow("partial"))
                }
            }
            
            return stats
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 短信统计数据类
     */
    data class SMSStats(
        var total: Int = 0,
        var success: Int = 0,
        var failed: Int = 0,
        var pending: Int = 0,
        var partialSuccess: Int = 0
    )
    
    /**
     * 推送记录数据类
     */
    data class PushRecord(
        val id: Long = 0,
        val smsId: Long,
        val serviceType: String,
        val serviceName: String,
        val status: Int,
        val errorMessage: String?,
        val pushTimestamp: Long,
        val retryCount: Int
    )
    
    /**
     * 保存短信
     */
    fun saveSMS(sender: String, content: String, timestamp: Long): Long {
        val db = this.writableDatabase
        
        try {
            val values = ContentValues().apply {
                put(COLUMN_SENDER, sender)
                put(COLUMN_CONTENT, content)
                put(COLUMN_TIMESTAMP, timestamp)
                put(COLUMN_STATUS, STATUS_PENDING)
            }
            
            return db.insert(TABLE_SMS, null, values)
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 添加推送记录
     */
    fun addPushRecord(
        smsId: Long,
        service: PushService,
        status: Int,
        errorMessage: String? = null
    ): Long {
        val db = this.writableDatabase
        val recordId: Long

        try {
            // 首先检查是否已经存在此服务的推送记录
            val existingRecordQuery = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_RECORD_ID),
                "$COLUMN_SMS_ID = ? AND $COLUMN_SERVICE_TYPE = ?",
                arrayOf(smsId.toString(), service.serviceType),
                null, null, null
            )
            
            if (existingRecordQuery.moveToFirst()) {
                recordId = existingRecordQuery.getLong(0)
                Timber.d("已存在推送记录 #${recordId}，更新状态而非新增")
                
                val values = ContentValues().apply {
                    put(COLUMN_PUSH_STATUS, status)
                    if (errorMessage != null) {
                        put(COLUMN_ERROR_MESSAGE, errorMessage)
                    }
                    put(COLUMN_PUSH_TIMESTAMP, System.currentTimeMillis())
                }
                
                db.update(
                    TABLE_PUSH_RECORDS, 
                    values, 
                    "$COLUMN_RECORD_ID = ?", 
                    arrayOf(recordId.toString())
                )
            } else {
                // 不存在记录，则创建新记录
                val values = ContentValues().apply {
                    put(COLUMN_SMS_ID, smsId)
                    put(COLUMN_SERVICE_TYPE, service.serviceType)
                    put(COLUMN_SERVICE_NAME, service.serviceName)
                    put(COLUMN_PUSH_STATUS, status)
                    put(COLUMN_ERROR_MESSAGE, errorMessage)
                    put(COLUMN_PUSH_TIMESTAMP, System.currentTimeMillis())
                    put(COLUMN_RETRY_COUNT, 0)
                }
                
                recordId = db.insert(TABLE_PUSH_RECORDS, null, values)
            }
            
            existingRecordQuery.close()
            
            // 如果所有推送记录都成功，更新短信状态为成功
            updateSMSStatusBasedOnPushRecords(smsId)
            
            return recordId
        } finally {
            // 确保在方法结束时关闭数据库连接
            // db.close() // 不在这里关闭，因为SQLiteOpenHelper管理连接池
        }
    }
    
    /**
     * 获取已存在的推送记录ID
     * 注意：此方法不再使用，由addPushRecord内部逻辑替代
     * 保留此方法是为了兼容性，避免影响其他调用
     */
    private fun getExistingPushRecordId(smsId: Long, serviceType: String): Long {
        val db = this.readableDatabase
        var recordId = -1L
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_RECORD_ID),
                "$COLUMN_SMS_ID = ? AND $COLUMN_SERVICE_TYPE = ?",
                arrayOf(smsId.toString(), serviceType),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                recordId = cursor.getLong(0)
            }
            
            cursor.close()
            return recordId
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 更新推送记录状态
     */
    fun updatePushRecordStatus(
        recordId: Long,
        status: Int,
        errorMessage: String? = null
    ) {
        val db = this.writableDatabase
        
        try {
            val values = ContentValues().apply {
                put(COLUMN_PUSH_STATUS, status)
                if (errorMessage != null) {
                    put(COLUMN_ERROR_MESSAGE, errorMessage)
                }
                put(COLUMN_PUSH_TIMESTAMP, System.currentTimeMillis())
            }
            
            db.update(TABLE_PUSH_RECORDS, values, "$COLUMN_RECORD_ID = ?", arrayOf(recordId.toString()))
            
            // 获取SMS ID并更新状态
            val smsIdQuery = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_SMS_ID),
                "$COLUMN_RECORD_ID = ?",
                arrayOf(recordId.toString()),
                null, null, null
            )
            
            var smsId: Long? = null
            if (smsIdQuery.moveToFirst()) {
                smsId = smsIdQuery.getLong(0)
            }
            smsIdQuery.close()
            
            if (smsId != null) {
                updateSMSStatusBasedOnPushRecords(smsId)
            }
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 根据推送记录状态更新短信状态
     */
    private fun updateSMSStatusBasedOnPushRecords(smsId: Long) {
        val db = this.readableDatabase
        
        try {
            val cursor = db.rawQuery(
                """
                SELECT 
                    COUNT(*) as total,
                    SUM(CASE WHEN $COLUMN_PUSH_STATUS = $STATUS_SUCCESS THEN 1 ELSE 0 END) as success,
                    SUM(CASE WHEN $COLUMN_PUSH_STATUS = $STATUS_FAILED THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN $COLUMN_PUSH_STATUS != $STATUS_SUCCESS AND $COLUMN_RETRY_COUNT >= $MAX_RETRY_COUNT THEN 1 ELSE 0 END) as max_retried
                FROM $TABLE_PUSH_RECORDS
                WHERE $COLUMN_SMS_ID = ?
                """.trimIndent(),
                arrayOf(smsId.toString())
            )
            
            if (cursor.moveToFirst()) {
                val total = cursor.getInt(0)
                val success = cursor.getInt(1)
                val failed = cursor.getInt(2)
                val maxRetried = cursor.getInt(3)
                
                val newStatus = when {
                    total == 0 -> STATUS_PENDING
                    success == total -> STATUS_SUCCESS
                    failed == total -> STATUS_FAILED
                    maxRetried > 0 -> STATUS_PARTIAL_SUCCESS // 部分成功部分已达最大重试次数
                    else -> STATUS_PENDING // 部分成功部分失败，保持pending状态
                }
                
                updateStatus(smsId, newStatus)
            }
            
            cursor.close()
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 获取所有推送记录
     */
    fun getAllPushRecords(): List<PushRecord> {
        val records = mutableListOf<PushRecord>()
        val db = this.readableDatabase
        
        val cursor = db.query(
            TABLE_PUSH_RECORDS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_PUSH_TIMESTAMP DESC"
        )
        
        while (cursor.moveToNext()) {
            records.add(cursorToPushRecord(cursor))
        }
        
        cursor.close()
        db.close()
        return records
    }
    
    /**
     * 获取失败的推送记录
     */
    fun getFailedPushRecords(): List<PushRecord> {
        val records = mutableListOf<PushRecord>()
        val db = this.readableDatabase
        
        val cursor = db.query(
            TABLE_PUSH_RECORDS,
            null,
            "$COLUMN_PUSH_STATUS = ?",
            arrayOf(STATUS_FAILED.toString()),
            null,
            null,
            "$COLUMN_PUSH_TIMESTAMP DESC"
        )
        
        while (cursor.moveToNext()) {
            records.add(cursorToPushRecord(cursor))
        }
        
        cursor.close()
        db.close()
        return records
    }
    
    /**
     * 根据ID获取推送记录
     */
    fun getPushRecordById(recordId: Long): PushRecord? {
        val db = this.readableDatabase
        var record: PushRecord? = null
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                null,
                "$COLUMN_RECORD_ID = ?",
                arrayOf(recordId.toString()),
                null,
                null,
                null
            )
            
            if (cursor.moveToFirst()) {
                record = cursorToPushRecord(cursor)
            }
            
            cursor.close()
            return record
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 根据短信ID获取推送记录
     */
    fun getPushRecordsBySmsId(smsId: Long): List<PushRecord> {
        val records = mutableListOf<PushRecord>()
        val db = this.readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                null,
                "$COLUMN_SMS_ID = ?",
                arrayOf(smsId.toString()),
                null,
                null,
                "$COLUMN_PUSH_TIMESTAMP DESC"
            )
            
            while (cursor.moveToNext()) {
                records.add(cursorToPushRecord(cursor))
            }
            
            cursor.close()
            return records
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 删除推送记录
     */
    fun deletePushRecord(recordId: Long) {
        val db = this.writableDatabase
        
        try {
            db.delete(TABLE_PUSH_RECORDS, "$COLUMN_RECORD_ID = ?", arrayOf(recordId.toString()))
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
    
    /**
     * 将游标转换为推送记录对象
     */
    private fun cursorToPushRecord(cursor: Cursor): PushRecord {
        return PushRecord(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RECORD_ID)),
            smsId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SMS_ID)),
            serviceType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SERVICE_TYPE)),
            serviceName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SERVICE_NAME)),
            status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PUSH_STATUS)),
            errorMessage = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ERROR_MESSAGE)),
            pushTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PUSH_TIMESTAMP)),
            retryCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_RETRY_COUNT))
        )
    }
    
    /**
     * 格式化时间戳为可读字符串
     */
    fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: Long): Int {
        val db = this.writableDatabase
        var retryCount = 0
        
        try {
            // 先获取当前重试次数
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_RETRY_COUNT),
                "$COLUMN_RECORD_ID = ?",
                arrayOf(recordId.toString()),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                retryCount = cursor.getInt(0) + 1
                val values = ContentValues().apply {
                    put(COLUMN_RETRY_COUNT, retryCount)
                }
                db.update(TABLE_PUSH_RECORDS, values, "$COLUMN_RECORD_ID = ?", arrayOf(recordId.toString()))
            }
            
            cursor.close()
            return retryCount
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }

    /**
     * 短信数据类
     */
    data class SMS(
        val id: Long,
        val sender: String,
        val content: String,
        val timestamp: Long,
        val status: Int
    )

    /**
     * 根据ID获取短信
     */
    fun getSMSById(smsId: Long): SMS? {
        val db = this.readableDatabase
        var sms: SMS? = null
        
        try {
            val cursor = db.query(
                TABLE_SMS,
                null,
                "$COLUMN_ID = ?",
                arrayOf(smsId.toString()),
                null, null, null
            )
            
            if (cursor.moveToFirst()) {
                sms = SMS(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    sender = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
                )
            }
            
            cursor.close()
            return sms
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }

    /**
     * 获取待处理但已达到最大重试次数的推送记录
     */
    fun getPendingRecordsWithMaxRetry(maxRetryCount: Int): List<PushRecord> {
        val records = mutableListOf<PushRecord>()
        val db = this.readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                null,
                "$COLUMN_PUSH_STATUS = ? AND $COLUMN_RETRY_COUNT >= ?",
                arrayOf(STATUS_PENDING.toString(), maxRetryCount.toString()),
                null,
                null,
                "$COLUMN_PUSH_TIMESTAMP DESC"
            )
            
            while (cursor.moveToNext()) {
                records.add(cursorToPushRecord(cursor))
            }
            
            cursor.close()
            return records
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }

    /**
     * 获取指定短信的推送记录中已成功的服务类型
     */
    fun getSuccessfulServiceTypes(smsId: Long): Set<String> {
        val db = this.readableDatabase
        val serviceTypes = mutableSetOf<String>()
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_SERVICE_TYPE),
                "$COLUMN_SMS_ID = ? AND $COLUMN_PUSH_STATUS = ?",
                arrayOf(smsId.toString(), STATUS_SUCCESS.toString()),
                null, null, null
            )
            
            while (cursor.moveToNext()) {
                serviceTypes.add(cursor.getString(0))
            }
            
            cursor.close()
            return serviceTypes
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }

    /**
     * 获取需要重试的推送记录
     * 仅返回失败且未达到最大重试次数的记录
     */
    fun getRetryablePushRecords(): List<PushRecord> {
        val records = mutableListOf<PushRecord>()
        val db = this.readableDatabase
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                null,
                "$COLUMN_PUSH_STATUS = ? AND $COLUMN_RETRY_COUNT < ?",
                arrayOf(STATUS_FAILED.toString(), MAX_RETRY_COUNT.toString()),
                null,
                null,
                "$COLUMN_PUSH_TIMESTAMP ASC"
            )
            
            while (cursor.moveToNext()) {
                records.add(cursorToPushRecord(cursor))
            }
            
            cursor.close()
            return records
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }

    /**
     * 获取指定短信的所有推送服务类型（无论状态如何）
     * 用于防止重复创建推送记录
     */
    fun getAllServiceTypesBySmsId(smsId: Long): Set<String> {
        val db = this.readableDatabase
        val serviceTypes = mutableSetOf<String>()
        
        try {
            val cursor = db.query(
                TABLE_PUSH_RECORDS,
                arrayOf(COLUMN_SERVICE_TYPE),
                "$COLUMN_SMS_ID = ?",
                arrayOf(smsId.toString()),
                null, null, null
            )
            
            while (cursor.moveToNext()) {
                serviceTypes.add(cursor.getString(0))
            }
            
            cursor.close()
            return serviceTypes
        } finally {
            // 不在这里关闭数据库连接
            // db.close()
        }
    }
}