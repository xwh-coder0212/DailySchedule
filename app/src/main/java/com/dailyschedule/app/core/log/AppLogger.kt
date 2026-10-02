package com.dailyschedule.app.core.log

import android.os.Build
import android.util.Log
import com.dailyschedule.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志入口。
 *
 * 规则：
 * - debug 包输出 VERBOSE 及以上；release 包只输出 WARN 及以上（避免日志里出现金额与备注）。
 * - tag 超过 23 字符会被系统截断，这里主动截断并加 `*` 标记，避免误判两个 tag 相同。
 * - 崩溃日志落盘到 `filesDir/crash/`，最多保留 5 份，超出删最旧的。
 */
object AppLogger {

    private const val CRASH_DIR = "crash"
    private const val MAX_CRASH_FILES = 5
    private const val MAX_TAG_LENGTH = 23

    private val minPriority: Int = if (BuildConfig.DEBUG) Log.VERBOSE else Log.WARN

    fun v(tag: String, message: String) {
        log(Log.VERBOSE, tag, message, null)
    }

    fun d(tag: String, message: String) {
        log(Log.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(Log.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.ERROR, tag, message, throwable)
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (priority < minPriority) return
        val safeTag = safeTag(tag)
        when (throwable) {
            null -> Log.println(priority, safeTag, message)
            else -> {
                Log.println(priority, safeTag, message)
                Log.println(priority, safeTag, Log.getStackTraceString(throwable))
            }
        }
    }

    /**
     * 把崩溃堆栈写入 `filesDir/crash/crash_<yyyyMMdd-HHmmss>.log`。
     *
     * 返回写入的文件；写入失败返回 null —— 崩溃报告本身绝不能再抛异常，
     * 否则会覆盖真实崩溃原因，让排查无从下手。
     */
    fun writeCrash(filesDir: File, throwable: Throwable): File? = try {
        val dir = File(filesDir, CRASH_DIR).apply { if (!exists()) mkdirs() }
        pruneOldCrashes(dir)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.log")
        file.writeText(buildCrashBody(throwable))
        file
    } catch (suppressed: Throwable) {
        Log.e("AppLogger", "Failed to write crash log", suppressed)
        null
    }

    private fun buildCrashBody(throwable: Throwable): String = buildString {
        appendLine("time=${System.currentTimeMillis()}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("sdk=${Build.VERSION.SDK_INT}")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        appendLine(Log.getStackTraceString(throwable))
    }

    private fun pruneOldCrashes(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.sortedBy { it.lastModified() }
            ?: return
        val overflow = files.size - (MAX_CRASH_FILES - 1)
        if (overflow > 0) {
            files.take(overflow).forEach { it.delete() }
        }
    }

    private fun safeTag(tag: String): String =
        if (tag.length <= MAX_TAG_LENGTH) tag else tag.take(MAX_TAG_LENGTH - 1) + "*"
}
