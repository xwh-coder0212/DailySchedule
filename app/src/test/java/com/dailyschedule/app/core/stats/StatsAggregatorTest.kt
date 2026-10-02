package com.dailyschedule.app.core.stats

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 图表聚合测试。
 *
 * 这一层守的是「图上的柱子和点落在哪一格」。它比数字本身更容易错：
 * 数字错了会看出来，格子错一位只会让人默默地把 15 号读成 8 号。
 *
 * 固定时区与日切时刻，所有期望值都是手算的（不是拿实现再算一遍）。
 */
class StatsAggregatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 日切凌晨 4 点、周一为一周之首 —— 与 App 默认一致 */
    private fun boundary(dayStartHour: Int = 4) =
        DayBoundary(dayStartHour = dayStartHour, weekStartDay = 1, zoneId = zone)

    // ── 周分布 ──

    @Test
    fun `专注周分布永远是七格，没有记录的日子补 0`() {
        // 2026-09-10 是周四（ISO 4）
        val buckets = StatsAggregator.focusByWeekday(
            sessions = listOf(session(startMs = wall(2026, 9, 10, 8, 0), minutes = 60)),
            boundary = boundary(),
        )

        assertThat(buckets.map { it.key }).containsExactly(1, 2, 3, 4, 5, 6, 7).inOrder()
        assertThat(buckets.map { it.value })
            .containsExactly(0L, 0L, 0L, 3_600_000L, 0L, 0L, 0L)
            .inOrder()
    }

    @Test
    fun `同一天的多段专注会累加到同一格`() {
        val buckets = StatsAggregator.focusByWeekday(
            sessions = listOf(
                // 周一
                session(startMs = wall(2026, 9, 7, 8, 0), minutes = 30),
                session(startMs = wall(2026, 9, 7, 14, 0), minutes = 45),
                // 周日
                session(startMs = wall(2026, 9, 13, 9, 0), minutes = 15),
            ),
            boundary = boundary(),
        )

        assertThat(buckets[0].value).isEqualTo(75 * 60_000L)
        assertThat(buckets[6].value).isEqualTo(15 * 60_000L)
    }

    @Test
    fun `凌晨的记录按日切归属到前一天`() {
        // 日切 4 点：9-08 凌晨 2 点属于业务日 9-07（周一）
        val buckets = StatsAggregator.focusByWeekday(
            sessions = listOf(session(startMs = wall(2026, 9, 8, 2, 0), minutes = 60)),
            boundary = boundary(),
        )

        assertThat(buckets[0].value).isEqualTo(60 * 60_000L)
        assertThat(buckets[1].value).isEqualTo(0L)
    }

    @Test
    fun `时长缺失的会话按 0 计，不会让整张图崩掉`() {
        val buckets = StatsAggregator.focusByWeekday(
            sessions = listOf(
                session(startMs = wall(2026, 9, 10, 8, 0), minutes = 60, durationMs = null),
            ),
            boundary = boundary(),
        )

        assertThat(buckets.sumOf { it.value }).isEqualTo(0L)
    }

    // ── 日趋势 ──

    @Test
    fun `日趋势刻度数等于天数，没花销的那天是 0`() {
        val buckets = StatsAggregator.dailyExpenseTrend(
            expenses = listOf(expense(occurredAt = wall(2026, 9, 8, 12, 0), cents = 2_800)),
            boundary = boundary(),
            startDate = LocalDate.of(2026, 9, 7),
            days = 7,
        )

        assertThat(buckets).hasSize(7)
        assertThat(buckets.map { it.value })
            .containsExactly(0L, 2_800L, 0L, 0L, 0L, 0L, 0L)
            .inOrder()
    }

    @Test
    fun `日趋势的横轴是 0 基偏移，方便界面等距摆放`() {
        val buckets = StatsAggregator.dailyExpenseTrend(
            expenses = emptyList(),
            boundary = boundary(),
            startDate = LocalDate.of(2026, 9, 7),
            days = 3,
        )

        assertThat(buckets.map { it.key }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `凌晨的花销算进前一天那一格`() {
        // 9-08 02:00 属于业务日 9-07，即偏移 0
        val buckets = StatsAggregator.dailyExpenseTrend(
            expenses = listOf(expense(occurredAt = wall(2026, 9, 8, 2, 0), cents = 500)),
            boundary = boundary(),
            startDate = LocalDate.of(2026, 9, 7),
            days = 3,
        )

        assertThat(buckets[0].value).isEqualTo(500L)
        assertThat(buckets[1].value).isEqualTo(0L)
    }

    // ── 月趋势 ──

    @Test
    fun `月趋势永远十二格，跨年的记录不会混进来`() {
        val buckets = StatsAggregator.monthlyExpenseTrend(
            expenses = listOf(
                expense(occurredAt = wall(2026, 3, 15, 12, 0), cents = 1_000),
                expense(occurredAt = wall(2026, 3, 20, 12, 0), cents = 500),
                // 日切 4 点：1 月 1 日 02:00 的业务日期属于上一年 12 月 31 日
                expense(occurredAt = wall(2026, 1, 1, 2, 0), cents = 9_999),
                // 另一个年份，必须被排除
                expense(occurredAt = wall(2025, 3, 15, 12, 0), cents = 7_777),
            ),
            boundary = boundary(),
            year = 2026,
        )

        assertThat(buckets).hasSize(12)
        assertThat(buckets.map { it.key }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            .inOrder()
        assertThat(buckets[2].value).isEqualTo(1_500L)
        assertThat(buckets.sumOf { it.value }).isEqualTo(1_500L)
    }

    // ── 均值与百分比 ──

    @Test
    fun `没有专注次数时均值是 0 而不是除零`() {
        assertThat(StatsAggregator.average(totalMs = 0L, count = 0)).isEqualTo(0L)
        assertThat(StatsAggregator.average(totalMs = 100L, count = -1)).isEqualTo(0L)
    }

    @Test
    fun `均值向下取整，不虚报时长`() {
        // 100 秒 ÷ 3 = 33.3 秒 → 33 秒。宁可少报一秒，也不报一个不存在的时长
        assertThat(StatsAggregator.average(totalMs = 100_000L, count = 3)).isEqualTo(33_333L)
    }

    @Test
    fun `百分比三等分加起来正好 100`() {
        val shares = StatsAggregator.percentShares(listOf(1_000L, 1_000L, 1_000L))

        assertThat(shares.sum()).isEqualTo(100)
        // 补齐的名额给第一个（小数部分并列时按原顺序）
        assertThat(shares).containsExactly(34, 33, 33).inOrder()
    }

    @Test
    fun `百分比覆盖不整齐的分布，误差被补齐而不是丢掉`() {
        val shares = StatsAggregator.percentShares(listOf(1L, 1L, 1L, 1L, 1L, 1L, 1L))

        assertThat(shares.sum()).isEqualTo(100)
    }

    @Test
    fun `全是 0 时每一项都是 0，不凭空补出一份份额`() {
        assertThat(StatsAggregator.percentShares(listOf(0L, 0L, 0L))).containsExactly(0, 0, 0)
        assertThat(StatsAggregator.percentShares(emptyList())).isEmpty()
    }

    @Test
    fun `单个项目占满 100`() {
        assertThat(StatsAggregator.percentShares(listOf(4_800L))).containsExactly(100)
    }

    // ── 刻度抽稀 ──

    @Test
    fun `刻度不超过上限时原样返回`() {
        val labels = listOf("1", "2", "3")
        assertThat(StatsAggregator.sparseLabels(labels, maxLabels = 6)).isEqualTo(labels)
    }

    @Test
    fun `刻度超限时抽稀，但首尾一定保留`() {
        val labels = (1..31).map { it.toString() }
        val sparse = StatsAggregator.sparseLabels(labels, maxLabels = 6)

        // 位置不能变：抽掉的留空串，长度必须与原来一致
        assertThat(sparse).hasSize(31)
        assertThat(sparse.first()).isEqualTo("1")
        assertThat(sparse.last()).isEqualTo("31")
        assertThat(sparse.count { it.isNotEmpty() }).isAtMost(7)
    }

    // ── 构造 ──

    private fun wall(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        startMs: Long,
        minutes: Int,
        durationMs: Long? = minutes * 60_000L,
    ): FocusSession = FocusSession(
        id = 0,
        status = SessionStatus.COMPLETED,
        startElapsedMs = startMs,
        startWallClockMs = startMs,
        endElapsedMs = startMs + (durationMs ?: 0L),
        endWallClockMs = startMs + (durationMs ?: 0L),
        durationMs = durationMs,
        createdAt = startMs,
        updatedAt = startMs,
    )

    private fun expense(occurredAt: Long, cents: Long): Expense = Expense(
        id = 0,
        amountCents = cents,
        categoryId = 1L,
        type = ExpenseType.EXPENSE,
        occurredAt = occurredAt,
    )
}
