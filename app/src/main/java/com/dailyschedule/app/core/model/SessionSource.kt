package com.dailyschedule.app.core.model

/**
 * 这条专注记录是怎么来的。
 *
 * 存在的唯一理由是**诚实**：计时器跑出来的时长有双时钟时间戳作证，
 * 用户事后补录的时长只有用户自己的说法。两者在统计上同等有效（都是真实投入），
 * 但在审阅历史时不该长得一模一样 —— 否则回看三个月前的记录，
 * 会分不清「我当时真的坐了 2 小时」和「我后来估了个 2 小时」。
 *
 * **DB 存字符串，不存序号**，与其它枚举一致：序号一旦重排，历史数据静默错位。
 */
enum class SessionSource {
    /** 计时器产生 */
    TIMER,

    /** 用户手动补录 */
    MANUAL,
    ;

    companion object {
        fun fromRaw(value: String?): SessionSource? =
            entries.firstOrNull { it.name == value }

        /** 旧数据没有这一列，迁移时一律按 TIMER 处理 —— 那时候还没有补录功能 */
        val DEFAULT: SessionSource = TIMER
    }
}
