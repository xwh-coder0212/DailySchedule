package com.dailyschedule.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.DailyTotals
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.timer.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 时间轴上的一条：一个已完成会话 + 它的项目名（项目可能已被删除，故可空） */
data class TimelineItem(
    val session: FocusSession,
    val projectName: String?,
)

data class HomeUiState(
    val activeSession: FocusSession? = null,
    val activeProjectName: String? = null,
    val timeline: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * 首页 ViewModel：两件事
 *  - 暴露当前活动会话、归属项目名、今日时间轴
 *  - 暴露今日聚合（学习时长、专注次数、今日消费）
 *
 * "实时秒数"由 Composable 端每秒 tick 重新读 `elapsedRealtime`，不污染这里。
 * 这样 ViewModel 的依赖图里不需要有 ticker，工程上更干净。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val expenseRepository: ExpenseRepository,
        private val projectRepository: ProjectRepository,
        private val preferencesRepository: PreferencesRepository,
        private val timerController: TimerController,
        private val clock: Clock,
    ) : ViewModel() {
        val state: StateFlow<HomeUiState> =
            preferencesRepository.observe()
                .flatMapLatest { prefs ->
                    val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
                    val now = clock.wallClockMillis()
                    val today = boundary.rangeOf(boundary.businessDateOf(now))

                    combine(
                        sessionRepository.observeActive(),
                        sessionRepository.observeTimeline(today.first, today.last + 1),
                        projectRepository.observeAll(),
                    ) { active, timeline, projects ->
                        // 项目名一次性映射，而不是每条会话单独查库
                        val nameById = projects.associate { it.id to it.name }
                        HomeUiState(
                            activeSession = active,
                            activeProjectName = active?.projectId?.let { nameById[it] },
                            timeline =
                                timeline.map { session ->
                                    TimelineItem(
                                        session = session,
                                        projectName = session.projectId?.let { nameById[it] },
                                    )
                                },
                            isLoading = false,
                        )
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeUiState())

        /**
         * 今日两个账本的汇总。
         *
         * 口径：**已完成**会话的 DB 总和，**不含正在跑的会话**。
         * 正在跑的那部分由 UI 叠加实时 elapsed —— 同一个数字不能有两种含义，
         * 否则排查时无从下手（Phase 4 §6）。
         */
        val todayTotals: StateFlow<DailyTotals> =
            preferencesRepository.observe()
                .flatMapLatest { prefs ->
                    val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
                    val now = clock.wallClockMillis()
                    val today = boundary.rangeOf(boundary.businessDateOf(now))
                    combine(
                        sessionRepository.observeTotalDuration(today.first, today.last + 1),
                        sessionRepository.observeCompletedCount(today.first, today.last + 1),
                        expenseRepository.observeTotalCents(
                            ExpenseType.EXPENSE,
                            today.first,
                            today.last + 1,
                        ),
                    ) { ms, count, cents ->
                        DailyTotals(
                            completedStudyMs = ms,
                            sessionCount = count,
                            expenseCents = cents,
                        )
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DailyTotals(0L, 0, 0L))

        fun start(projectId: Long?) {
            viewModelScope.launch { timerController.start(projectId) }
        }

        fun pause() {
            viewModelScope.launch { timerController.pause() }
        }

        fun resume() {
            viewModelScope.launch { timerController.resume() }
        }

        fun stop() {
            viewModelScope.launch { timerController.stop() }
        }
    }
