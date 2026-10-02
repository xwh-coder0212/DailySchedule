package com.dailyschedule.app.domain.usecase.session

import com.dailyschedule.app.core.result.AppError

/**
 * 单次时长的合法区间。
 *
 * 抽出来是因为现在有两条路径会写"用户直接指定的时长"：补录（新增一条）
 * 和修改既有记录的时长。两处各写一遍上下限，迟早会出现
 * 「补录最多 24 小时、修改最多 12 小时」这种莫名其妙的差别，
 * 而用户只会觉得这个 App 时好时坏。
 */
object SessionDurationRules {

    /**
     * 单次上限。
     *
     * 超过一天只可能是位数填错了，不是真实投入 —— 一整天不休息的专注
     * 即便真的发生，也不该由"事后填个数字"来记录。
     */
    const val MAX_DURATION_HOURS = 24
    const val MAX_DURATION_MS = MAX_DURATION_HOURS * 60L * 60_000L

    /** 返回非 null 即为不合法原因 */
    fun validate(durationMs: Long): AppError? = when {
        durationMs <= 0L -> AppError.Validation("时长必须大于 0")
        durationMs > MAX_DURATION_MS ->
            AppError.Validation("单次时长不能超过 $MAX_DURATION_HOURS 小时")
        else -> null
    }

    /**
     * 允许的「未来」容差。
     *
     * 界面按"以此刻为结束点"算开始时刻，保存时再读一次时钟，
     * 中间隔了几百毫秒是正常的。留一分钟容差是为了让这种正常情况不报错，
     * 同时仍然挡得住"把补录填到明天"这类真实错误。
     */
    const val FUTURE_TOLERANCE_MS = 60_000L
}
