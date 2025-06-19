package com.nothing.sms.monitor

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nothing.sms.monitor.service.SMSProcessingService
import com.nothing.sms.monitor.ui.navigation.AppNavHost
import com.nothing.sms.monitor.ui.theme.SMSMonitorTheme
import com.nothing.sms.monitor.util.StorageMigrationHelper
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        Timber.d("权限请求结果: $permissions, 全部授权: $allGranted")

        if (!allGranted) {
            // 检查哪些权限被拒绝了
            if (permissions.entries.any { !it.value }) {
                // 找到被拒绝的权限
                val deniedPermissions = permissions.filter { !it.value }.keys

                // 如果任何权限被永久拒绝（用户选择了"不再询问"），提示用户去设置页面手动开启
                val anyPermanentlyDenied = deniedPermissions.any {
                    !shouldShowRequestPermissionRationale(it)
                }
                if (anyPermanentlyDenied) {
                    // 提示用户去设置页面手动开启
                    Toast.makeText(
                        this,
                        "请手动开启权限，否则无法使用本应用",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 执行数据迁移（仅在用户解锁后才能访问MainActivity）
        performDataMigration()

        checkAndRequestPermissions()
        
        // 检查是否是从开机启动调用
        val autoStartService = intent.getBooleanExtra("auto_start_service", false)
        if (autoStartService) {
            Timber.i("从开机启动调用，启动短信监控服务")
            startSMSService()
            // 从开机启动时，可以选择直接关闭Activity或继续显示UI
            // 这里选择继续显示UI，用户可以根据需要修改
        }

        setContent {
            SMSMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }

    /**
     * 执行数据迁移
     * 将SharedPreferences从凭据加密存储迁移到设备加密存储
     */
    private fun performDataMigration() {
        // 仅在Android N及以上版本执行迁移
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Timber.d("开始执行数据迁移")
                // 迁移所有相关的SharedPreferences
                val prefsToMigrate = listOf(
                    "settings_service_prefs",  // SettingsService使用的
                    "push_services_config"     // BasePushService使用的
                )
                StorageMigrationHelper.migrateAllPreferences(this, prefsToMigrate)
                Timber.d("数据迁移完成")
            } catch (e: Exception) {
                Timber.e(e, "数据迁移失败")
            }
        }
    }

    /**
     * 检查并请求必要权限
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 接收短信权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }

        // 通知权限（Android 13及以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
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
            Timber.d("短信监控服务已启动")
        } catch (e: Exception) {
            Timber.e(e, "启动短信监控服务失败")
            Toast.makeText(this, "启动服务失败，请手动重启应用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()

        // 应用切换回来时，检查服务是否还在运行，如果不在则重启
        if (!isServiceRunning()) {
            Timber.d("检测到服务不在运行，尝试重启服务")
            startSMSService()
        } else {
            Timber.d("短信监控服务正在运行")
        }
    }

    /**
     * 检查短信处理服务是否正在运行
     */
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val services = manager.getRunningServices(Integer.MAX_VALUE)
            services.any { 
                it.service.className == SMSProcessingService::class.java.name 
            }
        } catch (e: Exception) {
            Timber.w(e, "检查服务运行状态失败")
            false // 如果检查失败，假设服务未运行，尝试重启
        }
    }

}