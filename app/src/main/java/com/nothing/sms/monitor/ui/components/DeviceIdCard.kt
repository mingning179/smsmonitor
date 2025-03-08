package com.nothing.sms.monitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.push.SettingsService

/**
 * 设备ID卡片
 * 显示设备唯一标识并提供复制功能
 */
@Composable
fun DeviceIdCard() {
    val context = LocalContext.current
    val settingsService = remember { SettingsService.getInstance(context) }
    val deviceId = remember { settingsService.getDeviceId() }
    
    CommonCard(
        title = "设备唯一标识",
    ) {
        Text(
            text = "该ID用于标识此设备，请勿修改",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = deviceId,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("设备ID", deviceId)
                clipboardManager.setPrimaryClip(clipData)
                
                Toast.makeText(
                    context,
                    "设备ID已复制到剪贴板",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "复制"
            )
            Text(
                text = "复制设备ID",
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
} 