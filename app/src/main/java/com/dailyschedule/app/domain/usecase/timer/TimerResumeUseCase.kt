package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 继续当前暂停中的会话。
 *
 * 把本段暂停时长累加进 `accumulatedPauseMs`，并清空 `pauseStartElapsedMs`。
 * 这里不校验 `accumulatedPauseMs` 不为负 —— DurationCalculator 已用 coerceAtLeast 兜底。
 */
class TimerResumeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): AppResult<Unit> {
        val active = sessionRepository.getActive()
            ?: return AppError.Timer("没有正在运行的会话").asFailure()

        if (active.status != SessionStatus.PAUSED) {
            return AppError.Timer("会话当前状态是 ${active.status}，不能继续").asFailure()
        }
        val pauseStart = active.pauseStartElapsedMs
            ?: return AppError.Timer("状态机异常：PAUSED 但 pauseStart 为空").asFailure()

        val nowElapsed = clock.elapsedRealtime()
        val pauseDelta = (nowElapsed - pauseStart).coerceAtLeast(0L)

        val updated = active.copy(
            status = SessionStatus.RUNNING,
            accumulatedPauseMs = active.accumulatedPauseMs + pauseDelta,
            pauseStartElapsedMs = null,
            updatedAt = clock.wallClockMillis(),
        )
        sessionRepository.update(updated)
        AppLogger.i(TAG, "继续 id=${active.id} 本次暂停=${pauseDelta}ms")
        return Unit.asSuccess()
    }

    private companion object {
        const val TAG = "TimerResume"
    }
}
