package com.dailyschedule.app.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationFormatterTest {

    private val minute = 60_000L
    private val hour = 3_600_000L

    @Test
    fun `时长不足一小时只显示分钟`() {
        assertThat(DurationFormatter.duration(40 * minute)).isEqualTo("40m")
        assertThat(DurationFormatter.duration(0)).isEqualTo("0m")
    }

    @Test
    fun `时长超过一小时显示小时与分钟`() {
        assertThat(DurationFormatter.duration(2 * hour + 35 * minute)).isEqualTo("2h 35m")
    }

    @Test
    fun `整小时不显示零分钟`() {
        assertThat(DurationFormatter.duration(2 * hour)).isEqualTo("2h")
    }

    @Test
    fun `不足一分钟的部分直接舍去不四舍五入`() {
        assertThat(DurationFormatter.duration(39 * minute + 59_000L)).isEqualTo("39m")
    }

    @Test
    fun `计时读数补零到两位`() {
        assertThat(DurationFormatter.clock(0)).isEqualTo("00:00:00")
        assertThat(DurationFormatter.clock(2 * hour + 35 * minute + 12_000L))
            .isEqualTo("02:35:12")
        assertThat(DurationFormatter.clock(40 * minute)).isEqualTo("00:40:00")
    }

    @Test
    fun `金额为整数时不显示角分`() {
        assertThat(DurationFormatter.amount(4_700L)).isEqualTo("¥ 47")
        assertThat(DurationFormatter.amount(0)).isEqualTo("¥ 0")
    }

    @Test
    fun `金额带千分位`() {
        assertThat(DurationFormatter.amount(128_600L)).isEqualTo("¥ 1,286")
        assertThat(DurationFormatter.amount(1_234_567_00L)).isEqualTo("¥ 1,234,567")
    }

    @Test
    fun `金额有角分时保留两位`() {
        assertThat(DurationFormatter.amount(1_250L)).isEqualTo("¥ 12.50")
        assertThat(DurationFormatter.amount(1_205L)).isEqualTo("¥ 12.05")
    }

    @Test
    fun `负数金额保留符号`() {
        assertThat(DurationFormatter.amount(-4_700L)).isEqualTo("-¥ 47")
    }

    @Test
    fun `amountExact 固定两位小数，便于账单竖排对齐`() {
        assertThat(DurationFormatter.amountExact(0L)).isEqualTo("¥ 0.00")
        assertThat(DurationFormatter.amountExact(2_800L)).isEqualTo("¥ 28.00")
        assertThat(DurationFormatter.amountExact(1_205L)).isEqualTo("¥ 12.05")
        assertThat(DurationFormatter.amountExact(128_450L)).isEqualTo("¥ 1,284.50")
    }

    @Test
    fun `amountExact 对支出显示负号`() {
        assertThat(DurationFormatter.amountExact(-2_800L)).isEqualTo("-¥ 28.00")
    }

    @Test
    fun `日历天数差跨零点算一天 —— 23点59到次日0点01只是两分钟但已是另一天`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val beforeMidnight = java.time.LocalDateTime.of(2026, 9, 10, 23, 59).atZone(zone).toInstant().toEpochMilli()
        val afterMidnight = java.time.LocalDateTime.of(2026, 9, 11, 0, 1).atZone(zone).toInstant().toEpochMilli()

        assertThat(DurationFormatter.calendarDaysBetween(beforeMidnight, afterMidnight, zone))
            .isEqualTo(1L)
    }

    @Test
    fun `日历天数差同一天为 0，往前为负`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val morning = java.time.LocalDateTime.of(2026, 9, 10, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val sameDayEvening = java.time.LocalDateTime.of(2026, 9, 10, 23, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDayMorning = java.time.LocalDateTime.of(2026, 9, 11, 8, 0).atZone(zone).toInstant().toEpochMilli()

        // 同一天里的先后不影响天数差，都是 0
        assertThat(DurationFormatter.calendarDaysBetween(morning, sameDayEvening, zone)).isEqualTo(0L)
        assertThat(DurationFormatter.calendarDaysBetween(sameDayEvening, morning, zone)).isEqualTo(0L)
        // 从后一天回看前一天是 -1 —— DayLabel 正是靠这个负数判「昨天」
        assertThat(DurationFormatter.calendarDaysBetween(nextDayMorning, morning, zone)).isEqualTo(-1L)
    }

    @Test
    fun `localDateOf 拆出年月日供界面拼文案`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val ms = java.time.LocalDateTime.of(2026, 9, 10, 12, 30).atZone(zone).toInstant().toEpochMilli()

        val parts = DurationFormatter.localDateOf(ms, zone)

        assertThat(parts.year).isEqualTo(2026)
        assertThat(parts.month).isEqualTo(9)
        assertThat(parts.day).isEqualTo(10)
    }
}
