package com.nothing.sms.monitor.db

// 添加SMSConstants导入
import android.content.Context
import com.nothing.sms.monitor.db.dao.PushRecordDao
import com.nothing.sms.monitor.db.dao.SMSDao
import com.nothing.sms.monitor.db.entity.PushRecordEntity
import com.nothing.sms.monitor.db.entity.SMSEntity
import com.nothing.sms.monitor.model.PushRecord
import com.nothing.sms.monitor.model.SMS
import com.nothing.sms.monitor.model.SMSStats
import com.nothing.sms.monitor.model.toModel
import com.nothing.sms.monitor.push.PushService
import timber.log.Timber

/**
 * 短信数据存储库
 * 作为应用程序和数据库之间的中间层
 * 
 * 注意：为解决"Cannot access database on the main thread"错误，
 * 我们在RoomSMSDatabase.getDatabase()方法中添加了.allowMainThreadQueries()，
 * 这允许在主线程上执行数据库操作。
 * 
 * 如果将来需要恢复使用协程而不是在主线程上查询，请：
 * 1. 移除RoomSMSDatabase中的allowMainThreadQueries()调用
 * 2. 在每个数据库操作方法中使用withContext(Dispatchers.IO) { ... }
 * 3. 将方法标记为suspend，例如：
 *    suspend fun saveSMS(...): Long = withContext(Dispatchers.IO) {
 *        smsDao.insert(...)
 *    }
 */
class SMSRepository(context: Context) {
    // 数据库实例和DAO
    private val database = RoomSMSDatabase.getDatabase(context)
    private val smsDao: SMSDao = database.smsDao()
    private val pushRecordDao: PushRecordDao = database.pushRecordDao()

    /**
     * 保存短信
     */
    fun saveSMS(sender: String, content: String, timestamp: Long, subscriptionId: Int): Long {
        val smsEntity = SMSEntity(
            sender = sender,
            content = content,
            timestamp = timestamp,
            subscriptionId = subscriptionId
        )
        return smsDao.insert(smsEntity)
    }

    /**
     * 获取需要重试的消息
     */
    fun getPendingMessages(): List<SMS> {
        val entities = smsDao.getPendingMessages()
        return entities.map { entity ->
            // 增加重试次数
            smsDao.updateRetryCount(entity.id, entity.retryCount + 1)
            entity.toModel()
        }
    }

    /**
     * 更新消息状态
     */
    fun updateStatus(id: Long, status: Int) {
        smsDao.updateStatus(id, status)
    }

    /**
     * 清理旧记录
     */
    fun cleanupOldRecords() {
        val cutoffTime = SMSConstants.getCleanupCutoffTime()
        smsDao.cleanupOldRecords(cutoffTime = cutoffTime)
    }

    /**
     * 获取短信统计信息
     */
    fun getStats(): SMSStats {
        return smsDao.getStats()
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
        // 检查是否已经存在该服务的记录
        val exists = pushRecordDao.recordExists(smsId, service.serviceType)

        if (exists > 0) {
            // 已存在记录，获取ID并更新状态
            val records = pushRecordDao.getBySmsId(smsId)
            val recordForService = records.firstOrNull { it.serviceType == service.serviceType }

            if (recordForService != null) {
                Timber.d("已存在推送记录 #${recordForService.id}，更新状态而非新增")

                pushRecordDao.updateStatus(
                    id = recordForService.id,
                    status = status,
                    errorMessage = errorMessage
                )

                // 更新短信状态
                updateSMSStatusBasedOnPushRecords(smsId)

                return recordForService.id
            }
        }

        // 创建新记录
        val pushRecord = PushRecordEntity(
            smsId = smsId,
            serviceType = service.serviceType,
            serviceName = service.serviceName,
            status = status,
            errorMessage = errorMessage
        )

        val recordId = pushRecordDao.insert(pushRecord)

        // 更新短信状态
        updateSMSStatusBasedOnPushRecords(smsId)

        return recordId
    }

