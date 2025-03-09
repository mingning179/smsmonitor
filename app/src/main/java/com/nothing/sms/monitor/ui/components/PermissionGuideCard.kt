package com.nothing.sms.monitor.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nothing.sms.monitor.util.PermissionUtils

/**
 * 权限状态枚举
 */
enum class PermissionStatus {
    GRANTED,     // 已授权
    DENIED,      // 未授权
    UNKNOWN      // 不可知状态（用于自启动等无法确定的权限）
}

/**
 * 权限引导卡片组件
 * 用于检查和申请应用所需权限
 */
@Composable
fun PermissionGuideCard(
    onAllPermissionsGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // 跟踪各种权限状态 - 仅使用工具类检查
    var hasSmsPermission by remember { mutableStateOf(PermissionUtils.hasSmsPermission(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionUtils.hasNotificationPermission(context))
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context))
    }

    // 确定是否需要额外的后台权限设置（如自启动权限）
    val needsExtraBackgroundPermission = remember {
        PermissionUtils.needsExtraBackgroundPermission()
    }

    // 决定是否显示卡片 始终显示
    val shouldShowCard = true

    // 检查是否所有权限都已获取
    LaunchedEffect(hasSmsPermission, hasNotificationPermission, isIgnoringBatteryOptimizations) {
        if (PermissionUtils.hasEssentialPermissions(context)) {
            onAllPermissionsGranted()
        }
    }

    // 短信权限请求启动器
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    // 通知权限请求启动器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // 刷新权限状态
    fun refreshPermissionStatus() {
        hasSmsPermission = PermissionUtils.hasSmsPermission(context)
        hasNotificationPermission = PermissionUtils.hasNotificationPermission(context)
        isIgnoringBatteryOptimizations = PermissionUtils.isIgnoringBatteryOptimizations(context)
    }

    // 每次组件重新组合时刷新权限状态
    LaunchedEffect(Unit) {
        refreshPermissionStatus()
    }

    // 获取当前生命周期所有者
    val lifecycleOwner = LocalLifecycleOwner.current

    // 当应用从后台返回前台时自动刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // 卡片UI部分
    AnimatedVisibility(
        visible = shouldShowCard,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        CommonCard(
            title = "应用权限设置",
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "要使短信监控功能正常工作，请确保以下权限和设置已开启：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 短信权限
            PermissionItem(
                title = "短信权限",
                description = "允许应用接收和处理短信",
                status = if (hasSmsPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                onRequestPermission = {
                    activity?.let {
                        PermissionUtils.requestSmsPermission(it, smsPermissionLauncher)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 通知权限
            PermissionItem(
                title = "通知权限",
                description = "允许应用发送通知提醒",
                status = if (hasNotificationPermission) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                onRequestPermission = {
                    activity?.let {
                        PermissionUtils.requestNotificationPermission(
                            it,
                            notificationPermissionLauncher
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 电池优化
            PermissionItem(
                title = "忽略电池优化",
                description = "确保应用在后台持续运行",
                status = if (isIgnoringBatteryOptimizations) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                onRequestPermission = {
                    activity?.let {
                        PermissionUtils.requestIgnoreBatteryOptimizations(it)
                    }
                }
            )

            // 后台自启动权限（国产手机）
            if (needsExtraBackgroundPermission) {
                Spacer(modifier = Modifier.height(8.dp))

                PermissionItem(
                    title = "后台自启动权限",
                    description = "请在系统设置中搜索并授予本应用自启动权限（该权限无法自动检测）",
                    status = PermissionStatus.UNKNOWN, // 自启动权限无法检测，始终为不可知状态
                    showSettingAlways = true, // 始终显示设置按钮
                    onRequestPermission = {
                        activity?.let {
                            PermissionUtils.openAutoStartSettings(it)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "设置权限后返回应用将自动刷新权限状态",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个权限项组件
 */
@Composable
private fun PermissionItem(
    title: String,
    description: String,
    status: PermissionStatus,
    showSettingAlways: Boolean = false, // 是否始终显示设置按钮
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // 根据不同状态显示不同图标
            when (status) {
                PermissionStatus.GRANTED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已授权",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                PermissionStatus.DENIED -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "未授权",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                PermissionStatus.UNKNOWN -> {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "不可知状态",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (status != PermissionStatus.GRANTED || showSettingAlways) {
            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(onClick = onRequestPermission) {
                Text("设置")
            }
        }
    }
} 