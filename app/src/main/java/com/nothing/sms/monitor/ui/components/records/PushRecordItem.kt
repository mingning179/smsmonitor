package com.nothing.sms.monitor.ui.components.records

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.db.SMSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 推送记录项
 */
@Composable
fun PushRecordItem(
    record: SMSDatabase.PushRecord,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val smsDatabase = remember { SMSDatabase(context) }
    val formattedTime = remember(record.pushTimestamp) {
        formatTimestamp(record.pushTimestamp)
    }

    // 短信相关状态
    var smsInfo by remember { mutableStateOf<SMSDatabase.SMS?>(null) }
    var smsTimestamp by remember { mutableStateOf("") }
    var isContentExpanded by remember { mutableStateOf(false) }

    // 异步加载短信内容
    LaunchedEffect(record.smsId) {
        withContext(Dispatchers.IO) {
            smsInfo = smsDatabase.getSMSById(record.smsId)
            if (smsInfo != null) {
                smsTimestamp = formatTimestamp(smsInfo!!.timestamp)
            }
        }
    }

    // 状态颜色和文本
    val statusColor = getStatusColor(record.status)
    val statusText = getStatusText(record.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (record.status == SMSDatabase.STATUS_FAILED)
            BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer)
        else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 卡片头部：状态和服务信息
            RecordHeader(
                statusColor = statusColor,
                statusText = statusText,
                serviceName = record.serviceName,
                retryCount = record.retryCount
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 推送时间
            Text(
                text = "推送时间: $formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 短信内容卡片
            if (smsInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SmsContentCard(
                    sms = smsInfo!!,
                    smsTimestamp = smsTimestamp,
                    isContentExpanded = isContentExpanded,
                    onExpandToggle = { isContentExpanded = !isContentExpanded },
                    onCopy = { copyToClipboard(context, smsInfo!!.content) }
                )
            }

            // 错误信息（如果有）
            if (!record.errorMessage.isNullOrBlank() && record.status == SMSDatabase.STATUS_FAILED) {
                Spacer(modifier = Modifier.height(6.dp))
                ErrorMessageBox(errorMessage = record.errorMessage)
            }

            // 重试按钮 (如果需要)
            if (record.canRetry) {
                Spacer(modifier = Modifier.height(8.dp))
                RetryButton(
                    onRetry = onRetry,
                )
            }
        }
    }
}

fun formatTimestamp(pushTimestamp: Long): String {
    val date = Date(pushTimestamp)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(date)
}

/**
 * 记录头部：状态和服务信息
 */
@Composable
private fun RecordHeader(
    statusColor: Color,
    statusText: String,
    serviceName: String,
    retryCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态标签
        StatusLabel(color = statusColor, text = statusText)

        // 服务名
        Text(
            text = serviceName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )

        // 重试次数
        if (retryCount > 0) {
            Text(
                text = "重试: ${retryCount}次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 状态标签
 */
@Composable
private fun StatusLabel(color: Color, text: String) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = color,
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * 短信内容卡片
 */
@Composable
private fun SmsContentCard(
    sms: SMSDatabase.SMS,
    smsTimestamp: String,
    isContentExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onCopy: () -> Unit
) {
    // 判断内容是否需要展开功能（超过100个字符）
    val contentNeedsExpand = sms.content.length > 100

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // 只有当内容需要展开时才添加点击事件
            .then(
                if (contentNeedsExpand) {
                    Modifier.clickable { onExpandToggle() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // 发件人和时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "发件人: ${sms.sender}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = smsTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 短信内容
            // 如果内容不需要展开，或者已经展开，则显示全部内容；否则最多显示3行
            Text(
                text = sms.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (!contentNeedsExpand || isContentExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            // 底部操作栏
            SmsCardActions(
                showExpandAction = contentNeedsExpand,
                isExpanded = isContentExpanded,
                onToggleExpand = onExpandToggle,
                onCopy = onCopy
            )
        }
    }
}

/**
 * 短信卡片底部操作栏
 */
@Composable
private fun SmsCardActions(
    showExpandAction: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/收起提示
        if (showExpandAction) {
            // 添加点击的视觉效果，使其更明显是可点击的元素
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded)
                            Icons.Default.ExpandLess
                        else
                            Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = if (isExpanded) "收起" else "展开全文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp)) // 占位
        }

        // 复制按钮
        TextButton(
            onClick = onCopy,
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "复制",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * 错误信息框
 */
@Composable
private fun ErrorMessageBox(errorMessage: String?) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "错误",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 重试按钮
 */
@Composable
private fun RetryButton(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "重试",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "重试推送",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * 根据状态获取颜色
 */
@Composable
private fun getStatusColor(status: Int): Color {
    return when (status) {
        SMSDatabase.STATUS_SUCCESS -> MaterialTheme.colorScheme.primary
        SMSDatabase.STATUS_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
}

/**
 * 根据状态获取文本
 */
@Composable
private fun getStatusText(status: Int): String {
    return when (status) {
        SMSDatabase.STATUS_SUCCESS -> "成功"
        SMSDatabase.STATUS_FAILED -> "失败"
        else -> "待处理"
    }
}

/**
 * 复制文本到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("短信内容", text)
    clipboardManager.setPrimaryClip(clipData)
    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT)
        .show()
} 