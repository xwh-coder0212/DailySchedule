package com.dailyschedule.app.domain.usecase.session

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 修改某次专注记录的时长。
 *
 * ## 它和补录的区别
 * 补录是**新增**一条本来不存在的记录；这里是**改**一条已经存在的记录
 * （通常是计时忘了关，坐下来发现多了两小时）。
 *
 * 两者共用 [SessionDurationRules] 的上下限，但改的记录**不长出新的 source 标记** ——
 * 它本来就是计时产生的，只是数字被修正过。
 *
 * ## 为什么不是"随便改"
 * 只接受时长，不接受起止时刻：起止时刻是原始证据，时长才是用户真正记得的东西。
 * 允许直接改起止时刻等于允许把时间轴拼成任意形状，那张时间轴就不再是记录了。
 */
class UpdateSessionDurationUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(sessionId: Long, durationMs: Long): AppResult<FocusSession> {
        SessionDurationRules.validate(durationMs)?.let { return it.asFailure() }

        val existing = sessionRepository.getById(sessionId)
            ?: return AppError.NotFound("这条专注记录不存在，可能已被删除").asFailure()

        if (existing.status != SessionStatus.COMPLETED) {
            // 正在跑的会话改"时长"没有意义：它的时长还没生成，
            // 改完会被下一次 stop 覆盖掉，用户会以为改了但没生效
            return AppError.Validation("正在进行的计时不能改时长").asFailure()
        }

        val updated = existing.withDuration(durationMs, nowWallClockMs = clock.wallClockMillis())
        sessionRepository.update(updated)
        AppLogger.i(TAG, "改时长 id=$sessionId -> ${durationMs}ms")
        return updated.asSuccess()
    }

    private companion object {
        const val TAG = "UpdateSessionDuration"
    }
}
