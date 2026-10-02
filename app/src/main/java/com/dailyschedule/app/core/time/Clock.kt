package com.dailyschedule.app.core.time

/**
 * 时间源抽象。
 *
 * 全 App 禁止直接调用 `SystemClock.elapsedRealtime()` 或 `System.currentTimeMillis()`，
 * 一律注入本接口。唯一理由：单测里注入 [FakeClock] 后，
 * "跨午夜跑 8 小时"这种场景能在几毫秒内跑完。
 */
interface Clock {
    /** 单调时钟：自开机起累加，含深度睡眠，不受系统时间与时区修改影响。用于算时长。 */
    fun elapsedRealtime(): Long

    /** 墙上时钟：可能被用户或 NTP 修改。用于算归属日期与展示。 */
    fun wallClockMillis(): Long
}
