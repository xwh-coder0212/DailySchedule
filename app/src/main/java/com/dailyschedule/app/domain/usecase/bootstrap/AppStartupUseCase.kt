package com.dailyschedule.app.domain.usecase.bootstrap

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.timer.TimerRebootRecoveryUseCase
import javax.inject.Inject

/**
 * App 进程启动时一次性跑完所有"恢复性"检查。
 *
 * 顺序很重要：时区检测 → 重启截断 → 通知栏重建。
 * 通知栏最后才动，那时 DB 已稳定。
 */
class AppStartupUseCase @Inject constructor(
    private val detectTimezoneChange: DetectTimezoneChangeUseCase,
    private val timerRebootRecovery: TimerRebootRecoveryUseCase,
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
) {

    suspend operator fun invoke() {
        val zoneChanged = detectTimezoneChange()
        val recovery = timerRebootRecovery()
        AppLogger.i(
            TAG,
            "启动恢复完成 zoneChanged=$zoneChanged recovery=$recovery",
        )
    }

    private companion object {
        const val TAG = "AppStartup"
    }
}
