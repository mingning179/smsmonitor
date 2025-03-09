package com.nothing.sms.monitor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.sms.monitor.ui.components.records.EmptyContent
import com.nothing.sms.monitor.ui.components.records.LoadingContent
import com.nothing.sms.monitor.ui.components.records.RecordsList
import com.nothing.sms.monitor.ui.components.records.RecordsTabs
import com.nothing.sms.monitor.ui.viewmodels.RecordsViewModel

/**
 * 推送记录屏幕
 * 展示短信推送历史记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen() {
    val context = LocalContext.current
    val viewModel: RecordsViewModel = viewModel(factory = RecordsViewModel.Factory(context))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("推送记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
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
            RecordsTabs(
                selectedTabIndex = viewModel.selectedTabIndex,
                onTabSelected = { viewModel.selectTab(it) }
            )

            // 内容区域
            when {
                viewModel.isLoading -> LoadingContent()
                viewModel.records.isEmpty() -> EmptyContent(isAllRecords = viewModel.selectedTabIndex == 0)
                else -> RecordsList(
                    records = viewModel.records,
                    onRetry = { viewModel.retryPushRecord(it) }
                )
            }
        }
    }
} 