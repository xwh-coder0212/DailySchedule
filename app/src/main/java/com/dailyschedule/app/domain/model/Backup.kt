package com.dailyschedule.app.domain.model

import com.dailyschedule.app.core.transfer.BackupCounts

/**
 * 恢复完成后实际写入的条数，用于给用户回执。
 *
 * 不复用 [BackupCounts] 当返回值是有原因的：那份 counts 是**文件里声明的**，
 * 这份是**真的写进库里的**。用户在回执里看到的必须是后者 ——
 * 前者只是文件的一面之词，而 [RestoreReport] 是这条路径唯一有资格说
 * "已经写完"的东西。
 */
data class RestoreReport(
    val counts: BackupCounts,
    /** 被跳过的活动会话数（RUNNING / PAUSED）。恒为 0 才是常态，见 BackupValidator */
    val skippedActiveSessions: Int,
)

/**
 * 恢复的结果。
 *
 * 已预料的失败用返回值表达，不用异常：`ActiveSessionRunning` 是一个正常的
 * 用户操作顺序问题（正在计时时去点恢复），不是程序错误。
 * 抛异常会让"预期内的拒绝"和"真的崩了"在调用方看来长得一样。
 */
sealed interface RestoreOutcome {
    data class Restored(val report: RestoreReport) : RestoreOutcome

    /**
     * 当前有会话正在计时，拒绝恢复。
     *
     * 这不是小心过头：恢复会清空 `focus_sessions`，而计时器的前台服务
     * 手里还攥着那条会话的 id。清掉之后再点"结束"，UPDATE 会命中 0 行，
     * 用户看到的是"我停不了计时"或者时长凭空消失。
     */
    data object ActiveSessionRunning : RestoreOutcome
}
