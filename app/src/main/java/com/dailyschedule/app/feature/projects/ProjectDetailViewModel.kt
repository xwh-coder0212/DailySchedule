package com.dailyschedule.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.project.DeleteProjectUseCase
import com.dailyschedule.app.domain.usecase.project.UpdateProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 周热力图的一格。
 *
 * `weekday` 是 ISO 星期（1 = 周一 … 7 = 周日），**不是**"从左数第几格" ——
 * 周起始日可由用户配置（默认周一），用真实星期号，界面才能把「三」标在正确的格子上。
 */
data class HeatDay(
    val weekday: Int,
    val totalMs: Long,
)

data class ProjectDetailUiState(
    val project: Project? = null,
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val monthMs: Long = 0L,
    val totalMs: Long = 0L,
    val totalCount: Int = 0,
    /** 固定 7 格，从本周第一天排到最后一天 */
    val weekDays: List<HeatDay> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * 待办详情面板的数据源。
 *
 * ## 为什么用 `open(id)` 而不是 `stateFor(id)` 返回一个 flow
 * 后者每次重组都会 `flatMapLatest` 出一条新链，而 Compose 每帧都可能重组。
 * 状态里存一个"当前打开的待办 id"，链只建一次，跟着 id 变化重跑，
 * 也顺带解决了"面板关掉后数据还在不在"的问题 —— `close()` 就是把它置空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val updateProject: UpdateProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val openedProjectId = MutableStateFlow<Long?>(null)

    fun open(projectId: Long) {
        openedProjectId.value = projectId
    }

    fun close() {
        openedProjectId.value = null
    }

    val state: StateFlow<ProjectDetailUiState> =
        combine(openedProjectId, preferencesRepository.observe()) { id, prefs -> id to prefs }
            .flatMapLatest { (projectId, prefs) ->
                if (projectId == null) {
                    return@flatMapLatest flowOf(ProjectDetailUiState())
                }
                val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
                val today = boundary.businessDateOf(clock.wallClockMillis())
                val todayRange = boundary.rangeOf(today)
                val weekRange = boundary.weekRangeOf(today)
                val monthRange = boundary.monthRangeOf(today)
                val weekStart = boundary.weekStartDateOf(today)

                // 四个周期 + 累计次数：同样是 4 个聚合查询，不是 N 个
                val totalsFlow = combine(
                    sessionRepository.observeTotalDurationOfProject(
                        projectId, todayRange.first, todayRange.last + 1,
                    ),
                    sessionRepository.observeTotalDurationOfProject(
                        projectId, weekRange.first, weekRange.last + 1,
                    ),
                    sessionRepository.observeTotalDurationOfProject(
                        projectId, monthRange.first, monthRange.last + 1,
                    ),
                    sessionRepository.observeTotalDurationOfProject(projectId, 0L, Long.MAX_VALUE),
                    sessionRepository.observeCompletedCountOfProject(projectId, 0L, Long.MAX_VALUE),
                ) { todayMs, weekMs, monthMs, totalMs, totalCount ->
                    PeriodTotals(todayMs, weekMs, monthMs, totalMs, totalCount)
                }

                combine(
                    totalsFlow,
                    projectRepository.observeAll(),
                    sessionRepository.observeInRange(weekRange.first, weekRange.last + 1),
                ) { totals, projects, weekSessions ->
                    // 热力图的分日归组放在 Kotlin 层：SQL 里不允许出现日期函数，
                    // 因为日切时刻是用户可配的，只有 DayBoundary 知道该怎么切。
                    val byBusinessDate = weekSessions
                        .filter { it.projectId == projectId }
                        .groupBy { boundary.businessDateOf(it.startWallClockMs) }

                    ProjectDetailUiState(
                        project = projects.firstOrNull { it.id == projectId },
                        todayMs = totals.todayMs,
                        weekMs = totals.weekMs,
                        monthMs = totals.monthMs,
                        totalMs = totals.totalMs,
                        totalCount = totals.totalCount,
                        weekDays = (0..6).map { offset ->
                            val day = weekStart.plusDays(offset.toLong())
                            HeatDay(
                                weekday = day.dayOfWeek.value,
                                totalMs = byBusinessDate[day].orEmpty()
                                    .sumOf { it.durationMs ?: 0L },
                            )
                        },
                        isLoading = false,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ProjectDetailUiState())

    /** 「更换背景」= 换卡片底色。卡片底色就是项目色，所以改的是 `colorHex`，不新增字段。 */
    fun setBackground(project: Project, colorHex: String) {
        if (project.colorHex == colorHex) return
        viewModelScope.launch { updateProject(project.copy(colorHex = colorHex)) }
    }

    fun delete(projectId: Long) {
        viewModelScope.launch { deleteProject(projectId) }
    }

    private data class PeriodTotals(
        val todayMs: Long,
        val weekMs: Long,
        val monthMs: Long,
        val totalMs: Long,
        val totalCount: Int,
    )
}
