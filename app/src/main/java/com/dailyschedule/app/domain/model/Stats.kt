package com.dailyschedule.app.domain.model

/** 项目在某区间内的累计时长（排行用，已按 totalDurationMs 倒序）。 */
data class ProjectDuration(
    val projectId: Long,
    val projectName: String,
    val colorHex: String,
    val iconName: String,
    val totalDurationMs: Long,
)

/** 分类在某区间内的累计金额（占比用，已按 totalCents 倒序）。 */
data class CategoryAmount(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val iconName: String,
    val totalCents: Long,
)

/**
 * 首页与统计页的"两个账本"。
 *
 * 注意 `studyMs` 的口径：**已完成会话的 DB 总和**，不含正在跑的会话。
 * 正在跑的那部分由上层叠加实时 elapsed，这里不混进来 ——
 * 否则同一个数字会有两种含义，排查时无从下手。
 */
data class DailyTotals(
    val completedStudyMs: Long,
    val sessionCount: Int,
    val expenseCents: Long,
)

/**
 * 图表里的一根柱子 / 一个点。
 *
 * `key` 的含义由系列决定，不在这里写死 —— 周分布里它是 ISO 星期号（1=周一…7=周日），
 * 日趋势里它是「第几天」（0 基），月趋势里它是月份（1..12）。
 * 之所以不复用两套类型，是因为聚合逻辑（补零、排序、取最大值）完全一样，
 * 分成两种只会让同一段代码抄两遍。
 *
 * 对外一律带上 `key`，而不是只给一个 `List<Long>`：
 * 只有值的列表在「哪些格子是空的」上会骗人 —— 一周里只有周三有数据时，
 * 值得长度为 1 的列表画不出「周三那根柱子在中间」，会画成贴着左边第一格。
 */
data class StatBucket(val key: Int, val value: Long)
