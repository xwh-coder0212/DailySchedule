package com.dailyschedule.app.core.model

/**
 * 会话状态。
 *
 * **DB 存字符串，不存序号。** 序号一旦重排（插入新枚举、调整顺序），历史数据全部错位，
 * 且这种错误不会崩溃，只会让统计悄悄变错。
 */
enum class SessionStatus {
    /** 计时中 */
    RUNNING,

    /** 暂停中 */
    PAUSED,

    /** 已结束，[durationMs] 已写入 */
    COMPLETED,

    /** 用户删除（软删除），不参与任何统计 */
    DISCARDED,
    ;

    companion object {
        /** 活动态：同一时刻全库最多一行。由部分唯一索引在 DB 层强制（见 DatabaseCallback）。 */
        val ACTIVE: Set<SessionStatus> = setOf(RUNNING, PAUSED)

        fun fromRaw(value: String?): SessionStatus? = entries.firstOrNull { it.name == value }
    }
}
