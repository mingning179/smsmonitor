package com.nothing.sms.monitor.ui.components.binding

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nothing.sms.monitor.push.ApiPushService
import com.nothing.sms.monitor.push.PushServiceManager
import com.nothing.sms.monitor.push.SettingsService
import com.nothing.sms.monitor.receiver.VerificationCodeReceiver
import com.nothing.sms.monitor.ui.components.CommonCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 常量定义
 */
private object Constants {
    const val INVALID_SUBSCRIPTION_ID = -1
    const val DEFAULT_SUBSCRIPTION_ID = 0
    const val VERIFICATION_CODE_MAX_LENGTH = 6
    const val COUNTDOWN_SECONDS = 60
    const val VERIFICATION_CODE_EXPIRY_MS = 60000 // 60秒
}

/**
 * 验证状态数据类
 */
data class VerificationState(
    val phoneNumber: String,
    val detectedSubscriptionId: Int = Constants.INVALID_SUBSCRIPTION_ID,
    val verificationCode: String = "",
    val isSendingCode: Boolean = false,
    val isVerifying: Boolean = false,
    val remainingSeconds: Int = 0,
    val errorMessage: String? = null
)

/**
 * 绑定卡片组件
 * 显示已绑定的手机号和添加新绑定的功能
 */
@Composable
fun BindingCard() {
    val context = LocalContext.current
    val pushServiceManager = remember { PushServiceManager.getInstance(context) }
    val settingsService = remember { pushServiceManager.settingsService }
    val apiPushService = remember { pushServiceManager.apiPushService }
    val coroutineScope = rememberCoroutineScope()
    var bindings by remember { mutableStateOf<List<SettingsService.BindingInfo>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showVerifyDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<SettingsService.BindingInfo?>(null) }

    // 加载绑定信息
    LaunchedEffect(refreshTrigger) {
        bindings = settingsService.getBindings()
    }

    CommonCard(
        title = "手机号绑定",
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 已绑定的手机号列表
            bindings.forEach { binding ->
                BindingItem(
                    binding = binding,
                    onDelete = { showDeleteConfirmDialog = binding }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 添加新绑定按钮
            if (bindings.size < 4) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加手机号绑定")
                }
            }
        }
    }

    // 添加绑定对话框
    if (showAddDialog) {
        AddBindingDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phoneNumber ->
                showAddDialog = false
                showVerifyDialog = phoneNumber
            }
        )
    }

    // 验证码对话框
    showVerifyDialog?.let { phoneNumber ->
        VerificationDialog(
            phoneNumber = phoneNumber,
            onDismiss = {
                showVerifyDialog = null
                VerificationCodeReceiver.clearLastCode()
            },
            onVerified = { _, subscriptionId ->
                coroutineScope.launch {
                    refreshTrigger += 1 // 刷新绑定列表
                    showVerifyDialog = null
                    VerificationCodeReceiver.clearLastCode()
                }
            },
            apiPushService = apiPushService
        )
    }

    // 删除确认对话框
    showDeleteConfirmDialog?.let { binding ->
        ConfirmationDialog(
            title = "删除绑定",
            message = "确定要删除手机号 ${binding.phoneNumber} (SIM${binding.subscriptionId}) 的绑定吗？",
            onConfirm = {
                coroutineScope.launch {
                    settingsService.removeBindingBySubscriptionId(binding.subscriptionId)
                    showDeleteConfirmDialog = null
                    refreshTrigger += 1
                }
            },
            onDismiss = { showDeleteConfirmDialog = null }
        )
    }
}

/**
 * 通用确认对话框
 */
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * 验证码对话框
 */
