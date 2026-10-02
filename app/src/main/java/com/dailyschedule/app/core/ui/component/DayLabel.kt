package com.dailyschedule.app.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter

/**
 * 「今天 / 昨天 / 前天 / 9月10日 / 2025年9月10日」。
 *
 * 用**日历天数差**而不是 24 小时窗口：23:59 与次日 00:01 只隔两分钟，
 * 但它们不是同一天，用毫秒除法会把它标成"今天"。
 *
 * 抽成公共组件是因为账单列表和专注历史列表都在用它 ——
 * 两份实现只要有一份忘了处理跨年，就会出现"2025年9月10日"被写成"9月10日"。
 */
@Composable
fun dayLabelOf(wallClockMs: Long, nowMs: Long): String {
    return when (DurationFormatter.calendarDaysBetween(wallClockMs, nowMs)) {
        0L -> stringResource(R.string.date_today)
        1L -> stringResource(R.string.date_yesterday)
        2L -> stringResource(R.string.date_before_yesterday)
        else -> {
            val date = DurationFormatter.localDateOf(wallClockMs)
            val thisYear = DurationFormatter.localDateOf(nowMs).year
            if (date.year == thisYear) {
                stringResource(R.string.date_month_day, date.month, date.day)
            } else {
                stringResource(R.string.date_year_month_day, date.year, date.month, date.day)
            }
        }
    }
}
