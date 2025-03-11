package com.nothing.sms.monitor.model

/**
 * 短信统计数据类
 * 包含总数、成功、失败、待处理和部分成功的消息数量
 */
data class SMSStats(
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val partialSuccess: Int = 0
) {
    /**
     * 计算成功率
     * @return 成功百分比（0-100）
     */
    fun getSuccessRate(): Float {
        if (total == 0) return 0f
        return success * 100f / total
    }

    /**
     * 计算失败率
     * @return 失败百分比（0-100）
     */
    fun getFailureRate(): Float {
        if (total == 0) return 0f
        return failed * 100f / total
    }

    /**
     * 格式化为可读字符串
     */
    override fun toString(): String {
        return "总计: $total | 成功: $success | 失败: $failed | 待处理: $pending | 部分成功: $partialSuccess"
    }
} 