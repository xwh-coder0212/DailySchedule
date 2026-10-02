package com.dailyschedule.app.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dailyschedule.app.core.export.ExportTable
import com.dailyschedule.app.core.export.XlsxWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把已组装好的 [ExportTable] 写进用户通过 SAF 选定的位置。
 *
 * 这一层是**唯一**需要 Android `Context` 的地方：`ContentResolver` 与 `Uri` 都是
 * Android 类型。上层（[com.dailyschedule.app.core.export]）保持纯 Kotlin，
 * 于是导出内容本身可以在 IDE 里直接单测，不必装模拟器。
 *
 * 写的是 `content://` URI，不是文件路径 —— 用户可能选到网盘客户端提供的
 * 虚拟文档，那里根本没有真实路径。**这不代表 App 联网**：本项目没有
 * INTERNET 权限，数据只是被交到用户选中的那个 App 手里。
 */
@Singleton
class XlsxExportWriter @Inject constructor(
    // 显式写 @param: 目标：Kotlin 2.x 起注解默认会同时落到字段上，
    // 不写目标会触发 KT-73255 的迁移提示。这里只想限定在构造参数上。
    @param:ApplicationContext private val context: Context,
) {

    /**
     * @return 实际写入的文件显示名（用户在系统选择器里可能改过），用于回执提示
     */
    suspend fun write(uri: Uri, tables: List<ExportTable>): String = withContext(Dispatchers.IO) {
        val displayName = displayNameOf(uri) ?: uri.lastPathSegment.orEmpty()
        // 模式必须是 "wt"（write + truncate）。默认的 "w" 在某些
        // DocumentsProvider 上不会截断已有文件，旧的尾部字节会留在新内容后面，
        // 结果是一个「打开就报损坏」的 xlsx —— 而用户看到的是导出成功。
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法打开所选位置（$displayName）")
        stream.use { XlsxWriter.write(tables, it) }
        displayName
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    }.getOrNull()
}
