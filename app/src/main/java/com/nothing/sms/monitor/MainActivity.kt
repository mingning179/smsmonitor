package com.nothing.sms.monitor

import android.Manifest
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

        checkAndRequestPermissions()

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
        Timber.d("检测到服务不在运行，尝试重启服务")
        startSMSService()
    }

}