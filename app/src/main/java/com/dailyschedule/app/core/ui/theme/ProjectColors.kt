package com.dailyschedule.app.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 项目色 / 分类色的解析。
 *
 * 用户挑的颜色存在 DB 里（`#RRGGBB`），可能来自旧版本、手工导入或异常数据。
 * 解析失败时**必须回落到一个确定值**，否则脏数据会让整个界面崩掉。
 *
 * 为什么集中在这里：如果每个页面各写一个 fallback 色，
 * 同一个坏数据在不同页面会显示成不同颜色，排查时无从下手。
 */
object ProjectColors {
    /** 解析失败的兜底色。中性灰，在浅色与深色主题下都可见。 */
    val Fallback = Color(0xFF8A8F98)

    /**
     * 深色底上用的内容色（项目色作卡片底时）。
     *
     * 不用 `Color.White` 的纯白：在 1px 的细体字上，纯白配高饱和底会有轻微的光晕。
     */
    val ContentOnDark = Color(0xFFF7F7F4)

    /** 浅色底上用的内容色。同理不用纯黑。 */
    val ContentOnLight = Color(0xFF1A1C1A)

    /**
     * 待办/分类可选的色板。
     *
     * 只给 12 个而不是开放取色器 —— 开放式取色会选出荧光绿和深蓝，
     * 在某个主题下必然看不见。限定色板既降低决策成本，也保证可读性。
     *
     * 从 `ProjectEditScreen` 移到这里的理由：Rev2 之后「新建/编辑待办」与
     * 「更换背景」是两个入口，各存一份色板迟早会出现"编辑页有这个色、
     * 换背景没有"。色板是主题的一部分，本来也不该放在某个页面里。
     */
    val Palette: List<String> =
        listOf(
            "#2FA37B", "#0F6E56", "#3D84D6", "#5A5F7A",
            "#9A6BD1", "#D9534F", "#E0705A", "#E8A33D",
            "#C9A227", "#7BA05B", "#4B8B8B", "#8A8F98",
        )

    /**
     * 解析 `#RRGGBB`。失败返回 [Fallback]。
     *
     * 这里不做"颜色规范化"（Phase 6 §2.4 的 HSL 亮度钳制）——
     * 那是展示层的职责，等 ThemePack 支持按主题派生时再加。
     */
    fun parse(hex: String?): Color {
        if (hex == null) return Fallback
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(Fallback)
    }

    /**
     * 这个颜色当**背景**时，上面该用深色字还是浅色字。
     *
     * Rev2 把待办卡片改成整块项目色（`#EAF3DE` 这种浅绿也会有），
     * 于是"字是什么颜色"不能再写死成白 —— 浅绿底上写白字等于没写字。
     *
     * 用 WCAG 的相对亮度公式而不是 HSL 的 L：
     * 人眼对绿最敏感、对蓝最不敏感，`#00FF00` 和 `#0000FF` 的 HSL 亮度差一倍，
     * 但实际观感差得更远。相对亮度先做 sRGB 反伽马，才是真正贴合观感的量。
     *
     * 阈值 0.45 略高于理论中值 0.5 —— 卡片是浅色底的情况更多
     * （用户挑色时倾向选中低饱和度的浅色），宁可多给一次深色字。
     */
    fun prefersDarkContentOn(hex: String?): Boolean {
        val rgb = rgbOf(hex) ?: return true
        return relativeLuminance(rgb.first, rgb.second, rgb.third) > DARK_CONTENT_THRESHOLD
    }

    /**
     * 项目色作背景时，内容（待办名、按钮）该用什么颜色。
     *
     * 页面只调这一个，不要去调 [prefersDarkContentOn] 再自己挑色 ——
     * 那样每个页面都要写一遍颜色字面量，而颜色字面量在页面里是被
     * `ThemeHardcodeTest` 明令禁止的（换主题风格时那处不会跟着变）。
     */
    fun contentOn(hex: String?): Color = if (prefersDarkContentOn(hex)) ContentOnLight else ContentOnDark

    /** "#RRGGBB" / "#AARRGGBB" → (r, g, b)。解析失败返回 null。 */
    private fun rgbOf(hex: String?): Triple<Int, Int, Int>? {
        if (hex == null) return null
        val cleaned = hex.removePrefix("#")
        val rgb =
            when (cleaned.length) {
                6 -> cleaned
                8 -> cleaned.substring(2) // 丢掉 alpha：对比度只看 RGB
                else -> return null
            }
        val value = rgb.toLongOrNull(16) ?: return null
        return Triple(
            ((value shr 16) and 0xFF).toInt(),
            ((value shr 8) and 0xFF).toInt(),
            (value and 0xFF).toInt(),
        )
    }

    private fun relativeLuminance(
        r: Int,
        g: Int,
        b: Int,
    ): Double = 0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b)

    private fun channelToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private const val DARK_CONTENT_THRESHOLD = 0.45
}
