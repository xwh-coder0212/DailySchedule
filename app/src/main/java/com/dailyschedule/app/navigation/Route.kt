package com.dailyschedule.app.navigation

import kotlinx.serialization.Serializable

/**
 * 类型安全路由。每个屏幕一个 @Serializable 对象或数据类，
 * 参数直接写在数据类里，不拼字符串。
 */
sealed interface Route

@Serializable
data object Projects : Route

@Serializable
data object Stats : Route

/** 记账 Tab（列表）。增删改的入口都在这张列表上。 */
@Serializable
data object Expense : Route

@Serializable
data class ProjectEdit(val projectId: Long? = null) : Route

/**
 * 某个待办的专注历史记录（可改时长、可删、可补录）。
 *
 * 待办详情本身不再是一条路由：Rev2 把它改成了从卡片弹出的面板，
 * 面板里的「专注历史记录」才需要独立页面。
 */
@Serializable
data class ProjectSessions(val projectId: Long) : Route

/**
 * 记账表单。`expenseId == null` 即「新增」。
 *
 * 新增与修改共用同一个路由，是因为两者的字段、校验、键盘完全一样，
 * 差别只在「保存」时是 insert 还是 update —— 拆成两条路由会让这份表单写两遍。
 */
@Serializable
data class ExpenseEdit(val expenseId: Long? = null) : Route

@Serializable
data object Settings : Route

@Serializable
data object CategoryManager : Route

@Serializable
data object DataTransfer : Route
