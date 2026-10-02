package com.dailyschedule.app.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationCalculatorTest {
    private val minute = 60_000L
    private val hour = 3_600_000L

    @Test
    fun `未结束会话按当前时间计算`() {
        val elapsed =
            DurationCalculator.elapsedOf(
                startElapsedMs = 1_000_000L,
                endElapsedMs = null,
                nowElapsedMs = 1_000_000L + 40 * minute,
            )
        assertThat(elapsed).isEqualTo(40 * minute)
    }

    @Test
    fun `已结束会话按结束时间计算`() {
        val elapsed =
            DurationCalculator.elapsedOf(
                startElapsedMs = 1_000_000L,
                endElapsedMs = 1_000_000L + 2 * hour + 35 * minute,
                nowElapsedMs = 1_000_000L + 10 * hour,
            )
        assertThat(elapsed).isEqualTo(2 * hour + 35 * minute)
    }

    @Test
    fun `暂停中的时间不计入`() {
        val now = 1_000_000L + 60 * minute
        val elapsed =
            DurationCalculator.elapsedOf(
                startElapsedMs = 1_000_000L,
                endElapsedMs = null,
                // 本用例只验证「当前正在暂停」的时间不计入，故不带历史暂停
                // （历史暂停的扣除由「多次暂停累计扣除」覆盖）。
                // 已暂停 20 分钟
                pauseStartElapsedMs = 1_000_000L + 40 * minute,
                nowElapsedMs = now,
            )
        // 60 分钟里，前 40 分钟在学习，后 20 分钟暂停中
        assertThat(elapsed).isEqualTo(40 * minute)
    }

    @Test
    fun `多次暂停累计扣除`() {
        val elapsed =
            DurationCalculator.elapsedOf(
                startElapsedMs = 0L,
                endElapsedMs = 100 * minute,
                accumulatedPauseMs = 10 * minute + 5 * minute,
                // end 已确定，now 不参与计算；但 elapsedOf 强制显式传，避免漏传时静默算错
                nowElapsedMs = 0L,
            )
        assertThat(elapsed).isEqualTo(85 * minute)
    }

    @Test
    fun `异常数据不会产生负数`() {
        val elapsed =
            DurationCalculator.elapsedOf(
                startElapsedMs = 1_000_000L,
                // 结束早于开始
                endElapsedMs = 500_000L,
                nowElapsedMs = 2_000_000L,
            )
        assertThat(elapsed).isEqualTo(0L)
    }

    // ---------- RebootResolver ----------

    @Test
    fun `正常计时的会话判定为 Normal`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 1_000_000L
        val nowElapsed = startElapsed + hour

        val result =
            RebootResolver.resolveActive(
                startElapsedMs = startElapsed,
                startWallClockMs = startWall,
                accumulatedPauseMs = 0L,
                pauseStartElapsedMs = null,
                nowElapsedMs = nowElapsed,
                nowWallClockMs = startWall + hour,
            )

        assertThat(result).isEqualTo(RebootResolver.Resolution.Normal(hour))
    }

    @Test
    fun `会话跨越手机重启则截断到开机时刻`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 1_000_000L // 会话开始时手机已运行约 16 分钟

        // 学习 50 分钟后关机，关机 10 分钟，重新开机后过了 10 分钟
        val downtime = 10 * minute
        val uptimeAfterBoot = 10 * minute
        val nowWall = startWall + 50 * minute + downtime + uptimeAfterBoot
        val nowElapsed = uptimeAfterBoot

        val result =
            RebootResolver.resolveActive(
                startElapsedMs = startElapsed,
                startWallClockMs = startWall,
                accumulatedPauseMs = 0L,
                pauseStartElapsedMs = null,
                nowElapsedMs = nowElapsed,
                nowWallClockMs = nowWall,
            )

        val expectedEnd = startWall + 50 * minute + downtime // 即开机时刻
        assertThat(result).isEqualTo(
            RebootResolver.Resolution.AutoStoppedAtReboot(
                endWallClockMs = expectedEnd,
                durationMs = 50 * minute + downtime,
            ),
        )
    }

    @Test
    fun `重启截断要扣除已确认的暂停时长`() {
        val startWall = 1_700_000_000_000L
        val result =
            RebootResolver.resolveActive(
                startElapsedMs = 5_000_000L,
                startWallClockMs = startWall,
                accumulatedPauseMs = 10 * minute,
                pauseStartElapsedMs = null,
                // 单调时钟倒退 → 确实重启过
                nowElapsedMs = 1_000_000L,
                nowWallClockMs = startWall + hour,
            )

        val bootWall = startWall + hour - 1_000_000L
        val expected = (bootWall - startWall - 10 * minute).coerceAtLeast(0L)
        assertThat(result).isEqualTo(
            RebootResolver.Resolution.AutoStoppedAtReboot(
                endWallClockMs = bootWall,
                durationMs = expected,
            ),
        )
    }

    @Test
    fun `系统时间被改慢判定为 ClockTampered 而不是静默截断`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 1_000_000L

        val result =
            RebootResolver.resolveActive(
                startElapsedMs = startElapsed,
                startWallClockMs = startWall,
                accumulatedPauseMs = 0L,
                pauseStartElapsedMs = null,
                // 单调时钟正常走了 1 小时
                nowElapsedMs = startElapsed + hour,
                // 但墙上时钟被调慢 2 小时
                nowWallClockMs = startWall + hour - 2 * hour,
            )

        assertThat(result).isEqualTo(RebootResolver.Resolution.ClockTampered)
    }

    @Test
    fun `系统时间被改快不会被误判成重启`() {
        // 这是只用 startWall < bootWall 单条件时会踩的坑：
        // 时间调快会让 bootWall 前移，看起来像"会话开始于开机之前"。
        val startWall = 1_700_000_000_000L
        val startElapsed = 1_000_000L

        val result =
            RebootResolver.resolveActive(
                startElapsedMs = startElapsed,
                startWallClockMs = startWall,
                accumulatedPauseMs = 0L,
                pauseStartElapsedMs = null,
                // 单调时钟没有倒退 → 没重启过
                nowElapsedMs = startElapsed + hour,
                // 墙上时钟被调快 2 小时
                nowWallClockMs = startWall + hour + 2 * hour,
            )

        // 不能是 AutoStoppedAtReboot —— 那会把错误时长静默写进数据库
        assertThat(result).isNotInstanceOf(RebootResolver.Resolution.AutoStoppedAtReboot::class.java)
        assertThat(result).isEqualTo(RebootResolver.Resolution.ClockTampered)
    }
}