@Composable
private fun VerificationDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onVerified: (String, Int) -> Unit,
    apiPushService: ApiPushService
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 使用单一状态对象管理所有状态
    var state by remember {
        mutableStateOf(
            VerificationState(
                phoneNumber = phoneNumber
            )
        )
    }

    // 监听接收到的验证码
    val lastReceivedCode by VerificationCodeReceiver.lastReceivedCode.collectAsState()

    // 自动填充验证码并识别SIM卡订阅ID
    LaunchedEffect(lastReceivedCode) {
        lastReceivedCode?.let { codeData ->
            // 检查是否在60秒内收到的验证码
            val isRecent =
                (System.currentTimeMillis() - codeData.timestamp) < Constants.VERIFICATION_CODE_EXPIRY_MS
            if (isRecent) {
                Timber.d("自动填充验证码: ${codeData.code}, SIM卡订阅ID: ${codeData.subscriptionId}")
                state = state.copy(
                    verificationCode = codeData.code,
                    detectedSubscriptionId = codeData.subscriptionId
                )
            }
        }
    }

    // 发送验证码功能
    fun sendVerificationCode() {
        if (state.isSendingCode || state.remainingSeconds > 0) return

        state = state.copy(isSendingCode = true, errorMessage = null)
        coroutineScope.launch {
            try {
                // 发送验证码时使用默认SIM卡订阅ID
                val result = apiPushService.sendVerificationCode(
                    state.phoneNumber
                )

                if (result.isSuccess) {
                    Toast.makeText(context, "验证码已发送", Toast.LENGTH_SHORT).show()

                    // 开始倒计时
                    state = state.copy(
                        isSendingCode = false,
                        remainingSeconds = Constants.COUNTDOWN_SECONDS
                    )
                    startCountdown(Constants.COUNTDOWN_SECONDS) { remainingSeconds ->
                        state = state.copy(remainingSeconds = remainingSeconds)
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    state = state.copy(
                        isSendingCode = false,
                        errorMessage = "发送失败: ${exception?.message ?: "未知错误"}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "发送验证码失败")
                state = state.copy(
                    isSendingCode = false,
                    errorMessage = "发送失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // 验证码验证功能
    fun verifyCode() {
        if (state.isVerifying || state.verificationCode.isEmpty()) return
        if (state.detectedSubscriptionId < 0) {
            state = state.copy(errorMessage = "未检测到SIM卡订阅ID，请确保收到验证码")
            return
        }

        state = state.copy(isVerifying = true, errorMessage = null)

        coroutineScope.launch {
            try {
                val result = apiPushService.verifyAndBind(
                    state.phoneNumber,
                    state.verificationCode,
                    state.detectedSubscriptionId
                )

                if (result.isSuccess) {
                    Toast.makeText(context, "验证成功，绑定完成", Toast.LENGTH_SHORT).show()
                    onVerified(state.phoneNumber, state.detectedSubscriptionId)
                } else {
                    val exception = result.exceptionOrNull()
                    state = state.copy(
                        isVerifying = false,
                        errorMessage = "验证失败: ${exception?.message ?: "验证码错误"}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "验证码验证失败")
                state = state.copy(
                    isVerifying = false,
                    errorMessage = "验证失败: ${e.message ?: "未知错误"}"
                )
            }
        }
    }

    // 首次进入自动发送验证码
    LaunchedEffect(Unit) {
        sendVerificationCode()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("验证手机号") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("手机号: ${state.phoneNumber}")
                if (state.detectedSubscriptionId >= 0) {
                    Text("检测到的SIM卡订阅ID: SIM${state.detectedSubscriptionId}")
                } else {
                    Text("等待检测SIM卡订阅ID...")
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.verificationCode,
                    onValueChange = {
                        state = state.copy(
                            verificationCode = it.take(Constants.VERIFICATION_CODE_MAX_LENGTH)
                        )
                    },
                    label = { Text("验证码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isVerifying
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 错误信息
                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 发送按钮
                Button(
                    onClick = { sendVerificationCode() },
                    enabled = !state.isSendingCode && state.remainingSeconds == 0 && !state.isVerifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        if (state.remainingSeconds > 0)
                            "重新发送 (${state.remainingSeconds})"
                        else
                            "发送验证码"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { verifyCode() },
                enabled = !state.isVerifying &&
                        state.verificationCode.length >= 4 &&
                        state.detectedSubscriptionId >= 0
            ) {
                if (state.isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("验证")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 倒计时辅助函数
 */
private suspend fun startCountdown(
    seconds: Int,
    onTick: (Int) -> Unit
) {
    for (i in seconds downTo 1) {
        onTick(i)
        delay(1000)
    }
    onTick(0)
}

/**
 * 单个绑定信息项
 */
@Composable
private fun BindingItem(
    binding: SettingsService.BindingInfo,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Phone,
            contentDescription = "手机号",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = binding.phoneNumber,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "SIM${binding.subscriptionId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 添加绑定对话框
 */
@Composable
private fun AddBindingDialog(
    onDismiss: () -> Unit,
    onConfirm: (phoneNumber: String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加手机号绑定") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "系统将自动根据接收到的验证码短信确定SIM卡订阅ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(phoneNumber) },
                enabled = phoneNumber.isNotBlank()
            ) {
                Text("下一步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 