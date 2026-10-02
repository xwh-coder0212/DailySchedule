package com.dailyschedule.app.data.db.model

import androidx.room.ColumnInfo

/** 项目维度的时间聚合结果。用于项目排行与项目四级累计。 */
data class ProjectDurationRow(
    @ColumnInfo(name = "projectId") val projectId: Long,
    @ColumnInfo(name = "projectName") val projectName: String,
    @ColumnInfo(name = "colorHex") val colorHex: String,
    @ColumnInfo(name = "iconName") val iconName: String,
    @ColumnInfo(name = "totalDurationMs") val totalDurationMs: Long,
)

/** 分类维度的金额聚合结果。用于分类占比。单位：分。 */
data class CategoryAmountRow(
    @ColumnInfo(name = "categoryId") val categoryId: Long,
    @ColumnInfo(name = "categoryName") val categoryName: String,
    @ColumnInfo(name = "colorHex") val colorHex: String,
    @ColumnInfo(name = "iconName") val iconName: String,
    @ColumnInfo(name = "totalCents") val totalCents: Long,
)

/** 单日聚合结果。用于消费/学习趋势（Phase 13 图表）。 */
data class DailyAmountRow(
    @ColumnInfo(name = "bucketStartMs") val bucketStartMs: Long,
    @ColumnInfo(name = "totalCents") val totalCents: Long,
)
