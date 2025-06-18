package com.nothing.sms.monitor.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import timber.log.Timber

/**
 * 自启动帮助工具类
 * 用于检测和处理不同厂商的自启动限制
 */
object AutoStartHelper {

    /**
     * 检查是否需要引导用户设置自启动权限
     */
    fun needAutoStartPermission(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> true
            manufacturer.contains("huawei") -> true
            manufacturer.contains("honor") -> true
            manufacturer.contains("oppo") -> true
            manufacturer.contains("vivo") -> true
            manufacturer.contains("oneplus") -> true
            manufacturer.contains("meizu") -> true
            manufacturer.contains("samsung") -> true
            else -> false
        }
    }

    /**
     * 获取厂商名称
     */
    fun getManufacturerName(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> "小米"
            manufacturer.contains("huawei") -> "华为"
            manufacturer.contains("honor") -> "荣耀"
            manufacturer.contains("oppo") -> "OPPO"
            manufacturer.contains("vivo") -> "vivo"
            manufacturer.contains("oneplus") -> "一加"
            manufacturer.contains("meizu") -> "魅族"
            manufacturer.contains("samsung") -> "三星"
            else -> Build.MANUFACTURER
        }
    }

    /**
     * 尝试跳转到自启动设置页面
     */
    fun jumpToAutoStartSetting(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Timber.d("当前设备厂商: $manufacturer")
        
        // 根据厂商获取可能的Intent列表
        val intents = when {
            manufacturer.contains("xiaomi") -> getMiuiAutoStartIntents()
            manufacturer.contains("huawei") -> getHuaweiAutoStartIntents()
            manufacturer.contains("honor") -> getHonorAutoStartIntents()
            manufacturer.contains("oppo") -> getOppoAutoStartIntents()
            manufacturer.contains("vivo") -> getVivoAutoStartIntents()
            manufacturer.contains("oneplus") -> getOnePlusAutoStartIntents()
            manufacturer.contains("meizu") -> getMeizuAutoStartIntents()
            manufacturer.contains("samsung") -> getSamsungAutoStartIntents()
            else -> getGenericAutoStartIntents()
        }

        // 尝试每个Intent，直到找到可用的
        for (intent in intents) {
            try {
                if (isIntentAvailable(context, intent)) {
                    context.startActivity(intent)
                    Timber.i("成功跳转到自启动设置: ${intent.component}")
                    
                    // 为华为设备添加特殊的用户提示
                    if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                        showHuaweiUserGuide(context)
                    } else {
                        showGeneralUserGuide(context)
                    }
                    return true
                } else {
                    Timber.d("Intent不可用: ${intent.component}")
                }
            } catch (e: Exception) {
                Timber.d("Intent启动失败: ${intent.component}, 错误: ${e.message}")
            }
        }
        
        // 如果所有特定Intent都失败，尝试通用的应用设置页面
        return tryGenericAppSettings(context)
    }

    /**
     * 检查Intent是否可用
     */
    private fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    }

    /**
     * 小米自启动设置Intent列表
     */
    private fun getMiuiAutoStartIntents(): List<Intent> {
        return listOf(
            // MIUI 12+ 新版本
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // MIUI 旧版本备用
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.securityadd.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 更旧版本的MIUI
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 安全中心主页
            Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.securitycenter.Main"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 华为自启动设置Intent列表
     * 集成了PermissionUtils中的优化实现
     */
    private fun getHuaweiAutoStartIntents(): List<Intent> {
        return listOf(
            // 优先使用系统管理器主界面（来自PermissionUtils的优化）
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.mainscreen.MainScreenActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 直接到应用启动管理页面
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 应用控制页面
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // 系统管理器主页面（备用）
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 荣耀自启动设置Intent列表
     */
    private fun getHonorAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * OPPO自启动设置Intent列表
     */
    private fun getOppoAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * vivo自启动设置Intent列表
     */
    private fun getVivoAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 一加自启动设置Intent列表
     */
    private fun getOnePlusAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 魅族自启动设置Intent列表
     */
    private fun getMeizuAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.security.SHOW_APPSEC"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.meizu.safe",
                    "com.meizu.safe.SecurityMainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 三星自启动设置Intent列表
     */
    private fun getSamsungAutoStartIntents(): List<Intent> {
        return listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 通用自启动设置Intent列表
     */
    private fun getGenericAutoStartIntents(): List<Intent> {
        return listOf(
            // 通用应用信息页面
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${android.os.Build.MANUFACTURER}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    /**
     * 尝试通用应用设置页面
     */
    private fun tryGenericAppSettings(context: Context): Boolean {
        return try {
            // 尝试跳转到应用详情页面
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (isIntentAvailable(context, intent)) {
                context.startActivity(intent)
                Timber.i("跳转到应用详情页面")
                true
            } else {
                // 最后尝试系统设置页面
                val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
                settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(settingsIntent)
                Timber.i("跳转到系统设置页面")
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "所有跳转方案都失败")
            false
        }
    }

    /**
     * 获取自启动权限设置说明
     */
    fun getAutoStartGuide(): String {
        val manufacturer = getManufacturerName()
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi" -> "请在MIUI的「自启动管理」中允许本应用自启动"
            "huawei" -> "请在华为手机管家的「应用启动管理」中允许本应用自动管理或手动管理所有权限"
            "honor" -> "请在荣耀手机管家的「应用启动管理」中允许本应用自动管理或手动管理所有权限"
            "oppo" -> "请在ColorOS的「自启动管理」中允许本应用自启动"
            "vivo" -> "请在vivo的「后台应用管理」中允许本应用自启动"
            "oneplus" -> "请在一加的「自启动权限管理」中允许本应用自启动"
            "meizu" -> "请在魅族的「权限管理」中允许本应用自启动"
            "samsung" -> "请在三星的「设备维护-电池-应用电源管理」中将本应用设为不受限制"
            else -> "请在${manufacturer}的自启动或应用权限管理中允许本应用自启动"
        }
    }

    /**
     * 显示华为设备的用户指导
     */
    private fun showHuaweiUserGuide(context: Context) {
        Toast.makeText(
            context,
            "请在系统管理器中找到并点击「应用启动管理」，然后找到【${getAppName(context)}】并开启自启动权限",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 显示通用的用户指导
     */
    private fun showGeneralUserGuide(context: Context) {
        Toast.makeText(
            context,
            "已跳转到设置页面，请找到【${getAppName(context)}】并开启自启动权限",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 获取应用名称
     */
    private fun getAppName(context: Context): String {
        return try {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        } catch (e: Exception) {
            context.packageName
        }
    }
} 