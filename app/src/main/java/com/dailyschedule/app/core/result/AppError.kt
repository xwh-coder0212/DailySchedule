package com.dailyschedule.app.core.result

/**
 * 应用内错误模型。
 *
 * 设计约束：
 * 1. 不是 Throwable —— 不需要堆栈也能表达"业务上失败了"；需要堆栈时放 [cause]。
 * 2. 分类到能直接用 UI 文案的程度：Validation 提示用户改输入，Database/Timer 提示重试或导出数据。
 * 3. [message] 是**技术信息**（进日志），不是给用户看的文案。UI 文案由 ViewModel 按类型查 strings。
 */
sealed interface AppError {

    val message: String
    val cause: Throwable?

    /** 输入不合法：金额为 0、结束早于开始、必填项为空。用户改一下就能继续。 */
    data class Validation(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** 找不到目标：要编辑的会话/消费已被删除。 */
    data class NotFound(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** 数据库层失败：约束冲突、磁盘满、IO 异常。 */
    data class Database(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** 计时状态机非法转换：对已结束的会话暂停、同时启动两个会话。 */
    data class Timer(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** 导入导出失败：文件不可读、schema 版本不兼容、无写入权限。 */
    data class Storage(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    /** 未归类。出现即代表有 catch 分支需要补充分类，日志里能查到。 */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError

    companion object {

        /**
         * 兜底映射。仅用于"没有更具体信息"的 catch 分支。
         *
         * data 层应在自己的 catch 里显式转 [Database]，而不是依赖这里猜类型 —— 让 [Unknown]
         * 出现在日志里是一种信号，说明某处 catch 写得不够具体。
         */
        fun from(throwable: Throwable): AppError = when (throwable) {
            // 只有「参数不合法」才算校验失败。
            // IllegalStateException 是程序状态错误（bug），若也归到 Validation，
            // 真实缺陷会被当成用户输入问题而掩盖 —— 必须落到 Unknown 便于排查。
            is IllegalArgumentException ->
                Validation(throwable.message ?: throwable::class.java.simpleName, throwable)

            is SecurityException, is java.io.IOException ->
                Storage(throwable.message ?: throwable::class.java.simpleName, throwable)

            else -> Unknown(throwable.message ?: throwable::class.java.simpleName, throwable)
        }
    }
}
