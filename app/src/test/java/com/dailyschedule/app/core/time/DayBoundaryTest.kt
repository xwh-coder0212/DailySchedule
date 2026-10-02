package com.dailyschedule.app.core.time

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 日期边界。这是全 App 最容易埋 bug 的地方 —— 错了不会崩，只是数字悄悄不对，
 * 等发现时已经攒了几周脏数据。所以每个用例都对应 Phase 4 §7.4 列出的边界。
 */
class DayBoundaryTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val boundary = DayBoundary(dayStartHour = 4, weekStartDay = 1, zoneId = shanghai)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        second: Int = 0,
        millis: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000)
        .atZone(shanghai)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `普通白天归属当天`() {
        assertThat(boundary.businessDateOf(at(2026, 9, 7, 14, 0)))
            .isEqualTo(LocalDate.of(2026, 9, 7))
    }

    @Test
    fun `凌晨日切之前归属前一天`() {
        assertThat(boundary.businessDateOf(at(2026, 9, 7, 3, 30)))
            .isEqualTo(LocalDate.of(2026, 9, 6))
    }

    @Test
    fun `日切临界点归属当天`() {
        assertThat(boundary.businessDateOf(at(2026, 9, 7, 4, 0, 0)))
            .isEqualTo(LocalDate.of(2026, 9, 7))
    }

    @Test
    fun `日切前一毫秒归属前一天`() {
        assertThat(boundary.businessDateOf(at(2026, 9, 7, 3, 59, 59, 999)))
            .isEqualTo(LocalDate.of(2026, 9, 6))
    }

    @Test
    fun `跨午夜会话整段归属开始日不拆分`() {
        // 09-07 23:30 开始，09-08 01:00 结束
        val start = at(2026, 9, 7, 23, 30)
        val end = at(2026, 9, 8, 1, 0)

        val startDate = boundary.businessDateOf(start)
        assertThat(startDate).isEqualTo(LocalDate.of(2026, 9, 7))

        // 归属按开始时间算，结束时间不参与 —— 所以整段都在这一个区间里
        val range = boundary.rangeOf(startDate)
        assertThat(start in range).isTrue()
        // 日切是 04:00：01:00 还没到切点，仍落在同一业务日。
        // 这正是「跨午夜不拆分」的含义 —— 跨过 00:00 不等于跨过业务日。
        assertThat(end in range).isTrue()
    }

    @Test
    fun `月末最后一天归属当月`() {
        val t = at(2026, 9, 30, 23, 0)
        assertThat(boundary.businessDateOf(t)).isEqualTo(LocalDate.of(2026, 9, 30))
        assertThat(t in boundary.monthRangeOf(LocalDate.of(2026, 9, 1))).isTrue()
    }

    @Test
    fun `月初凌晨归属上月`() {
        // 10-01 02:00 在日切 04:00 之前 → 业务日期仍是 09-30
        val t = at(2026, 10, 1, 2, 0)
        assertThat(boundary.businessDateOf(t)).isEqualTo(LocalDate.of(2026, 9, 30))
        assertThat(t in boundary.monthRangeOf(LocalDate.of(2026, 10, 1))).isFalse()
        assertThat(t in boundary.monthRangeOf(LocalDate.of(2026, 9, 1))).isTrue()
    }

    @Test
    fun `年末最后一天归属当年`() {
        val t = at(2026, 12, 31, 23, 59)
        assertThat(boundary.businessDateOf(t)).isEqualTo(LocalDate.of(2026, 12, 31))
        assertThat(t in boundary.yearRangeOf(LocalDate.of(2026, 1, 1))).isTrue()
    }

    @Test
    fun `跨年周落在同一周区间内`() {
        // 2026-12-31 是周四，该周从 12-28（周一）开始
        val range = boundary.weekRangeOf(LocalDate.of(2026, 12, 31))

        assertThat(at(2026, 12, 28, 10, 0) in range).isTrue()
        assertThat(at(2026, 12, 31, 10, 0) in range).isTrue()
        assertThat(at(2027, 1, 1, 10, 0) in range).isTrue() // 跨年后仍属同一周
        assertThat(at(2027, 1, 4, 10, 0) in range).isFalse() // 下一个周一已属新周
    }

    @Test
    fun `业务日区间与归属判定互为逆运算`() {
        // 遍历 72 小时，每 30 分钟取一点，验证：
        // 任一时刻算出的业务日，其区间必定包含该时刻。
        val start = at(2026, 9, 6, 0, 0)
        for (offset in 0..(72 * 2)) {
            val t = start + offset * 30 * 60 * 1000L
            val date = boundary.businessDateOf(t)
            val range = boundary.rangeOf(date)
            assertWithMessage("时刻 $t 归属 $date，但不在其区间 $range 内")
                .that(t in range)
                .isTrue()
        }
    }

    @Test
    fun `同一时刻在不同时区口径下归属不同日期`() {
        // Phase 6 §12.1 的例子：北京时间 10-01 10:00
        val t = LocalDateTime.of(2026, 10, 1, 10, 0)
            .atZone(shanghai)
            .toInstant()
            .toEpochMilli()

        val inShanghai = DayBoundary(dayStartHour = 4, zoneId = shanghai)
        val inLondon = DayBoundary(dayStartHour = 4, zoneId = ZoneId.of("Europe/London"))

        assertThat(inShanghai.businessDateOf(t)).isEqualTo(LocalDate.of(2026, 10, 1))
        // 同一时刻在伦敦是 03:00，早于日切 04:00 → 退回 09-30，于是跨月了
        assertThat(inLondon.businessDateOf(t)).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun `日切时刻可配置`() {
        val midnight = DayBoundary(dayStartHour = 0, zoneId = shanghai)
        assertThat(midnight.businessDateOf(at(2026, 9, 7, 3, 30)))
            .isEqualTo(LocalDate.of(2026, 9, 7))
    }
}
