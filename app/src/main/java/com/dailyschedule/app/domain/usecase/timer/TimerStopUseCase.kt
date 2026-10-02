package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DurationCalculator
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 结束当前会话。
 *
 * 暂停中也可以结束 —— 暂停时长照算。
 * 权威时长 = `DurationCalculator.elapsedOf(...)` 用双时钟算出来的值。
 */
class TimerStopUseCase
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(): AppResult<FocusSession> {
            val active =
                sessionRepository.getActive()
                    ?: return AppError.Timer("没有正在运行的会话").asFailure()

            if (active.status != SessionStatus.RUNNING && active.status != SessionStatus.PAUSED) {
                return AppError.Timer("会话状态 ${active.status} 不能结束").asFailure()
            }

            val nowElapsed = clock.elapsedRealtime()
            val nowWall = clock.wallClockMillis()

            val durationMs =
                DurationCalculator.elapsedOf(
                    startElapsedMs = active.startElapsedMs,
                    endElapsedMs = nowElapsed,
                    accumulatedPauseMs = active.accumulatedPauseMs,
                    pauseStartElapsedMs = active.pauseStartElapsedMs,
                    nowElapsedMs = nowElapsed,
                )

            val updated =
                active.copy(
                    status = SessionStatus.COMPLETED,
                    endElapsedMs = nowElapsed,
                    endWallClockMs = nowWall,
                    durationMs = durationMs,
                    pauseStartElapsedMs = null,
                    updatedAt = nowWall,
                )
            sessionRepository.update(updated)
            AppLogger.i(TAG, "结束 id=${active.id} 时长=${durationMs}ms")
            return updated.asSuccess()
        }

        private companion object {
            const val TAG = "TimerStop"
        }
    }
