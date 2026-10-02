package com.dailyschedule.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        Spacer(Modifier.height(24.dp))
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        if (active == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_no_session),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = state.activeProjectName
                        ?: stringResource(R.string.record_no_project),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DurationFormatter.clock(elapsedOfActive(active, nowElapsedMs)),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        colors = ButtonDefaults.buttonColors(
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tab_home),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(Modifier.padding(top = 8.dp)) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DurationFormatter.timeOfDay(item.session.startWallClockMs),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.projectName ?: stringResource(R.string.record_no_project),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = DurationFormatter.duration(item.session.durationMs ?: 0L),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

private fun elapsedOfActive(session: FocusSession?, nowElapsedMs: Long): Long {
    if (session == null || nowElapsedMs == 0L) return 0L
    val pauseDelta = session.pauseStartElapsedMs
        ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
        ?: 0L
    val raw = nowElapsedMs - session.startElapsedMs - session.accumulatedPauseMs - pauseDelta
    return raw.coerceAtLeast(0L)
}
