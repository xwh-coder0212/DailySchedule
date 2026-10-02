package com.dailyschedule.app.domain.model

/**
 * 项目（领域模型）。
 *
 * 项目是"时间账"的维度，也是"金钱账"的可选维度。
 * 归档只影响**计时选择器**，任何统计都必须包含归档项目 ——
 * 否则考完研一归档，三年历史就凭空消失了。
 */
data class Project(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isArchived: Boolean = false,
    /** null = 未设目标 */
    val dailyTargetMinutes: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
