package com.dailyschedule.app.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.dailyschedule.app.MainActivity
import com.dailyschedule.app.R
import com.dailyschedule.app.core.time.DurationFormatter

/**
 * 通知通道集中定义。
 *
 * 三类通道，每一类只服务一种用途（Phase 3 决策）：
 * - ACTIVE_TIMER：计时常驻通知，前台服务持有
 * - POMODORO_END：番茄到点提醒
 * - PENDING_REVIEW：待确认会话提醒
 *
 * 误删/重建通道会丢用户当前的 channel 偏好，因此 channel id 是稳定的字符串，
 * 永远不要再修改它们。
 */
object TimerNotifications {

    const val CHANNEL_ACTIVE_TIMER = "active_timer"
    const val CHANNEL_POMODORO_END = "pomodoro_end"
    const val CHANNEL_PENDING_REVIEW = "pending_review"

    const val NOTIFICATION_ID_ACTIVE = 1001
    const val NOTIFICATION_ID_POMODORO = 1002
    const val NOTIFICATION_ID_REBOOT = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        listOf(
            NotificationChannel(
                CHANNEL_ACTIVE_TIMER,
                context.getString(R.string.notif_channel_active_timer),
                NotificationManager.IMPORTANCE_LOW,  // 不响铃不震动
            ).apply {
                description = context.getString(R.string.notif_channel_active_timer_desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_POMODORO_END,
                context.getString(R.string.notif_channel_pomodoro_end),
                NotificationManager.IMPORTANCE_DEFAULT,  // 到点要响
            ),
            NotificationChannel(
                CHANNEL_PENDING_REVIEW,
                context.getString(R.string.notif_channel_pending_review),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        ).forEach(manager::createNotificationChannel)
    }

    /**
     * 构造一个用系统 Chronometer 展示的活动计时通知。
     * Chronometer 自我更新，省掉每秒 IPC 与通知重建。
     *
     * @param startElapsedMs 会话开始时刻（elapsedRealtime），Chronometer 的 base
     * @param projectName    显示在标题上
     */
    fun buildActiveTimerNotification(
        context: Context,
        startElapsedMs: Long,
        projectName: String?,
        paused: Boolean,
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (projectName.isNullOrBlank()) {
            context.getString(R.string.timer_notif_title_anonymous)
        } else {
            context.getString(R.string.timer_notif_title_with_project, projectName)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(
                if (paused) context.getString(R.string.timer_notif_paused)
                else null
            )
            .setUsesChronometer(!paused)
            .setWhen(startElapsedMs)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // 暂停中时不用 chronometer（chronometer 不会停，会继续走）
        if (paused) builder.setShowWhen(false)

        return builder.build()
    }
}
