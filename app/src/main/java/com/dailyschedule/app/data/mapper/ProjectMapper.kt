package com.dailyschedule.app.data.mapper

import com.dailyschedule.app.data.db.entity.ProjectEntity
import com.dailyschedule.app.domain.model.Project

fun ProjectEntity.toDomain(): Project =
    Project(
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

fun Project.toEntity(): ProjectEntity =
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
