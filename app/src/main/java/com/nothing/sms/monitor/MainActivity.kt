package com.nothing.sms.monitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startSMSService()
            Toast.makeText(this, "所有权限已授予，服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要短信权限才能运行应用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // 检查SMS权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        
        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startSMSService()
        }
    }
    
    private fun startSMSService() {
        val serviceIntent = Intent(this, SMSProcessingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val pushServiceManager = remember { PushServiceManager.getInstance(context) }
    val settingsService = remember { SettingsService.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 从SettingsService获取关键字和状态上报间隔
    var keywords by remember { mutableStateOf(settingsService.getKeywords().joinToString(" ")) }
    var statusReportInterval by remember { mutableStateOf(settingsService.getStatusReportInterval().toString()) }
    
    // 获取API推送服务
    val apiService = remember { pushServiceManager.getService("api") }
    // 可编辑的API配置项
    var apiConfigItems by remember { 
        mutableStateOf(apiService?.getConfigItems() ?: emptyList()) 
    }
    
    // API URL配置
    val apiUrlItem = apiConfigItems.find { it.key == "api_url" }
    var apiUrl by remember(apiUrlItem) { mutableStateOf(apiUrlItem?.value ?: "") }
    var isTestingApiConnection by remember { mutableStateOf(false) }
    
    // 获取钉钉推送服务
    val dingTalkService = remember { pushServiceManager.getService("dingtalk") }
    
    // 可编辑的配置项
    var dingTalkConfigItems by remember { 
        mutableStateOf(dingTalkService?.getConfigItems() ?: emptyList()) 
    }
    
    // 是否正在测试钉钉连接
    var isTestingDingtalkConnection by remember { mutableStateOf(false) }
    
    // 是否显示消息模板编辑器
    var showDingtalkTemplateEditor by remember { mutableStateOf(false) }
    
    // 设备ID - 从SettingsService获取
    val deviceId = remember { settingsService.getDeviceId() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "短信监控服务",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 设备ID显示（只读）
        Text(
            text = "设备唯一ID",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = deviceId,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "此ID是设备的唯一标识，请勿修改",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        // 复制到剪贴板
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipData = android.content.ClipData.newPlainText("设备ID", deviceId)
                        clipboardManager.setPrimaryClip(clipData)
                        
                        Toast.makeText(
                            context,
                            "设备ID已复制到剪贴板",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制设备ID")
                }
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // API配置
        Text(
            text = "API服务器配置",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (apiService != null) {
            // API URL输入框
            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如: http://192.168.1.100:8080") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (apiUrl.isNotBlank()) {
                            // 保存到PushService
                            apiService.saveConfigs(mapOf("api_url" to apiUrl))
                            // 刷新配置项
                            apiConfigItems = apiService.getConfigItems()
                            
                            Toast.makeText(
                                context,
                                "API地址已保存",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "API地址不能为空",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        if (apiUrl.isNotBlank()) {
                            isTestingApiConnection = true
                            coroutineScope.launch {
                                // 先保存再测试
                                apiService.saveConfigs(mapOf("api_url" to apiUrl))
                                // 刷新配置项
                                apiConfigItems = apiService.getConfigItems()
                                
                                // 测试连接
                                val result = apiService.testConnection()
                                isTestingApiConnection = false
                                
                                result.fold(
                                    onSuccess = { isSuccessful ->
                                        val message = if (isSuccessful) "连接成功" else "连接失败：服务器返回错误"
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { exception ->
                                        Toast.makeText(
                                            context,
                                            "连接失败: ${exception.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "请先输入API地址",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isTestingApiConnection
                ) {
                    if (isTestingApiConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("测试连接")
                    }
                }
            }
        } else {
            // API服务不可用
            Text(
                text = "API推送服务不可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // 钉钉推送配置
        Text(
            text = "钉钉推送配置",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (dingTalkService != null) {
            // 从配置项中找到对应的项
            val enabledItem = dingTalkConfigItems.find { it.key == "enabled" }
            val webhookItem = dingTalkConfigItems.find { it.key == "webhook_url" }
            val secretItem = dingTalkConfigItems.find { it.key == "secret" }
            val templateItem = dingTalkConfigItems.find { it.key == "message_template" }
            
            // 是否启用钉钉
            val isDingtalkEnabled = enabledItem?.value?.toBoolean() ?: false
            
            // 启用/禁用开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "启用钉钉推送",
                    modifier = Modifier.weight(1f)
                )
                
                Switch(
                    checked = isDingtalkEnabled,
                    onCheckedChange = { enabled ->
                        // 保存到配置
                        dingTalkService.saveConfigs(mapOf("enabled" to enabled.toString()))
                        // 刷新配置项
                        dingTalkConfigItems = dingTalkService.getConfigItems()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Webhook URL
            var webhookUrl by remember(webhookItem) { mutableStateOf(webhookItem?.value ?: "") }
            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("钉钉Webhook URL") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isDingtalkEnabled
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Secret
            var secret by remember(secretItem) { mutableStateOf(secretItem?.value ?: "") }
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("钉钉安全密钥（可选）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isDingtalkEnabled
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (webhookUrl.isNotBlank()) {
                            // 保存配置
                            val configs = mutableMapOf<String, String>()
                            configs["webhook_url"] = webhookUrl
                            configs["secret"] = secret
                            dingTalkService.saveConfigs(configs)
                            
                            // 刷新配置项
                            dingTalkConfigItems = dingTalkService.getConfigItems()
                            
                            Toast.makeText(
                                context,
                                "钉钉配置已保存",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (isDingtalkEnabled) {
                            Toast.makeText(
                                context,
                                "Webhook URL不能为空",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDingtalkEnabled
                ) {
                    Text("保存")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        if (webhookUrl.isNotBlank()) {
                            isTestingDingtalkConnection = true
                            coroutineScope.launch {
                                // 先保存配置再测试
                                val configs = mutableMapOf<String, String>()
                                configs["webhook_url"] = webhookUrl
                                configs["secret"] = secret
                                dingTalkService.saveConfigs(configs)
                                
                                // 测试连接
                                val result = dingTalkService.testConnection()
                                isTestingDingtalkConnection = false
                                
                                // 刷新配置项
                                dingTalkConfigItems = dingTalkService.getConfigItems()
                                
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "钉钉连接测试成功",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailure = { exception ->
                                        Toast.makeText(
                                            context,
                                            "钉钉连接测试失败: ${exception.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "请先输入Webhook URL",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isDingtalkEnabled && !isTestingDingtalkConnection
                ) {
                    if (isTestingDingtalkConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("测试连接")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { showDingtalkTemplateEditor = !showDingtalkTemplateEditor },
                modifier = Modifier.fillMaxWidth(),
                enabled = isDingtalkEnabled
            ) {
                Text(if (showDingtalkTemplateEditor) "隐藏消息模板" else "编辑消息模板")
            }
            
            if (showDingtalkTemplateEditor) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // 消息模板
                var messageTemplate by remember(templateItem) { 
                    mutableStateOf(templateItem?.value ?: "") 
                }
                
                OutlinedTextField(
                    value = messageTemplate,
                    onValueChange = { messageTemplate = it },
                    label = { Text("钉钉消息模板") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = isDingtalkEnabled
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        // 保存消息模板
                        dingTalkService.saveConfigs(mapOf("message_template" to messageTemplate))
                        
                        // 刷新配置项
                        dingTalkConfigItems = dingTalkService.getConfigItems()
                        
                        Toast.makeText(
                            context,
                            "消息模板已保存",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isDingtalkEnabled
                ) {
                    Text("保存模板")
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "支持的变量: {sender}, {time}, {content}, {device}, {device_id}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            // 未找到钉钉服务
            Text(
                text = "钉钉推送服务不可用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // 关键字配置
        Text(
            text = "关键字设置",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            label = { Text("关键字（用逗号或空格分隔）") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val keywordList = keywords
                    .split("[，, ]".toRegex())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                
                settingsService.saveKeywords(keywordList)
                Toast.makeText(
                    context,
                    "关键字已更新",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存关键字")
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // 状态上报间隔配置
        Text(
            text = "状态上报间隔（分钟）",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = statusReportInterval,
            onValueChange = { statusReportInterval = it },
            label = { Text("上报间隔") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                try {
                    val interval = statusReportInterval.toLong()
                    if (interval in 1..120) {
                        settingsService.saveStatusReportInterval(interval)
                        Toast.makeText(
                            context,
                            "状态上报间隔已更新",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "间隔必须在1-120分钟之间",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(
                        context,
                        "请输入有效的数值",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存上报间隔")
        }
        
        // 推送服务列表
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "已注册的推送服务",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val allServices = pushServiceManager.getAllServices()
        
        if (allServices.isEmpty()) {
            Text(
                text = "没有找到推送服务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            allServices.forEach { service ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (service.isEnabled) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = service.serviceName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = if (service.isEnabled) "已启用" else "已禁用",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (service.isEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}