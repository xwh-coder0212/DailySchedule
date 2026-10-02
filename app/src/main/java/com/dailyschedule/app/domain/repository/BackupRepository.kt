package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.core.transfer.BackupCounts
import com.dailyschedule.app.core.transfer.BackupDocument
import com.dailyschedule.app.domain.model.RestoreOutcome
import kotlinx.coroutines.flow.Flow

/**
 * 备份与恢复。
 *
 * 与 Excel 导出（`XlsxExportWriter` 那条路）是**两件事**，不要合并：
 * - Excel 是给人看和存档的报表，导不回来；
 * - 这个接口产出的 JSON 是完整备份，能原样恢复，代价是与表结构耦合、需要管版本。
 *
 * 放在 `domain` 而不是让 ViewModel 直接用 `XlsxExportWriter` 那样越过这一层，
 * 是因为这里要做的事（读全库、在一个事务里替换全库）属于业务规则，
 * 不是"写一个文件"这样的技术动作。
 */
interface BackupRepository {

    /**
     * 各表**会写进备份**的条数，随数据变化自动更新。
     *
     * 用 COUNT(*) 而不是"读全表再 size"：这个数字只服务于界面上一行提示，
     * 为了显示四个数字把上万行读进内存，越到后面越慢，而慢在这里毫无意义。
     *
     * 返回 Flow 是因为它必须与 [snapshot] 用同一个口径 ——
     * 两处各写一遍过滤条件，迟早会出现"界面说 42 条、文件里 40 条"。
     */
    fun observeCounts(): Flow<BackupCounts>

    /**
     * 读全库，组装成一份备份文档。
     *
     * **不含活动会话**（RUNNING / PAUSED），理由见 `SessionDao.getAllSettledOnce`。
     *
     * @param appVersion 写进文件的 versionName，只供人看。由调用方传入，
     *   因为 `BuildConfig` 是构建期产物，不该渗进数据层。
     */
    suspend fun snapshot(appVersion: String): BackupDocument

    /**
     * 用文档内容**整体替换**当前数据。
     *
     * 调用前必须先过 `BackupValidator.validate`。这里不再校验文档内部一致性 ——
     * 但会重新确认"此刻没有会话在计时"，因为那件事在文件里看不出来。
     *
     * 全量替换而不是合并：合并需要一套 id 冲突消解规则（同 id 谁赢？
     * 同名不同 id 算不算同一个项目？），任何一条猜错都会把两段历史搅在一起，
     * 而且事后无法分辨。替换的语义是确定的，配合"导入前自动本地备份"
     * 与一次明确的二次确认，用户始终有退路。
     */
    suspend fun replaceAll(document: BackupDocument): RestoreOutcome
}
