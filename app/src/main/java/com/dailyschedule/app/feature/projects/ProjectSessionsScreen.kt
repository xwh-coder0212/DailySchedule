package com.dailyschedule.app.feature.projects

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.component.SwipeRevealActionsWidth
import com.dailyschedule.app.core.ui.component.SwipeRevealRow
import com.dailyschedule.app.core.ui.component.dayLabelOf
import com.dailyschedule.app.domain.model.FocusSession

/**
 * 专注历史记录。
 *
 * 一个待办下的全部专注记录，可改时长、可删，也可以补录一段。
 * 这三件事是 Rev2 决策 3 的落点：「补录时长算新增一条记录」——
 * 入口就在这里，因为"补录"永远相对于某个待办发生。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSessionsScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: ProjectSessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(projectId) { viewModel.open(projectId) }

    val nowMs = remember { System.currentTimeMillis() }
    var revealedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<FocusSession?>(null) }
    var deleting by remember { mutableStateOf<FocusSession?>(null) }
    var addingManual by remember { mutableStateOf(false) }

    val messageText =
        message?.let { m ->
            when (m) {
                SessionsMessage.DurationOutOfRange ->
                    stringResource(R.string.session_add_invalid_duration)
                SessionsMessage.NoRoomForDuration -> stringResource(R.string.session_add_no_room)
                is SessionsMessage.Failure -> m.text
            }
        }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.project?.name ?: stringResource(R.string.session_history_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    TotalCard(totalCount = state.totalCount, totalMs = state.totalMs)
                }

                if (state.sessions.isEmpty() && !state.isLoading) {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.session_history_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRowItem(
                            session = session,
                            nowMs = nowMs,
                            revealed = revealedId == session.id,
                            onRevealedChange = { open ->
                                revealedId = if (open) session.id else null
                            },
                            onEdit = {
                                revealedId = null
                                editing = session
                            },
                            onDelete = {
                                revealedId = null
                                deleting = session
                            },
                        )
                    }
                }

                item(key = "bottom-spacer") { Box(Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = { addingManual = true },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.session_add_action),
                )
            }
        }
    }

    editing?.let { session ->
        DurationDialog(
            title = stringResource(R.string.session_history_edit_title),
            initialMinutes = ((session.durationMs ?: 0L) / 60_000L).toInt(),
            confirmLabel = stringResource(R.string.common_save),
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                viewModel.changeDuration(session.id, minutes)
                editing = null
            },
        )
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.session_history_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.session_history_delete_body,
                        dayLabelOf(session.startWallClockMs, nowMs),
                        DurationFormatter.duration(session.durationMs ?: 0L),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(session.id)
                    deleting = null
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (addingManual) {
        AddManualDialog(
            onDismiss = { addingManual = false },
            onConfirm = { minutes, day, note ->
                viewModel.addManual(minutes, day, note)
                addingManual = false
            },
        )
    }
}

@Composable
private fun TotalCard(
    totalCount: Int,
    totalMs: Long,
) {
    val (hours, minutes) = DurationFormatter.splitHoursMinutes(totalMs)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.project_detail_total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = totalCount.toString(),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                    )
                    Text(
                        text = stringResource(R.string.project_detail_times, totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = hours.toString(),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                    )
                    Text(
                        text = stringResource(R.string.project_detail_hours, hours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                    Text(
                        text = minutes.toString(),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.project_detail_minutes, minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRowItem(
    session: FocusSession,
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
                label = stringResource(R.string.session_history_edit_title),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onEdit,
            )
            RevealAction(
                label = stringResource(R.string.common_delete),
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
                        if (revealed) onRevealedChange(false) else onEdit()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dayLabelOf(session.startWallClockMs, nowMs),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (session.isManual) {
                        ManualBadge(modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    text = "${DurationFormatter.timeOfDay(session.startWallClockMs)} – ${
                        DurationFormatter.timeOfDay(
                            session.endWallClockMs ?: session.startWallClockMs,
                        )
                    }",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = DurationFormatter.duration(session.durationMs ?: 0L),
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
            )
        }
    }
}

/** 「补录」角标。刻意做成文字而不是图标：它是这条记录的性质，不是可点的操作。 */
@Composable
fun ManualBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = stringResource(R.string.session_history_manual_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
            maxLines = 1,
        )
    }
}

/** 分钟内输入。改时长与补录共用，差别只在标题与按钮文案。 */
@Composable
private fun DurationDialog(
    title: String,
    initialMinutes: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initialMinutes > 0) initialMinutes.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    // 只收数字：这里量的是分钟，小数点没有意义，
                    // 放进来只会在解析时变成 0 或崩溃
                    if (input.length <= 5 && input.all { it.isDigit() }) {
                        text = input
                    } else if (input.isEmpty()) {
                        text = ""
                    }
                },
                label = { Text(stringResource(R.string.session_add_duration_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = text.toIntOrNull()?.let { it > 0 } == true,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun AddManualDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, ManualDay, String?) -> Unit,
) {
    var minutesText by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(ManualDay.TODAY) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input ->
                        if (input.length <= 5 && input.all { it.isDigit() }) {
                            minutesText = input
                        } else if (input.isEmpty()) {
                            minutesText = ""
                        }
                    },
                    label = { Text(stringResource(R.string.session_add_duration_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.session_add_day_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ManualDay.entries.forEach { option ->
                        FilterChip(
                            selected = day == option,
                            onClick = { day = option },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            ManualDay.TODAY -> R.string.session_add_day_today
                                            ManualDay.YESTERDAY -> R.string.session_add_day_yesterday
                                            ManualDay.DAY_BEFORE -> R.string.session_add_day_before
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 50) note = it },
                    label = { Text(stringResource(R.string.record_note_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        minutesText.toIntOrNull() ?: 0,
                        day,
                        note.takeIf { it.isNotBlank() },
                    )
                },
                enabled = minutesText.toIntOrNull()?.let { it > 0 } == true,
            ) {
                Text(stringResource(R.string.session_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
