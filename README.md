# 短信监控应用

这是一个Android平台的短信监控应用，能够自动捕获并通过多种方式推送短信内容。支持多SIM卡设备和手机号绑定验证功能。

## 功能特点

- **关键词过滤**：根据预设关键词自动识别重要短信
- **多渠道推送**：支持API接口和钉钉群等多种推送方式
- **服务扩展**：具备可扩展的推送服务框架，易于添加新的推送渠道
- **手机号绑定**：通过短信验证码验证，支持多SIM卡订阅ID绑定
- **多SIM卡支持**：自动识别短信来源SIM卡订阅ID，支持多卡设备
- **可靠性保障**：自动重试机制确保消息不丢失
- **灵活配置**：各推送渠道可单独开启/关闭和配置
- **记录管理**：完整的推送记录查看和管理功能
- **界面友好**：现代化的Material 3设计风格，流畅的用户体验

## 多版本构建

本项目支持一键构建三个不同环境的版本：

### 版本说明

1. **主线版本** (`mainline`)
   - 应用ID: `com.nothing.sms.monitor.main`
   - API: `http://localhost:8080/api`
   - 用途: 主线开发版本

2. **财小桃测试版** (`cxt_test`)
   - 应用ID: `com.nothing.sms.monitor.test`
   - API: `http://mapp.zqxiaolv.cn/sms-center/app/autoCodeDevice`
   - 用途: 财小桃测试环境

3. **财小桃正式版** (`cxt_prod`)
   - 应用ID: `com.nothing.sms.monitor`
   - API: `http://pro.caixiaotaoai.com/sms-center/app/autoCodeDevice`
   - 用途: 财小桃生产环境

### 一键构建所有版本

```bash
# 清理并构建所有环境的release版本
./gradlew clean assembleRelease
```

执行此命令将自动：
- 构建三个环境的release包
- 将APK复制到 `release/` 目录
- 按统一格式重命名APK文件
- 所有APK使用同一构建时间戳

### 输出文件

构建完成后，`release/` 目录将包含：
```
验证码信使-1.2-主线版-20250618_170401.apk
验证码信使-1.2-财小桃测试版-20250618_170401.apk
验证码信使-1.2-财小桃正式版-20250618_170401.apk
```

### 并行安装

由于使用不同的应用ID，三个版本可以同时安装在同一设备上，方便并行测试。

## 编译指南

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17 或更高版本
- Gradle 8.0 或更高版本

### 构建单个版本

```bash
./gradlew clean assembleMainlineRelease   # 构建主线版本
./gradlew clean assembleCxt_testRelease   # 构建财小桃测试版
./gradlew clean assembleCxt_prodRelease   # 构建财小桃正式版
```

### 签名配置

应用已配置好签名信息：

- 密钥库文件：`app/keystore/release.keystore`
- 密钥库密码：`smsmonitor`
- 密钥别名：`smsmonitor`
- 密钥密码：`smsmonitor`

> **注意**：当前配置的签名密钥是随机生成的临时密钥，仅用于开发和测试目的。正式发布版本应使用您自己的签名密钥替换此临时密钥。在生产环境中，请务必使用安全存储的正式签名密钥。

### 自定义版本信息

如需修改版本号，在`app/build.gradle.kts`文件中修改：

```kotlin
defaultConfig {
    versionCode = 13
    versionName = "1.3"
    // ...
}
```

### 修改API配置

如需修改API地址，在`app/build.gradle.kts`中的`productFlavors`部分修改对应版本的`buildConfigField`。

详细的构建指南请参考：[docs/BUILD_VARIANTS_GUIDE.md](docs/BUILD_VARIANTS_GUIDE.md)

## 使用指南

1. **安装应用**：安装APK并授予短信读取权限
2. **配置关键词**：设置需要监控的短信关键词
3. **配置推送服务**：选择并配置所需的推送渠道
4. **绑定手机号**：通过短信验证码验证手机号，支持多SIM卡订阅ID分别绑定
5. **启动监控**：应用将在后台自动监控并推送符合条件的短信

## 技术实现

- 使用Jetpack Compose构建现代化UI
- 采用协程处理异步操作
- 实现可扩展的推送服务框架
- 使用SQLite数据库存储短信和推送记录
- 集成OkHttp处理网络请求
- 多SIM卡订阅ID信息传递和识别机制
- 手机号绑定验证流程
- Android Build Variants实现多环境构建

## 手机号绑定功能

应用实现了手机号绑定功能，主要特点：

- **多SIM卡订阅ID支持**：一个设备可以绑定多个SIM卡对应的手机号
- **验证码验证**：通过短信验证码确认手机号所有权
- **自动识别**：能够自动识别短信来自哪个SIM卡订阅ID
- **安全存储**：绑定信息安全存储在本地
- **灵活管理**：支持添加、删除绑定关系

## 短信监控流程

1. 收到短信时自动识别SIM卡订阅ID
2. 根据关键词过滤重要短信
3. 存储短信内容和来源信息
4. 将短信内容和SIM卡订阅ID信息推送到配置的服务
5. 服务器可通过SIM卡订阅ID信息匹配绑定的手机号

## 开源说明

本项目基于MIT许可证开源，您可以自由使用、修改和分发本软件。详情请查看LICENSE文件。

## 贡献指南

欢迎对本项目提出建议或贡献代码。请遵循以下步骤：

1. Fork本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启一个Pull Request

## 第三方库

本项目使用了以下开源库：

- Jetpack Compose：UI框架
- OkHttp：网络请求
- Timber：日志工具

## 免责声明

本应用仅供学习和研究使用，使用者应当遵守当地法律法规，尊重用户隐私。开发者不对因使用本软件而产生的任何问题负责。 