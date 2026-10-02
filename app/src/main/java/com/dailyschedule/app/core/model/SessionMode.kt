package com.dailyschedule.app.core.model

/**
 * 计时模式。
 *
 * `POMODORO` 不是独立计时机制，而是"带目标时长的正计时"：
 * 状态机、时长算法、存储结构与 `STOPWATCH` 完全一致，只多一个 [targetDurationMs]
 * 用于到点提醒。这样避免了"中断的番茄算不算"这类数据歧义。
 */
enum class SessionMode {
    /** 正计时，无预设目标 */
    STOPWATCH,

    /** 番茄模式：本质是正计时 + targetDurationMs 到点提醒 */
    POMODORO,
    ;

    companion object {
        fun fromRaw(value: String?): SessionMode? = entries.firstOrNull { it.name == value }
    }
}
