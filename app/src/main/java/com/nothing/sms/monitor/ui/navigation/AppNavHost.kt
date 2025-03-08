package com.nothing.sms.monitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nothing.sms.monitor.ui.screens.HomeScreen
import com.nothing.sms.monitor.ui.screens.RecordsScreen
import com.nothing.sms.monitor.ui.screens.SettingsScreen

/**
 * 应用导航路由
 */
sealed class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : AppDestination("home", "短信监控", Icons.Default.Message)
    object Records : AppDestination("records", "推送记录", Icons.Default.History)
    object Settings : AppDestination("settings", "设置", Icons.Default.Settings)
}

/**
 * 所有可用导航项
 */
val appTabItems = listOf(
    AppDestination.Home,
    AppDestination.Records,
    AppDestination.Settings
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                appTabItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                // 避免创建多个实例，返回到起始位置
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // 避免重复点击创建多个实例
                                launchSingleTop = true
                                // 恢复状态
                                restoreState = true
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
        }
    }
} 