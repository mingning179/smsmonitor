package com.nothing.sms.monitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.ui.components.PushServiceConfigCard
import com.nothing.sms.monitor.ui.components.StatusReportSettingCard

/**
 * 设置屏幕
 * 提供应用各项设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val pushServiceManager = remember { PushServiceManager.getInstance(context) }
    val services = remember { pushServiceManager.getAllServices() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 状态上报设置
            StatusReportSettingCard()
            
            // 推送服务配置
            services.forEach { service ->
                PushServiceConfigCard(service = service)
            }
        }
    }
} 