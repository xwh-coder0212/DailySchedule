package com.dailyschedule.app.core.transfer

import com.dailyschedule.app.core.model.SessionStatus

/**
 * 导入被拒绝的原因。
 *
 * 用枚举而不是一句拼好的中文，是为了让界面层自己决定文案 ——
 * 与 `ExportOutcome` 同样的理由，将来换语言时不用回来改领域层。
 */
enum class BackupRejection {
    /** `format` 字段不是本项目的标识：拿错了文件 */
    NOT_A_BACKUP,

    /** 结构版本比本版 App 新。让它继续，等于用旧代码去解释新字段 */
    TOO_NEW,

    /** 结构版本比本版 App 旧，且没有对应的迁移路径 */
    TOO_OLD,

    /** `counts` 与实际条数对不上：文件被截断或手改过 */
    COUNT_MISMATCH,

    /** 同一张表里出现重复 id。写回去必然主键冲突，且冲突会中途发生 */
    DUPLICATE_ID,

    /** 引用了文件里不存在的项目或分类。写回去必然外键失败 */
    DANGLING_REFERENCE,

    /** 数值越界：金额 ≤ 0、已完成会话缺时长、累计暂停为负等 */
    INVALID_VALUES,
}

/**
 * 校验结果。
 *
 * [Rejected] 里的 `detail` 是给日志的（含具体 id、具体条数），不进界面 ——
 * 界面只需要知道"哪一类问题"，具体数字对用户没有意义。
 */
sealed interface BackupValidation {

    /**
     * 通过，可以进入恢复流程。
     *
     * [activeSessions] 是文件里处于 RUNNING / PAUSED 的会话数。它们**不会被恢复**，
     * 恢复流程会跳过并回报。理由见 [BackupValidator]。
     */
    data class Ok(val activeSessions: Int) : BackupValidation

    data class Rejected(val reason: BackupRejection, val detail: String) : BackupValidation
}

/**
 * 导入前的完整性校验。**纯 Kotlin，不碰数据库、不碰文件**。
 *
 * ## 为什么校验必须全部在"清库之前"完成
 * 导入是「删掉现在的一切，写入文件里的一切」。如果校验和写入是交织的
 * （读到一半发现第 900 行外键错了），那么用户的状态是：库已经空了。
 * 事务能回滚写入，但**回滚不了用户的信任** —— 更现实的问题是，
 * 用错误信息告诉用户"文件有问题"的同时数据已经没了，这是不可接受的。
 *
 * 因此顺序固定为：全部解码 → 全部校验 → 打开事务 → 删 → 写。
 * 前两步失败时数据库一个字节都没动。
 *
 * ## 为什么跳过活动会话而不是拒绝整个文件
 * 活动会话的两个单调时钟（`startElapsedMs` / `endElapsedMs`）来自**这一次开机**，
 * 换设备、重启、跨版本恢复之后它们不对应任何真实时刻：一个"已跑了 3 小时"的
 * 会话在恢复后会显示成任意数字。项目里已经有同样的判断 ——
 * `TimerRebootRecoveryUseCase` 在重启后就是截断而不是延续。
 *
 * 而且活动会话还不算一条事实：它没有 `durationMs`，导出的 Excel 里本来也没有它。
 * 所以这里的选择是：照常接受文件，跳过这些行，并把跳过条数报给用户。
 */
object BackupValidator {

    fun validate(document: BackupDocument): BackupValidation {
        if (document.format != BackupCodec.FORMAT_ID) {
            return BackupValidation.Rejected(
                BackupRejection.NOT_A_BACKUP,
                "format=${document.format}，期望 ${BackupCodec.FORMAT_ID}",
            )
        }
        if (document.schemaVersion > BackupCodec.SCHEMA_VERSION) {
            return BackupValidation.Rejected(
                BackupRejection.TOO_NEW,
                "schemaVersion=${document.schemaVersion}，本版支持到 ${BackupCodec.SCHEMA_VERSION}",
            )
        }
        if (document.schemaVersion < 1) {
            return BackupValidation.Rejected(
                BackupRejection.TOO_OLD,
                "schemaVersion=${document.schemaVersion}",
            )
        }

        val data = document.data
        countMismatch(document)?.let { return it }
        duplicateId(data)?.let { return it }
        danglingReference(data)?.let { return it }
        invalidValue(data)?.let { return it }

        return BackupValidation.Ok(
            activeSessions = data.sessions.count { it.status in SessionStatus.ACTIVE },
        )
    }

