package com.nothing.sms.monitor.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.db.SMSDatabase
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 推送记录屏幕
 * 展示短信推送历史记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen() {
    val context = LocalContext.current
    val smsDatabase = remember { SMSDatabase(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<SMSDatabase.PushRecord>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 加载推送记录
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            // 根据选择的标签加载不同记录
            records = when (selectedTabIndex) {
                0 -> smsDatabase.getAllPushRecords()
                1 -> smsDatabase.getFailedPushRecords()
                else -> emptyList()
            }
        }
        isLoading = false
    }
    
    // 自动刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000) // 每10秒自动刷新一次
            refreshTrigger++
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("推送记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 标签页
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { 
                        selectedTabIndex = 0
                        refreshTrigger++
                    },
                    text = { Text("全部记录") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { 
                        selectedTabIndex = 1
                        refreshTrigger++
                    },
                    text = { Text("失败记录") }
                )
            }
            
            // 记录列表或加载中提示
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...")
                }
            } else if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTabIndex == 0) "暂无推送记录" else "暂无失败记录",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    items(records) { record ->
                        PushRecordItem(
                            record = record,
                            onRetry = {
                                // 发送重试广播
                                val intent = Intent(context, SMSProcessingService::class.java).apply {
                                    action = SMSProcessingService.ACTION_RETRY_PUSH
                                    putExtra(SMSProcessingService.EXTRA_PUSH_RECORD_ID, record.id)
                                }
                                context.startService(intent)
                                
                                // 通知用户
                                android.widget.Toast.makeText(
                                    context,
                                    "已发送重试请求",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                
                                // 延迟刷新列表
                                coroutineScope.launch {
                                    delay(1000)
                                    refreshTrigger++
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PushRecordItem(
    record: SMSDatabase.PushRecord,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val smsDatabase = remember { SMSDatabase(context) }
    val formattedTime = remember(record.pushTimestamp) { 
        smsDatabase.formatTimestamp(record.pushTimestamp) 
    }
    
    // 获取关联的短信信息
    var smsInfo by remember { mutableStateOf<SMSDatabase.SMS?>(null) }
    var smsTimestamp by remember { mutableStateOf("") }
    // 控制短信内容展开状态
    var isContentExpanded by remember { mutableStateOf(false) }
    
    // 异步加载短信内容
    LaunchedEffect(record.smsId) {
        withContext(Dispatchers.IO) {
            smsInfo = smsDatabase.getSMSById(record.smsId)
            if (smsInfo != null) {
                smsTimestamp = smsDatabase.formatTimestamp(smsInfo!!.timestamp)
            }
        }
    }
    
    // 复制短信内容到剪贴板
    fun copyToClipboard(text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("短信内容", text)
        clipboardManager.setPrimaryClip(clipData)
        android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = when (record.status) {
                SMSDatabase.STATUS_SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                SMSDatabase.STATUS_FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 状态和服务名
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示器
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (record.status) {
                                SMSDatabase.STATUS_SUCCESS -> Color.Green
                                SMSDatabase.STATUS_FAILED -> Color.Red
                                else -> Color.Gray
                            },
                            shape = CircleShape
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 状态文本
                Text(
                    text = when (record.status) {
                        SMSDatabase.STATUS_SUCCESS -> "成功"
                        SMSDatabase.STATUS_FAILED -> "失败"
                        else -> "待处理"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 服务名
                Text(
                    text = record.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                
                // 重试次数
                if (record.retryCount > 0) {
                    Text(
                        text = "重试: ${record.retryCount}次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 时间戳
            Text(
                text = "推送时间: $formattedTime",
                style = MaterialTheme.typography.bodySmall
            )
            
            // 短信信息卡片
            if (smsInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isContentExpanded = !isContentExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // 发件人和时间
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "发件人: ${smsInfo!!.sender}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = smsTimestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 短信内容 - 可点击展开/折叠
                        Text(
                            text = smsInfo!!.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (isContentExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // 底部操作栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 展开/收起提示
                            if (smsInfo!!.content.length > 100) {
                                Text(
                                    text = if (isContentExpanded) "收起" else "展开查看完整内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp)) // 占位
                            }
                            
                            // 复制按钮
                            Button(
                                onClick = { copyToClipboard(smsInfo!!.content) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = "复制内容",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            
            // 错误信息（如果有）
            if (!record.errorMessage.isNullOrBlank() && record.status == SMSDatabase.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "错误: ${record.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 失败记录才显示重试按钮
            if (record.status == SMSDatabase.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onRetry,
                        enabled = record.retryCount < 3
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重试"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重试推送")
                    }
                }
            }
        }
    }
} 