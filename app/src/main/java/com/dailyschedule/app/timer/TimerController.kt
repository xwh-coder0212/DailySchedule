package com.dailyschedule.app.timer

import android.content.Context
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.timer.TimerPauseUseCase
import com.dailyschedule.app.domain.usecase.timer.TimerResumeUseCase
import com.dailyschedule.app.domain.usecase.timer.TimerStartUseCase
import com.dailyschedule.app.domain.usecase.timer.TimerStopUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 计时的唯一门面。
 *
 * **为什么要有这一层**：UseCase 是纯业务逻辑，不该依赖 Android Context；
 * 而"开始计时"在业务之外还要做一件事——把前台服务拉起来，否则通知不会常驻，
 * 用户锁屏后就看不到计时还在跑。ViewModel 也不该直接碰 Context。
 * 所以由这一层持有 Context，把「业务写入」与「系统副作用」串起来。
 *
 * **前台服务不承载计时正确性**（Phase 5 §6）：时长由 DB 里的双时钟时间戳算出。
 * 服务被杀最坏的结果是通知没了，不是数据错了。
 */
@Singleton
class TimerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val timerStart: TimerStartUseCase,
    private val timerPause: TimerPauseUseCase,
    private val timerResume: TimerResumeUseCase,
    private val timerStop: TimerStopUseCase,
) {

    /**
     * 只有 start 需要显式拉起服务。
     *
     * pause / resume / stop 之后**不需要**给服务发指令 ——
     * 服务订阅了 `observeActive()`，DB 一改，Flow 立刻回流刷新通知；
     * 会话结束（active 变 null）时服务还会自己 `stopSelf()`。
     * 多发一次指令反而容易在服务刚被杀又重建时产生竞态。
     */
    suspend fun start(projectId: Long?): AppResult<FocusSession> {
        val result = timerStart(projectId = projectId)
        if (result.isSuccess()) {
            runCatching {
                TimerForegroundService.start(context)
            }.onFailure { t ->
                // 服务起不来 ≠ 计时失败。数据已落库，时长照样正确，只是通知不常驻。
                AppLogger.e(TAG, "前台服务启动失败", t)
            }
        }
        return result
    }

    suspend fun pause(): AppResult<Unit> = timerPause()

    suspend fun resume(): AppResult<Unit> = timerResume()

    suspend fun stop(): AppResult<FocusSession> {
        val result = timerStop()
        if (result.isSuccess()) {
            runCatching {
                TimerForegroundService.stop(context)
            }.onFailure { AppLogger.w(TAG, "停止服务失败") }
        }
        return result
    }

    private fun AppResult<*>.isSuccess(): Boolean = this is AppResult.Success

    private companion object {
        const val TAG = "TimerController"
    }
}
