package com.dailyschedule.app.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dailyschedule.app.core.transfer.BackupCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 SAF 读写备份 JSON。与 `XlsxExportWriter` 同一套路数：
 * 这一层是唯一需要 Android `Context` 的地方，编码/校验留在纯 Kotlin 的
 * `core/transfer` 里，于是"文件里到底写了什么"可以在纯 JVM 单测里断言。
 *
 * 读的是 `content://` URI，不是文件路径 —— 用户可能从网盘客户端选文件，
 * 那里没有真实路径。**这不代表 App 联网**：本项目没有 INTERNET 权限，
 * 数据只是从用户选中的那个 App 手里接过来。
 */
@Singleton
class JsonBackupStore @Inject constructor(
    // 显式 @param: 避免注解落到字段上（KT-73255），与 XlsxExportWriter 一致
    @param:ApplicationContext private val context: Context,
) {

    /**
     * @return 实际写入的文件显示名（用户可能在选择器里改过），用于回执提示
     */
    suspend fun write(uri: Uri, text: String): String = withContext(Dispatchers.IO) {
        val displayName = displayNameOf(uri) ?: uri.lastPathSegment.orEmpty()
        // 模式必须是 "wt"（write + truncate）：默认 "w" 在部分 DocumentsProvider
        // 上不截断已有文件，旧的尾部字节会留在新内容后面，得到一个
        // "JSON 解析到一半失败"的文件 —— 而用户看到的是导出成功。
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法打开所选位置（$displayName）")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        displayName
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val displayName = displayNameOf(uri) ?: uri.lastPathSegment.orEmpty()
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件（$displayName）")
        stream.use { readCapped(it, displayName) }
    }

    /**
     * 读取但**不超过 [BackupCodec.MAX_BYTES]**。
     *
     * 不能用 `available()` 先问大小：对 `content://` 流它经常返回 0 或一个
     * 与实际不符的数（本地文件还好，云盘来源完全不可信）。所以只能一边读
     * 一边数，超了立刻抛错。宁可多几行循环，也不要"先 readBytes() 再检查长度"——
     * 那等于把 OOM 的机会先给出去，再讨论限额。
     */
    private fun readCapped(input: InputStream, displayName: String): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > BackupCodec.MAX_BYTES) {
                throw IOException(
                    "文件过大（超过 ${BackupCodec.MAX_BYTES / 1024 / 1024} MB），" +
                        "看起来不是一份备份（$displayName）",
                )
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()
}
