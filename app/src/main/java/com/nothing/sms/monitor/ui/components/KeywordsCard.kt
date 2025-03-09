package com.nothing.sms.monitor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nothing.sms.monitor.push.SettingsService
import kotlinx.coroutines.delay
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
    val keyboardController = LocalSoftwareKeyboardController.current

    var keywords by remember { mutableStateOf(settingsService.getKeywords()) }
    var newKeyword by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var monitorAllSms by remember { mutableStateOf(keywords.isEmpty()) }
    var userManuallyUnchecked by remember { mutableStateOf(false) }

    // 刷新关键字列表
    LaunchedEffect(refreshTrigger) {
        keywords = settingsService.getKeywords()
        if (!userManuallyUnchecked) {
            monitorAllSms = keywords.isEmpty()
        }
    }

    CommonCard(
        title = "短信监控设置",
    ) {
        // 监控模式选择
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = monitorAllSms,
                        onCheckedChange = { checked ->
                            monitorAllSms = checked
                            if (!checked) {
                                userManuallyUnchecked = true
                            }

                            if (checked && keywords.isNotEmpty()) {
                                // 如果选择监控所有短信，则清空关键字
                                coroutineScope.launch {
                                    keywords.forEach { settingsService.removeKeyword(it) }
                                    refreshTrigger += 1
                                }
                            } else if (checked) {
                                userManuallyUnchecked = false
                            }
                        }
                    )

                    Text(
                        text = "监控所有短信",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (monitorAllSms) {
                    Text(
                        text = "将接收所有验证码短信的通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 52.dp)
                    )
                }
            }
        }

        // 不监控所有短信时显示关键字部分
        AnimatedVisibility(
            visible = !monitorAllSms,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        Text(
                            text = "关键字过滤模式",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加关键字"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加关键字")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "只监控包含以下关键字的短信:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 显示现有关键字
                if (keywords.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "提示",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            Text(
                                text = "请添加至少一个关键字，或选择监控所有短信",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    LaunchedEffect(keywords) {
                        if (keywords.isEmpty() && !userManuallyUnchecked) {
                            delay(1500) // 给用户一点时间看到提示
                            monitorAllSms = true
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        keywords.forEachIndexed { index, keyword ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            KeywordItem(
                                keyword = keyword,
                                onDelete = {
                                    showDeleteConfirmDialog = keyword
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加关键字对话框
    if (showAddDialog) {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(showAddDialog) {
            delay(100)
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newKeyword = ""
            },
            title = { Text("添加关键字") },
            text = {
                Column {
                    Text(
                        "添加需要监控的短信关键字，短信内容包含此关键字时会触发通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("请输入关键字") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "关键字") },
                        trailingIcon = {
                            if (newKeyword.isNotEmpty()) {
                                IconButton(onClick = { newKeyword = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            coroutineScope.launch {
                                settingsService.addKeyword(newKeyword.trim())
                                showAddDialog = false
                                newKeyword = ""
                                monitorAllSms = false
                                refreshTrigger += 1
                                keyboardController?.hide()
                            }
                        }
                    },
                    enabled = newKeyword.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newKeyword = ""
                        keyboardController?.hide()
                    }
                ) {
                    Text("取消")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }

    // 删除确认对话框
    showDeleteConfirmDialog?.let { keyword ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("删除关键字") },
            text = { Text("确定要删除关键字\"${keyword}\"吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            settingsService.removeKeyword(keyword)
                            showDeleteConfirmDialog = null
                            refreshTrigger += 1
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = null }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 关键字项组件
 */
@Composable
private fun KeywordItem(
    keyword: String,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
} 