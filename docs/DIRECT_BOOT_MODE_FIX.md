# 直接启动模式修复说明

## 问题描述

应用在设备启动但用户未解锁时崩溃，错误信息：
```
java.lang.IllegalStateException: SharedPreferences in credential encrypted storage are not available until after user (id 0) is unlocked
```

这个错误发生在应用的Application类onCreate方法中尝试访问SharedPreferences时。

## 问题原因

Android 7.0（API 24）引入了直接启动模式（Direct Boot Mode）。在这种模式下，存储被分为两种类型：

1. **设备加密存储（Device Encrypted Storage, DE）**：设备启动后即可访问
2. **凭据加密存储（Credential Encrypted Storage, CE）**：需要用户解锁后才能访问

默认情况下，SharedPreferences存储在CE中。当应用在设备启动但用户未解锁时运行（如通过BOOT_COMPLETED广播），访问CE存储会导致崩溃。

## 解决方案

### 1. 修改SharedPreferences使用设备加密存储

#### SettingsService.kt
```kotlin
private val prefs: SharedPreferences by lazy {
    // 使用设备加密存储，以便在用户未解锁时也能访问
    val context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appContext.createDeviceProtectedStorageContext()
    } else {
        appContext
    }
    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
```

#### BasePushService.kt
```kotlin
protected val prefs: SharedPreferences by lazy {
    // 使用设备加密存储，以便在用户未解锁时也能访问
    val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }
    storageContext.getSharedPreferences(PREF_PUSH_SERVICES, Context.MODE_PRIVATE)
}
```

### 2. 添加directBootAware属性

在AndroidManifest.xml中，为需要在直接启动模式下运行的组件添加`android:directBootAware="true"`：

```xml
<application
    android:name="com.nothing.sms.monitor.Application"
    ...
    android:directBootAware="true">

<service
    android:name="com.nothing.sms.monitor.service.SMSProcessingService"
    ...
    android:directBootAware="true" />

<receiver
    android:name="com.nothing.sms.monitor.receiver.SMSReceiver"
    ...
    android:directBootAware="true">

<receiver
    android:name="com.nothing.sms.monitor.receiver.BootReceiver"
    ...
    android:directBootAware="true">
```

### 3. 数据迁移

创建StorageMigrationHelper工具类，用于将现有数据从CE存储迁移到DE存储。在MainActivity的onCreate方法中执行迁移：

```kotlin
private fun performDataMigration() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            val prefsToMigrate = listOf(
                "settings_service_prefs",
                "push_services_config"
            )
            StorageMigrationHelper.migrateAllPreferences(this, prefsToMigrate)
        } catch (e: Exception) {
            Timber.e(e, "数据迁移失败")
        }
    }
}
```

## 注意事项

1. **安全性考虑**：设备加密存储的安全性低于凭据加密存储。只应将必要的数据存储在DE中，敏感信息应继续存储在CE中。

2. **数据迁移**：首次更新后，应用会自动将旧数据从CE迁移到DE。迁移只会在用户解锁后执行一次。

3. **兼容性**：Android N以下版本不支持直接启动模式，代码已做了版本判断处理。

## 测试方法

1. 重启设备
2. 在不解锁的情况下等待设备完全启动
3. 应用应该能够正常接收和处理短信，不会崩溃
4. 解锁设备后打开应用，确认所有功能正常工作

## 相关文件

- `app/src/main/java/com/nothing/sms/monitor/push/SettingsService.kt`
- `app/src/main/java/com/nothing/sms/monitor/push/BasePushService.kt`
- `app/src/main/java/com/nothing/sms/monitor/util/StorageMigrationHelper.kt`
- `app/src/main/java/com/nothing/sms/monitor/MainActivity.kt`
- `app/src/main/AndroidManifest.xml` 