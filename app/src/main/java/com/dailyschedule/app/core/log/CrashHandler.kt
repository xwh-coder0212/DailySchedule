package com.dailyschedule.app.core.log

import android.content.Context

/**
 * 未捕获异常处理器：落盘后交还给系统默认处理器。
 *
 * 两个要点：
 * 1. **必须交还默认处理器**，否则崩溃后进程静默退出，用户只看到"应用无响应"，
 *    且 Google Play 的崩溃统计拿不到数据。
 * 2. 落盘失败不能影响第 1 步 —— [AppLogger.writeCrash] 内部已吞掉自身异常。
 */
class CrashHandler(
    private val context: Context,
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        AppLogger.e(TAG, "Uncaught exception on thread=${thread.name}", throwable)
        AppLogger.writeCrash(context.filesDir, throwable)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    fun install() {
        // 重复 install 会把上一个 CrashHandler 当成 defaultHandler 包起来，形成链。
        if (Thread.getDefaultUncaughtExceptionHandler() is CrashHandler) return
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    private companion object {
        const val TAG = "CrashHandler"
    }
}
