package com.dailyschedule.app.core.transfer

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import kotlinx.serialization.Serializable

/**
 * 备份文件的根对象。
 *
 * ## 为什么是「实体快照」而不是「领域对象快照」
 * 恢复要重建的是**关系**：一条会话属于哪个项目、一笔账挂在哪个分类上。
 * 而关系是靠 `id` 表达的。领域模型在写入时让数据库分配 id，导出时不带 id，
 * 恢复时就只能重新分配 —— 于是必须有第二套「旧 id → 新 id」的映射表，
 * 映射错了不会崩，只会把 A 项目的时长记到 B 项目头上。
 *
 * 所以这里直接导出实体：连 id 一起，一个字段不落。恢复 = 原样写回，
 * 没有映射、没有推算、没有"大概应该是"。代价是这份文件与表结构耦合，
 * 因此用 [schemaVersion] 显式管版本。
 *
 * ## 字段一律不给默认值
 * 这不是风格问题。给了默认值，一个与本项目无关的 `{}` 也能解码成功，
 * 然后 count 校验全过（都是 0）、引用校验全过（没有引用）——
 * 于是导入一个空 JSON 会把用户三年的数据清空并提示「恢复成功」。
 * 缺字段就必须解码失败，失败发生在**动数据库之前**，用户什么都没丢。
 */
@Serializable
data class BackupDocument(
    /** 固定为 [BackupCodec.FORMAT_ID]。用来挡住"把别的 App 的 JSON 丢进来" */
    val format: String,
    /** 本文件的结构版本。与数据库版本是两件事，见 [BackupCodec] */
    val schemaVersion: Int,
    /** 写这份文件时的 App versionName，仅供人看 */
    val appVersion: String,
    /** 写这份文件时的 Room schema 版本，仅供人看与排查 */
    val dbVersion: Int,
    val exportedAt: Long,
    /** 与 [exportedAt] 同一时刻的可读形式。人打开文件至少要能一眼看出这是什么时候导的 */
    val exportedAtIso: String,
    val counts: BackupCounts,
    val data: BackupData,
)

/**
 * 各表条数。
 *
 * 存在的唯一理由是**自证完整**：JSON 被截断（网盘同步到一半、传输中断）后
 * 往往仍是合法 JSON，只是数组少了一截。有这个字段，[BackupValidator] 就能
 * 在动数据库之前发现「说好 42 条只给了 17 条」。
 */
@Serializable
data class BackupCounts(
    val projects: Int,
    val categories: Int,
    val sessions: Int,
    val expenses: Int,
)

/**
 * 四张表的全量内容。
 *
 * 顺序（维度表在前）不影响 JSON 语义，只是为了人读起来顺：
 * 先看有哪些项目和分类，再看挂在它们下面的记录。
 */
@Serializable
data class BackupData(
    val categories: List<CategoryRecord>,
    val projects: List<ProjectRecord>,
    val sessions: List<SessionRecord>,
    val expenses: List<ExpenseRecord>,
)

@Serializable
data class CategoryRecord(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isPreset: Boolean,
    val isEnabled: Boolean,
    val sortOrder: Int,
)

@Serializable
data class ProjectRecord(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isArchived: Boolean,
    val dailyTargetMinutes: Int?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 一条专注会话。
 *
 * 双时钟字段全部保留：`*ElapsedMs` 是单调时钟，`*WallClockMs` 是墙上时钟。
 * 恢复只能原样写回，不能拿墙上时钟反算单调时钟 —— 两者在不同的开机周期里
 * 起点不同，反算出来的时长会与用户当初看到的数字不一致。
 */
@Serializable
data class SessionRecord(
    val id: Long,
    val projectId: Long?,
    val status: SessionStatus,
    val needsReview: Boolean,
    val startElapsedMs: Long,
    val startWallClockMs: Long,
    val endElapsedMs: Long?,
    val endWallClockMs: Long?,
    val accumulatedPauseMs: Long,
    val pauseStartElapsedMs: Long?,
    val mode: SessionMode,
    val targetDurationMs: Long?,
    val source: SessionSource,
    val durationMs: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ExpenseRecord(
    val id: Long,
    /** 整数分。备份里也不存"元"，否则往返一次就有精度损失 */
    val amountCents: Long,
    val currency: String,
    val categoryId: Long,
    val projectId: Long?,
    val type: ExpenseType,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
