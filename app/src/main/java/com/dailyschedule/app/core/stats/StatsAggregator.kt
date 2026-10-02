package com.dailyschedule.app.core.stats

import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.StatBucket
import java.time.LocalDate

/**
 * 统计页的图表数据聚合。
 *
 * **纯函数**：不碰 IO、不碰 Context、不读系统时间。
 * 「一周里哪几天有数据」「空的那格补不补零」「横轴刻度对不对」
 * 这些最容易出错、又最难在真机上肉眼发现的地方，全在这里被单测钉死。
 *
 * ## 一条贯穿全文件的规则：**空格子要留着，值填 0**
 * 图表的数据源不能是「只有值的列表」。一周里只有周三有数据时，
 * 长度为 1 的列表画出来是贴着左边的一根柱子，而正确画法是中间那根。
 * 所以这里所有函数都返回**定长、带 key** 的 [StatBucket] 列表，
 * 由界面按 key 摆放，界面不需要知道"缺了哪几天"。
 *
 * ## 日期的归属一律走 [DayBoundary]
 * 日切时刻是用户可配的（默认凌晨 4 点）。凌晨 2 点记的一笔账属于前一天，
 * 如果这里图省事用 `LocalDate.ofInstant(...)` 直接取日期，
 * 那笔账会跑到第二天的柱子上 —— 用户看到的分布图和明细列表对不上，
 * 而这种错不会崩溃，只会让人怀疑数据。
 */
object StatsAggregator {

    /** ISO 星期号范围：1=周一 … 7=周日。展示顺序固定周一起，不跟 weekStartDay 走。 */
    val WEEKDAYS: IntRange = 1..7

    /**
     * 专注时长的周分布。
     *
     * @param sessions 必须是**已完成**会话（调用方传 `observeInRange` 的结果）
     */
    fun focusByWeekday(
        sessions: List<FocusSession>,
        boundary: DayBoundary,
    ): List<StatBucket> {
        val totals = MutableList(WEEKDAYS.count()) { 0L }
        sessions.forEach { session ->
            val weekday = boundary.businessDateOf(session.startWallClockMs).dayOfWeek.value
            totals[weekday - 1] += session.durationMs ?: 0L
        }
        return WEEKDAYS.map { StatBucket(it, totals[it - 1]) }
    }

    /** 消费金额的周分布。调用方只需保证列表里都是支出（收入不该进消费图）。 */
    fun expenseByWeekday(
        expenses: List<Expense>,
        boundary: DayBoundary,
    ): List<StatBucket> {
        val totals = MutableList(WEEKDAYS.count()) { 0L }
        expenses.forEach { expense ->
            val weekday = boundary.businessDateOf(expense.occurredAt).dayOfWeek.value
            totals[weekday - 1] += expense.amountCents
        }
        return WEEKDAYS.map { StatBucket(it, totals[it - 1]) }
    }

    /**
     * 按天的消费趋势。
     *
     * @param startDate 第一个刻度所属的业务日期
     * @param days 刻度个数（本周 7、本月 28~31、今日 1）
     * @return key 为 **0 基天数**（第几天），便于界面等距摆放
     */
    fun dailyExpenseTrend(
        expenses: List<Expense>,
        boundary: DayBoundary,
        startDate: LocalDate,
        days: Int,
    ): List<StatBucket> {
        require(days > 0) { "刻度天数必须为正，实际为 $days" }
        val totals = HashMap<LocalDate, Long>()
        expenses.forEach { expense ->
            val date = boundary.businessDateOf(expense.occurredAt)
            totals[date] = (totals[date] ?: 0L) + expense.amountCents
        }
        return (0 until days).map { offset ->
            StatBucket(offset, totals[startDate.plusDays(offset.toLong())] ?: 0L)
        }
    }

    /**
     * 按月的消费趋势。
     *
     * @return key 为月份 1..12，永远是 12 个刻度，没有记录的月份画 0
     */
    fun monthlyExpenseTrend(
        expenses: List<Expense>,
        boundary: DayBoundary,
        year: Int,
    ): List<StatBucket> {
        val totals = LongArray(MONTHS_PER_YEAR)
        expenses.forEach { expense ->
            val date = boundary.businessDateOf(expense.occurredAt)
            // 跨年边界：日切 4 点时，1 月 1 日 02:00 的业务日期属于上一年 12 月，
            // 这条判断决定它该画在哪一年的图里，漏掉它年末那笔会凭空消失
            if (date.year == year) totals[date.monthValue - 1] += expense.amountCents
        }
        return (1..MONTHS_PER_YEAR).map { month -> StatBucket(month, totals[month - 1]) }
    }

    /**
     * 均值。次数为 0 时返回 0 而不是抛异常 ——
     * 「这个周期还没专注过」是正常状态，界面要显示 0，不是崩掉。
     */
    fun average(totalMs: Long, count: Int): Long =
        if (count <= 0) 0L else totalMs / count

    /**
     * 把一长串刻度标签抽稀到 [maxLabels] 个左右，抽掉的位置留空串。
     *
     * **留空串而不是缩短列表**：折线图的每个点必须占一个等宽的格子，
     * 少写一个标签会让后面的标签全部左移，看图的人会把「15 号」读成「8 号」。
     * 空串占位、不显示文字，横轴位置才是对的。
     *
     * 首尾一定保留 —— 横轴的两端是"这段数据从哪到哪"，比中间的刻度更重要。
     * 也正因为要保住末位，实际条数可能比 [maxLabels] 多一条。
     */
    fun sparseLabels(labels: List<String>, maxLabels: Int): List<String> {
        require(maxLabels > 0) { "刻度上限必须为正，实际为 $maxLabels" }
        if (labels.size <= maxLabels) return labels
        val step = (labels.size + maxLabels - 1) / maxLabels
        return labels.mapIndexed { index, label ->
            if (index % step == 0 || index == labels.lastIndex) label else ""
        }
    }

    /**
     * 一组数值 → 整数百分比，**保证加起来正好 100**。
     *
     * 逐个四舍五入会把三等分变成 33/33/33（看起来只有 99%），
     * 把七等分变成 14×7=98。用户没法判断这是"我记错了"还是"App 算错了"。
     * 用最大余数法：先取整，剩下的名额按小数部分从大到小补。
     *
     * 全为 0 时返回全 0，不制造一个凭空出现的 100%。
     */
    fun percentShares(values: List<Long>): List<Int> {
        val total = values.sum()
        if (total <= 0L) return values.map { 0 }

        val exact = values.map { it.toDouble() * 100.0 / total.toDouble() }
        val floors = exact.map { it.toInt() }
        var remaining = 100 - floors.sum()

        // 按小数部分降序补名额；用下标排序保证结果稳定（同分时按原顺序）
        val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
        val result = floors.toMutableList()
        for (index in order) {
            if (remaining <= 0) break
            result[index] += 1
            remaining -= 1
        }
        return result
    }

    private const val MONTHS_PER_YEAR = 12
}
