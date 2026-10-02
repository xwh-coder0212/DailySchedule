package com.dailyschedule.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dailyschedule.app.core.model.ThemePack

/**
 * 主题风格包。
 *
 * ## 为什么要有这一层
 * 将来要加"卡通风 / 简洁风 / 少女风"。如果现在把颜色写死在组件里，
 * 换风格要改几百处；有了这一层，新增风格 = 新增一个实现类 + 一个枚举值，
 * **任何页面都不用动**。
 *
 * ## 它管什么
 * 只管"视觉表达"：浅色/深色两套色板、圆角、字号。
 * 不管布局、不管数据、不管业务。
 *
 * ## 页面该用什么
 * 页面一律用 `MaterialTheme.colorScheme.*` 这类**语义 token**（primary / onSurface /
 * surfaceContainer …），不要碰具体色值。语义 token 由这里提供的色板填充，
 * 换风格时值变了、语义没变，页面自然跟着变。
 */
interface ThemePackSpec {
    val light: androidx.compose.material3.ColorScheme
    val dark: androidx.compose.material3.ColorScheme
    val shapes: Shapes
}

/**
 * 石墨青。MVP 唯一的一套。
 *
 * 设计要点（Phase 6 §2）：
 * - 深色用 `#141615` 而非纯黑：纯黑在 OLED 上会让文字产生光晕
 * - 深色背景不用饱和色，层级靠 `surfaceContainer` 明度差表达，不用阴影
 *   （阴影在深色下几乎不可见，还拖慢滚动）
 */
object GraphiteTealSpec : ThemePackSpec {
    override val light =
        lightColorScheme(
            primary = Color(0xFF0F6E56),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCDEBDF),
            onPrimaryContainer = Color(0xFF04352A),
            secondary = Color(0xFF5A5F7A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDDE1F0),
            onSecondaryContainer = Color(0xFF171B2E),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1A1C1A),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF5F5F1),
            surfaceContainer = Color(0xFFF0F0EA),
            surfaceContainerHigh = Color(0xFFECECE6),
            surfaceContainerHighest = Color(0xFFE4E4DE),
            onSurfaceVariant = Color(0xFF5F6058),
            outline = Color(0xFFD6D6CC),
            outlineVariant = Color(0xFFE4E4DE),
            error = Color(0xFFA32D2D),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF7D6D6),
            onErrorContainer = Color(0xFF5A1616),
        )

    override val dark =
        darkColorScheme(
            primary = Color(0xFF6FD3B4),
            onPrimary = Color(0xFF04352A),
            primaryContainer = Color(0xFF12503F),
            onPrimaryContainer = Color(0xFFCDEBDF),
            secondary = Color(0xFFB9BEDB),
            onSecondary = Color(0xFF22273D),
            secondaryContainer = Color(0xFF2C3149),
            onSecondaryContainer = Color(0xFFDDE1F0),
            surface = Color(0xFF141615),
            onSurface = Color(0xFFE4E6E1),
            surfaceContainerLowest = Color(0xFF0F110F),
            surfaceContainerLow = Color(0xFF1B1E1C),
            surfaceContainer = Color(0xFF212421),
            surfaceContainerHigh = Color(0xFF262A28),
            surfaceContainerHighest = Color(0xFF2F332F),
            onSurfaceVariant = Color(0xFFA3A69F),
            outline = Color(0xFF3A3E3B),
            outlineVariant = Color(0xFF2A2E2B),
            error = Color(0xFFF09595),
            onError = Color(0xFF501313),
            errorContainer = Color(0xFF6E1F1F),
            onErrorContainer = Color(0xFFF7D6D6),
        )

    override val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp),
        )
}

/** 枚举 → 具体实现。新增风格时在这里加一条分支。 */
fun ThemePack.spec(): ThemePackSpec =
    when (this) {
        ThemePack.GRAPHITE_TEAL -> GraphiteTealSpec
    }
