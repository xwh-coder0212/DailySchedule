package com.dailyschedule.app.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailyschedule.app.R

/**
 * 调整待办顺序。
 *
 * ## 为什么用上下箭头而不是拖拽
 * 拖拽排序要处理长列表自动滚动、拖到屏幕边缘、拖拽中的占位空隙，
 * 而这三种情况在 Compose 的 `LazyColumn` 里都得自己实现。
 * 上下箭头在这个规模（考研四科 + 几个杂项）下只需要 2–3 次点击，
 * 而且**盲操作可用**：每个按钮位置固定，不需要看到目标位置就能连点。
 *
 * 每次移动都立刻落库，没有"保存顺序"按钮 —— 顺序是偏好，不是事务。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectReorderSheet(
    orderedIds: List<Long>,
    nameOf: (Long) -> String,
    onMove: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 本地维护一份顺序，点击立刻生效，不用等数据库回流 ——
    // 等回流的话连点两次会读到同一个旧列表，第二次点击就被吞掉。
    var order by remember(orderedIds) { mutableStateOf(orderedIds) }

    fun move(
        from: Int,
        to: Int,
    ) {
        if (from !in order.indices || to !in order.indices) return
        val next = order.toMutableList().apply { add(to, removeAt(from)) }
        order = next
        onMove(next)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.reorder_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (order.isEmpty()) {
                Text(
                    text = stringResource(R.string.reorder_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                order.forEachIndexed { index, id ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = nameOf(id),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { move(index, index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Outlined.ArrowUpward,
                                contentDescription = stringResource(R.string.reorder_move_up),
                            )
                        }
                        IconButton(
                            onClick = { move(index, index + 1) },
                            enabled = index < order.lastIndex,
                        ) {
                            Icon(
                                Icons.Outlined.ArrowDownward,
                                contentDescription = stringResource(R.string.reorder_move_down),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
