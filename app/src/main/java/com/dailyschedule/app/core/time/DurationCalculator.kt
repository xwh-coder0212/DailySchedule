package com.dailyschedule.app.core.time

import kotlin.math.abs

/**
 * 时长计算。全部是纯函数，不依赖任何 Android API，可在 JVM 上直接单测。
 *
 * 关键：时长由时间戳算出，不是"每秒 +1"。App 进后台、锁屏、进程被杀
 * 都不影响结果的正确性。
 */
object DurationCalculator {

    /**
     * @param startElapsedMs      开始时刻（单调时钟）
     * @param endElapsedMs        结束时刻，null 表示仍在运行
     * @param accumulatedPauseMs  已结束的暂停累计
     * @param pauseStartElapsedMs 非 null 表示当前正处于暂停中
     * @param nowElapsedMs        当前单调时钟，用于补齐"未结束"与"暂停中"的部分
     */
    fun elapsedOf(
        startElapsedMs: Long,
        endElapsedMs: Long?,
        accumulatedPauseMs: Long = 0L,
        pauseStartElapsedMs: Long? = null,
        nowElapsedMs: Long,
    ): Long {
        val end = endElapsedMs ?: nowElapsedMs
        val ongoingPause = pauseStartElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
        val pause = accumulatedPauseMs + ongoingPause
        return (end - startElapsedMs - pause).coerceAtLeast(0L)
    }
}

/**
 * 手机重启与系统时间被改动的判定（Phase 5 §6.4，决策 D4-1）。
 *
 * 为什么需要它：`SystemClock.elapsedRealtime()` 自开机起累加，手机重启后归零。
 * 若不做判定，`nowElapsed - startElapsed` 会算成负数或极小值，时长直接错掉。
 *
 * 解决办法是一个恒等式：
 *    开机时刻（墙上时钟） = 当前墙上时钟 − 当前 elapsedRealtime
 *
 * 它不需要额外持久化任何字段，也不需要 BOOT_COMPLETED 权限。
 */
object RebootResolver {

    /** 两次取时钟之间存在微小间隔，判定重启时留出容差。 */
    const val TOLERANCE_MS = 2_000L

    /** 单调时钟与墙上时钟的偏差超过这个值，认为系统时间被改过。 */
    const val CLOCK_TOLERANCE_MS = 300_000L

    /** 本次开机的墙上时刻。 */
    fun bootWallClockMs(nowWallClockMs: Long, nowElapsedMs: Long): Long =
        nowWallClockMs - nowElapsedMs

    sealed interface Resolution {

        /** 正常：会话在当前开机周期内，时长可信。 */
        data class Normal(val elapsedMs: Long) : Resolution

        /**
         * 会话跨越了重启，已按开机时刻自动截断（D4-1）。
         * [endWallClockMs] 是算出来的事实值，不是估算，因此不需要人工确认。
         */
        data class AutoStoppedAtReboot(
            val endWallClockMs: Long,
            val durationMs: Long,
        ) : Resolution

        /** 单调时钟与墙上时钟严重不一致 —— 系统时间被改过，必须交人工确认。 */
        data object ClockTampered : Resolution
    }

    /**
     * 只用于 RUNNING / PAUSED 的活动会话。
     * 已结束的会话不参与判定（结束后到现在的墙钟跨度本就不该等于时长）。
     */
    fun resolveActive(
        startElapsedMs: Long,
        startWallClockMs: Long,
        accumulatedPauseMs: Long,
        pauseStartElapsedMs: Long?,
        nowElapsedMs: Long,
        nowWallClockMs: Long,
    ): Resolution {
        val bootWall = bootWallClockMs(nowWallClockMs, nowElapsedMs)

        // 判定重启需要两个条件同时成立：
        //
        //  1) looksLikeReboot：会话开始于本次开机之前
        //  2) monotonicReset：单调时钟倒退 —— 这是"确实重启过"的硬证据
        //
        // 只用条件 1 会把「用户把系统时间调快」误判成重启，从而静默写入错误时长。
        // 加上条件 2 后，那种情况会落到下面的 ClockTampered 分支，交人工确认 ——
        // 宁可让用户点一下，也不能让系统悄悄记错数。
        //
        // 代价：若上次开机时间极短（如开机 5 分钟就开始计时），重启后
        // startElapsed > nowElapsed 可能不成立，会漏判为重启。
        // 但漏判后会被 ClockTampered 兜住，不会产生错误数据。
        val looksLikeReboot = startWallClockMs < bootWall - TOLERANCE_MS
        val monotonicReset = startElapsedMs > nowElapsedMs

        if (looksLikeReboot && monotonicReset) {
            // 截断到开机时刻。注意：开机时刻晚于关机时刻，中间隔了一次重启耗时
            // （通常几十秒到两三分钟），这段会被计入时长。误差量级可接受，
            // 且无法避免 —— 我们只能观测到开机，观测不到关机。
            val duration = (bootWall - startWallClockMs - accumulatedPauseMs).coerceAtLeast(0L)
            return Resolution.AutoStoppedAtReboot(
                endWallClockMs = bootWall,
                durationMs = duration,
            )
        }

        val elapsed = DurationCalculator.elapsedOf(
            startElapsedMs = startElapsedMs,
            endElapsedMs = null,
            accumulatedPauseMs = accumulatedPauseMs,
            pauseStartElapsedMs = pauseStartElapsedMs,
            nowElapsedMs = nowElapsedMs,
        )

        // 墙上跨度应当约等于「已计时长 + 所有暂停」，偏差过大说明有人动过系统时间
        val ongoingPause = pauseStartElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
        val wallSpan = nowWallClockMs - startWallClockMs
        val expectedWallSpan = elapsed + accumulatedPauseMs + ongoingPause

        if (abs(wallSpan - expectedWallSpan) > CLOCK_TOLERANCE_MS) {
            return Resolution.ClockTampered
        }

        return Resolution.Normal(elapsed)
    }
}
