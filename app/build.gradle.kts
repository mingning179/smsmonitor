import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 在脚本开始时生成统一的构建时间戳
val buildTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}


android {
    namespace = "com.nothing.sms.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nothing.sms.monitor"
        minSdk = 25
        targetSdk = 35
        versionCode = 13
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = "smsmonitor"
            keyAlias = "smsmonitor"
            keyPassword = "smsmonitor"
        }
    }

    // 配置三个版本的Product Flavors
    flavorDimensions += "version"

    productFlavors {
        create("mainline") {
            dimension = "version"
            applicationIdSuffix = ".main"
            versionNameSuffix = "-mainline"
            buildConfigField("String", "DEFAULT_API_URL", "\"http://localhost:8080/api\"")
            resValue("string", "app_name", "验证码信使")
        }
        
        create("cxt_test") {
            dimension = "version"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-cxt-test"
            buildConfigField("String", "DEFAULT_API_URL", "\"http://mapp.zqxiaolv.cn/sms-center/app/autoCodeDevice\"")
            resValue("string", "app_name", "财小桃测试版")
        }
        
        create("cxt_prod") {
            dimension = "version"
            // 正式版不添加后缀，保持原有的包名
            versionNameSuffix = "-cxt-prod"
            buildConfigField("String", "DEFAULT_API_URL", "\"http://pro.caixiaotaoai.com/sms-center/app/autoCodeDevice\"")
            resValue("string", "app_name", "财小桃正式版")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true  // 启用BuildConfig
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    // Icons
    implementation(libs.androidx.material.icons.extended)

    // OkHttp for network requests
    implementation(libs.okhttp)
    // Gson for JSON parsing
    implementation(libs.gson)
    // Timber
    implementation(libs.timber)

    // Room数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// 在每个release任务完成后复制对应的APK
tasks.whenTaskAdded {
    if (name.matches(Regex("assemble.*Release"))) {
        doLast {
            val releaseDir = File(rootDir, "release")
            if (!releaseDir.exists()) {
                releaseDir.mkdirs()
            }

            val versionName = android.defaultConfig.versionName

            // 处理当前任务对应的APK
            val flavorName = when {
                name.contains("Mainline") -> "mainline"
                name.contains("Cxt_test") -> "cxt_test"
                name.contains("Cxt_prod") -> "cxt_prod"
                else -> null
            }

            if (flavorName != null) {
                val displayName = when (flavorName) {
                    "mainline" -> "主线版"
                    "cxt_test" -> "财小桃测试版"
                    "cxt_prod" -> "财小桃正式版"
                    else -> flavorName
                }

                val flavorApkDir = File(project.layout.buildDirectory.get().asFile, "outputs/apk/$flavorName/release")
                if (flavorApkDir.exists()) {
                    val apkFile = flavorApkDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()
                    if (apkFile != null) {
                        val destFile = File(releaseDir, "验证码信使-${versionName}-${displayName}-${buildTimestamp}.apk")
                        apkFile.copyTo(destFile, overwrite = true)
                        println("✅ 已复制: ${destFile.name}")
                    }
                }
            }
        }
    }
}