package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.RebootResolver
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 处理 BOOT_COMPLETED 与 App 冷启动时的"时间穿越"情况。
 *
 * 决策 D4-1：
 * - 真重启 → 截断到开机时刻（bootWallClockMs），写库后发通知，**不**走待确认
 * - ClockTampered → 标 needsReview=true，交用户手动修正
 * - 一切正常 → 啥也不做
 *
 * 幂等：连续触发两次第二次会自然 no-op（无活动会话 → 第一步 return）。
 */
class TimerRebootRecoveryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): AppResult<Outcome> {
        val active = sessionRepository.getActive()
            ?: return Outcome.NoActive.asSuccess()

        val nowElapsed = clock.elapsedRealtime()
        val nowWall = clock.wallClockMillis()

        val resolution = RebootResolver.resolveActive(
            startElapsedMs = active.startElapsedMs,
            startWallClockMs = active.startWallClockMs,
            accumulatedPauseMs = active.accumulatedPauseMs,
            pauseStartElapsedMs = active.pauseStartElapsedMs,
            nowElapsedMs = nowElapsed,
            nowWallClockMs = nowWall,
        )

        return when (resolution) {
            is RebootResolver.Resolution.Normal -> Outcome.NoActive.asSuccess()

            is RebootResolver.Resolution.AutoStoppedAtReboot -> {
                AppLogger.w(TAG, "检测到重启，截断会话 id=${active.id}")
                val updated = active.copy(
                    status = SessionStatus.COMPLETED,
                    endElapsedMs = resolution.endWallClockMs,         // 近似：用墙上时钟跨度
                    endWallClockMs = resolution.endWallClockMs,
                    durationMs = resolution.durationMs,
                    pauseStartElapsedMs = null,
                    needsReview = false,
                    updatedAt = nowWall,
                )
                sessionRepository.update(updated)
                Outcome.AutoStoppedAtReboot(updated).asSuccess()
            }

            RebootResolver.Resolution.ClockTampered -> {
                AppLogger.w(TAG, "检测到系统时间被改动，标待确认 id=${active.id}")
                val updated = active.copy(
                    needsReview = true,
                    updatedAt = nowWall,
                )
                sessionRepository.update(updated)
                Outcome.ClockTampered(updated).asSuccess()
            }
        }
    }

    sealed interface Outcome {
        data object NoActive : Outcome
        data class AutoStoppedAtReboot(val session: com.dailyschedule.app.domain.model.FocusSession) : Outcome
        data class ClockTampered(val session: com.dailyschedule.app.domain.model.FocusSession) : Outcome
    }

    private companion object {
        const val TAG = "TimerRebootRecovery"
    }
}
