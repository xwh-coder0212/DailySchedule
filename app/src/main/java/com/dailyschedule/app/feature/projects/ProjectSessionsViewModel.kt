package com.dailyschedule.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.result.onFailure
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.session.DeleteSessionUseCase
import com.dailyschedule.app.domain.usecase.session.ManualSessionAnchor
import com.dailyschedule.app.domain.usecase.session.RecordManualSessionUseCase
import com.dailyschedule.app.domain.usecase.session.SessionDurationRules
import com.dailyschedule.app.domain.usecase.session.UpdateSessionDurationUseCase
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

/** 补录时选"哪一天"。今天用「此刻结束」为锚，其余用当天的默认开始时刻。 */
enum class ManualDay { TODAY, YESTERDAY, DAY_BEFORE }

/**
 * 要弹给用户的一句话。
 *
 * 分成两类而不是一个 String：前两种是**界面自己**校验出来的，
 * 文案在 strings.xml 里（要跟着语言走）；第三种是领域层返回的
 * `AppError.message`，它本来就是写给用户看的中文，直接透出。
 * 混成一个 String 的话，界面就只能靠比对魔法字符串来分辨，改一处文案就断一处。
 */
sealed interface SessionsMessage {
    data object DurationOutOfRange : SessionsMessage
    data object NoRoomForDuration : SessionsMessage
    data class Failure(val text: String) : SessionsMessage
}

data class ProjectSessionsUiState(
    val project: Project? = null,
    val sessions: List<FocusSession> = emptyList(),
    val totalMs: Long = 0L,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * 专注历史记录页。
 *
 * ## 为什么列表只取最近 [RECENT_LIMIT] 条，而"累计次数"是全量
 * 头部那个"累计 90 次"是 `COUNT(*)` 查出来的真值；
 * 列表为了不把三年的记录一次读进内存，只取最近若干条。
 * 两者口径不同是**刻意**的：一个回答"总共多少"，一个回答"最近发生了什么"，
 * 若为了让两个数字看起来一致而把累计也截断，那才是真的错。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectSessionsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val recordManual: RecordManualSessionUseCase,
    private val updateSessionDuration: UpdateSessionDurationUseCase,
    private val deleteSession: DeleteSessionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val openedProjectId = MutableStateFlow(0L)

    private val _message = MutableStateFlow<SessionsMessage?>(null)
    val message: StateFlow<SessionsMessage?> = _message.asStateFlow()

    fun open(projectId: Long) {
        if (openedProjectId.value != projectId) openedProjectId.value = projectId
    }

    fun consumeMessage() {
        _message.value = null
    }

    val state: StateFlow<ProjectSessionsUiState> =
        combine(openedProjectId, preferencesRepository.observe()) { id, prefs -> id to prefs }
            .flatMapLatest { (projectId, prefs) ->
                combine(
                    projectRepository.observeAll(),
                    sessionRepository.observeByProject(projectId, RECENT_LIMIT),
                    sessionRepository.observeTotalDurationOfProject(projectId, 0L, Long.MAX_VALUE),
                    sessionRepository.observeCompletedCountOfProject(projectId, 0L, Long.MAX_VALUE),
                ) { projects, sessions, totalMs, totalCount ->
                    ProjectSessionsUiState(
                        project = projects.firstOrNull { it.id == projectId },
                        sessions = sessions,
                        totalMs = totalMs,
                        totalCount = totalCount,
                        isLoading = false,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ProjectSessionsUiState())

    /**
     * 补录一段。
     *
     * 界面给的是「分钟数 + 哪一天」，这里负责把它翻成一对自洽的时间戳。
     * 换算放在 ViewModel 而不是界面：它依赖日切设置与当前时刻，
     * 写在 Composable 里会和重组纠缠在一起。
     */
    fun addManual(durationMinutes: Int, day: ManualDay, note: String?) {
        val durationMs = durationMinutes * 60_000L
        if (!isDurationAcceptable(durationMinutes)) {
            _message.value = SessionsMessage.DurationOutOfRange
            return
        }
        viewModelScope.launch {
            val prefs = preferencesRepository.get()
            val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
            val nowMs = clock.wallClockMillis()
            val offsetDays = when (day) {
                ManualDay.TODAY -> 0L
                ManualDay.YESTERDAY -> 1L
                ManualDay.DAY_BEFORE -> 2L
            }
            val businessDate = boundary.businessDateOf(nowMs).minusDays(offsetDays)
            val dayRange = boundary.rangeOf(businessDate)

            val startWallClockMs = ManualSessionAnchor.resolveStart(
                businessDayStartMs = dayRange.first,
                durationMs = durationMs,
                nowMs = nowMs,
                anchorToNow = day == ManualDay.TODAY,
            )
            if (startWallClockMs == null) {
                _message.value = SessionsMessage.NoRoomForDuration
                return@launch
            }

            recordManual(
                RecordManualSessionUseCase.Request(
                    projectId = openedProjectId.value,
                    durationMs = durationMs,
                    startWallClockMs = startWallClockMs,
                    note = note,
                ),
            ).onFailure { _message.value = SessionsMessage.Failure(it.message) }
        }
    }

    fun changeDuration(sessionId: Long, durationMinutes: Int) {
        if (!isDurationAcceptable(durationMinutes)) {
            _message.value = SessionsMessage.DurationOutOfRange
            return
        }
        viewModelScope.launch {
            updateSessionDuration(sessionId, durationMinutes * 60_000L)
                .onFailure { _message.value = SessionsMessage.Failure(it.message) }
        }
    }

    fun delete(sessionId: Long) {
        viewModelScope.launch {
            deleteSession(sessionId).onFailure { _message.value = SessionsMessage.Failure(it.message) }
        }
    }

    /** 上下限与领域层共用一套，避免界面放行一个领域层会拒的值 */
    private fun isDurationAcceptable(durationMinutes: Int): Boolean {
        if (durationMinutes <= 0) return false
        val durationMs = durationMinutes * 60_000L
        return SessionDurationRules.validate(durationMs) == null
    }

    private companion object {
        /** 列表最多显示多少条。头部累计数字不受它影响 */
        const val RECENT_LIMIT = 200
    }
}
