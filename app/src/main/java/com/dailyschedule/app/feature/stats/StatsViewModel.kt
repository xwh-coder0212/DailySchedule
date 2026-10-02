package com.dailyschedule.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.stats.StatsAggregator
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.CategoryAmount
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.ProjectDuration
import com.dailyschedule.app.domain.model.StatBucket
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class StatsPeriod { TODAY, WEEK, MONTH, YEAR }

/** 统计页的两个分段。时间与钱在同一个 Tab 里切换，不再各占一页。 */
enum class StatsSegment { FOCUS, EXPENSE }

/** 趋势图的横轴粒度。今天/本周/本月按天，本年按月。 */
enum class TrendUnit { DAY, MONTH }

data class StatsUiState(
    val segment: StatsSegment = StatsSegment.FOCUS,
    val period: StatsPeriod = StatsPeriod.WEEK,

    // ── 专注 ──
    val studyMs: Long = 0L,
    val sessionCount: Int = 0,
    val byProject: List<ProjectDuration> = emptyList(),
    val focusByWeekday: List<StatBucket> = emptyList(),

    // ── 消费 ──
    val expenseCents: Long = 0L,
    val byCategory: List<CategoryAmount> = emptyList(),
    val expenseByWeekday: List<StatBucket> = emptyList(),
    val expenseTrend: List<StatBucket> = emptyList(),
    val trendLabels: List<String> = emptyList(),
    val trendUnit: TrendUnit = TrendUnit.DAY,

    val isLoading: Boolean = true,
) {
    /** 均值：总时长 ÷ 次数。次数为 0 时聚合层给 0，界面显示 0 而不是崩 */
    val averageMs: Long get() = StatsAggregator.average(studyMs, sessionCount)

    val hasFocusData: Boolean get() = sessionCount > 0
    val hasExpenseData: Boolean get() = expenseCents > 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val expenseRepository: ExpenseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    // 默认落到「本周」：参考图里统计页的两张图都是本周视角，
    // 而单看今日的分布图只有一根柱子，几乎不传递信息
    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val _segment = MutableStateFlow(StatsSegment.FOCUS)
    val segment: StateFlow<StatsSegment> = _segment.asStateFlow()

    fun onPeriodSelected(period: StatsPeriod) {
        _period.value = period
    }

    fun onSegmentSelected(segment: StatsSegment) {
        _segment.value = segment
    }

    /**
     * 周期区间一律由 [DayBoundary] 算出，禁止 `LocalDate.now()` 直接算，
     * SQL 里也不允许出现日期函数 —— 日切时刻是用户可配的，
     * 只有走同一个换算层，跨月跨年与日切边界才可能被单测覆盖。
     */
    val state: StateFlow<StatsUiState> =
        combine(_period, _segment, preferencesRepository.observe()) { period, segment, prefs ->
            Triple(period, segment, prefs.dayStartHour to prefs.weekStartDay)
        }
            .flatMapLatest { (period, segment, dayConfig) ->
                val (dayStartHour, weekStartDay) = dayConfig
                val boundary = DayBoundary(dayStartHour, weekStartDay)
                val date = boundary.businessDateOf(clock.wallClockMillis())
                val range = when (period) {
                    StatsPeriod.TODAY -> boundary.rangeOf(date)
                    StatsPeriod.WEEK -> boundary.weekRangeOf(date)
                    StatsPeriod.MONTH -> boundary.monthRangeOf(date)
                    StatsPeriod.YEAR -> boundary.yearRangeOf(date)
                }
                val from = range.first
                val to = range.last + 1

                combine(
                    focusPart(boundary, from, to),
                    expensePart(boundary, period, date, from, to),
                ) { focus, expense ->
                    StatsUiState(
                        segment = segment,
                        period = period,
                        studyMs = focus.totalMs,
                        sessionCount = focus.count,
                        byProject = focus.byProject,
                        focusByWeekday = focus.byWeekday,
                        expenseCents = expense.totalCents,
                        byCategory = expense.byCategory,
                        expenseByWeekday = expense.byWeekday,
                        expenseTrend = expense.trend.points,
                        trendLabels = StatsAggregator.sparseLabels(
                            expense.trend.labels, MAX_TREND_LABELS,
                        ),
                        trendUnit = trendUnitOf(period),
                        isLoading = false,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), StatsUiState())

    private fun focusPart(boundary: DayBoundary, from: Long, to: Long): Flow<FocusPart> = combine(
        sessionRepository.observeTotalDuration(from, to),
        sessionRepository.observeCompletedCount(from, to),
        sessionRepository.observeDurationByProject(from, to),
        sessionRepository.observeInRange(from, to),
    ) { totalMs, count, byProject, sessions ->
        FocusPart(
            totalMs = totalMs,
            count = count,
            byProject = byProject,
            byWeekday = StatsAggregator.focusByWeekday(sessions, boundary),
        )
    }

    private fun expensePart(
        boundary: DayBoundary,
        period: StatsPeriod,
        date: LocalDate,
        from: Long,
        to: Long,
    ): Flow<ExpensePart> =
        combine(
            expenseRepository.observeTotalCents(ExpenseType.EXPENSE, from, to),
            expenseRepository.observeTotalByCategory(ExpenseType.EXPENSE, from, to),
            expenseRepository.observeInRange(ExpenseType.EXPENSE, from, to),
        ) { totalCents, byCategory, expenses ->
            ExpensePart(
                totalCents = totalCents,
                byCategory = byCategory,
                byWeekday = StatsAggregator.expenseByWeekday(expenses, boundary),
                trend = buildTrend(boundary, period, date, expenses),
            )
        }

    /**
     * 趋势线的刻度与横轴标签。今天/本周/本月按天，本年按月 ——
     * 一年画 365 个点在手机上是一堵墙，看趋势只需要 12 个刻度。
     *
     * [period] 由调用方从上游捕获后传进来，**不读 `_period.value`**：
     * 那是个可变状态，在流重组的中途读它，可能拿到下一次切换后的值，
     * 于是"图是本周的、横轴刻度却是本月的"。
     */
    private fun buildTrend(
        boundary: DayBoundary,
        period: StatsPeriod,
        date: LocalDate,
        expenses: List<Expense>,
    ): TrendSpec = when (period) {
        StatsPeriod.TODAY -> dayTrend(boundary, expenses, date, days = 1)
        StatsPeriod.WEEK -> dayTrend(boundary, expenses, boundary.weekStartDateOf(date), DAYS_PER_WEEK)
        StatsPeriod.MONTH -> dayTrend(boundary, expenses, date.withDayOfMonth(1), date.lengthOfMonth())
        StatsPeriod.YEAR -> TrendSpec(
            points = StatsAggregator.monthlyExpenseTrend(expenses, boundary, date.year),
            labels = (1..MONTHS_PER_YEAR).map { it.toString() },
        )
    }

    /** 按天的趋势。标签用「几号」而不是星期几：跨月时星期几会重复，几号不会。 */
    private fun dayTrend(
        boundary: DayBoundary,
        expenses: List<Expense>,
        startDate: LocalDate,
        days: Int,
    ): TrendSpec = TrendSpec(
        points = StatsAggregator.dailyExpenseTrend(expenses, boundary, startDate, days),
        labels = (0 until days).map { startDate.plusDays(it.toLong()).dayOfMonth.toString() },
    )

    private fun trendUnitOf(period: StatsPeriod): TrendUnit = when (period) {
        StatsPeriod.YEAR -> TrendUnit.MONTH
        else -> TrendUnit.DAY
    }

    private data class FocusPart(
        val totalMs: Long,
        val count: Int,
        val byProject: List<ProjectDuration>,
        val byWeekday: List<StatBucket>,
    )

    private data class ExpensePart(
        val totalCents: Long,
        val byCategory: List<CategoryAmount>,
        val byWeekday: List<StatBucket>,
        val trend: TrendSpec,
    )

    /** 趋势图的纵轴值与横轴标签。两者必须等长，所以绑在一起传，不给拆散的机会。 */
    private data class TrendSpec(val points: List<StatBucket>, val labels: List<String>)

    private companion object {
        const val DAYS_PER_WEEK = 7
        const val MONTHS_PER_YEAR = 12

        /** 横轴最多显示几个刻度。再多就挤在一起，不如抽稀 */
        const val MAX_TREND_LABELS = 6
    }
}
