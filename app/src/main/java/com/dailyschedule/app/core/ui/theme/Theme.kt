package com.dailyschedule.app.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dailyschedule.app.core.model.ThemePack

/*
 * 主题入口。
 *
 * 唯一职责：把色板/圆角喂给 MaterialTheme。页面只用 `MaterialTheme.colorScheme.*`
 * 语义 token，因此换风格时只需换一个 ThemePack，页面一行都不用改。
 *
 * 颜色有两个来源，二选一：
 * - 系统动态取色（Monet，Android 12+）—— 跟随壁纸
 * - ThemePackSpec 自带色板 —— 由 ThemePack 决定
 *
 * 圆角与字号**永远**来自 ThemePackSpec，与颜色来源无关。
 */

/**
 * 等宽数字。所有时长与金额必须套用，否则秒数从 1 跳到 8 时
 * 数字串宽度会变，整个界面左右抖动。
 */
val TabularNumbers =
    TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
    )

/** 首屏三个大数字 */
val DisplayNumberStyle = TabularNumbers.copy(fontSize = 34.sp)

/** 计时中的实时时长 */
val TimerNumberStyle = TabularNumbers.copy(fontSize = 40.sp)

/**
 * @param dynamicColor 用系统动态取色（Monet）覆盖色板。Android 12 以下自动失效，
 *   回落到 [themePack] 的色板。**只换颜色，不换 [ThemePackSpec.shapes]**——
 *   圆角与字号属于"风格"，不该被壁纸决定。
 */
@Composable
fun DailyScheduleTheme(
    themePack: ThemePack = ThemePack.GRAPHITE_TEAL,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    typography: Typography = Typography(),
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
