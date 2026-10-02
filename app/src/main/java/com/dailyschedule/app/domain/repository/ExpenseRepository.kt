package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.domain.model.CategoryAmount
import com.dailyschedule.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {

    fun observeInRange(type: ExpenseType, startMs: Long, endMs: Long): Flow<List<Expense>>

    fun observeRecent(type: ExpenseType, limit: Int): Flow<List<Expense>>

    fun observeTotalCents(type: ExpenseType, startMs: Long, endMs: Long): Flow<Long>

    fun observeTotalByCategory(
        type: ExpenseType,
        startMs: Long,
        endMs: Long,
    ): Flow<List<CategoryAmount>>

    /** 某项目上的累计花费（P1 才做 UI，字段与查询现在就有） */
    fun observeTotalOfProject(projectId: Long, type: ExpenseType): Flow<Long>

    fun observeOfProject(projectId: Long): Flow<List<Expense>>

    /** 全部消费（含收入），按发生时间升序。**仅供导出**，不要拿去做统计 */
    fun observeAll(): Flow<List<Expense>>

    suspend fun getById(id: Long): Expense?

    suspend fun add(expense: Expense): Long

    suspend fun update(expense: Expense)

    /** 软删除：标 DISCARDED？不 —— 消费金额小、误删直接改，MVP 用真删除 */
    suspend fun delete(id: Long)

    suspend fun count(): Int
}
