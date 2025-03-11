package com.nothing.sms.monitor.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nothing.sms.monitor.db.dao.PushRecordDao
import com.nothing.sms.monitor.db.dao.SMSDao
import com.nothing.sms.monitor.db.entity.PushRecordEntity
import com.nothing.sms.monitor.db.entity.SMSEntity

/**
 * Room数据库类
 * 定义数据库架构和提供DAO接口
 */
@Database(
    entities = [SMSEntity::class, PushRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RoomSMSDatabase : RoomDatabase() {
    /**
     * 短信DAO
     */
    abstract fun smsDao(): SMSDao

    /**
     * 推送记录DAO
     */
    abstract fun pushRecordDao(): PushRecordDao

    companion object {
        // 单例模式实例
        @Volatile
        private var INSTANCE: RoomSMSDatabase? = null

        /**
         * 获取数据库实例
         */
        fun getDatabase(context: Context): RoomSMSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoomSMSDatabase::class.java,
                    SMSConstants.DATABASE_NAME
                )
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 