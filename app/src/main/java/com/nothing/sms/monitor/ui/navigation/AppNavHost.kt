package com.nothing.sms.monitor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
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
    object Home : AppDestination("home", "消息中心", Icons.Filled.Notifications)
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                appTabItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selected,
                        alwaysShowLabel = true,
                        onClick = {
                            try {
                                // 清理回退栈到起始页面
                                navController.popBackStack(
                                    navController.graph.findStartDestination().id,
                                    inclusive = false
                                )
                                // 如果目标不在回退栈中，则导航到目标
                                if (!navController.popBackStack(item.route, inclusive = false)) {
                                    navController.navigate(item.route) {
                                        launchSingleTop = true
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "导航到 ${item.route} 时出错")
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