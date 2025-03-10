package com.nothing.sms.monitor.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.push.ApiPushService
import com.nothing.sms.monitor.push.PushService
import com.nothing.sms.monitor.service.SMSProcessingService
import com.nothing.sms.monitor.ui.components.binding.BindingCard
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 推送服务配置卡片
 * 统一的推送服务配置界面
 */
@Composable
fun PushServiceConfigCard(service: PushService) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 获取服务配置项
    var configItems by remember { mutableStateOf(service.getConfigItems()) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isTogglingEnabled by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 用于跟踪表单值的变化
    var configValues by remember {
        mutableStateOf(configItems.associate { it.key to it.value }.toMutableMap())
    }

    // 用于跟踪服务是否启用
    val isEnabled = configValues["enabled"]?.toBoolean() ?: false

    // 每当refreshTrigger变化，重新获取配置
    LaunchedEffect(refreshTrigger) {
        configItems = service.getConfigItems()
        configValues = configItems.associate { it.key to it.value }.toMutableMap()
    }

    CommonCard(
        title = "${service.serviceName}配置",
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 服务启用开关
            val enabledItem = configItems.find { it.key == "enabled" }
            if (enabledItem != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = enabledItem.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isChecked ->
                            if (!isTogglingEnabled) {
                                isTogglingEnabled = true
                                coroutineScope.launch {
                                    try {
                                        // 先更新本地状态
                                        configValues = configValues.toMutableMap().apply {
                                            this["enabled"] = isChecked.toString()
                                        }

                                        // 保存到服务
                                        service.saveConfigs(mapOf("enabled" to isChecked.toString()))

                                        // 刷新配置
                                        refreshTrigger += 1

                                        Toast.makeText(
                                            context,
                                            if (isChecked) "已启用${service.serviceName}" else "已禁用${service.serviceName}",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // 当服务启用时，处理待处理的消息
                                        if (isChecked) {
                                            // 发送处理待处理消息的广播
                                            val intent = Intent(
                                                context,
                                                SMSProcessingService::class.java
                                            ).apply {
                                                action = SMSProcessingService.ACTION_PROCESS_PENDING
                                            }
                                            context.startService(intent)
                                        }
                                    } catch (e: Exception) {
                                        // 如果保存失败，恢复原状态
                                        configValues = configValues.toMutableMap().apply {
                                            this["enabled"] = (!isChecked).toString()
                                        }
                                        Timber.e(e, "切换服务状态失败")
                                        Toast.makeText(
                                            context,
                                            "操作失败: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isTogglingEnabled = false
                                    }
                                }
                            }
                        },
                        enabled = !isTogglingEnabled
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // 只在服务启用时显示其他配置
            if (isEnabled) {
                // 非启用项的其他配置项
                configItems.filter { it.key != "enabled" }.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        when (item.type) {
                            PushService.ConfigType.TEXT,
                            PushService.ConfigType.PASSWORD -> {
                                OutlinedTextField(
                                    value = configValues[item.key] ?: "",
                                    onValueChange = {
                                        configValues =
                                            configValues.toMutableMap()
                                                .apply { this[item.key] = it }
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = item.type != PushService.ConfigType.TEXTAREA,
                                    placeholder = { Text(item.hint) }
                                )
                            }

                            PushService.ConfigType.TEXTAREA -> {
                                OutlinedTextField(
                                    value = configValues[item.key] ?: "",
                                    onValueChange = {
                                        configValues =
                                            configValues.toMutableMap()
                                                .apply { this[item.key] = it }
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    placeholder = { Text(item.hint) }
                                )
                            }

                            PushService.ConfigType.BOOLEAN -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Switch(
                                        checked = configValues[item.key]?.toBoolean() ?: false,
                                        onCheckedChange = { isChecked ->
                                            configValues = configValues.toMutableMap().apply {
                                                this[item.key] = isChecked.toString()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 保存和测试按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                try {
                                    service.saveConfigs(configValues)
                                    refreshTrigger += 1 // 触发刷新
                                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Timber.e(e, "保存配置失败")
                                    Toast.makeText(
                                        context,
                                        "保存失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && !isTesting && !isTogglingEnabled
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存设置")
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            isTesting = true
                            coroutineScope.launch {
                                try {
                                    // 先保存最新配置
                                    service.saveConfigs(configValues)
                                    refreshTrigger += 1 // 触发刷新

                                    // 测试连接
                                    val result = service.testConnection()
                                    result.fold(
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                "连接测试成功",
                                                Toast.LENGTH_SHORT
                                            )
                                                .show()
                                        },
                                        onFailure = { e ->
                                            Toast.makeText(
                                                context,
                                                "连接测试失败: ${e.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "测试连接失败")
                                    Toast.makeText(
                                        context,
                                        "测试失败: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting && !isSaving && !isTogglingEnabled
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("测试连接")
                        }
                    }
                }

                // 如果是API推送服务，显示手机号绑定卡片
                if (service is ApiPushService) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(24.dp))
                    BindingCard()
                }
            } else {
                // 当服务未启用时显示提示
                Text(
                    text = "启用${service.serviceName}服务以配置详细设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 