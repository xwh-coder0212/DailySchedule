package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 开始一个会话。
 *
 * 业务规则：
 * - 同一时刻全库最多一个 RUNNING/PAUSED（DB 层部分唯一索引兜底，这里做应用层友好提示）
 * - 写入 createdAt/updatedAt = 当前墙上时钟（用于审计/排序）
 * - lastUsedProjectId 自动更新（首页下次默认带上这个项目）
 */
class TimerStartUseCase
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val preferencesRepository: PreferencesRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(
            projectId: Long?,
            mode: SessionMode = SessionMode.STOPWATCH,
            targetDurationMs: Long? = null,
        ): AppResult<FocusSession> {
            val active = sessionRepository.getActive()
            if (active != null) {
                AppLogger.w(TAG, "已有活动会话 id=${active.id}，拒绝开始新的")
                return AppError.Timer("已有正在运行的会话，请先结束").asFailure()
            }

            val nowElapsed = clock.elapsedRealtime()
            val nowWall = clock.wallClockMillis()

            val session =
                FocusSession.start(
                    projectId = projectId,
                    mode = mode,
                    targetDurationMs = targetDurationMs,
                    startElapsedMs = nowElapsed,
                    startWallClockMs = nowWall,
                )
            val id = sessionRepository.insert(session)

            // 上次项目记忆：用于首页默认选中。仅当传入了 projectId 才记
            if (projectId != null) {
                preferencesRepository.setLastUsedProjectId(projectId)
            }
            AppLogger.i(TAG, "开始会话 id=$id project=$projectId mode=$mode")
            return session.copy(id = id).asSuccess()
        }

        private companion object {
            const val TAG = "TimerStart"
        }
    }
