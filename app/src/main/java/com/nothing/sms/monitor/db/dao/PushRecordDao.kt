package com.nothing.sms.monitor.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nothing.sms.monitor.db.SMSConstants
import com.nothing.sms.monitor.db.entity.PushRecordEntity

/**
 * 推送记录数据访问对象
 * 定义对推送记录表的操作方法
 */
@Dao
interface PushRecordDao {
    /**
     * 插入推送记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pushRecordEntity: PushRecordEntity): Long

    /**
     * 更新推送记录
     */
    @Update
    fun update(pushRecordEntity: PushRecordEntity)

    /**
     * 检查记录是否已存在
     * 根据短信ID和服务类型检查
     */
    @Query("SELECT COUNT(*) FROM push_records WHERE sms_id = :smsId AND service_type = :serviceType")
    fun recordExists(smsId: Long, serviceType: String): Int

    /**
     * 增加重试次数
     */
    @Query("UPDATE push_records SET retry_count = retry_count + 1 WHERE id = :id")
    fun incrementRetryCount(id: Long): Int

    /**
     * 获取重试次数
     */
    @Query("SELECT retry_count FROM push_records WHERE id = :id")
    fun getRetryCount(id: Long): Int?

    /**
     * 更新推送记录状态
     */
    @Query("UPDATE push_records SET status = :status, error_message = :errorMessage, push_timestamp = :timestamp WHERE id = :id")
    fun updateStatus(
        id: Long,
        status: Int,
        errorMessage: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /**
     * 根据ID获取推送记录
     */
    @Query("SELECT * FROM push_records WHERE id = :id")
    fun getById(id: Long): PushRecordEntity?

    /**
     * 根据短信ID获取推送记录
     */
    @Query("SELECT * FROM push_records WHERE sms_id = :smsId ORDER BY push_timestamp DESC")
    fun getBySmsId(smsId: Long): List<PushRecordEntity>

    /**
     * 获取所有推送记录
     */
    @Query("SELECT * FROM push_records ORDER BY push_timestamp DESC")
    fun getAllRecords(): List<PushRecordEntity>

    /**
     * 根据状态获取推送记录
     */
    @Query("SELECT * FROM push_records WHERE status = :status ORDER BY push_timestamp DESC")
    fun getRecordsByStatus(status: Int = SMSConstants.STATUS_FAILED): List<PushRecordEntity>

    /**
     * 获取需要重试的推送记录
     * 状态为失败且重试次数小于最大重试次数
     */
    @Query(
        """
        SELECT * FROM push_records 
        WHERE status = :status 
        AND retry_count < :maxRetryCount
        ORDER BY push_timestamp ASC
    """
    )
    fun getRetryableRecords(
        status: Int = SMSConstants.STATUS_FAILED,
        maxRetryCount: Int = SMSConstants.MAX_RETRY_COUNT
    ): List<PushRecordEntity>

    /**
     * 获取成功的服务类型
     */
    @Query(
        """
        SELECT service_type FROM push_records 
        WHERE sms_id = :smsId 
        AND status = :status
    """
    )
    fun getSuccessfulServiceTypes(
        smsId: Long,
        status: Int = SMSConstants.STATUS_SUCCESS
    ): List<String>

    /**
     * 获取指定短信的所有推送服务类型（无论状态）
     */
    @Query("SELECT service_type FROM push_records WHERE sms_id = :smsId")
    fun getAllServiceTypesBySmsId(smsId: Long): List<String>

    /**
     * 获取待处理但已达到最大重试次数的推送记录
     */
    @Query(
        """
        SELECT * FROM push_records 
        WHERE status = :status 
        AND retry_count >= :maxRetryCount
    """
    )
    fun getPendingRecordsWithMaxRetry(
        status: Int = SMSConstants.STATUS_PENDING,
        maxRetryCount: Int = SMSConstants.MAX_RETRY_COUNT
    ): List<PushRecordEntity>
} 