    /**
     * 更新推送记录状态
     */
    fun updatePushRecordStatus(recordId: Long, status: Int, errorMessage: String? = null) {
        pushRecordDao.updateStatus(recordId, status, errorMessage)

        // 获取SMS ID并更新状态
        val record = pushRecordDao.getById(recordId)
        if (record != null) {
            updateSMSStatusBasedOnPushRecords(record.smsId)
        }
    }

    /**
     * 根据推送记录状态更新短信状态
     */
    private fun updateSMSStatusBasedOnPushRecords(smsId: Long) {
        val records = pushRecordDao.getBySmsId(smsId)

        if (records.isEmpty()) {
            updateStatus(smsId, SMSConstants.STATUS_PENDING)
            return
        }

        val total = records.size
        val success = records.count { it.status == SMSConstants.STATUS_SUCCESS }
        val failed = records.count { it.status == SMSConstants.STATUS_FAILED }
        val maxRetried = records.count {
            it.status != SMSConstants.STATUS_SUCCESS &&
                    it.retryCount >= SMSConstants.MAX_RETRY_COUNT
        }

        val newStatus = when {
            success == total -> SMSConstants.STATUS_SUCCESS
            failed == total -> SMSConstants.STATUS_FAILED
            maxRetried > 0 -> SMSConstants.STATUS_PARTIAL_SUCCESS // 部分成功部分已达最大重试次数
            else -> SMSConstants.STATUS_PENDING // 部分成功部分失败，保持pending状态
        }

        updateStatus(smsId, newStatus)
    }

    /**
     * 获取所有推送记录
     */
    fun getAllPushRecords(): List<PushRecord> {
        val records = pushRecordDao.getAllRecords()
        return records.map { it.toModel() }
    }

    /**
     * 获取失败的推送记录
     */
    fun getFailedPushRecords(): List<PushRecord> {
        val entities = pushRecordDao.getRecordsByStatus(SMSConstants.STATUS_FAILED)
        return entities.map { it.toModel() }
    }

    /**
     * 根据ID获取推送记录
     */
    fun getPushRecordById(recordId: Long): PushRecord? {
        val entity = pushRecordDao.getById(recordId)
        return entity?.toModel()
    }

    /**
     * 根据短信ID获取推送记录
     */
    fun getPushRecordsBySmsId(smsId: Long): List<PushRecord> {
        val entities = pushRecordDao.getBySmsId(smsId)
        return entities.map { it.toModel() }
    }

    /**
     * 增加重试次数
     */
    fun incrementRetryCount(recordId: Long): Int {
        pushRecordDao.incrementRetryCount(recordId)
        return pushRecordDao.getRetryCount(recordId) ?: 0
    }

    /**
     * 根据ID获取短信
     */
    fun getSMSById(smsId: Long): SMS? {
        val entity = smsDao.getById(smsId)
        return entity?.toModel()
    }

    /**
     * 获取待处理但已达到最大重试次数的推送记录
     */
    fun getPendingRecordsWithMaxRetry(): List<PushRecord> {
        val entities = pushRecordDao.getPendingRecordsWithMaxRetry()
        return entities.map { it.toModel() }
    }

    /**
     * 获取指定短信的推送记录中已成功的服务类型
     */
    fun getSuccessfulServiceTypes(smsId: Long): Set<String> {
        val types = pushRecordDao.getSuccessfulServiceTypes(smsId)
        return types.toSet()
    }

    /**
     * 获取需要重试的推送记录
     */
    fun getRetryablePushRecords(): List<PushRecord> {
        val entities = pushRecordDao.getRetryableRecords()
        return entities.map { it.toModel() }
    }

    /**
     * 获取指定短信的所有推送服务类型
     */
    fun getAllServiceTypesBySmsId(smsId: Long): Set<String> {
        val types = pushRecordDao.getAllServiceTypesBySmsId(smsId)
        return types.toSet()
    }
} 