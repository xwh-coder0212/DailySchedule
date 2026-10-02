package com.dailyschedule.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额输入的解析与回填。纯 JVM，可单测。
 *
 * 存在的理由：金额在这个 App 里有两种表示 —— 用户敲的字符串，和库里的整数分。
 * 两者的换算散落在各个 ViewModel 里时，"12.5 → 1250" 与 "1250 → 12.5" 会各写一遍，
 * 而只要有一处用了 `(yuan * 100).toLong()` 这类浮点写法，就会出现
 * 0.1 + 0.2 那个老问题：0.29 元变成 28 分。
 */
object AmountInput {
    private const val SCALE = 2

    /**
     * "12.5" → 1250。解析失败返回 null。
     *
     * 返回 null 而不是 0：调用方必须自己决定"这是输入错误，要提示"，
     * 静默兜底成 0 会让用户以为记成功了。
     */
    fun parseCents(text: String): Long? {
        val normalized = text.trim()
        if (normalized.isEmpty() || normalized == ".") return null
        return runCatching {
            BigDecimal(normalized)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .movePointRight(SCALE)
                .longValueExact()
        }.getOrNull()
    }

    /**
     * 1250 → "12.5" / 2800 → "28"。用于把既有一笔账回填到输入框。
     *
     * 不补足两位小数：用户看到的应当是他当初敲进去的样子，
     * "28" 又变成 "28.00" 会让人怀疑自己是不是记错了数。
     */
    fun toEditableText(amountCents: Long): String {
        val yuan = amountCents / 100L
        val fen = amountCents % 100L
        return when {
            fen == 0L -> yuan.toString()
            fen % 10L == 0L -> "$yuan.${fen / 10L}"
            else -> "$yuan.${"%02d".format(fen)}"
        }
    }
}
