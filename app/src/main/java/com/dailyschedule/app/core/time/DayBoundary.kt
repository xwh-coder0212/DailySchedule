package com.dailyschedule.app.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 全 App 唯一的日期口径。
 *
 * 铁律：所有周期性统计查询只接收 [LongRange]（两个 epoch millis），
 * SQL 中禁止出现 `date('now')` / `strftime`，业务代码中禁止直接 `LocalDate.now()`。
 * 日切时刻、周起始日都可由用户配置，把换算放在这里才能被单测覆盖。
 *
 * @param dayStartHour 日切时刻，默认凌晨 4 点。该时刻之前的记录归属前一天。
 * @param weekStartDay 周起始日，1 = 周一（ISO）。
 */
class DayBoundary(
    val dayStartHour: Int,
    val weekStartDay: Int = 1,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    init {
        require(dayStartHour in 0..23) { "dayStartHour 必须在 0..23，实际为 $dayStartHour" }
        require(weekStartDay in 1..7) { "weekStartDay 必须在 1..7（1=周一），实际为 $weekStartDay" }
    }

    /** 时间戳 → 业务日期。日切时刻之前算前一天。 */
    fun businessDateOf(wallClockMs: Long): LocalDate {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(wallClockMs), zoneId)
        return if (local.hour < dayStartHour) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /**
     * 业务日期 → 该日的 [startMs, endMs)。
     *
     * 业务日期 D 的区间 = D 的 dayStartHour 到 D+1 的 dayStartHour。
     * 例：日切 4 点时，业务日 09-07 = 09-07 04:00 → 09-08 04:00。
     */
    fun rangeOf(date: LocalDate): LongRange {
        val start = date.atTime(dayStartHour, 0, 0, 0)
        val end = date.plusDays(1).atTime(dayStartHour, 0, 0, 0)
        return start.toMillis() until end.toMillis()
    }

    /** 业务日期所在周的第一天（按 [weekStartDay]）。热力图要从这一天开始往后数 7 天。 */
    fun weekStartDateOf(date: LocalDate): LocalDate {
        val offset = (date.dayOfWeek.value - weekStartDay + 7) % 7
        return date.minusDays(offset.toLong())
    }

    /** 业务日期所在周的 [startMs, endMs)。 */
    fun weekRangeOf(date: LocalDate): LongRange {
        val weekStart = weekStartDateOf(date)
        val start = weekStart.atTime(dayStartHour, 0, 0, 0)
        val end = weekStart.plusDays(7).atTime(dayStartHour, 0, 0, 0)
        return start.toMillis() until end.toMillis()
    }

    /**
     * 业务日期所在月的 [startMs, endMs)。
     *
     * 注意起点是「当月 1 号的 dayStartHour」而不是 1 号 00:00：
     * 1 号凌晨 00:00–04:00 的业务日期属于上月最后一天，不该算进本月。
     */
    fun monthRangeOf(date: LocalDate): LongRange {
        val start = date.withDayOfMonth(1).atTime(dayStartHour, 0, 0, 0)
        val end = date.plusMonths(1).withDayOfMonth(1).atTime(dayStartHour, 0, 0, 0)
        return start.toMillis() until end.toMillis()
    }

    /** 业务日期所在年的 [startMs, endMs)。同理，起点是 1 月 1 号的 dayStartHour。 */
    fun yearRangeOf(date: LocalDate): LongRange {
        val start = date.withDayOfYear(1).atTime(dayStartHour, 0, 0, 0)
        val end = date.plusYears(1).withDayOfYear(1).atTime(dayStartHour, 0, 0, 0)
        return start.toMillis() until end.toMillis()
    }

    /** 两个整日之间的天数，用于趋势图的横轴刻度。 */
    fun daysBetween(from: LocalDate, to: LocalDate): Int =
        Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays().toInt()

    private fun LocalDateTime.toMillis(): Long = atZone(zoneId).toInstant().toEpochMilli()
}
