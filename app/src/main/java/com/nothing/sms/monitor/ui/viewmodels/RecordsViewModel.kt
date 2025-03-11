package com.nothing.sms.monitor.ui.viewmodels

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nothing.sms.monitor.db.SMSRepository
import com.nothing.sms.monitor.model.PushRecord
import com.nothing.sms.monitor.service.SMSProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 推送记录ViewModel
 */
class RecordsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SMSRepository(application)
    private var _showFailedOnly = false

    // 推送记录列表
    var records by mutableStateOf<List<PushRecord>>(emptyList())
        private set
    var selectedTabIndex by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    /**
     * 选择标签
     */
    fun selectTab(index: Int) {
        if (selectedTabIndex != index) {
            selectedTabIndex = index
            refresh()
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        isLoading = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    records = when (selectedTabIndex) {
                        0 -> repository.getAllPushRecords()
                        1 -> repository.getFailedPushRecords()
                        else -> emptyList()
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 重试推送记录
     */
    fun retryPushRecord(recordId: Long) {
        viewModelScope.launch {
            // 发送重试广播
            val intent = Intent(getApplication(), SMSProcessingService::class.java).apply {
                action = SMSProcessingService.ACTION_RETRY_PUSH
                putExtra(SMSProcessingService.EXTRA_PUSH_RECORD_ID, recordId)
            }
            getApplication<Application>().startService(intent)

            // 通知用户
            android.widget.Toast.makeText(
                getApplication(),
                "已发送重试请求",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            // 稍等片刻后刷新数据
            kotlinx.coroutines.delay(1000)
            refresh()
        }
    }

    /**
     * 工厂类，用于创建ViewModel实例
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
                return RecordsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 