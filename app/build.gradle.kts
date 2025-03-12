import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        versionCode = 1
        versionName = "1.0"

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

// 配置自定义APK输出目录
tasks.whenTaskAdded {
    if (name.contains("assembleRelease")) {
        this.doLast {
            val releaseDir = File(rootDir, "release")
            if (!releaseDir.exists()) {
                releaseDir.mkdirs()
            }

            val currentTime =
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

            val apkFileName = "${android.defaultConfig.applicationId}-" +
                    "${android.defaultConfig.versionName}-${currentTime}.apk"
            val sourceApk = project.layout.buildDirectory
                .file("outputs/apk/release/app-release.apk").get().asFile
            val destApk = File(releaseDir, apkFileName)

            if (sourceApk.exists()) {
                sourceApk.copyTo(destApk, overwrite = true)
                println("APK已复制到: ${destApk.absolutePath}")
            } else {
                println("源APK文件不存在: ${sourceApk.absolutePath}")
            }
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