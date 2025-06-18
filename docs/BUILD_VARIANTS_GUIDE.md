# 验证码信使 多版本构建指南

## 概述

本项目使用 **Android Build Variants** 来管理三个不同版本的配置，避免了多分支管理的复杂性。通过Product Flavors实现一套代码构建多个环境版本。

## 版本配置

项目配置了三个版本：

### 1. 主线版本 (mainline)
- **应用ID**: `com.nothing.sms.monitor.main`
- **应用名称**: `验证码信使-主线版`
- **API URL**: `http://localhost:8080/api`
- **用途**: 主线开发版本

### 2. 财小桃测试版 (cxt_test)
- **应用ID**: `com.nothing.sms.monitor.test`
- **应用名称**: `验证码信使-财小桃测试版`
- **API URL**: `http://mapp.zqxiaolv.cn/sms-center/app/autoCodeDevice`
- **用途**: 财小桃测试环境

### 3. 财小桃正式版 (cxt_prod)
- **应用ID**: `com.nothing.sms.monitor`
- **应用名称**: `验证码信使-财小桃正式版`
- **API URL**: `http://pro.caixiaotaoai.com/sms-center/app/autoCodeDevice`
- **用途**: 财小桃生产环境

## 构建方法

### 一键构建所有版本（推荐）

```bash
# 清理并构建所有release版本
./gradlew clean assembleRelease
```

执行此命令将：
1. 清理旧的构建文件
2. 同时构建三个版本的release包
3. 自动将APK复制到 `release/` 目录并重命名
4. 所有APK使用统一的构建时间戳

### 单独构建某个版本

```bash
./gradlew clean assembleMainlineRelease   # 构建主线版本
./gradlew clean assembleCxt_testRelease   # 构建财小桃测试版
./gradlew clean assembleCxt_prodRelease   # 构建财小桃正式版
```

### 在 Android Studio 中构建

1. 打开 Android Studio
2. 在 `Build Variants` 面板中选择对应的变体：
   - `mainlineRelease`
   - `cxt_testRelease`
   - `cxt_prodRelease`
3. 点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

## 输出文件

构建完成后，APK 文件会自动复制到 `release/` 目录下，文件命名格式：

```
验证码信使-{版本号}-{版本名}-{统一时间戳}.apk
```

示例：
- `验证码信使-1.2-主线版-20250618_170401.apk`
- `验证码信使-1.2-财小桃测试版-20250618_170401.apk`
- `验证码信使-1.2-财小桃正式版-20250618_170401.apk`

**注意**：同一次构建的所有APK使用统一的时间戳，表示它们属于同一次构建批次。

## 同时安装多个版本

由于不同版本使用了不同的 `applicationId`，您可以在同一台设备上同时安装多个版本：

- 主线版本：`com.nothing.sms.monitor.main`
- 财小桃测试版：`com.nothing.sms.monitor.test`
- 财小桃正式版：`com.nothing.sms.monitor`

这样可以方便地进行并行测试和环境对比。

## 技术实现细节

### 自动化APK处理

项目使用优雅的gradle脚本实现自动化：

1. **统一时间戳**：在脚本开始时生成一次构建时间戳，所有APK共享
2. **任务监听**：监听每个release任务完成事件
3. **自动复制**：任务完成后自动复制APK到release目录
4. **智能重命名**：按照统一格式重命名APK文件

### 核心配置

APK处理逻辑位于 `app/build.gradle.kts` 中：

```kotlin
// 统一构建时间戳
val buildTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

// 监听任务完成并自动处理APK
tasks.whenTaskAdded {
    if (name.matches(Regex("assemble.*Release"))) {
        doLast {
            // 自动复制和重命名逻辑
        }
    }
}
```

## 修改配置

### 修改API URL

在 `app/build.gradle.kts` 文件中的 `productFlavors` 部分修改：

```kotlin
productFlavors {
    create("mainline") {
        buildConfigField("String", "DEFAULT_API_URL", "\"http://your-new-api.com/api\"")
    }
    create("cxt_test") {
        buildConfigField("String", "DEFAULT_API_URL", "\"http://your-test-api.com/api\"")
    }
    create("cxt_prod") {
        buildConfigField("String", "DEFAULT_API_URL", "\"http://your-prod-api.com/api\"")
    }
}
```

### 修改版本信息

在 `defaultConfig` 中修改：

```kotlin
defaultConfig {
    versionCode = 13
    versionName = "1.3"
    // ...
}
```

## 优势

1. **单代码库**：避免多分支维护的复杂性
2. **一键构建**：一个命令构建所有版本
3. **自动化处理**：APK自动复制和重命名
4. **统一时间戳**：清晰标识同批次构建
5. **并行安装**：不同版本可同时安装测试
6. **配置隔离**：各版本配置完全独立

此方案完全替代了之前的多分支管理方式，提供了更优雅和高效的多环境构建解决方案。 