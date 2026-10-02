package com.dailyschedule.app.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.core.ui.theme.ProjectColors
import com.dailyschedule.app.domain.model.FocusSession
import kotlinx.coroutines.delay

/**
 * 待办页。
 *
 * 结构：**正在专注卡（仅在有活动会话时存在）** → 待办卡片列表 → 已归档。
 *
 * 「计时」在 Rev2 里从记录页搬到了这里：卡片右侧就是「开始」，
 * 开始之后顶部才长出那张计时卡。注意这里是**条件渲染**，不是占位：
 * 没有活动会话时整张卡不存在，而不是显示一行灰字「未开始计时」。
 * 空状态用一句话占位会把「现在什么都没在跑」这件正常的事说成一个异常。
 */
@Composable
fun ProjectsScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSessions: (Long) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 点卡片弹详情面板，而不是切页。面板要的是"看一眼再决定做什么"，
    // 切页会把列表顶掉、还会让返回栈变深一层。
    var detailProjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reordering by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 1 秒 tick 只驱动计时卡。没有活动会话时循环根本不启动，不空转耗电。
    var nowElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.activeSession?.id, state.activeSession?.pauseStartElapsedMs) {
        while (state.activeSession != null) {
            nowElapsed = android.os.SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }

    // 用 Box 而不是 Scaffold：外层 AppNavHost 已经提供了 Scaffold 与顶部栏，
    // 嵌套 Scaffold 会导致 padding 叠加、FAB 位置错乱。
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.common_loading))
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.activeSession?.let { session ->
                    item(key = "active-session") {
                        ActiveSessionCard(
                            session = session,
                            projectName = state.activeProjectName,
                            nowElapsedMs = nowElapsed,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onStop = viewModel::stop,
                        )
                    }
                }

                if (state.active.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.project_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    item(key = "hint") {
                        Text(
                            text = stringResource(R.string.projects_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    items(state.active, key = { it.project.id }) { stat ->
                        ProjectCard(
                            stat = stat,
                            onClick = { detailProjectId = stat.project.id },
                            onStart = { viewModel.start(stat.project.id) },
                            isThisRunning = state.runningProjectId == stat.project.id,
                            isTimerRunning = state.isTimerRunning,
                        )
                    }
                }

                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Text(
                            text = stringResource(R.string.project_archived_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(state.archived, key = { it.project.id }) { stat ->
                        ProjectCard(
                            stat = stat,
                            onClick = { detailProjectId = stat.project.id },
                            onStart = { viewModel.start(stat.project.id) },
                            isThisRunning = state.runningProjectId == stat.project.id,
                            isTimerRunning = state.isTimerRunning,
                            dimmed = true,
                        )
                    }
                }

                item(key = "bottom-spacer") { Box(Modifier.padding(bottom = 80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = onCreate,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.project_create),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
        )
    }

    detailProjectId?.let { id ->
        ProjectDetailSheet(
            projectId = id,
            onDismiss = { detailProjectId = null },
            onEdit = { onEdit(it) },
            onOpenSessions = {
                detailProjectId = null
                onOpenSessions(it)
            },
            onReorder = {
                detailProjectId = null
                reordering = true
            },
        )
    }

    if (reordering) {
        val orderedIds = state.active.map { it.project.id } + state.archived.map { it.project.id }
        val nameById = (state.active + state.archived).associate { it.project.id to it.project.name }
        ProjectReorderSheet(
            orderedIds = orderedIds,
            nameOf = { nameById[it].orEmpty() },
            onMove = viewModel::reorder,
            onDismiss = { reordering = false },
        )
    }
}

/**
 * 正在专注卡。
 *
 * 只在有活动会话时渲染。会话一结束（结束 / 被重启恢复流程收尾）它就消失，
 * 不需要任何「收起」逻辑 —— 状态本身就是条件。
 */
@Composable
private fun ActiveSessionCard(
    session: FocusSession,
    projectName: String?,
    nowElapsedMs: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val paused = session.pauseStartElapsedMs != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text =
                    buildString {
                        append(
                            stringResource(
                                if (paused) {
                                    R.string.projects_active_paused
                                } else {
                                    R.string.projects_active_label
                                },
                            ),
                        )
                        append(" · ")
                        append(projectName ?: stringResource(R.string.record_no_project))
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = DurationFormatter.clock(session.elapsedSinceStart(nowElapsedMs)),
                style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (paused) onResume else onPause) {
                    Text(
                        stringResource(
                            if (paused) R.string.record_resume else R.string.record_pause,
                        ),
                    )
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

/**
 * 待办卡片。
 *
 * 只有「待办名」和「开始」两件东西 —— 今日/累计/本月三个数字都挪进了详情面板。
 * 列表页的职责是「选一个待办开始」，不是「巡检所有待办的数字」；
 * 三个数字堆在每一行会让人扫不动列表，也没人会在这里逐个对比。
 *
 * 卡片底面直接用项目色，而不是「灰底 + 一个色点」：
 * 一排同色系的项目能靠颜色被一眼区分（考研四科各一色），
 * 而色点小到需要逐个去看。字色由 [ProjectColors.prefersDarkContentOn] 按背景亮度选，
 * 所以用户挑到 `#EAF3DE` 这种浅绿时不会出现白字压白底。
 *
 * 全库同时最多一个活动会话，所以计时进行中时**所有**卡片按钮都置灰：
 * 让用户点下去再弹一个「已有正在运行的会话」，是把一个已知事实变成一次报错。
 */
@Composable
private fun ProjectCard(
    stat: ProjectStat,
    onClick: () -> Unit,
    onStart: () -> Unit,
    isThisRunning: Boolean,
    isTimerRunning: Boolean,
    dimmed: Boolean = false,
) {
    val background = ProjectColors.parse(stat.project.colorHex)
    val onBackground = ProjectColors.contentOn(stat.project.colorHex)

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                // 已归档压暗：归档的语义是"不再选中它"，视觉上就该退到后面去
                containerColor = if (dimmed) background.copy(alpha = 0.45f) else background,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stat.project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = onBackground,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
            )
            Button(
                onClick = onStart,
                enabled = !isTimerRunning,
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        // 卡片底色是任意色，按钮不能用 primary —— 会撞色。
                        // 用"背景的反色"配一点点透明度，任何底色上都读得出。
                        containerColor = onBackground.copy(alpha = 0.16f),
                        contentColor = onBackground,
                        disabledContainerColor = onBackground.copy(alpha = 0.08f),
                        disabledContentColor = onBackground.copy(alpha = 0.55f),
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            if (isThisRunning) {
                                R.string.project_card_running
                            } else {
                                R.string.project_card_start
                            },
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}
