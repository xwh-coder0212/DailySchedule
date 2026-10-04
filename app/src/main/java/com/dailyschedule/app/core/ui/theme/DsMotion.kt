package com.dailyschedule.app.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/*
 * 动效规范。
 *
 * ## 建这个文件要解决的问题
 * 加这个文件之前，全项目 `AnimatedVisibility` / `animateContentSize` / `Crossfade` /
 * `animateColorAsState` / `animateFloatAsState` 的出现次数**全部是 0**。
 * 也就是说界面里的一切变化都是硬切：列表增删瞬间出现、卡片高度跳变、
 * 按钮状态直接替换、页面切换没有过渡。
 *
 * 对"用起来是否舒服"来说，这个的影响比配色和圆角都大 —— 硬切会让界面显得廉价。
 *
 * ## 时长分三档
 * 避免每处各拍一个数：
 * - [DurationQuick]：颜色、透明度这类小幅反馈
 * - [DurationStandard]：尺寸、位置、内容切换
 * - [DurationSlow]：跨页面、大面积展开
 */

/** 160ms。颜色、透明度这类小幅反馈。 */
val DurationQuick = 160

/** 240ms。尺寸、位置、内容切换。 */
val DurationStandard = 240

/** 360ms。跨页面、大面积展开。 */
val DurationSlow = 360

/** 进入用减速曲线：起步快、收尾稳，比线性自然。 */
val DsEasingEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** 离开用标准曲线。 */
val DsEasingExit: Easing = FastOutSlowInEasing

/** 标准动效规格。`dsTween()` 不传参即为 240ms。 */
fun <T> dsTween(durationMillis: Int = DurationStandard): FiniteAnimationSpec<T> =
    tween(durationMillis = durationMillis, easing = DsEasingEnter)
