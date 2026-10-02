package com.dailyschedule.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.result.onFailure
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.model.ProjectDuration
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.project.ArchiveProjectUseCase
import com.dailyschedule.app.domain.usecase.project.CreateProjectUseCase
import com.dailyschedule.app.domain.usecase.project.DeleteProjectUseCase
import com.dailyschedule.app.domain.usecase.project.ReorderProjectsUseCase
import com.dailyschedule.app.domain.usecase.project.UpdateProjectUseCase
import com.dailyschedule.app.timer.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 一个项目在四个周期上的累计投入。
 *
 * 注意：**归档项目照样参与统计**。归档只影响"能否在计时页选中它"，
 * 一旦在这里过滤掉归档，考完研一归档，三年累计就凭空归零了。
 */
data class ProjectStat(
    val project: Project,
    val todayMs: Long,
    val weekMs: Long,
    val monthMs: Long,
    val totalMs: Long,
)

data class ProjectsUiState(
    val active: List<ProjectStat> = emptyList(),
    val archived: List<ProjectStat> = emptyList(),
    /** 当前正在计时的项目 id；null 表示没有活动会话 */
    val runningProjectId: Long? = null,
    /** 是否已有活动会话。全库最多一个，所以这里是一个布尔而不是「每个项目一个」 */
    val isTimerRunning: Boolean = false,
    /**
     * 活动会话本体。待办页顶部那张计时卡要用它算实时读数、判断暂停态。
     *
     * 放在这里而不是让界面再订阅一个 `observeActive()`：那是同一条 Flow 的第二次订阅，
     * 两个 StateFlow 各自 debounce，会出现「卡片已经变进行中、计时卡还没出现」的一帧。
     */
    val activeSession: FocusSession? = null,
    val activeProjectName: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val createProject: CreateProjectUseCase,
    private val updateProject: UpdateProjectUseCase,
    private val archiveProject: ArchiveProjectUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val reorderProjects: ReorderProjectsUseCase,
    private val clock: Clock,
    private val timerController: TimerController,
) : ViewModel() {

    /** 排序失败的原因。`AppError.message` 本来就是给用户看的中文 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 四个周期的项目时长只开 4 个 Flow，不是 N×4。
     *
     * 每个 `observeDurationByProject` 一次返回全部项目的聚合结果，
     * 按 projectId 组装即可。项目数增长不会让 Flow 数量爆炸。
     */
    val state: StateFlow<ProjectsUiState> = preferencesRepository.observe()
        .flatMapLatest { prefs ->
            val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
            val now = clock.wallClockMillis()
            val today = boundary.rangeOf(boundary.businessDateOf(now))
            val week = boundary.weekRangeOf(boundary.businessDateOf(now))
            val month = boundary.monthRangeOf(boundary.businessDateOf(now))

            val statsFlow = combine(
                projectRepository.observeAll(),
                sessionRepository.observeDurationByProject(today.first, today.last + 1),
                sessionRepository.observeDurationByProject(week.first, week.last + 1),
                sessionRepository.observeDurationByProject(month.first, month.last + 1),
                sessionRepository.observeDurationByProject(0L, Long.MAX_VALUE),
            ) { projects, todayRows, weekRows, monthRows, totalRows ->
                val todayMap = todayRows.associateBy { it.projectId }
                val weekMap = weekRows.associateBy { it.projectId }
                val monthMap = monthRows.associateBy { it.projectId }
                val totalMap = totalRows.associateBy { it.projectId }

                projects.map { p ->
                    ProjectStat(
                        project = p,
                        todayMs = todayMap[p.id]?.totalDurationMs ?: 0L,
                        weekMs = weekMap[p.id]?.totalDurationMs ?: 0L,
                        monthMs = monthMap[p.id]?.totalDurationMs ?: 0L,
                        totalMs = totalMap[p.id]?.totalDurationMs ?: 0L,
                    )
                }
            }

            // 活动会话单独合并：它不依赖日切设置，也不该跟着 prefs 变化重算 4 次聚合
            combine(statsFlow, sessionRepository.observeActive()) { stats, active ->
                ProjectsUiState(
                    active = stats.filter { !it.project.isArchived },
                    archived = stats.filter { it.project.isArchived },
                    runningProjectId = active?.projectId,
                    isTimerRunning = active != null,
                    activeSession = active,
                    // 项目名从同一份 stats 里取，不再查库；待办已被删除时留 null，
                    // 交给界面显示「未指定待办」——这正是 Rev2 决策 2 要的行为。
                    activeProjectName = active?.projectId
                        ?.let { id -> stats.firstOrNull { it.project.id == id }?.project?.name },
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ProjectsUiState())

    /**
     * 从待办卡片直接开始计时。
     *
     * 这里**不**再判断「是否已有活动会话」：`TimerStartUseCase` 已经判过，
     * 数据库的触发器还会再兜一次底。在这里写第三遍，只会多出一处
     * 可能不同步的判断。按钮的置灰由 `ProjectsUiState.isTimerRunning` 负责。
     */
    fun start(projectId: Long) {
        viewModelScope.launch { timerController.start(projectId = projectId) }
    }

    // ── 计时卡上的三个动作 ──
    // Rev2 把「计时」从记录页并进待办页：卡片右侧是「开始」，顶部计时卡是暂停/继续/结束。

    fun pause() { viewModelScope.launch { timerController.pause() } }

    fun resume() { viewModelScope.launch { timerController.resume() } }

    fun stop() { viewModelScope.launch { timerController.stop() } }

    /**
     * 重排待办。
     *
     * 传进来的必须是**全部**待办的 id（活动在前、归档在后）：
     * `reorder` 的语义是"把第 i 个的 sortOrder 改成 i"，
     * 少传的那些会保留旧值，从而插到别的待办中间。
     * `ReorderProjectsUseCase` 会校验这一点。
     */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            reorderProjects(orderedIds).onFailure { _message.value = it.message }
        }
    }

    fun create(name: String, iconName: String, colorHex: String, targetMinutes: Int?) {
        viewModelScope.launch { createProject(name, iconName, colorHex, targetMinutes) }
    }

    fun update(project: Project) {
        viewModelScope.launch { updateProject(project) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { archiveProject(id, archived) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { deleteProject(id) }
    }

    suspend fun findProject(id: Long): Project? = projectRepository.getById(id)
}
