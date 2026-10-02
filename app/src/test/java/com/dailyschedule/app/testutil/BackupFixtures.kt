package com.dailyschedule.app.testutil

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.transfer.BackupCodec
import com.dailyschedule.app.core.transfer.BackupCounts
import com.dailyschedule.app.core.transfer.BackupData
import com.dailyschedule.app.core.transfer.BackupDocument
import com.dailyschedule.app.core.transfer.CategoryRecord
import com.dailyschedule.app.core.transfer.ExpenseRecord
import com.dailyschedule.app.core.transfer.ProjectRecord
import com.dailyschedule.app.core.transfer.SessionRecord

/**
 * 备份相关的测试夹具。
 *
 * 默认参数刻意选成"合法且能通过校验"的那一档：测试要构造的是**某一条**不合法，
 * 于是每条用例只需写出它关心的那个字段，其余保持默认。
 * 反过来（默认为空/0）会让每个用例都被迫先补一堆无关字段，
 * 而真正的意图被淹没在噪声里。
 */
object BackupFixtures {

    fun category(id: Long, name: String = "分类$id") = CategoryRecord(
        id = id,
        name = name,
        iconName = "more_horiz",
        colorHex = "#8A8F98",
        isPreset = false,
        isEnabled = true,
        sortOrder = id.toInt(),
    )

    fun project(
        id: Long,
        name: String = "项目$id",
        isArchived: Boolean = false,
        dailyTargetMinutes: Int? = null,
    ) = ProjectRecord(
        id = id,
        name = name,
        iconName = "menu_book",
        colorHex = "#2FA37B",
        isArchived = isArchived,
        dailyTargetMinutes = dailyTargetMinutes,
        sortOrder = id.toInt(),
        createdAt = 1_000L + id,
        updatedAt = 2_000L + id,
    )

    fun session(
        id: Long,
        projectId: Long? = null,
        status: SessionStatus = SessionStatus.COMPLETED,
        source: SessionSource = SessionSource.TIMER,
        startWallClockMs: Long = 10_000L + id,
        durationMs: Long? = 3_600_000L,
        note: String? = null,
    ): SessionRecord {
        // COMPLETED 必须是"有权威时长"的；其余状态允许为 null。
        // 这个区分是数据模型本身的性质，夹具照做，免得用例拿到一个
        // 现实中不可能存在的组合（已完成但没时长）。
        val effectiveDuration = if (status == SessionStatus.COMPLETED) durationMs else null
        val endWall = effectiveDuration?.let { startWallClockMs + it }
        return SessionRecord(
            id = id,
            projectId = projectId,
            status = status,
            needsReview = false,
            startElapsedMs = startWallClockMs,
            startWallClockMs = startWallClockMs,
            endElapsedMs = endWall,
            endWallClockMs = endWall,
            accumulatedPauseMs = 0L,
            pauseStartElapsedMs = null,
            mode = SessionMode.STOPWATCH,
            targetDurationMs = null,
            source = source,
            durationMs = effectiveDuration,
            note = note,
            createdAt = startWallClockMs,
            updatedAt = startWallClockMs,
        )
    }

    fun expense(
        id: Long,
        categoryId: Long,
        projectId: Long? = null,
        amountCents: Long = 800L,
        type: ExpenseType = ExpenseType.EXPENSE,
    ) = ExpenseRecord(
        id = id,
        amountCents = amountCents,
        currency = "CNY",
        categoryId = categoryId,
        projectId = projectId,
        type = type,
        note = null,
        occurredAt = 30_000L + id,
        createdAt = 30_000L + id,
        updatedAt = 30_000L + id,
    )

    /**
     * 组装一份文档。
     *
     * [counts] 默认由实际条数算出 —— "声明与实际一致"是绝大多数用例的前提，
     * 只有专门测 COUNT_MISMATCH 的那条才会显式传一个错的值进去。
     */
    fun document(
        categories: List<CategoryRecord> = listOf(category(1)),
        projects: List<ProjectRecord> = listOf(project(1)),
        sessions: List<SessionRecord> = listOf(session(1, projectId = 1)),
        expenses: List<ExpenseRecord> = listOf(expense(1, categoryId = 1)),
        format: String = BackupCodec.FORMAT_ID,
        schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
        counts: BackupCounts? = null,
    ) = BackupDocument(
        format = format,
        schemaVersion = schemaVersion,
        appVersion = "1.0.0-debug",
        dbVersion = 2,
        exportedAt = 1_759_400_000_000L,
        exportedAtIso = "2026-10-02T12:43:19.412+08:00",
        counts = counts ?: BackupCounts(
            projects = projects.size,
            categories = categories.size,
            sessions = sessions.size,
            expenses = expenses.size,
        ),
        data = BackupData(
            categories = categories,
            projects = projects,
            sessions = sessions,
            expenses = expenses,
        ),
    )
}
