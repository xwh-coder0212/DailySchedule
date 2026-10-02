package com.dailyschedule.app.data.transfer

import android.content.Context
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.transfer.BackupCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入前的自动本地备份。**导入会清空全库**，这是它唯一的存在理由。
 *
 * ## 为什么不指望用户自己先导一份
 * 真实的操作顺序是：用户换机、拿到一份旧备份、点导入、发现选错了文件。
 * 到这一步再告诉他"你应该先导出备份"是没用的 —— 数据已经没了。
 * 所以「导入」这个动作自己必须先留一手：进事务之前，把**当前**全库
 * 原样存成同一格式的 JSON，放在应用私有目录里。
 *
 * 私有目录（`filesDir`）的好处与坏处都很明确：
 * - 好处：卸载前一直在，不需要任何存储权限，用户误删不了。
 * - 坏处：用户自己也拿不到（除非再走一次导出）。
 *
 * 于是这里配一个读回接口 [latest]，让界面能提供「撤销上次导入」。
 * 只留 [KEEP] 份，滚动覆盖：留太多会长期占空间且没有任何人会去看第 4 份，
 * 留 1 份又会在"导入 A、发现不对、导入 B、又不对"时无路可退。
 *
 * ## 它不能替代用户自己的备份
 * 卸载 App 会一起删掉这里。这一点必须写在界面上，不能让用户以为
 * "反正有自动备份"。
 */
@Singleton
class PreImportBackupKeeper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** 保留份数。见类注释里为什么是 3 而不是 1 或 10 */
    private val directory: File
        get() = File(context.filesDir, DIR_NAME)

    /**
     * 存一份导入前的快照。
     *
     * @return 落盘的文件名，写进回执给用户看
     */
    suspend fun keep(json: String, stamp: String): String = withContext(Dispatchers.IO) {
        val dir = directory
        if (!dir.exists() && !dir.mkdirs()) {
            // 存不下就抛错，让导入流程中止。
            // 这里不能"记个日志继续导" —— 那样用户会在一个没有退路的
            // 状态下完成清库操作，而这正是本类要防的事。
            error("无法创建自动备份目录：${dir.absolutePath}")
        }
        val name = "$PREFIX$stamp.${BackupCodec.FILE_EXTENSION}"
        File(dir, name).writeText(json)
        prune()
        name
    }

    /** 最近一次导入前备份的**内容**，没有则返回 null */
    suspend fun latest(): String? = withContext(Dispatchers.IO) {
        newestFile()?.readText()
    }

    /** 最近一次导入前备份的文件名，用于在界面上说明"撤销会回到哪一份" */
    suspend fun latestName(): String? = withContext(Dispatchers.IO) { newestFile()?.name }

    private fun newestFile(): File? = directory
        .listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
        // 文件名里的时间戳是 yyyyMMdd_HHmm，字典序即时间序，不必解析
        ?.maxByOrNull { it.name }

    private fun prune() {
        val all = directory
            .listFiles { file -> file.isFile && file.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            ?: return
        all.drop(KEEP).forEach { stale ->
            if (!stale.delete()) {
                // 删不掉不影响本次导入的正确性，只影响占用。
                // 记一笔就好，不要因为这个把导入搞失败。
                AppLogger.w(TAG, "旧的自动备份删不掉：${stale.name}")
            }
        }
    }

    private companion object {
        const val TAG = "PreImportBackup"

        const val DIR_NAME = "pre-import-backup"

        /** 前缀。用于识别"哪些是本类产生的文件"，别去删目录里其他东西 */
        const val PREFIX = "pre-import-"

        const val KEEP = 3
    }
}
