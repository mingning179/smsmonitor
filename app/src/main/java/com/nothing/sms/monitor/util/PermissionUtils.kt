package com.nothing.sms.monitor.util

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 权限工具类
 * 提供检查和请求各种权限的方法
 */
object PermissionUtils {

    /**
     * 检查是否有接收短信权限
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否有发送通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        // Android 13 (API 33) 及以上需要单独申请通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        // 低版本Android可以通过NotificationManagerCompat检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.areNotificationsEnabled()
        }

        // 低版本Android默认有通知权限
        return true
    }

    /**
     * 检查是否已忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 检查是否满足基本的权限要求
     * 注意：这里不再考虑自启动权限，因为它是不可知的
     */
    fun hasEssentialPermissions(context: Context): Boolean {
        return hasSmsPermission(context) &&
                hasNotificationPermission(context) &&
                isIgnoringBatteryOptimizations(context)
    }

    /**
     * 申请短信权限，处理各种权限状态
     */
    fun requestSmsPermission(
        activity: Activity,
        permissionLauncher: ActivityResultLauncher<String>
    ) {
        if (!hasSmsPermission(activity)) {
            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
                // 用户曾经拒绝过，再次请求权限
                permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            } else {
                // 首次请求或用户选择了"不再询问"
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.RECEIVE_SMS
                    ) == PackageManager.PERMISSION_DENIED
                ) {
                    // 已被拒绝且选择了"不再询问"，跳转到设置页面
                    Toast.makeText(activity, "请在设置中手动开启短信权限", Toast.LENGTH_LONG).show()
                    openAppSettings(activity)
                } else {
                    // 首次请求
                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
            }
        }
    }

    /**
     * 申请通知权限，处理各种权限状态和Android版本差异
     */
    fun requestNotificationPermission(
        activity: Activity,
        permissionLauncher: ActivityResultLauncher<String>
    ) {
        if (!hasNotificationPermission(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+可以通过权限请求系统
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // 首次请求或用户选择了"不再询问"
                    if (ContextCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_DENIED
                    ) {
                        // 已被拒绝且选择了"不再询问"，跳转到设置页面
                        Toast.makeText(activity, "请在设置中手动开启通知权限", Toast.LENGTH_LONG)
                            .show()
                        openAppSettings(activity)
                    } else {
                        // 首次请求
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            } else {
                // 低版本Android需要通过应用设置
                openNotificationSettings(activity)
            }
        }
    }

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "请求忽略电池优化失败")
            // 如果专用Intent失败，尝试打开常规电池优化设置
            openBatteryOptimizationSettings(activity)
        }
    }

    /**
     * 打开电池优化设置
     */
    fun openBatteryOptimizationSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开电池优化设置失败")
        }
    }

    /**
     * 打开应用详情设置
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "打开应用详情设置失败")
        }
    }

    /**
     * 专门针对华为设备打开自启动权限设置
     * 华为设备系统版本差异较大，需要尝试多种方案
     */
    private fun openHuaweiAutoStartSettings(activity: Activity) {
        try {
            // 方案1: 直接使用EMUI 11+或HarmonyOS常用的路径
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.mainscreen.MainScreenActivity"
            )
            activity.startActivity(intent)
            Toast.makeText(
                activity,
                "请在系统管理器中找到并点击「应用启动管理」",
                Toast.LENGTH_LONG
            ).show()
            return
        } catch (e: Exception) {
            Timber.e(e, "打开华为自启动设置失败")
            openAppManagerSettings(activity)
        }
    }

    /**
     * 打开自启动设置（针对各厂商机型）
     * 注意：不同厂商的路径不同，可能不全面
     */
    fun openAutoStartSettings(activity: Activity) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        try {
            // 华为设备需要特殊处理
            if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                openHuaweiAutoStartSettings(activity)
                return
            }
            Timber.w("无法找到自启动设置页面，跳转到应用管理设置: $manufacturer")
            // 如果无法找到特定路径，跳转到应用管理设置
            openAppManagerSettings(activity)
        } catch (e: Exception) {
            Timber.e(e, "打开自启动设置失败: $manufacturer")
            // 如果失败，打开应用管理设置
            openAppManagerSettings(activity)
        }
    }

    /**
     * 打开应用管理设置
     * 这个界面通常允许用户搜索应用
     */
    fun openAppManagerSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS)
            activity.startActivity(intent)
            // 提示用户搜索应用
            Toast.makeText(
                activity,
                "请在应用列表中搜索: ${activity.packageManager.getApplicationLabel(activity.applicationInfo)}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Timber.e(e, "打开应用管理设置失败")
            // 如果连应用管理设置都无法打开，退回到应用详情
            openAppSettings(activity)
        }
    }

    /**
     * 判断当前设备是否需要额外的后台权限设置
     * 主要是针对国产手机的定制系统
     */
    fun needsExtraBackgroundPermission(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("huawei") ||
                manufacturer.contains("honor") ||
                manufacturer.contains("oppo") ||
                manufacturer.contains("vivo") ||
                manufacturer.contains("samsung") ||
                manufacturer.contains("lenovo") ||
                manufacturer.contains("oneplus") ||
                manufacturer.contains("meizu")
    }

    /**
     * 打开通知设置
     */
    fun openNotificationSettings(activity: Activity) {
        try {
            val intent = when {
                // Android 8.0 及以上可以直接跳转到应用通知设置
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    }
                }
                // 低版本 Android 使用应用详情
                else -> {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                }
            }

            activity.startActivity(intent)
            // 提示用户在设置中寻找通知选项
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(
                    activity,
                    "请在应用设置中找到\"通知\"选项并开启",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "打开通知设置失败")
            // 如果专用Intent失败，尝试打开应用详情设置
            openAppSettings(activity)
        }
    }
} 