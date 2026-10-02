package com.dailyschedule.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailyschedule.app.R

/**
 * 底部导航的三个顶层入口，每个持有独立返回栈。
 *
 * Rev2 把原来的四项（今日 / 统计 / 记录 / 项目）收敛成三项：
 *  - 「今日」并进「统计」。底栏只剩三格时，一个只讲"今天"的 Tab 会和统计页的
 *    「今日」周期完全重复，而它独有的时间轴本来就属于"今天发生了什么"。
 *  - 「记录」拆开：计时归待办（卡片右侧就是「开始」），记一笔独占「记账」。
 *
 * 顺序即优先级：待办是启动页 —— 这个 App 最高频的动作是「选一个待办开始专注」。
 * 图标统一用 Outlined 风格，选中态靠颜色而非填充切换，避免视觉跳变。
 */
enum class TopLevelDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val route: Route,
) {
    PROJECTS(
        labelRes = R.string.tab_projects,
        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
        route = Projects,
    ),
    STATS(
        labelRes = R.string.tab_stats,
        icon = Icons.Outlined.BarChart,
        route = Stats,
    ),
    EXPENSE(
        labelRes = R.string.tab_expense,
        icon = Icons.Outlined.AccountBalanceWallet,
        route = Expense,
    ),
}
