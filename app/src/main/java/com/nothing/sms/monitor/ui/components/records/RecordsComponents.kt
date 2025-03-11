package com.nothing.sms.monitor.ui.components.records

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.model.PushRecord

/**
 * 标签页
 */
@Composable
fun RecordsTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    TabRow(selectedTabIndex = selectedTabIndex) {
        Tab(
            selected = selectedTabIndex == 0,
            onClick = { onTabSelected(0) },
            text = { Text("全部记录") }
        )
        Tab(
            selected = selectedTabIndex == 1,
            onClick = { onTabSelected(1) },
            text = { Text("失败记录") }
        )
    }
}

/**
 * 加载中内容
 */
@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("加载中...")
    }
}

/**
 * 空内容提示
 */
@Composable
fun EmptyContent(isAllRecords: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isAllRecords) "暂无推送记录" else "暂无失败记录",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 推送记录列表组件
 */
@Composable
fun PushRecordsList(
    records: List<PushRecord>,
    onRetry: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
    ) {
        items(records) { record ->
            // 定义一个明确返回类型的函数，注意变量命名
            val itemClickHandler: () -> Unit = {
                onRetry(record.id)
            }

            PushRecordItem(
                record = record,
                onRetry = itemClickHandler
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
} 