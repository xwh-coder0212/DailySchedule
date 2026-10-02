package com.dailyschedule.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.timer.TimerRebootRecoveryUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * 处理 BOOT_COMPLETED 与 MY_PACKAGE_REPLACED 广播。
 *
 * 收到 BOOT_COMPLETED 时不能直接 startForegroundService（Android 12+ 限制），
 * 只能起一个 Worker / JobScheduler / 直接同步执行。MVP 选最简的"直接执行"。
 *
 * 必须快速完成——广播 onReceive 有 10 秒上限，但工作只有一个 DB UPDATE，几毫秒。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var sessionRepository: SessionRepository

    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var timerRebootRecovery: TimerRebootRecoveryUseCase

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        AppLogger.i(TAG, "收到广播 ${intent.action}")
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                // 时区检测：关机期间时区可能变了
                val current = ZoneId.systemDefault().id
                preferencesRepository.updateKnownZoneId(current)

                // 重启截断
                timerRebootRecovery()
            } catch (t: Throwable) {
                AppLogger.e(TAG, "启动后处理失败", t)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
