package com.nothing.sms.monitor.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nothing.sms.monitor.db.SMSConstants
import com.nothing.sms.monitor.db.entity.SMSEntity
import com.nothing.sms.monitor.model.SMSStats

/**
 * 短信数据访问对象
 * 定义对短信表的操作方法
 */
@Dao
interface SMSDao {
    /**
     * 插入短信
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(smsEntity: SMSEntity): Long

    /**
     * 更新短信
     */
    @Update
    fun update(smsEntity: SMSEntity)

    /**
     * 更新短信状态
     */
    @Query("UPDATE sms SET status = :status WHERE _id = :id")
    fun updateStatus(id: Long, status: Int)

    /**
     * 更新重试次数
     */
    @Query("UPDATE sms SET retry_count = :retryCount WHERE _id = :id")
    fun updateRetryCount(id: Long, retryCount: Int): Int

    /**
     * 根据ID获取短信
     */
    @Query("SELECT * FROM sms WHERE _id = :id")
    fun getById(id: Long): SMSEntity?

    /**
     * 获取需要重试的消息
     */
    @Query(
        """
        SELECT * FROM sms 
        WHERE status IN (${SMSConstants.STATUS_PENDING}, ${SMSConstants.STATUS_FAILED}) 
        AND retry_count < ${SMSConstants.MAX_RETRY_COUNT}
        ORDER BY timestamp DESC
    """
    )
    fun getPendingMessages(): List<SMSEntity>

    /**
     * 清理旧记录（默认保留7天）
     */
    @Query("DELETE FROM sms WHERE status = :status AND timestamp < :cutoffTime")
    fun cleanupOldRecords(status: Int = SMSConstants.STATUS_SUCCESS, cutoffTime: Long)

    /**
     * 获取短信统计信息
     */
    @Query(
        """
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN status = ${SMSConstants.STATUS_SUCCESS} THEN 1 ELSE 0 END) as success,
            SUM(CASE WHEN status = ${SMSConstants.STATUS_FAILED} THEN 1 ELSE 0 END) as failed,
            SUM(CASE WHEN status = ${SMSConstants.STATUS_PENDING} THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = ${SMSConstants.STATUS_PARTIAL_SUCCESS} THEN 1 ELSE 0 END) as partialSuccess
        FROM sms
    """
    )
    fun getStats(): SMSStats

    /**
     * 查询分页的短信记录
     */
    @Query("SELECT * FROM sms ORDER BY timestamp DESC")
    fun getAll(): List<SMSEntity>
} 