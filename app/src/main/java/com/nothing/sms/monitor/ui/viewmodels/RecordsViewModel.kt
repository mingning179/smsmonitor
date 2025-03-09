package com.nothing.sms.monitor.ui.viewmodels

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nothing.sms.monitor.db.SMSDatabase
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 推送记录的视图模型，处理数据获取和业务逻辑
 */
class RecordsViewModel(private val context: Context) : ViewModel() {

    private val smsDatabase = SMSDatabase(context)

    // UI状态
    var records by mutableStateOf<List<SMSDatabase.PushRecord>>(emptyList())
        private set
    var selectedTabIndex by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(true)
        private set

    init {
        loadRecords()
        startAutoRefresh()
    }

    /**
     * 加载推送记录
     */
    fun loadRecords() {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                records = when (selectedTabIndex) {
                    0 -> smsDatabase.getAllPushRecords()
                    1 -> smsDatabase.getFailedPushRecords()
                    else -> emptyList()
                }
            }
            isLoading = false
        }
    }

    /**
     * 切换标签页
     */
    fun selectTab(index: Int) {
        selectedTabIndex = index
        loadRecords()
    }

    /**
     * 手动刷新
     */
    fun refresh() {
        loadRecords()
    }

    /**
     * 启动自动刷新
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(10000) // 每10秒自动刷新一次
                loadRecords()
            }
        }
    }

    /**
     * 重试推送失败的记录
     */
    fun retryPushRecord(recordId: Long) {
        viewModelScope.launch {
            // 发送重试广播
            val intent = Intent(context, SMSProcessingService::class.java).apply {
                action = SMSProcessingService.ACTION_RETRY_PUSH
                putExtra(SMSProcessingService.EXTRA_PUSH_RECORD_ID, recordId)
            }
            context.startService(intent)

            // 通知用户
            android.widget.Toast.makeText(
                context,
                "已发送重试请求",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            // 延迟刷新列表
            delay(1000)
            loadRecords()
        }
    }

    /**
     * 工厂类，用于创建ViewModel实例
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
                return RecordsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 