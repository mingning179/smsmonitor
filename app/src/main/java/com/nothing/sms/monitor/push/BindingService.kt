package com.nothing.sms.monitor.push

/**
 * 手机号绑定服务接口
 * 定义手机号验证和绑定相关的方法
 */
interface BindingService {
    /**
     * 发送验证码
     * @param phoneNumber 手机号
     * @return 发送结果
     */
    suspend fun sendVerificationCode(phoneNumber: String): Result<Boolean>

    /**
     * 验证并绑定
     * @param phoneNumber 手机号
     * @param code 验证码
     * @param subscriptionId SIM卡订阅ID
     * @return 绑定结果
     */
    suspend fun verifyAndBind(
        phoneNumber: String,
        code: String,
        subscriptionId: Int
    ): Result<Boolean>
}