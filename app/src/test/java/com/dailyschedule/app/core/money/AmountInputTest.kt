package com.dailyschedule.app.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 金额字符串 ↔ 整数分的互转。
 *
 * 这是整个 App 里唯一允许出现浮点的地方，也是唯一一处错了会静默错数据的地方：
 * "12.5" 解析成 1250 还是 125，界面上看不出差别，只有对账时才发现少了一位。
 */
class AmountInputTest {
    @Test
    fun `整数元解析为分`() {
        assertThat(AmountInput.parseCents("28")).isEqualTo(2_800L)
    }

    @Test
    fun `一位小数按角补齐`() {
        assertThat(AmountInput.parseCents("12.5")).isEqualTo(1_250L)
    }

    @Test
    fun `两位小数精确到分`() {
        assertThat(AmountInput.parseCents("12.05")).isEqualTo(1_205L)
    }

    @Test
    fun `三位小数四舍五入到分`() {
        assertThat(AmountInput.parseCents("12.345")).isEqualTo(1_235L)
        assertThat(AmountInput.parseCents("12.344")).isEqualTo(1_234L)
    }

    @Test
    fun `用 BigDecimal 而不是 Double —— 0_29 元不会变成 28 分`() {
        // Double 下 0.29 * 100 == 28.999999999999996，直接 toLong() 会得到 28
        assertThat(AmountInput.parseCents("0.29")).isEqualTo(29L)
        assertThat(AmountInput.parseCents("1.15")).isEqualTo(115L)
        assertThat(AmountInput.parseCents("8.87")).isEqualTo(887L)
    }

    @Test
    fun `空串与单独的小数点返回 null，而不是静默当成 0`() {
        assertThat(AmountInput.parseCents("")).isNull()
        assertThat(AmountInput.parseCents("   ")).isNull()
        assertThat(AmountInput.parseCents(".")).isNull()
    }

    @Test
    fun `非数字返回 null`() {
        assertThat(AmountInput.parseCents("abc")).isNull()
        assertThat(AmountInput.parseCents("1,000")).isNull()
    }

    @Test
    fun `回填时去掉无意义的零`() {
        assertThat(AmountInput.toEditableText(2_800L)).isEqualTo("28")
        assertThat(AmountInput.toEditableText(1_250L)).isEqualTo("12.5")
        assertThat(AmountInput.toEditableText(1_205L)).isEqualTo("12.05")
        assertThat(AmountInput.toEditableText(0L)).isEqualTo("0")
    }

    @Test
    fun `解析与回填互为逆运算`() {
        listOf("28", "12.5", "12.05", "0.29", "1", "0.01").forEach { text ->
            val cents = AmountInput.parseCents(text)
            assertThat(cents).isNotNull()
            val roundTripped = AmountInput.parseCents(AmountInput.toEditableText(cents!!))
            assertThat(roundTripped).isEqualTo(cents)
        }
    }
}
