package com.example.ledger.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ledger.R
import com.example.ledger.ui.auth.AuthScreen
import com.example.ledger.ui.calendar.CalendarScreen
import com.example.ledger.ui.category.CategoryScreen
import com.example.ledger.ui.home.HomeScreen
import com.example.ledger.ui.record.RecordEditScreen
import com.example.ledger.ui.settings.SettingsScreen
import com.example.ledger.ui.stats.StatsScreen
import androidx.compose.foundation.layout.padding

/** 底部导航项 */
private data class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector
)

/**
 * 应用导航图。
 * 底部导航仅在四个一级页面显示，记账编辑页与分类管理页为二级页面。
 */
@Composable
fun LedgerNavHost(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val destinations = remember {
        listOf(
            TopLevelDestination(Routes.HOME, R.string.home_title, Icons.Filled.ReceiptLong),
            TopLevelDestination(Routes.CALENDAR, R.string.calendar_title, Icons.Filled.CalendarMonth),
            TopLevelDestination(Routes.STATS, R.string.stats_title, Icons.Filled.PieChart),
            TopLevelDestination(Routes.SETTINGS, R.string.settings_title, Icons.Filled.Person)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (destinations.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // 保持一级页面状态，避免来回切换时重复加载
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.label)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.AUTH) {
                AuthScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onAddRecord = { navController.navigate(Routes.RECORD_NEW) },
                    onEditRecord = { recordId ->
                        navController.navigate(Routes.recordEdit(recordId))
                    }
                )
            }

            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onEditRecord = { recordId ->
                        navController.navigate(Routes.recordEdit(recordId))
                    }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    onEditRecord = { recordId ->
                        navController.navigate(Routes.recordEdit(recordId))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenCategories = { navController.navigate(Routes.CATEGORY) },
                    onSignedOut = {
                        navController.navigate(Routes.AUTH) {
                            // 退出登录后清空整个返回栈，防止用返回键回到已退出账号的页面
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.CATEGORY) {
                CategoryScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RECORD_NEW) {
                RecordEditScreen(
                    recordId = null,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.RECORD_EDIT,
                arguments = listOf(
                    navArgument("recordId") { type = NavType.StringType }
                )
            ) { entry ->
                RecordEditScreen(
                    recordId = entry.arguments?.getString("recordId"),
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
