package com.dailyschedule.app.data.mapper

import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.model.ProjectDurationRow
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.ProjectDuration

fun FocusSessionEntity.toDomain(): FocusSession =
    FocusSession(
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

fun FocusSession.toEntity(): FocusSessionEntity =
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

fun ProjectDurationRow.toDomain(): ProjectDuration =
    ProjectDuration(
        projectId = projectId,
        projectName = projectName,
        colorHex = colorHex,
        iconName = iconName,
        totalDurationMs = totalDurationMs,
    )
