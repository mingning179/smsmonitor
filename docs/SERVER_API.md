### SMS推送监控系统 API文档

---

### 基础信息

- **基础URL**: `http://your-server-domain/api`
- **内容类型**: `application/json; charset=utf-8`
- **认证方式**: 设备ID验证

---

### 1. 发送验证码接口

**接口路径**: `/send-code`  
**方法**: POST  
**功能描述**: 向指定手机号发送验证码，用于绑定设备  

#### 请求参数

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| phoneNumber | String | 是 | 手机号码 |
| deviceId | String | 是 | 设备唯一标识ID |
| deviceInfo | String | 是 | 设备信息，如制造商和型号 |

#### 请求示例
```json
{
    "phoneNumber": "13800138000",
    "deviceId": "SMS_12345abcde",
    "deviceInfo": "Xiaomi Redmi Note 10"
}
```

#### 响应参数

| 参数名 | 类型 | 描述 |
|-------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 结果消息或错误信息 |

#### 响应示例 (成功)
```json
{
    "success": true,
    "message": "验证码已发送"
}
```

#### 响应示例 (失败)
```json
{
    "success": false,
    "message": "发送验证码失败：手机号格式不正确"
}
```

---

### 2. 验证绑定接口

**接口路径**: `/verify-bind`  
**方法**: POST  
**功能描述**: 验证用户输入的验证码并完成设备绑定，由服务器确定最终的subscriptionId  

#### 请求参数

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| phoneNumber | String | 是 | 手机号码 |
| code | String | 是 | 用户收到的验证码 |
| subscriptionId | Integer | 是 | 客户端建议的SIM卡订阅ID |
| deviceId | String | 是 | 设备唯一标识ID |
| deviceInfo | String | 是 | 设备信息 |

#### 请求示例
```json
{
    "phoneNumber": "13800138000",
    "code": "123456",
    "subscriptionId": 1,
    "deviceId": "SMS_12345abcde",
    "deviceInfo": "Xiaomi Redmi Note 10"
}
```

#### 响应参数

| 参数名 | 类型 | 描述 |
|-------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 结果消息或错误信息 |
| subscriptionId | Integer | 服务器确认的订阅ID（可能与请求中的不同） |

#### 响应示例 (成功)
```json
{
    "success": true,
    "message": "设备绑定成功",
    "subscriptionId": 2
}
```

#### 响应示例 (失败)
```json
{
    "success": false,
    "message": "验证失败：验证码错误或已过期"
}
```

---

### 3. 短信上报接口

**接口路径**: `/report-sms`  
**方法**: POST  
**功能描述**: 上报接收到的短信内容  

#### 请求参数

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| sender | String | 是 | 短信发送者号码 |
| content | String | 是 | 短信内容 |
| timestamp | Long | 是 | 接收短信的时间戳 |
| deviceInfo | String | 是 | 设备信息 |
| deviceId | String | 是 | 设备唯一标识ID |
| subscriptionId | Integer | 是 | SIM卡订阅ID |

#### 请求示例
```json
{
    "sender": "10086",
    "content": "您的账户余额为100元，已扣除50元话费。",
    "timestamp": 1646202458000,
    "deviceInfo": "Xiaomi Redmi Note 10",
    "deviceId": "SMS_12345abcde",
    "subscriptionId": 2
}
```

#### 响应参数

| 参数名 | 类型 | 描述 |
|-------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 结果消息或错误信息 |

#### 响应示例 (成功)
```json
{
    "success": true,
    "message": "短信上报成功"
}
```

#### 响应示例 (失败)
```json
{
    "success": false,
    "message": "短信上报失败：设备未绑定"
}
```

---

### 4. 状态上报接口

**接口路径**: `/report-status`  
**方法**: POST  
**功能描述**: 上报设备短信监控状态数据  

#### 请求参数

| 参数名 | 类型 | 必填 | 描述 |
|-------|------|-----|------|
| deviceId | String | 是 | 设备唯一标识ID |
| totalSMS | Integer | 是 | 总短信数量 |
| successSMS | Integer | 是 | 成功上报的短信数量 |
| failedSMS | Integer | 是 | 上报失败的短信数量 |
| pendingSMS | Integer | 是 | 待处理的短信数量 |
| timestamp | Long | 是 | 上报时间戳 |
| deviceInfo | String | 是 | 设备信息 |

#### 请求示例
```json
{
    "deviceId": "SMS_12345abcde",
    "totalSMS": 100,
    "successSMS": 95,
    "failedSMS": 3,
    "pendingSMS": 2,
    "timestamp": 1646202458000,
    "deviceInfo": "Xiaomi Redmi Note 10"
}
```

#### 响应参数

| 参数名 | 类型 | 描述 |
|-------|------|------|
| success | Boolean | 操作是否成功 |
| message | String | 结果消息或错误信息 |

#### 响应示例 (成功)
```json
{
    "success": true,
    "message": "状态上报成功"
}
```

#### 响应示例 (失败)
```json
{
    "success": false,
    "message": "状态上报失败：设备未注册"
}
```

---

### 错误码说明

| 错误码 | 描述 |
|--------|------|
| 400 | 请求参数错误 |
| 401 | 设备未授权 |
| 403 | 访问被拒绝 |
| 404 | 接口不存在 |
| 500 | 服务器内部错误 |

### 安全建议

1. 所有API通信建议使用HTTPS加密传输
2. 验证码有效期建议设置为5分钟
3. 同一手机号发送验证码应有频率限制（如1分钟内最多1次，24小时内最多5次）
4. 验证失败次数限制（如同一验证码最多尝试3次）
5. 服务器应对deviceId进行有效性验证
6. 敏感数据（6. 敏感数据（如手机号）应在存储前进行加密处理
7. 建议实现IP限制，防止恶意请求
8. 对请求频率进行限制，防止DoS攻击

### 实现注意事项

1. **验证码生成**：建议使用6位数字验证码，避免使用容易混淆的字符
2. **订阅ID处理**：服务器应根据实际情况确定最终的subscriptionId，并在响应中返回
3. **数据持久化**：所有绑定关系和短信数据应妥善存储，建议使用关系型数据库
4. **日志记录**：记录所有API调用，便于问题排查
5. **异常处理**：所有接口应有完善的异常处理机制，确保服务稳定性
6. **数据备份**：定期备份数据，防止数据丢失

### 测试环境

为方便开发和测试，建议提供以下测试环境功能：

1. 测试用验证码固定为"123456"
2. 提供测试账号和设备ID
3. 提供API调用日志查看接口
4. 提供模拟短信发送功能

希望这份API文档能够帮助您顺利实现服务器端功能。如有任何疑问或需要进一步的说明，请随时提出。
