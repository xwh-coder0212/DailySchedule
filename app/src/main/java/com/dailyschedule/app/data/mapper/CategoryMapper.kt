package com.dailyschedule.app.data.mapper

import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.data.db.entity.CategoryEntity

/**
 * Entity ⇄ Domain 映射。
 *
 * 多写这几十行机械代码，换的是：**UI 层永远不认识 Room**。
 * 表结构演进、换存储方案、给 domain model 加计算属性，都不会外溢到 feature 层。
 */

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    isPreset = isPreset,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    isPreset = isPreset,
    isEnabled = isEnabled,
    sortOrder = sortOrder,
)
