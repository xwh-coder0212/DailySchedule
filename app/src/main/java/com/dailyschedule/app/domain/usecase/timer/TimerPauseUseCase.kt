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
 * 暂停当前会话。
 *
 * 写 `pauseStartElapsedMs`，下一次 [TimerResumeUseCase] 用它计算本段暂停时长并累加到
 * `accumulatedPauseMs`。**这一列只增不减**，已完成的暂停不会"撤销"。
 */
class TimerPauseUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): AppResult<Unit> {
        val active = sessionRepository.getActive()
            ?: return AppError.Timer("没有正在运行的会话").asFailure()

        if (active.status != SessionStatus.RUNNING) {
            return AppError.Timer("会话当前状态是 ${active.status}，不能暂停").asFailure()
        }
        if (active.pauseStartElapsedMs != null) {
            return AppError.Timer("会话已经处于暂停中").asFailure()
        }

        val nowElapsed = clock.elapsedRealtime()
        val updated = active.copy(
            status = SessionStatus.PAUSED,
            pauseStartElapsedMs = nowElapsed,
            updatedAt = clock.wallClockMillis(),
        )
        sessionRepository.update(updated)
        AppLogger.i(TAG, "暂停 id=${active.id}")
        return Unit.asSuccess()
    }

    private companion object {
        const val TAG = "TimerPause"
    }
}
