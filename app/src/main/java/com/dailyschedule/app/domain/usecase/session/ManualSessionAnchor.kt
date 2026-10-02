package com.dailyschedule.app.domain.usecase.session

/**
 * 把「某一天 + 一段时长」落成一对自洽的时间戳。
 *
 * ## 为什么需要这么一个东西
 * 补录时用户只记得两件事：**多久**、**哪一天**。但库里存的是两个时刻，
 * 而所有统计查询都是按 `startWallClockMs` 归入区间的（见 `SessionDao` 的区间约定）。
 * 所以"开始时刻落在选中的那一天里"不是美观问题，是这个记录会不会被算进那一天的问题。
 *
 * ## 锚点规则
 * - **今天**：以"此刻结束"为锚 —— 用户说"今天刚做完"，那就是刚刚
 * - **昨天 / 前天**：以那天业务日开始后 9 小时为锚（"上午 9 点左右开始"）——
 *   一个几小时前的历史记录，具体几点开始是编的，不如编一个稳定的值，
 *   至少同一天补录的两段不会互相重叠
 *
 * 两种锚点都要夹到"最晚能开始的时刻"（= 现在 - 时长）之前，
 * 否则补录出来的记录会结束在未来。
 */
object ManualSessionAnchor {
    /**
     * 历史日期的默认开始时刻：业务日开始后 9 小时。
     *
     * 用"业务日开始后 9h"而不是"本地 9:00"：日切时刻可配（0..23），
     * 日切 23 点时本地 9:00 落在业务日之外，那条记录就会被算到前一天去。
     */
    const val DEFAULT_START_OFFSET_MS = 9 * 3_600_000L

    /**
     * 算出一个可以安全落库的开始时刻。
     *
     * @param businessDayStartMs 所选业务日的起点（`DayBoundary.rangeOf` 的 first）
     * @param durationMs 用户给的时长
     * @param nowMs 现在
     * @param anchorToNow 是否以"此刻结束"为锚（选中的是今天时为 true）
     * @return 开始时刻；返回 null 表示**这一天放不下这段时长**
     *         （开始时刻会落到那一天之前），界面该提示换一天或缩短时长
     */
    fun resolveStart(
        businessDayStartMs: Long,
        durationMs: Long,
        nowMs: Long,
        anchorToNow: Boolean,
    ): Long? {
        // 结束时刻不能在未来，所以开始时刻最晚只能是「现在 - 时长」
        val latestStartMs = nowMs - durationMs
        if (latestStartMs < businessDayStartMs) return null

        val preferredStartMs =
            if (anchorToNow) {
                latestStartMs
            } else {
                businessDayStartMs + DEFAULT_START_OFFSET_MS
            }
        // 夹到上限：历史日期上"上午 9 点开始"也仍然可能晚于"现在 - 时长"
        // （比如现在才早上 6 点，却要补录昨天 20 小时的专注）
        return minOf(preferredStartMs, latestStartMs).coerceAtLeast(businessDayStartMs)
    }
}
