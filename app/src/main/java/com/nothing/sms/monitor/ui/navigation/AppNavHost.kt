package com.nothing.sms.monitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nothing.sms.monitor.ui.screens.AboutScreen
import com.nothing.sms.monitor.ui.screens.HomeScreen
import com.nothing.sms.monitor.ui.screens.RecordsScreen
import com.nothing.sms.monitor.ui.screens.SettingsScreen
import timber.log.Timber

/**
 * 应用导航路由
 */
sealed class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : AppDestination("home", "短信监控", Icons.AutoMirrored.Filled.Message)
    object Records : AppDestination("records", "推送记录", Icons.Default.History)
    object Settings : AppDestination("settings", "设置", Icons.Default.Settings)
    object About : AppDestination("about", "关于", Icons.Default.Info)
}

/**
 * 所有可用导航项
 */
val appTabItems = listOf(
    AppDestination.Home,
    AppDestination.Records,
    AppDestination.Settings,
    AppDestination.About
)

/**
 * 应用主导航组件
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestination.Home.route
) {
    // 记录上次点击以防止重复点击相同项
    var lastSelectedRoute by rememberSaveable { mutableStateOf(startDestination) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 添加日志记录导航状态变化
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            Timber.d("导航到: ${destination.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                appTabItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selected,
                        onClick = {
                            // 如果点击非当前选中的项，直接导航到目标
                            if (item.route != currentRoute) {
                                lastSelectedRoute = item.route
                                // 清理回退栈，确保导航直接跳转
                                navController.navigate(item.route) {
                                    // 清理回退栈，直接跳转到目标
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // 防止多次点击创建多个实例
                                    launchSingleTop = true
                                    // 恢复状态
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen()
            }
            composable(AppDestination.Records.route) {
                RecordsScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
            composable(AppDestination.About.route) {
                AboutScreen()
            }
        }
    }
} 