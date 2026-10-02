package com.dailyschedule.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dailyschedule.app.R
import com.dailyschedule.app.feature.expense.ExpenseEditScreen
import com.dailyschedule.app.feature.expense.ExpenseListScreen
import com.dailyschedule.app.feature.projects.ProjectEditScreen
import com.dailyschedule.app.feature.projects.ProjectSessionsScreen
import com.dailyschedule.app.feature.projects.ProjectsScreen
import com.dailyschedule.app.feature.settings.CategoryManageScreen
import com.dailyschedule.app.feature.settings.SettingsScreen
import com.dailyschedule.app.feature.stats.StatsScreen
import com.dailyschedule.app.feature.transfer.DataTransferScreen

/**
 * 单一 Activity + 底部 3 Tab，每个 Tab 独立返回栈。
 *
 * 独立栈靠三件事：
 *  - popUpTo(startDestination) { saveState = true }   切走时保存栈与滚动位置
 *  - launchSingleTop = true                           重复点同一 Tab 不堆新实例
 *  - restoreState = true                              切回来时恢复
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // 顶层 Tab 才显示顶部栏与底栏；详情页自带自己的 TopAppBar，
    // 这样返回箭头与标题归各页面自己管，不用在这里拼路由判断。
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { dest ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }

    Scaffold(
        topBar = {
            if (currentTopLevel != null) {
                TopAppBar(
                    title = { Text(stringResource(currentTopLevel.labelRes)) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Settings) }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTopLevel != null) {
                DailyScheduleBottomBar(
                    navController = navController,
                    backStackEntry = backStackEntry,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Projects,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Projects> {
                ProjectsScreen(
                    onCreate = { navController.navigate(ProjectEdit(null)) },
                    onEdit = { navController.navigate(ProjectEdit(it)) },
                    onOpenSessions = { navController.navigate(ProjectSessions(it)) },
                )
            }
            composable<Stats> { StatsScreen() }
            composable<Expense> {
                ExpenseListScreen(
                    onAdd = { navController.navigate(ExpenseEdit(null)) },
                    onEdit = { navController.navigate(ExpenseEdit(it)) },
                )
            }
            composable<ExpenseEdit> { entry ->
                val args = entry.toRoute<ExpenseEdit>()
                ExpenseEditScreen(
                    expenseId = args.expenseId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ProjectSessions> { entry ->
                val args = entry.toRoute<ProjectSessions>()
                ProjectSessionsScreen(
                    projectId = args.projectId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ProjectEdit> { entry ->
                val args = entry.toRoute<ProjectEdit>()
                ProjectEditScreen(
                    projectId = args.projectId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Settings> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategoryManager = { navController.navigate(CategoryManager) },
                    onOpenDataTransfer = { navController.navigate(DataTransfer) },
                )
            }
            composable<CategoryManager> {
                CategoryManageScreen(onBack = { navController.popBackStack() })
            }
            composable<DataTransfer> {
                DataTransferScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    val startDestinationId = graph.findStartDestination().id
    navigate(destination.route) {
        popUpTo(startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun DailyScheduleBottomBar(
    navController: NavController,
    backStackEntry: NavBackStackEntry?,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.hasRoute(destination.route::class) } == true

            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
