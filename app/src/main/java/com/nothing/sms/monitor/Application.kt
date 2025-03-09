package com.nothing.sms.monitor

import android.app.Application
import android.content.Intent
import android.os.Build
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService
import com.nothing.sms.monitor.service.SMSProcessingService
import timber.log.Timber

/**
 * 应用程序类，负责初始化应用
 */
class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.d("应用程序初始化")

        // 初始化服务
        initializeServices()

        // 启动主服务
        startSMSService()
    }

    /**
     * 初始化各项服务
     */
    private fun initializeServices() {
        // 初始化设置服务
        SettingsService.getInstance(this)

        // 初始化推送服务管理器
        PushServiceManager.getInstance(this)
    }

    /**
     * 启动短信处理服务
     */
    private fun startSMSService() {
        try {
            val serviceIntent = Intent(this, SMSProcessingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "启动服务失败")
        }
    }
}