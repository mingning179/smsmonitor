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
import com.nothing.sms.monitor.db.SMSRepository
import com.nothing.sms.monitor.ui.components.DeviceIdCard
import com.nothing.sms.monitor.ui.components.PermissionGuideCard
import com.nothing.sms.monitor.ui.components.ServiceStatusCard

/**
 * 短信监控主屏幕
 * 显示应用当前状态和关键字设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { SMSRepository(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息中心") },
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
            // 权限引导卡片 - 确保用户开启必要权限
            PermissionGuideCard()

            // 设备ID卡片
            DeviceIdCard()

            // 服务状态卡片
            ServiceStatusCard(repository = repository)
        }
    }
} 