package com.dailyschedule.app.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.theme.ProjectColors

/**
 * 待办详情面板。
 *
 * 结构照参考图，但裁掉了锁机、定时提醒这类不做的东西（Rev2 §6）。
 * 「更换背景」改的是**卡片底色**，也就是项目色，不是另存一个背景字段 ——
 * 这样面板项留下了，又不用读相册、不用加字段、不用迁移数据库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailSheet(
    projectId: Long,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSessions: (Long) -> Unit,
    onReorder: () -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.open(projectId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val project = state.project
        if (project == null) {
            // 打开面板的瞬间数据还没到，或者这个待办刚被别处删掉。
            // 不画骨架屏：面板是弹出层，闪一下骨架比直接不画更晃眼。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showBackgroundPicker = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.project_detail_change_background),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton(
                    label = stringResource(R.string.common_edit),
                    onClick = { onEdit(project.id) },
                )
                SheetButton(
                    label = stringResource(R.string.project_detail_reorder),
                    onClick = onReorder,
                )
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.common_delete), maxLines = 1)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTile(
                    icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    label = stringResource(R.string.project_detail_sessions),
                    onClick = { onOpenSessions(project.id) },
                )
                SheetTile(
                    icon = { Icon(Icons.Outlined.PieChart, contentDescription = null) },
                    label = stringResource(R.string.project_detail_stats),
                    onClick = { showStats = true },
                )
            }

            SectionCard(title = stringResource(R.string.project_detail_heatmap)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val weekdays = stringArrayResource(R.array.weekday_short)
                    val maxMs = state.weekDays.maxOfOrNull { it.totalMs } ?: 0L
                    state.weekDays.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = weekdays.getOrElse(day.weekday - 1) { "?" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            HeatDot(
                                colorHex = project.colorHex,
                                totalMs = day.totalMs,
                                maxMs = maxMs,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.project_detail_heatmap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionCard(title = stringResource(R.string.project_detail_total)) {
                val (hours, minutes) = DurationFormatter.splitHoursMinutes(state.totalMs)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    NumberWithUnit(state.totalCount.toString(), stringResource(R.string.project_detail_times, state.totalCount))
                    Row(verticalAlignment = Alignment.Bottom) {
                        NumberWithUnit(hours.toString(), stringResource(R.string.project_detail_hours, hours))
                        Spacer(Modifier.size(12.dp))
                        NumberWithUnit(minutes.toString(), stringResource(R.string.project_detail_minutes, minutes))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showBackgroundPicker) {
        BackgroundPickerDialog(
            currentHex = state.project?.colorHex,
            onDismiss = { showBackgroundPicker = false },
            onPick = { hex ->
                state.project?.let { viewModel.setBackground(it, hex) }
                showBackgroundPicker = false
            },
        )
    }

    if (showStats) {
        ProjectStatsDialog(
            projectName = state.project?.name.orEmpty(),
            todayMs = state.todayMs,
            weekMs = state.weekMs,
            monthMs = state.monthMs,
            totalMs = state.totalMs,
            onDismiss = { showStats = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(stringResource(R.string.project_detail_delete_title, state.project?.name.orEmpty()))
            },
            text = { Text(stringResource(R.string.project_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    state.project?.let { viewModel.delete(it.id) }
                    confirmDelete = false
                    onDismiss()
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * 热力图的一格。
 *
 * 用"相对本周最大值的比例"分四档，而不是绝对分钟数：
 * 考研周每天 6 小时、假期周每天 20 分钟，用绝对阈值的话后者整周都是一片空。
 * 它只回答"哪天有专注、哪天比哪天多"，不回答"够不够"，所以按周归一化是对的。
 */
@Composable
private fun HeatDot(colorHex: String?, totalMs: Long, maxMs: Long) {
    val filled = totalMs > 0L && maxMs > 0L
    val fraction = if (filled) totalMs.toFloat() / maxMs.toFloat() else 0f
    val alpha = when {
        !filled -> 0f
        fraction >= 0.66f -> 1f
        fraction >= 0.33f -> 0.58f
        else -> 0.30f
    }
    val base = ProjectColors.parse(colorHex)

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (filled) base.copy(alpha = alpha)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .then(
                if (filled) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                },
            ),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun NumberWithUnit(number: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = number,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun RowScope.SheetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun RowScope.SheetTile(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
        }
    }
}

/** 内置背景 = 项目色板。不读相册，也就不需要相册权限。 */
@Composable
private fun BackgroundPickerDialog(
    currentHex: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_detail_change_background)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.project_detail_background_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProjectColors.Palette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ProjectColors.parse(hex))
                                        .then(
                                            if (hex == currentHex) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = RoundedCornerShape(10.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { onPick(hex) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun ProjectStatsDialog(
    projectName: String,
    todayMs: Long,
    weekMs: Long,
    monthMs: Long,
    totalMs: Long,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_detail_stats_title, projectName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsLine(stringResource(R.string.stats_period_today), todayMs)
                StatsLine(stringResource(R.string.stats_period_week), weekMs)
                StatsLine(stringResource(R.string.stats_period_month), monthMs)
                StatsLine(stringResource(R.string.stats_period_total), totalMs)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_confirm)) }
        },
    )
}

@Composable
private fun StatsLine(label: String, valueMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DurationFormatter.duration(valueMs),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}
