package com.dailyschedule.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.dailyschedule.app.core.ui.theme.DsSpacing

/**
 * 统一的空状态。
 *
 * ## 为什么需要它
 * 加这个组件之前，全项目没有任何空状态组件（`EmptyState` 命中 0 处）。
 * 各页面的"没有数据"是自己拼的：多数是一行灰色小字，个别页面什么都不显示
 * —— 用户看到空白，不知道是"这里本来就没内容"还是"加载失败"。
 *
 * ## 三件事必须同时有
 * - 图标：让空白区域有视觉落点，不至于显得像渲染出错
 * - 标题：一句话说清"这里是空的"
 * - 说明：告诉用户**下一步做什么**（可空，但强烈建议填）
 *
 * ## 用法
 * ```
 * DsEmptyState(
 *     icon = Icons.Outlined.History,
 *     title = stringResource(R.string.home_timeline_empty),
 *     description = stringResource(R.string.home_timeline_empty_hint),
 * )
 * ```
 *
 * @param icon 视觉落点。建议用 `Icons.Outlined.*`，与填充风格区分开。
 * @param title 一句话说明空的原因。
 * @param description 引导动作。为空则不占位。
 * @param contentColor 前景色。默认 `onSurfaceVariant`（普通表面）；
 *   放在 `primaryContainer` 这类彩色卡片上时必须显式传 `onPrimaryContainer`，
 *   否则对比度不足、字发灰。
 * @param actionLabel 可选的行动按钮文案，需与 [onAction] 成对出现。
 * @param onAction 按钮点按回调。
 */
@Composable
fun DsEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(DsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            // 图标是纯装饰，标题已经承载了语义。写 null 避免读屏重复念一遍。
            contentDescription = null,
            modifier = Modifier.size(DsSpacing.xxxl),
            tint = contentColor,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
