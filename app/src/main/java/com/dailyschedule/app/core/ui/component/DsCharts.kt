package com.dailyschedule.app.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统计页的三个图表组件。
 *
 * ## 为什么手写而不是引库
 * 只画环 / 柱 / 折线三种静态图，`Canvas` 各几十行就够。引一个图表库要多背几百 KB
 * 和一条第三方依赖，而本项目「零第三方依赖」是硬约束（导出 xlsx 也是手写的，
 * 理由同源）。
 *
 * ## 颜色由调用方传进来
 * 这三个组件**不认识主题** —— 它们只接收 `Color` 参数。这样做的原因是
 * `ThemeHardcodeTest` 禁止在 `core/ui/theme` 之外写 `Color(0x…)` 字面量，
 * 而图表恰好是最容易顺手写死颜色的地方。数据色（项目色/分类色）来自用户数据，
 * 主题色来自 `MaterialTheme.colorScheme`，两者都在调用点决定。
 *
 * ## 输入类型
 * [DonutSlice] 是环图的一瓣：`value <= 0` 的瓣会被跳过，不画出一条零宽的线。
 */

data class DonutSlice(val value: Long, val color: Color)

/**
 * 环形图。
 *
 * @param trackColor 底环颜色（没有数据的那部分）
 * @param centerLabel 环心的小字标签，传 null 则不显示
 * @param centerValue 环心的主字
 */
@Composable
fun DsDonutChart(
    slices: List<DonutSlice>,
    trackColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 16.dp,
    centerLabel: String? = null,
    centerValue: String? = null,
) {
    val visible = slices.filter { it.value > 0L }
    val total = visible.sumOf { it.value }

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = FULL_CIRCLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )

            if (total <= 0L) return@Canvas

            var startAngle = START_AT_TOP
            visible.forEach { slice ->
                val sweep = slice.value.toFloat() / total.toFloat() * FULL_CIRCLE
                // 每瓣尾部收掉一个固定角度当间隙。瓣本身比间隙还窄时收成 0，
                // 不让 coerce 出负数 —— 负 sweep 会反向画出一段重叠的弧
                val drawnSweep =
                    when {
                        visible.size == 1 -> sweep
                        else -> (sweep - SLICE_GAP_DEGREES).coerceAtLeast(0f)
                    }
                if (drawnSweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = drawnSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                startAngle += sweep
            }
        }

        if (centerValue != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (centerLabel != null) {
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 图例的一行。 */
data class LegendItem(val label: String, val valueText: String, val color: Color)

/**
 * 环图 / 柱图旁边的图例。
 *
 * 用两列网格而不是一列：项目或分类一多，一列会把卡片撑得很长，
 * 用户在图上看到「红色占一半」之后要滚屏去找红色是什么。
 */
@Composable
fun DsLegendGrid(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(item.color),
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = item.valueText,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                ),
                            maxLines = 1,
                        )
                    }
                }
                // 补足最后一行的空格，否则单数项会被拉伸成整行宽
                repeat(columns - rowItems.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 柱图的一根柱。 */
data class BarDatum(val label: String, val value: Long)

/**
 * 竖向柱状图。
 *
 * 用 Box/Row 布局而不是 `Canvas`：柱子的高度是比例，标签是文本，
 * 交给布局系统就不用手算基线对齐和文字换行。
 *
 * 值为 0 的柱子画一条 2dp 的浅色短横，而不是完全不画 ——
 * 「这天是 0」和「这天不存在」是两件事，空白会让人以为是后者。
 */
@Composable
fun DsBarChart(
    bars: List<BarDatum>,
    barColor: Color,
    zeroColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
) {
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.value }.coerceAtLeast(1L)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(chartHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (bar.value <= 0L) {
                        Box(
                            Modifier
                                .fillMaxWidth(ZERO_BAR_WIDTH)
                                .height(ZERO_BAR_HEIGHT)
                                .clip(RoundedCornerShape(2.dp))
                                .background(zeroColor),
                        )
                    } else {
                        val ratio = (bar.value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(BAR_WIDTH)
                                .fillMaxHeight(ratio)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barColor),
                        )
                    }
                }
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * 折线图（带渐变面积）。
 *
 * @param points 纵轴值，等距分布
 * @param xLabels 横轴标签，与 [points] 等长；元素为空串表示不显示该刻度
 * @param showDots 点少时才画圆点。31 个点上画 31 个圆点会糊成一条带子
 */
@Composable
fun DsLineChart(
    points: List<Long>,
    xLabels: List<String>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 132.dp,
    showDots: Boolean = points.size <= MAX_DOTS,
) {
    if (points.size < MIN_POINTS) return
    val maxValue = points.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            val stepX = if (points.size == 1) 0f else size.width / (points.size - 1).toFloat()
            val usableHeight = size.height - STROKE_PX

            val offsets =
                points.mapIndexed { index, value ->
                    val x = stepX * index
                    val ratio = value.toFloat() / maxValue.toFloat()
                    val y = usableHeight - (usableHeight * ratio.coerceIn(0f, 1f)) + STROKE_PX / 2f
                    Offset(x, y)
                }

            val linePath =
                Path().apply {
                    offsets.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }

            // 面积：折线 + 回到左下/右下。用闭合路径而不是两条线，
            // 否则渐变要自己裁，边缘会出现一条没填满的缝
            val areaPath =
                Path().apply {
                    addPath(linePath)
                    lineTo(offsets.last().x, size.height)
                    lineTo(offsets.first().x, size.height)
                    close()
                }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = STROKE_PX, cap = StrokeCap.Round),
            )
            if (showDots) {
                offsets.forEach { drawCircle(color = lineColor, radius = DOT_RADIUS_PX, center = it) }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
        ) {
            xLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val FULL_CIRCLE = 360f
private const val START_AT_TOP = -90f
private const val SLICE_GAP_DEGREES = 2f
private const val STROKE_PX = 2.5f
private const val DOT_RADIUS_PX = 3.5f
private const val MAX_DOTS = 12
private const val MIN_POINTS = 2
private const val BAR_WIDTH = 0.62f
private const val ZERO_BAR_WIDTH = 0.3f
private val ZERO_BAR_HEIGHT = 2.dp
