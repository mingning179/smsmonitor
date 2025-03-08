package com.nothing.sms.monitor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.push.SettingsService
import kotlinx.coroutines.launch

/**
 * 关键字设置卡片
 * 管理短信过滤关键字
 */
@Composable
fun KeywordsCard() {
    val context = LocalContext.current
    val settingsService = remember { SettingsService.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var keywords by remember { mutableStateOf(settingsService.getKeywords()) }
    var newKeyword by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // 刷新关键字列表
    LaunchedEffect(refreshTrigger) {
        keywords = settingsService.getKeywords()
    }
    
    CommonCard(
        title = "短信关键字设置",
    ) {
        Text(
            text = "设置需要监控的短信关键字，当短信内容包含以下关键字时会触发推送。如果不设置关键字，将监控所有短信。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 添加新关键字
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text("新关键字") },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (newKeyword.isNotBlank()) {
                        coroutineScope.launch {
                            settingsService.addKeyword(newKeyword)
                            newKeyword = ""
                            refreshTrigger += 1
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "添加"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 显示现有关键字
        if (keywords.isEmpty()) {
            Text(
                text = "当前未设置关键字，将监控所有短信",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "当前关键字列表:",
                style = MaterialTheme.typography.titleSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column {
                keywords.forEachIndexed { index, keyword ->
                    if (index > 0) Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = keyword,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    settingsService.removeKeyword(keyword)
                                    refreshTrigger += 1
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
} 