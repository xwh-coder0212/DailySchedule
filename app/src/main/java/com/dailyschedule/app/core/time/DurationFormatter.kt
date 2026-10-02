package com.dailyschedule.app.core.time

/** 本地日历日期。界面拿它拼「9月10日」这类文案，中文措辞留在 strings.xml。 */
data class LocalDateParts(val year: Int, val month: Int, val day: Int)

/**
 * 展示层格式化。纯 JVM，可单测。
 *
 * 时长与金额一律返回等宽数字友好格式（配合 `tnum` 使用）。
 */
object DurationFormatter {
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_HOUR = 3_600_000L

    /**
     * 时长 → 人类可读。"40m" / "2h 35m" / "1h"。
     * 不足一分��的部分直接舍去，不做四舍五入——统计口径要稳定。
     */
    fun duration(durationMs: Long): String {
        val (hours, minutes) = splitHoursMinutes(durationMs)
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /** 计时中的实时读数 → "02:35:12"。 */
    fun clock(durationMs: Long): String {
        val safe = durationMs.coerceAtLeast(0L)
        val totalSeconds = safe / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /**
     * 时长 → (小时, 分钟)。
     *
     * 给"118 小时 17 分钟"这类中文单位的文案用：中文里单位和数字分开写，
     * 拼成一个字符串再拆回去不如一开始就分开给。不足一分钟的部分同样舍去。
     */
    fun splitHoursMinutes(durationMs: Long): Pair<Long, Long> {
        val safe = durationMs.coerceAtLeast(0L)
        val totalMinutes = safe / MS_PER_MINUTE
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    /** 金额（整数分）→ "¥ 47" / "¥ 1,286" / "¥ 12.50"。千分位手写，不受 Locale 影响。 */
    fun amount(amountCents: Long): String {
        val sign = if (amountCents < 0) "-" else ""
        val abs = kotlin.math.abs(amountCents)
        val yuan = abs / 100L
        val fen = abs % 100L
        val grouped = groupThousands(yuan)
        return if (fen == 0L) {
            "$sign¥ $grouped"
        } else {
            "$sign¥ $grouped.${"%02d".format(fen)}"
        }
    }

    /**
     * 金额（整数分）→ "¥ 28.00" / "-¥ 1,284.50"。
     *
     * 与 [amount] 的差别只在**小数位固定两位**：账单列表逐笔竖排对齐时，
     * "¥ 28" 和 "¥ 12.50" 的小数点不在一条线上，扫一眼就串行。
     */
    fun amountExact(amountCents: Long): String {
        val sign = if (amountCents < 0) "-" else ""
        val abs = kotlin.math.abs(amountCents)
        val yuan = abs / 100L
        val fen = abs % 100L
        return "$sign¥ ${groupThousands(yuan)}.${"%02d".format(fen)}"
    }

    /** 一天中的时刻 → "08:30"。 */
    fun timeOfDay(
        wallClockMs: Long,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): String {
        val local =
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(wallClockMs),
                zoneId,
            )
        return "%02d:%02d".format(local.hour, local.minute)
    }

    /**
     * 两个时刻之间的**日历天数差**（不是 24 小时窗口）。
     *
     * 23:59 与次日 00:01 相隔 2 分钟，但它们是两天 —— 用毫秒除法会算成 0 天，
     * 「今天」就标到了昨天那条上。所以必须落到 LocalDate 再减。
     */
    fun calendarDaysBetween(
        fromWallClockMs: Long,
        toWallClockMs: Long,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Long {
        val from = java.time.Instant.ofEpochMilli(fromWallClockMs).atZone(zoneId).toLocalDate()
        val to = java.time.Instant.ofEpochMilli(toWallClockMs).atZone(zoneId).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(from, to)
    }

    /** 本地日历日期。用来拼「9月10日」这类文案 —— 中文留在 strings.xml，这里只给数字。 */
    fun localDateOf(
        wallClockMs: Long,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): LocalDateParts {
        val local = java.time.Instant.ofEpochMilli(wallClockMs).atZone(zoneId).toLocalDate()
        return LocalDateParts(local.year, local.monthValue, local.dayOfMonth)
    }

    private fun groupThousands(value: Long): String = value.toString().reversed().chunked(3).joinToString(",").reversed()
}
