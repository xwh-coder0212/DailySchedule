package com.dailyschedule.app.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出操作按钮的行容器。
 *
 * ## 为什么不用 `SwipeToDismissBox`
 * M3 那个组件的语义是「滑过阈值就消失」，滑到底会触发 `confirmValueChange` 并**复位**。
 * 我们要的是「滑开后停在那里，等用户点编辑或删除」，两者行为相反：
 * 若强行让 `confirmValueChange` 返回 false 再补一个回调，会出现
 * 「手指还没松，行已经弹回去」的抖动，而且用户第二次想关掉它时没有手感想。
 *
 * ## 为什么也不用 `AnchoredDraggable`
 * 它是对的，但 API 在几个 Compose 版本里改过名（`DraggableAnchors` / `AnchoredDraggableState`
 * 的构造签名与 `anchoredDraggable` 修饰符参数都变过）。这里手写一个两档吸附，
 * 只用到 `Animatable` + `draggable` 这两个极其稳定的 API，升级 Compose 不会炸。
 *
 * 吸附规则：拖过一半、或者甩动速度够快，就打开；否则弹回。
 */
@Composable
fun SwipeRevealRow(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    actionsWidth: Dp,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val actionPx = with(LocalDensity.current) { actionsWidth.toPx().coerceAtLeast(1f) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 外部状态变化（比如点了删除、列表刷新）总是以动画收口，
    // 不用判断"当前偏移是多少才该动" —— 直接朝目标走，重复触发无副作用。
    LaunchedEffect(revealed, actionPx) {
        offset.animateTo(
            targetValue = if (revealed) -actionPx else 0f,
            animationSpec = tween(durationMillis = 200),
        )
    }

    Box(modifier = modifier.clip(RoundedCornerShape(11.dp))) {
        // 操作区垫在底层。它不参与测量，尺寸完全由 content 决定（matchParentSize）。
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                modifier =
                    Modifier
                        .width(actionsWidth)
                        .fillMaxHeight(),
                content = actions,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        // 用 layout 而不是 offset：偏移量只在放置阶段读取，
                        // 拖动时不触发 content 的重组与重绘，只重排位置。
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(offset.value.roundToInt(), 0)
                        }
                    }
                    // `rememberDraggableState` 的回调**不是** suspend 函数，所以 snapTo
                    // 必须自己起协程。launch 按调用顺序排队，多个 delta 会依次生效，
                    // 不会出现后一帧先于前一帧写入导致的位置抖动。
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state =
                            rememberDraggableState { delta ->
                                // 不跟手指反向走：右滑时已经贴住 0，再拉也不动。
                                scope.launch {
                                    offset.snapTo((offset.value + delta).coerceIn(-actionPx, 0f))
                                }
                            },
                        onDragStopped = { velocity ->
                            val passedHalf = offset.value < -actionPx / 2f
                            val flungOpen = velocity < -FLING_VELOCITY
                            val flungClosed = velocity > FLING_VELOCITY
                            val open =
                                when {
                                    flungClosed -> false
                                    flungOpen -> true
                                    else -> passedHalf
                                }
                            onRevealedChange(open)
                            // 自己先动起来，不等父级把 revealed 回传 ——
                            // 少一帧延迟，手指松开的那一瞬间就已经在走动画。
                            scope.launch {
                                offset.animateTo(
                                    targetValue = if (open) -actionPx else 0f,
                                    animationSpec = tween(durationMillis = 180),
                                )
                            }
                        },
                    ),
        ) {
            content()
        }
    }
}

/** px/s。手指快速扫过时位移常常不到一半，光看位移会让快滑失败。 */
private const val FLING_VELOCITY = 700f

/** 一行动作区的默认宽度：两个等宽按钮。 */
val SwipeRevealActionsWidth: Dp = 144.dp
