package com.dailyschedule.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消费分类。维度表。
 *
 * `isPreset = true` 的分类不可删除（UI 层屏蔽），但可通过 `isEnabled = false` 停用。
 * 外键 RESTRICT 兜底：即使 UI 出 bug，数据库也不会允许删掉还有消费的分类。
 */
@Entity(
    tableName = "categories",
    indices = [Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isPreset: Boolean = false,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)
