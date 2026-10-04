package com.dailyschedule.app.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距与尺寸的唯一来源。
 *
 * ## 为什么要有它
 * 加这个文件之前，间距是散落在页面里的 233 处 dp 字面量：`16.dp` 出现 43 次、
 * `8.dp` 39 次、`12.dp` 33 次；另有 `9.dp` / `11.dp` / `14.dp` / `22.dp` / `26.dp`
 * 这类不在 4dp 网格上的值。后果是同类元素的间距并不一致 —— 用户说不出哪里怪，
 * 但会觉得"没做过设计"。
 *
 * 收进令牌后，"整体松一点 / 紧一点"只改这里一处，不用翻十几个页面。
 *
 * ## 网格
 * 除 [hairline] 这类结构性尺寸外，一律 4dp 的整数倍。
 * 页面里不应再出现 `N.dp` 字面量（除 [androidx.compose.ui.unit.Dp] 计算得出的值）。
 */
object DsSpacing {
    /** 0dp。语义化用，比裸 `0.dp` 表意清楚。 */
    val none = 0.dp

    /** 2dp。仅用于描边、极紧凑的图标内边距。 */
    val hairline = 2.dp

    /** 4dp。图标与文字的间隙。 */
    val xs = 4.dp

    /** 8dp。同一组内元素的间隙。 */
    val sm = 8.dp

    /** 12dp。列表行内的横向间隙。 */
    val md = 12.dp

    /** 16dp。页面左右边距、卡片内边距的默认值。 */
    val lg = 16.dp

    /** 20dp。大卡片的内边距。 */
    val xl = 20.dp

    /** 24dp。区块之间的间隔。 */
    val xxl = 24.dp

    /** 32dp。大区块之间的间隔。 */
    val xxxl = 32.dp

    /** 48dp。空状态、页面首尾的留白。 */
    val huge = 48.dp

    /** 页面左右边距。 */
    val screenHorizontal = lg

    /** 区块之间的垂直间隔。 */
    val sectionGap = lg

    /** 列表行最小高度。低于此值手指容易点空、误触。 */
    val listRowMinHeight = 56.dp

    /**
     * 最小触摸目标。
     *
     * Material 无障碍要求 44–48dp。`IconButton` / `Button` 自带这个下限，
     * 但用 `Modifier.clickable` 自建的小图标按钮必须显式套用，
     * 否则会出现"看着能点、实际点不中"。
     */
    val minTouchTarget = 48.dp
}
