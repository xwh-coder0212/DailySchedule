package com.dailyschedule.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 项目。维度表，不是事实表。
 *
 * **归档不等于删除**：`isArchived = 1` 只影响"能否被选中开始计时"，
 * 所有历史统计查询**禁止**加 `isArchived` 过滤 —— 否则归档考研项目的瞬间，
 * 三年的历史投入就从统计里消失了。这是本项目最容易写错的一条。
 */
@Entity(
    tableName = "projects",
    indices = [Index("sortOrder"), Index("isArchived")],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 允许重名，唯一性由 UI 提示约束，DB 不加唯一索引 */
    val name: String,
    /** 存图标名字符串而非资源 id：可导出、可跨版本迁移 */
    val iconName: String,
    /** "#RRGGBB"。用户挑的原色，展示时按主题派生（见 Phase 6 颜色规范化） */
    val colorHex: String,
    val isArchived: Boolean = false,
    /** null = 未设目标 */
    val dailyTargetMinutes: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)
