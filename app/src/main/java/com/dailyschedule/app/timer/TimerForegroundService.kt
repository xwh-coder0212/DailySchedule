package com.dailyschedule.app.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import com.dailyschedule.app.domain.usecase.timer.TimerPauseUseCase
import com.dailyschedule.app.domain.usecase.timer.TimerResumeUseCase
import com.dailyschedule.app.domain.usecase.timer.TimerStopUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 计时常驻前台服务。
 *
 * **关键设计**（Phase 5 §6）：
 * - 服务**不**承载计时正确性。状态与时长全在 DB，DB 是唯一真相。
 * - 服务只是"让通知活着 + 番茄到点提醒"。
 * - 用 `setUsesChronometer(true)` 让系统自带的 Chronometer 更新，免去每秒 IPC。
 *
 * 三件事由它做：
 * 1. 监听 `SessionRepository.observeActive()`，活动会话出现则升前台并显示通知，消失则退出前台
 * 2. 提供 `ACTION_PAUSE` / `ACTION_RESUME` / `ACTION_STOP` 三个通知栏控制入口，
 *    动作落到 UseCase 改 DB —— **服务绝不自改状态**，改完由 Flow 回流刷新通知
 * 3. 随时间显示项目名
 */
@AndroidEntryPoint
class TimerForegroundService : Service() {
    @Inject lateinit var sessionRepository: SessionRepository

    @Inject lateinit var projectRepository: ProjectRepository

    @Inject lateinit var timerPause: TimerPauseUseCase

    @Inject lateinit var timerResume: TimerResumeUseCase

    @Inject lateinit var timerStop: TimerStopUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannels(this)
        AppLogger.i(TAG, "服务创建")

        observerJob =
            scope.launch {
                sessionRepository.observeActive().collectLatest { session ->
                    if (session == null) {
                        AppLogger.i(TAG, "无活动会话，退出前台")
                        stopForegroundCompat()
                        stopSelf()
                        return@collectLatest
                    }

                    val projectName =
                        session.projectId?.let { pid ->
                            runCatching { projectRepository.getById(pid)?.name }.getOrNull()
                        }

                    val notif =
                        TimerNotifications.buildActiveTimerNotification(
                            context = this@TimerForegroundService,
                            startElapsedMs = session.startElapsedMs,
                            projectName = projectName,
                            paused = session.pauseStartElapsedMs != null,
                        )
                    startForegroundCompat(notif)
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> scope.launch { timerPause() }
            ACTION_RESUME -> scope.launch { timerResume() }
            ACTION_STOP -> scope.launch { timerStop() }
            null -> Unit // 首次启动，交给 onCreate 里的 Flow 处理
            else -> AppLogger.w(TAG, "未知动作 ${intent.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        scope.cancel()
        AppLogger.i(TAG, "服务销毁")
        super.onDestroy()
    }

    private fun startForegroundCompat(notif: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TimerNotifications.NOTIFICATION_ID_ACTIVE,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(TimerNotifications.NOTIFICATION_ID_ACTIVE, notif)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        NotificationManagerCompat.from(this)
            .cancel(TimerNotifications.NOTIFICATION_ID_ACTIVE)
    }

    companion object {
        private const val TAG = "TimerFGService"

        const val ACTION_PAUSE = "com.dailyschedule.app.action.PAUSE"
        const val ACTION_RESUME = "com.dailyschedule.app.action.RESUME"
        const val ACTION_STOP = "com.dailyschedule.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerForegroundService::class.java))
        }
    }
}
