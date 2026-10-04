package com.dailyschedule.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * 排版的唯一来源。
 *
 * ## 建这个文件要解决的问题
 * 主题里原本已经定义了三个排版令牌，但**引用数都是 0**：页面没有用它们，
 * 而是各自手写 `fontFeatureSettings = "tnum"`，全项目共 20 处。
 * 同一件事有 20 份副本，改一处不会全局生效 —— 令牌层等于不存在。
 *
 * 现在收敛成两条路：
 * - 数字（时长、金额、计数）一律用 [numeric] / [numericEmphasis]
 * - 首屏大数字用 [DisplayNumberStyle]，计时器实时时长用 [TimerNumberStyle]
 *
 * ## 边界
 * [DsTypography] 目前**沿用 Material3 的默认字号**。具体字号属于排版评审的范围，
 * 尚未定稿，所以不在这里擅自改。字号定稿后只改 [DsTypography] 一处，页面不用动。
 */

/**
 * 等宽数字（tnum）。
 *
 * 所有时长与金额必须套用，否则秒数从 1 跳到 8 时数字串宽度会变，整个界面左右抖动。
 */
val TabularNumbers =
    TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
    )

/** 首屏三个大数字。 */
val DisplayNumberStyle =
    TabularNumbers.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
    )

/** 计时中的实时时长。 */
val TimerNumberStyle =
    TabularNumbers.copy(
        fontSize = 40.sp,
        lineHeight = 48.sp,
    )

/**
 * 把任意样式转成等宽数字样式，保留原有字号 / 字重 / 行高。
 *
 * 用法：`style = numeric(MaterialTheme.typography.bodyMedium)`
 */
fun numeric(base: TextStyle): TextStyle = base.copy(fontFeatureSettings = "tnum")

/** 需要偏重的等宽数字（金额、汇总值）。 */
fun numericEmphasis(base: TextStyle): TextStyle =
    base.copy(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
    )

/**
 * 排版入口，喂给 `MaterialTheme`。
 *
 * 字号定稿后只改这里。
 */
val DsTypography: Typography = Typography()
