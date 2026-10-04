package com.dailyschedule.app.feature.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.component.DsEmptyState
import com.dailyschedule.app.core.ui.component.SwipeRevealActionsWidth
import com.dailyschedule.app.core.ui.component.SwipeRevealRow
import com.dailyschedule.app.core.ui.component.dayLabelOf
import com.dailyschedule.app.core.ui.theme.ProjectColors
import com.dailyschedule.app.core.ui.theme.numeric
import com.dailyschedule.app.core.ui.theme.numericEmphasis
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 记账页（列表）。
 *
 * Rev2 把记账从"只有一个录入表单"改成"列表 + 增删改"：
 * 原来进来就是键盘，翻不到这个月记过什么，也没法改一笔错的。
 *
 * 顶部是月度汇总（含与上月的对比），中间是当月明细，右下角 + 进入录入。
 * 每一行**左滑**露出「编辑 / 删除」。
 */
@Composable
fun ExpenseListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ExpenseListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 只落一次"现在"：用来把每条账标成今天/昨天/前天。
    // 跨零点不刷新是可以接受的 —— 那一刻本来也没有新数据推过来触发重组。
    val nowMs = remember { System.currentTimeMillis() }

    // 左滑状态留一份 id 而不是每行一个布尔：全库同时只允许一行处于露出态，
    // 打开第二行时第一行必须自己收回去，否则满屏都是摊开的删除按钮。
    var revealedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<ExpenseRow?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "month-switcher") {
                MonthSwitcher(
                    year = state.year,
                    month = state.month,
                    canGoNext = state.canGoNext,
                    onPrev = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
            }

            item(key = "summary") {
                MonthSummaryCard(
                    month = state.month,
                    isCurrentMonth = !state.canGoNext,
                    totalCents = state.monthTotalCents,
                    lastMonthCents = state.lastMonthTotalCents,
                )
            }

            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    DsEmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.expense_empty_title),
                        description = stringResource(R.string.expense_empty),
                    )
                }
            } else {
                items(state.rows, key = { it.expense.id }) { row ->
                    ExpenseRowItem(
                        row = row,
                        nowMs = nowMs,
                        revealed = revealedId == row.expense.id,
                        onRevealedChange = { open ->
                            revealedId = if (open) row.expense.id else null
                        },
                        onEdit = {
                            revealedId = null
                            onEdit(row.expense.id)
                        },
                        onDelete = {
                            revealedId = null
                            pendingDelete = row
                        },
                    )
                }
            }

            item(key = "bottom-spacer") { Box(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.expense_add),
            )
        }
    }

    pendingDelete?.let { row ->
        DeleteExpenseDialog(
            row = row,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(row.expense.id)
                pendingDelete = null
            },
        )
    }
}

/** `‹ 2026年9月 ›`。往前的箭头不设上限（历史总得能翻），往后的在当前月置灰。 */
@Composable
private fun MonthSwitcher(
    year: Int,
    month: Int,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.expense_prev_month),
            )
        }
        Text(
            text = stringResource(R.string.expense_month_label, year, month),
            style = numeric(MaterialTheme.typography.titleMedium),
            modifier = Modifier.widthIn(min = 96.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.expense_next_month),
            )
        }
    }
}

@Composable
private fun MonthSummaryCard(
    month: Int,
    isCurrentMonth: Boolean,
    totalCents: Long,
    lastMonthCents: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text =
                    if (isCurrentMonth) {
                        stringResource(R.string.expense_month_total)
                    } else {
                        stringResource(R.string.expense_month_total_named, month)
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = DurationFormatter.amountExact(totalCents),
                style =
                    numeric(MaterialTheme.typography.headlineMedium).copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = comparisonText(totalCents, lastMonthCents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「较上月 ¥ 1460.00 · 少 12%」。
 *
 * 上月为 0 时不显示百分比：任何数除以 0 都是无穷大，
 * 这时说「多 100%」是错的，说「上月无记录」才是事实。
 */
@Composable
private fun comparisonText(
    totalCents: Long,
    lastMonthCents: Long,
): String {
    if (lastMonthCents <= 0L) {
        return stringResource(
            R.string.expense_vs_last_month,
            DurationFormatter.amountExact(lastMonthCents),
            stringResource(R.string.expense_vs_unknown),
        )
    }
    val deltaPercent = ((totalCents - lastMonthCents).toDouble() / lastMonthCents * 100).roundToInt()
    val trend =
        when {
            deltaPercent == 0 -> stringResource(R.string.expense_vs_flat)
            deltaPercent > 0 -> stringResource(R.string.expense_vs_more, deltaPercent)
            else -> stringResource(R.string.expense_vs_less, abs(deltaPercent))
        }
    return stringResource(
        R.string.expense_vs_last_month,
        DurationFormatter.amountExact(lastMonthCents),
        trend,
    )
}

@Composable
private fun ExpenseRowItem(
    row: ExpenseRow,
    nowMs: Long,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeRevealRow(
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        actionsWidth = SwipeRevealActionsWidth,
        modifier = Modifier.fillMaxWidth(),
        actions = {
            RevealAction(
                label = stringResource(R.string.expense_edit),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onEdit,
            )
            RevealAction(
                label = stringResource(R.string.expense_delete),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onDelete,
            )
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable {
                        // 露出状态下点行体 = 收回去。否则点一下没反应，会以为卡住了。
                        if (revealed) onRevealedChange(false) else onEdit()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(ProjectColors.parse(row.category?.colorHex)),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
            ) {
                Text(
                    text = row.category?.name ?: stringResource(R.string.expense_no_category),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (row.category == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Text(
                    text = "${dayLabelOf(row.expense.occurredAt, nowMs)} ${
                        DurationFormatter.timeOfDay(row.expense.occurredAt)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = DurationFormatter.amountExact(signedCents(row.expense.amountCents, row.expense.type)),
                style = numericEmphasis(MaterialTheme.typography.titleSmall),
            )
        }
    }
}

@Composable
private fun RowScope.RevealAction(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxSize()
                .background(container)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@Composable
private fun DeleteExpenseDialog(
    row: ExpenseRow,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.expense_delete_body,
                    row.category?.name ?: stringResource(R.string.expense_no_category),
                    DurationFormatter.amountExact(
                        signedCents(row.expense.amountCents, row.expense.type),
                    ),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** 支出取负、收入取正，只影响显示，不动库里存的数。 */
private fun signedCents(
    amountCents: Long,
    type: ExpenseType,
): Long = if (type == ExpenseType.EXPENSE) -amountCents else amountCents
