package com.dailyschedule.app.core.time

/**
 * 测试用时间源。两个时钟独立推进，才能模拟"改系统时间"和"手机重启"。
 */
class FakeClock(
    var elapsedMs: Long = 0L,
    var wallMs: Long = 0L,
) : Clock {

    override fun elapsedRealtime(): Long = elapsedMs

    override fun wallClockMillis(): Long = wallMs

    /** 两个时钟同步前进 —— 正常流逝。 */
    fun advance(deltaMs: Long) {
        elapsedMs += deltaMs
        wallMs += deltaMs
    }

    /**
     * 模拟手机重启：单调时钟归零，墙上时钟继续走（RTC 由电池供电）。
     * @param downtimeMs 关机持续的时长
     */
    fun simulateReboot(downtimeMs: Long) {
        wallMs += downtimeMs
        elapsedMs = 0L
    }

    /** 模拟用户或 NTP 改动系统时间：只有墙上时钟跳变。 */
    fun jumpWallClock(deltaMs: Long) {
        wallMs += deltaMs
    }
}
