package com.nothing.sms.monitor.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.db.SMSRepository
import com.nothing.sms.monitor.model.SMSStats
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 服务状态卡片
 * 显示服务运行状态和短信统计信息
 */
@Composable
fun ServiceStatusCard(
    modifier: Modifier = Modifier,
    repository: SMSRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 统计数据
    var stats by remember { mutableStateOf(SMSStats()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 加载统计数据
    fun loadStats() {
        isLoading = true
        scope.launch {
            val newStats = withContext(Dispatchers.IO) {
                repository.getStats()
            }
            stats = newStats
            isLoading = false
        }
    }

    // 自动刷新
    LaunchedEffect(refreshTrigger) {
        loadStats()
    }

    // 每隔10秒自动刷新一次
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            refreshTrigger += 1
        }
    }

    CommonCard(
        title = "服务状态",
        modifier = modifier
    ) {
        // 服务启动状态
        Text(
            text = "短信监控服务正在运行中",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 短信处理统计
        Text(
            text = "短信处理统计:",
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "总计: ${stats.total} | 成功: ${stats.success} | 失败: ${stats.failed} | 待处理: ${stats.pending} | 部分成功: ${stats.partialSuccess}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    refreshTrigger += 1
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新"
                )
                Text(
                    text = "刷新状态",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            Button(
                onClick = {
                    // 发送处理待处理消息的Intent
                    val serviceIntent = Intent(context, SMSProcessingService::class.java).apply {
                        action = SMSProcessingService.ACTION_PROCESS_PENDING
                    }
                    context.startService(serviceIntent)

                    // 显示一个Toast提示
                    Toast.makeText(
                        context,
                        "正在处理待处理消息...",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 延迟刷新UI
                    scope.launch {
                        delay(2000) // 增加延迟，让服务有足够时间处理
                        refreshTrigger += 1
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("处理待处理")
            }
        }
    }
} 