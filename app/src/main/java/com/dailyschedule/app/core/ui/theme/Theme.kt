package com.dailyschedule.app.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dailyschedule.app.core.model.ThemePack

/*
 * 主题入口。
 *
 * 唯一职责：把色板 / 圆角 / 排版喂给 MaterialTheme。页面只用
 * `MaterialTheme.colorScheme.*`、`MaterialTheme.shapes.*`、`MaterialTheme.typography.*`
 * 这类语义 token，因此换风格时只需换一个 ThemePack，页面一行都不用改。
 *
 * 各层的归属（改东西之前先确认改的是哪一层）：
 * - 颜色：两个来源二选一 —— 系统动态取色（Monet，Android 12+）跟随壁纸，
 *   或 ThemePackSpec 自带色板。见 ThemePackSpec.kt。
 * - 圆角：[ThemePackSpec.shapes]，与颜色来源无关。页面用 `MaterialTheme.shapes.*`，
 *   不要写 `RoundedCornerShape(N.dp)`。
 * - 排版：见 DsText.kt。页面用 [numeric] 处理数字，不要手写 tnum。
 * - 间距：见 DsSpacing.kt。页面用 DsSpacing.*，不要写 dp 字面量。
 * - 动效：见 DsMotion.kt。
 */

/**
 * 应用主题。
 *
 * @param themePack 风格包。决定色板与圆角。
 * @param dynamicColor 用系统动态取色（Monet）覆盖色板。Android 12 以下自动失效，
 *   回落到 [themePack] 的色板。**只换颜色，不换 [ThemePackSpec.shapes]** ——
 *   圆角与字号属于"风格"，不该被壁纸决定。
 * @param typography 排版。默认 [DsTypography]，字号定稿后只改那一处。
 */
@Composable
fun DailyScheduleTheme(
    themePack: ThemePack = ThemePack.GRAPHITE_TEAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    typography: Typography = DsTypography,
    content: @Composable () -> Unit,
) {
    val spec = themePack.spec()

    // 只有 Android 12+ 才拿得到系统取色板；低版本走主题包，行为完全不变。
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme =
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> spec.dark
            else -> spec.light
        }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = spec.shapes,
        typography = typography,
        content = content,
    )
}
