package com.dailyschedule.app.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.component.DsEmptyState
import com.dailyschedule.app.core.ui.theme.DsSpacing
import com.dailyschedule.app.core.ui.theme.numeric
import com.dailyschedule.app.core.ui.theme.numericEmphasis
import com.dailyschedule.app.domain.model.FocusSession
import kotlinx.coroutines.delay

/**
 * 今日页。
 *
 * 顺序即优先级：正在跑的会话 → 今日三个数字 → 今日时间轴。
 * 晚上回来一眼能回答"我今天到底做了什么"。
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val totals by viewModel.todayTotals.collectAsStateWithLifecycle()

    // 1 秒 tick 只驱动"正在跑的会话"。没有活动会话时循环不启动，不空转耗电。
    var nowElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.activeSession?.status) {
        while (state.activeSession != null) {
            nowElapsed = android.os.SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DsSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(DsSpacing.sectionGap),
    ) {
        ActiveSessionCard(
            state = state,
            nowElapsedMs = nowElapsed,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onStop = viewModel::stop,
        )

        DailyTotalsCard(
            // 正在跑的会话也计入"今日学习" —— 否则用户明明在学，数字却是 0
            studyMs = totals.completedStudyMs + elapsedOfActive(state.activeSession, nowElapsed),
            expenseCents = totals.expenseCents,
            sessionCount = totals.sessionCount,
        )

        TimelineCard(items = state.timeline)

        Spacer(Modifier.height(DsSpacing.xxl))
    }
}

@Composable
private fun ActiveSessionCard(
    state: HomeUiState,
    nowElapsedMs: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val active = state.activeSession
    Card(
        // 有会话 <-> 无会话切换时高度差别很大，直接跳会被看成"闪一下"。
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        if (active == null) {
            DsEmptyState(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.home_no_session),
                // 卡片底色是 primaryContainer，前景必须用配套的 onPrimaryContainer，
                // 否则用默认的 onSurfaceVariant 会发灰、对比度不足。
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Column(modifier = Modifier.padding(DsSpacing.xl)) {
                Text(
                    text =
                        state.activeProjectName
                            ?: stringResource(R.string.record_no_project),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DsSpacing.sm))
                Text(
                    text = DurationFormatter.clock(elapsedOfActive(active, nowElapsedMs)),
                    style = numericEmphasis(MaterialTheme.typography.displayMedium),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(DsSpacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(DsSpacing.sm)) {
                    if (active.pauseStartElapsedMs == null) {
                        Button(onClick = onPause) {
                            Text(stringResource(R.string.record_pause))
                        }
                    } else {
                        Button(onClick = onResume) {
                            Text(stringResource(R.string.record_resume))
                        }
                    }
                    Button(
                        onClick = onStop,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        Text(stringResource(R.string.record_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTotalsCard(
    studyMs: Long,
    expenseCents: Long,
    sessionCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(DsSpacing.xl),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatColumn(
                label = stringResource(R.string.home_label_study),
                value = DurationFormatter.duration(studyMs),
            )
            StatColumn(
                label = stringResource(R.string.home_label_expense),
                value = DurationFormatter.amount(expenseCents),
            )
            StatColumn(
                label = stringResource(R.string.home_label_focus),
                value = sessionCount.toString(),
            )
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = numericEmphasis(MaterialTheme.typography.titleLarge),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineCard(items: List<TimelineItem>) {
    Card(
        // 列表增删时高度平滑过渡，不要瞬间撑开。
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(Modifier.padding(DsSpacing.lg)) {
            Text(
                text = stringResource(R.string.tab_home),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isEmpty()) {
                DsEmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.home_timeline_empty),
                )
            } else {
                Column(Modifier.padding(top = DsSpacing.sm)) {
                    items.forEach { item ->
                        TimelineRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = DsSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DurationFormatter.timeOfDay(item.session.startWallClockMs),
            style = numeric(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.projectName ?: stringResource(R.string.record_no_project),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = DsSpacing.md),
        )
        Text(
            text = DurationFormatter.duration(item.session.durationMs ?: 0L),
            style = numericEmphasis(MaterialTheme.typography.bodyMedium),
        )
    }
}

private fun elapsedOfActive(
    session: FocusSession?,
    nowElapsedMs: Long,
): Long {
    if (session == null || nowElapsedMs == 0L) return 0L
    val pauseDelta =
        session.pauseStartElapsedMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
            ?: 0L
    val raw = nowElapsedMs - session.startElapsedMs - session.accumulatedPauseMs - pauseDelta
    return raw.coerceAtLeast(0L)
}
