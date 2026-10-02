package com.dailyschedule.app.data.transfer

import com.dailyschedule.app.core.transfer.CategoryRecord
import com.dailyschedule.app.core.transfer.ExpenseRecord
import com.dailyschedule.app.core.transfer.ProjectRecord
import com.dailyschedule.app.core.transfer.SessionRecord
import com.dailyschedule.app.data.db.entity.CategoryEntity
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity

/**
 * 实体 ⇄ 备份记录。手写展开每一个字段。
 *
 * ## 为什么不找一个自动映射库
 * 自动映射（反射、注解处理器）在这里有个无法接受的失效模式：**默默漏字段**。
 * 给实体加一列、忘了加进 [SessionRecord]，自动映射不报错，备份文件里那一列是空的，
 * 恢复之后这一列全部回到默认值 —— 而且是在几个月后用户换手机时才发现的。
 *
 * 手写的代价是"加字段要改四个地方"，但漏掉任何一处**编译期就会失败**
 * （`SessionRecord` 的构造函数少一个参数、或者 fromRecord 少一个赋值）。
 * 一次编译错误的成本，换掉一整类数据静默丢失的可能，很划算。
 *
 * ## 两边字段名允许不一致，但一律不用名字做映射
 * 这里是逐个具名赋值，不是按名字配对。所以将来 Entity 与 Record 的同名字段
 * 顺序不同、类型收窄（如 `String` → `String?`）都不会出错 ——
 * 只有真正漏了字段才会编译失败。
 */

fun CategoryEntity.toRecord(): CategoryRecord =
    CategoryRecord(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isPreset = isPreset,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
    )

fun CategoryRecord.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isPreset = isPreset,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
    )

// ── 项目 ──

fun ProjectEntity.toRecord(): ProjectRecord =
    ProjectRecord(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isArchived = isArchived,
        dailyTargetMinutes = dailyTargetMinutes,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ProjectRecord.toEntity(): ProjectEntity =
    ProjectEntity(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        isArchived = isArchived,
        dailyTargetMinutes = dailyTargetMinutes,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ── 会话 ──

fun FocusSessionEntity.toRecord(): SessionRecord =
    SessionRecord(
        id = id,
        projectId = projectId,
        status = status,
        needsReview = needsReview,
        startElapsedMs = startElapsedMs,
        startWallClockMs = startWallClockMs,
        endElapsedMs = endElapsedMs,
        endWallClockMs = endWallClockMs,
        accumulatedPauseMs = accumulatedPauseMs,
        pauseStartElapsedMs = pauseStartElapsedMs,
        mode = mode,
        targetDurationMs = targetDurationMs,
        source = source,
        durationMs = durationMs,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun SessionRecord.toEntity(): FocusSessionEntity =
    FocusSessionEntity(
        id = id,
        projectId = projectId,
        status = status,
        needsReview = needsReview,
        startElapsedMs = startElapsedMs,
        startWallClockMs = startWallClockMs,
        endElapsedMs = endElapsedMs,
        endWallClockMs = endWallClockMs,
        accumulatedPauseMs = accumulatedPauseMs,
        pauseStartElapsedMs = pauseStartElapsedMs,
        mode = mode,
        targetDurationMs = targetDurationMs,
        source = source,
        durationMs = durationMs,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

// ── 消费 ──

fun ExpenseEntity.toRecord(): ExpenseRecord =
    ExpenseRecord(
        id = id,
        amountCents = amountCents,
        currency = currency,
        categoryId = categoryId,
        projectId = projectId,
        type = type,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ExpenseRecord.toEntity(): ExpenseEntity =
    ExpenseEntity(
        id = id,
        amountCents = amountCents,
        currency = currency,
        categoryId = categoryId,
        projectId = projectId,
        type = type,
        note = note,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
