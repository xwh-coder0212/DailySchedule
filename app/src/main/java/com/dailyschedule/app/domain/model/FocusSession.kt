package com.dailyschedule.app.domain.model

import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus

/**
 * 专注会话（领域模型）。与 Entity 一一对应，但**不认识 Room**。
 *
 * 时间字段是 epoch millis，不是 Instant —— 本项目所有时间都参与纯 JVM 单测，
 * Long 让 `FakeClock` 与测试数据写起来毫无心智负担，也避免 java.time 序列化差异。
 */
data class FocusSession(
    val id: Long = 0,
    val projectId: Long? = null,
    val status: SessionStatus,
    val needsReview: Boolean = false,
    // ── 双时钟 ──
    val startElapsedMs: Long,
    val startWallClockMs: Long,
    val endElapsedMs: Long? = null,
    val endWallClockMs: Long? = null,
    val accumulatedPauseMs: Long = 0,
    /** 非 null 即表示此刻正在暂停 */
    val pauseStartElapsedMs: Long? = null,
    val mode: SessionMode = SessionMode.STOPWATCH,
    val targetDurationMs: Long? = null,
    /** 计时器产生还是用户补录。补录的记录在列表页会带「补录」角标 */
    val source: SessionSource = SessionSource.DEFAULT,
    /** 权威时长；未结束为 null */
    val durationMs: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isActive: Boolean get() = status in SessionStatus.ACTIVE

    /** 当前是否已处于暂停中（而不是"累计暂停过"） */
    val isPaused: Boolean get() = pauseStartElapsedMs != null

    /** 是否为用户补录。界面据此显示「补录」角标 */
    val isManual: Boolean get() = source == SessionSource.MANUAL

    /**
     * 从开始到 `nowElapsedMs` 的**净专注时长**（已扣除累计暂停与当前这段暂停）。
     *
     * `nowElapsedMs` 必须来自 `SystemClock.elapsedRealtime()`（单调时钟），
     * 不能传墙上时钟 —— 用户改系统时间后，墙上时钟算出来的时长会跳。
     *
     * 这个方法以前在首页和记录页各写了一遍，两份公式只要有一份漏掉 `pauseStartElapsedMs`，
     * 就会出现「暂停了但秒数还在涨」。收进模型里，只有一处。
     */
    fun elapsedSinceStart(nowElapsedMs: Long): Long {
        val pauseDelta =
            pauseStartElapsedMs
                ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
                ?: 0L
        val raw = nowElapsedMs - startElapsedMs - accumulatedPauseMs - pauseDelta
        return raw.coerceAtLeast(0L)
    }

    /**
     * 改掉一段已结束会话的时长。
     *
     * **只动"结束"，不动"开始"**：开始时刻是用户对"我什么时候坐下"的记忆锚点，
     * 改时长不该把它挪走；而结束时刻本来就是算出来的，挪它不丢信息。
     *
     * 同时把 `endElapsedMs` 一起平移，是为了保住
     * `end - start - accumulatedPause = durationMs` 这条不变量 ——
     * 设置页的「数据体检」正是按它查脏数据的，只改其中一个，
     * 改完立刻就被自己的体检报出来。
     *
     * `source` 不变：这条记录是计时产生的就是计时产生的，
     * 事后改了个数不会让它变成「补录」。
     *
     * 前置条件 `durationMs > 0` 由调用方（用例）校验，这里用 require 兜底，
     * 因为传 0 进来是不可恢复的业务错误，不是可以静默兜底的输入。
     */
    fun withDuration(
        durationMs: Long,
        nowWallClockMs: Long,
    ): FocusSession {
        require(durationMs > 0L) { "时长必须大于 0，调用方应先校验" }
        return copy(
            durationMs = durationMs,
            endWallClockMs = startWallClockMs + durationMs,
            endElapsedMs = startElapsedMs + accumulatedPauseMs + durationMs,
            updatedAt = nowWallClockMs,
        )
    }

    companion object {
        /**
         * 新建一个活动会话。调用方必须传 clock 取到的两个时间，不许自己取系统时间。
         */
        fun start(
            projectId: Long?,
            mode: SessionMode,
            targetDurationMs: Long?,
            startElapsedMs: Long,
            startWallClockMs: Long,
        ): FocusSession =
            FocusSession(
                projectId = projectId,
                status = SessionStatus.RUNNING,
                mode = mode,
                targetDurationMs = targetDurationMs,
                startElapsedMs = startElapsedMs,
                startWallClockMs = startWallClockMs,
                createdAt = startWallClockMs,
                updatedAt = startWallClockMs,
            )

        /**
         * 用户补录一条已经完成的历史记录。
         *
         * ## 为什么补录必须自己造这两个时间戳，而不是从 durationMs 反推
         * `durationMs` 是权威值，时间戳是原始证据。补录时用户只给了「多久」和
         * 「哪一天」，所以这里的做法是：**以用户选定的开始时刻为锚点，
         * 让它往后走 durationMs，得到结束时刻**。两个时钟一起平移，
         * 于是 `end - start = durationMs` 这条不变量在补录数据上照样成立，
         * 设置页的「数据体检」不会把补录记录误报成不一致。
         *
         * 单调时钟（elapsedRealtime）在补录场景下没有真实来源，
         * 就用墙上时钟代填 —— 它只用于算时长，而时长已经是权威值。
         * **这一点是刻意的近似**，写成注释而不是留空：留空会让「体检」查询
         * 把补录记录当成脏数据。
         */
        fun recorded(
            projectId: Long?,
            durationMs: Long,
            startWallClockMs: Long,
            note: String?,
            nowWallClockMs: Long,
            mode: SessionMode = SessionMode.STOPWATCH,
        ): FocusSession {
            val endWallClockMs = startWallClockMs + durationMs
            return FocusSession(
                projectId = projectId,
                status = SessionStatus.COMPLETED,
                source = SessionSource.MANUAL,
                mode = mode,
                startElapsedMs = startWallClockMs,
                startWallClockMs = startWallClockMs,
                endElapsedMs = endWallClockMs,
                endWallClockMs = endWallClockMs,
                durationMs = durationMs,
                note = note,
                createdAt = nowWallClockMs,
                updatedAt = nowWallClockMs,
            )
        }
    }
}
