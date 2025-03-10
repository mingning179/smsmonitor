package com.nothing.sms.monitor.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.nothing.sms.monitor.push.PushServiceManager
import kotlinx.coroutines.launch

/**
 * 状态上报设置卡片
 * 配置状态上报间隔
 */
@Composable
fun StatusReportSettingCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushServiceManager = remember { PushServiceManager.getInstance(context) }
    val settingsService = remember { pushServiceManager.settingsService }

    var statusReportInterval by remember {
        mutableStateOf(settingsService.getStatusReportInterval().toString())
    }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 刷新状态
    LaunchedEffect(refreshTrigger) {
        statusReportInterval = settingsService.getStatusReportInterval().toString()
    }

    CommonCard(
        title = "状态上报设置",
    ) {
        Text(
            text = "设置向服务器上报状态的时间间隔（分钟）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = statusReportInterval,
                onValueChange = { statusReportInterval = it },
                label = { Text("上报间隔（分钟）") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val interval = statusReportInterval.toLongOrNull() ?: 15L
                        settingsService.saveStatusReportInterval(interval)
                        statusReportInterval = interval.toString()
                        refreshTrigger += 1
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("保存设置")
            }
        }
    }
} 