    private fun countMismatch(document: BackupDocument): BackupValidation? {
        val data = document.data
        val actual = BackupCounts(
            projects = data.projects.size,
            categories = data.categories.size,
            sessions = data.sessions.size,
            expenses = data.expenses.size,
        )
        if (actual == document.counts) return null
        return BackupValidation.Rejected(
            BackupRejection.COUNT_MISMATCH,
            "声明 ${document.counts}，实际 $actual",
        )
    }

    private fun duplicateId(data: BackupData): BackupValidation? {
        duplicateIn(data.categories.map { it.id })?.let {
            return BackupValidation.Rejected(BackupRejection.DUPLICATE_ID, "categories 内重复 id=$it")
        }
        duplicateIn(data.projects.map { it.id })?.let {
            return BackupValidation.Rejected(BackupRejection.DUPLICATE_ID, "projects 内重复 id=$it")
        }
        duplicateIn(data.sessions.map { it.id })?.let {
            return BackupValidation.Rejected(BackupRejection.DUPLICATE_ID, "sessions 内重复 id=$it")
        }
        duplicateIn(data.expenses.map { it.id })?.let {
            return BackupValidation.Rejected(BackupRejection.DUPLICATE_ID, "expenses 内重复 id=$it")
        }
        return null
    }

    private fun duplicateIn(ids: List<Long>): Long? {
        val seen = HashSet<Long>(ids.size)
        return ids.firstOrNull { !seen.add(it) }
    }

    private fun danglingReference(data: BackupData): BackupValidation? {
        val projectIds = data.projects.mapTo(HashSet()) { it.id }
        val categoryIds = data.categories.mapTo(HashSet()) { it.id }

        data.sessions.firstOrNull { it.projectId != null && it.projectId !in projectIds }
            ?.let {
                return BackupValidation.Rejected(
                    BackupRejection.DANGLING_REFERENCE,
                    "sessions id=${it.id} 指向不存在的 projectId=${it.projectId}",
                )
            }
        data.expenses.firstOrNull { it.projectId != null && it.projectId !in projectIds }
            ?.let {
                return BackupValidation.Rejected(
                    BackupRejection.DANGLING_REFERENCE,
                    "expenses id=${it.id} 指向不存在的 projectId=${it.projectId}",
                )
            }
        data.expenses.firstOrNull { it.categoryId !in categoryIds }
            ?.let {
                return BackupValidation.Rejected(
                    BackupRejection.DANGLING_REFERENCE,
                    "expenses id=${it.id} 指向不存在的 categoryId=${it.categoryId}",
                )
            }
        return null
    }

    private fun invalidValue(data: BackupData): BackupValidation? {
        data.projects.firstOrNull { it.name.isBlank() }?.let {
            return BackupValidation.Rejected(BackupRejection.INVALID_VALUES, "projects id=${it.id} 名称为空")
        }
        data.categories.firstOrNull { it.name.isBlank() }?.let {
            return BackupValidation.Rejected(
                BackupRejection.INVALID_VALUES,
                "categories id=${it.id} 名称为空",
            )
        }
        data.sessions.firstOrNull { it.status == SessionStatus.COMPLETED && it.durationMs == null }
            ?.let {
                return BackupValidation.Rejected(
                    BackupRejection.INVALID_VALUES,
                    "sessions id=${it.id} 状态 COMPLETED 但没有 durationMs",
                )
            }
        data.sessions.firstOrNull { (it.durationMs ?: 0L) < 0L || it.accumulatedPauseMs < 0L }
            ?.let {
                return BackupValidation.Rejected(
                    BackupRejection.INVALID_VALUES,
                    "sessions id=${it.id} 时长或累计暂停为负",
                )
            }
        data.expenses.firstOrNull { it.amountCents <= 0L }?.let {
            return BackupValidation.Rejected(
                BackupRejection.INVALID_VALUES,
                "expenses id=${it.id} 金额 ${it.amountCents} 分不大于 0",
            )
        }
        return null
    }
}
