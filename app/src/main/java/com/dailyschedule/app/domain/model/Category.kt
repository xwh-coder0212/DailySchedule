package com.dailyschedule.app.domain.model

/** 消费分类（领域模型）。 */
data class Category(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isPreset: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)
