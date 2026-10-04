package com.dailyschedule.app.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.stats.StatsAggregator
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.component.BarDatum
import com.dailyschedule.app.core.ui.component.DonutSlice
import com.dailyschedule.app.core.ui.component.DsBarChart
import com.dailyschedule.app.core.ui.component.DsDonutChart
import com.dailyschedule.app.core.ui.component.DsLegendGrid
import com.dailyschedule.app.core.ui.component.DsLineChart
import com.dailyschedule.app.core.ui.component.LegendItem
import com.dailyschedule.app.core.ui.theme.ProjectColors
import com.dailyschedule.app.domain.model.StatBucket

/**
 * 统计页。
 *
 * ## Rev2 改了什么
 * 时间与钱从「上下两段」改成**同一页里的两个分段**（专注 / 消费）。
 * 依据是 §3.2：底栏只剩三格，而"今天学了多久"和"今天花了多少"
 * 本来就是同一个问题的两面 —— 都是"我今天投入了什么"。
 * 分段切换让每个分段都能独占整屏，塞得下环形、柱状、折线三张图。
 *
 * ## 图表口径
 * 所有图都只陈述事实（哪段时间投入了多少），不做目标、不判好坏 ——
 * 这是 Rev2 §4 划的游戏化分界线。所以这里没有"达成率""超额""连续 N 天"。
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekdayLabels = stringArrayResource(R.array.weekday_short).toList()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SegmentSelector(
            selected = state.segment,
            onSelected = viewModel::onSegmentSelected,
        )
        PeriodSelector(
            selected = state.period,
            onSelected = viewModel::onPeriodSelected,
        )

        when (state.segment) {
            StatsSegment.FOCUS -> FocusSection(state, weekdayLabels)
            StatsSegment.EXPENSE -> ExpenseSection(state, weekdayLabels)
        }

        Box(Modifier.padding(bottom = 32.dp))
    }
}

// ── 专注分段 ──

@Composable
private fun FocusSection(
    state: StatsUiState,
    weekdayLabels: List<String>,
) {
    SectionCard(title = stringResource(R.string.stats_time_section)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BigStatCell(
                label = stringResource(R.string.stats_period_total),
                value = state.sessionCount.toString(),
            )
            BigStatCell(
                label = stringResource(R.string.stats_total_duration),
                value = DurationFormatter.duration(state.studyMs),
            )
            BigStatCell(
                label = stringResource(R.string.stats_average),
                value = DurationFormatter.duration(state.averageMs),
            )
        }
    }

    if (state.sessionCount <= 0) {
        EmptyHint()
    } else {
        val rows =
            state.byProject
                .filter { it.totalDurationMs > 0L }
                .map { ShareRow(it.projectName, ProjectColors.parse(it.colorHex), it.totalDurationMs) }
                .withUnassigned(
                    total = state.studyMs,
                    label = stringResource(R.string.stats_share_other),
                )
        if (rows.isNotEmpty()) {
            ShareCard(
                title = stringResource(R.string.stats_focus_share),
                rows = rows,
                centerValue = DurationFormatter.duration(state.studyMs),
                centerLabel = stringResource(R.string.stats_center_total),
            )
        }

        SectionCard(title = stringResource(R.string.stats_focus_distribution)) {
            DsBarChart(
                bars = state.focusByWeekday.toBars(weekdayLabels),
                barColor = MaterialTheme.colorScheme.primary,
                zeroColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// ── 消费分段 ──

@Composable
private fun ExpenseSection(
    state: StatsUiState,
    weekdayLabels: List<String>,
) {
    SectionCard(title = stringResource(R.string.stats_money_section)) {
        BigStatCell(
            label = stringResource(R.string.stats_expense_total),
            value = DurationFormatter.amount(state.expenseCents),
        )
    }

    if (state.expenseCents <= 0L) {
        EmptyHint()
    } else {
        val rows =
            state.byCategory
                .filter { it.totalCents > 0L }
                .map { ShareRow(it.categoryName, ProjectColors.parse(it.colorHex), it.totalCents) }
                .withUnassigned(
                    total = state.expenseCents,
                    label = stringResource(R.string.stats_share_other),
                )
        if (rows.isNotEmpty()) {
            ShareCard(
                title = stringResource(R.string.stats_category_share),
                rows = rows,
                centerValue = DurationFormatter.amount(state.expenseCents),
                centerLabel = stringResource(R.string.stats_center_total),
            )
        }

        SectionCard(title = stringResource(R.string.stats_expense_distribution)) {
            DsBarChart(
                bars = state.expenseByWeekday.toBars(weekdayLabels),
                barColor = MaterialTheme.colorScheme.primary,
                zeroColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        // 只有一个刻度画不出趋势。今日这张图正好只有一个点，
        // 与其画一根孤零零的柱子冒充折线，不如不画
        if (state.expenseTrend.size >= 2) {
            SectionCard(title = stringResource(R.string.stats_expense_trend)) {
                Column {
                    Text(
                        text =
                            when (state.trendUnit) {
                                TrendUnit.DAY -> stringResource(R.string.stats_trend_by_day)
                                TrendUnit.MONTH -> stringResource(R.string.stats_trend_by_month)
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DsLineChart(
                        points = state.expenseTrend.map { it.value },
                        xLabels = state.trendLabels,
                        lineColor = MaterialTheme.colorScheme.primary,
                        // 渐变填充用主题色的低透明度版本，而不是另一个色值 ——
                        // 换主题时面积色跟着走，不会留下一块旧主题的颜色
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = AREA_ALPHA),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

// ── 图表与卡片 ──

/** 环图 + 图例的一行数据。名称与颜色的来源随分段不同，但画法一样。 */
private data class ShareRow(val label: String, val color: Color, val value: Long)

