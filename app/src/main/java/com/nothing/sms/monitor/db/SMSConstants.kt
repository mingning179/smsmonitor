package com.nothing.sms.monitor.db

/**
 * 短信和推送记录相关常量
 * 从RoomSMSDatabase中分离出来以减少耦合
 */
interface SMSConstants {
    companion object {
        // 数据库名称
        const val DATABASE_NAME = "sms_database"

        // 数据留存天数
        const val DEFAULT_RETENTION_DAYS = 7L

        // 最大重试次数
        const val MAX_RETRY_COUNT = 3

        // 状态常量
        const val STATUS_PENDING = 0
        const val STATUS_SUCCESS = 1
        const val STATUS_FAILED = 2
        const val STATUS_PARTIAL_SUCCESS = 3

        /**
         * 获取清理截止时间
         * @param retentionDays 数据保留天数
         * @return 清理截止时间戳
         */
        fun getCleanupCutoffTime(retentionDays: Long = DEFAULT_RETENTION_DAYS): Long {
            return System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000)
        }
    }
} 