/**
 * 「占比」卡片：左边环图，右边图例。
 *
 * 百分比统一在这里算，且用 [StatsAggregator.percentShares] 保证**加起来正好 100**。
 * 若让每个调用点自己除再四舍五入，三项目就会显示成 33/33/33。
 *
 * 图例**单列**（不是网格）：环图已经占了 132dp，右侧只剩约 120dp，
 * 两列的话每个项目名只能显示两个字，等于没写。
 */
@Composable
private fun ShareCard(
    title: String,
    rows: List<ShareRow>,
    centerValue: String,
    centerLabel: String,
) {
    if (rows.isEmpty()) return
    val shares = StatsAggregator.percentShares(rows.map { it.value })
    // mapIndexed 是 inline 函数，它的 lambda 会被内联进本 Composable，
    // 所以这里可以直接用 stringResource；比 LocalContext.current.getString 严格更优：
    // 前者跟随 Configuration 变化重新取值，后者可能拿到变更前的旧资源
    val legendItems =
        rows.mapIndexed { index, row ->
            LegendItem(
                label = row.label,
                // percentShares 与 rows 等长，下标必定安全
                valueText = stringResource(R.string.stats_percent, shares[index]),
                color = row.color,
            )
        }

    SectionCard(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DsDonutChart(
                slices = rows.map { DonutSlice(it.value, it.color) },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                centerValue = centerValue,
                centerLabel = centerLabel,
            )
            DsLegendGrid(
                items = legendItems,
                columns = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.stats_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 分段切换（专注 / 消费）。
 *
 * 与 [PeriodSelector] 长得像但**不该合并**：这一排切的是"看哪个账本"，
 * 下一排切的是"看多长". 合并成一个四选一会让"本周的消费"这种组合无处表达。
 */
@Composable
private fun SegmentSelector(
    selected: StatsSegment,
    onSelected: (StatsSegment) -> Unit,
) {
    val items =
        listOf(
            StatsSegment.FOCUS to R.string.stats_segment_focus,
            StatsSegment.EXPENSE to R.string.stats_segment_expense,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (segment, labelRes) ->
            val isSelected = segment == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        )
                        .clickable { onSelected(segment) }
                        .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: StatsPeriod,
    onSelected: (StatsPeriod) -> Unit,
) {
    val items =
        listOf(
            StatsPeriod.TODAY to R.string.stats_period_today,
            StatsPeriod.WEEK to R.string.stats_period_week,
            StatsPeriod.MONTH to R.string.stats_period_month,
            StatsPeriod.YEAR to R.string.stats_period_year,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (period, labelRes) ->
            val isSelected = period == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                        .clickable { onSelected(period) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun BigStatCell(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = value,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 补上「没有归到任何项目/分类」的那部分，凑不齐就加一行「其他」。
 *
 * 为什么必须补：明细查询是 `INNER JOIN projects`，没挂待办的会话根本不在结果里；
 * 分类被删掉之后那一笔也一样查不出来。于是「总时长 10 小时」配一张
 * 加起来只有 6 小时的环图 —— 两个数字都来自数据库，但没人解释那 4 小时去哪了。
 * 用户只能怀疑是自己记错了，或者 App 算错了。
 *
 * 差额为 0 时不加这一行：一个恒为 0 的「其他」比不显示更让人困惑。
 */
private fun List<ShareRow>.withUnassigned(
    total: Long,
    label: String,
): List<ShareRow> {
    val accounted = sumOf { it.value }
    val missing = total - accounted
    return if (missing > 0L) this + ShareRow(label, ProjectColors.Fallback, missing) else this
}

/**
 * 周分布 → 柱图数据。
 *
 * 标签按 `key - 1` 取 `weekday_short`：该数组下标 = ISO 星期号 - 1，
 * 而聚合层保证 key 恒为 1..7（周一到周日），两边口径一致。
 * 越界时留空串而不是抛异常 —— 标签错一格是显示问题，崩溃是可用性问题。
 */
private fun List<StatBucket>.toBars(weekdayLabels: List<String>): List<BarDatum> =
    map { bucket ->
        BarDatum(
            label = weekdayLabels.getOrElse(bucket.key - 1) { "" },
            value = bucket.value,
        )
    }

private const val AREA_ALPHA = 0.22f
