package com.dailyschedule.app.data.repository

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.data.db.dao.ExpenseDao
import com.dailyschedule.app.data.mapper.toDomain
import com.dailyschedule.app.data.mapper.toEntity
import com.dailyschedule.app.domain.model.CategoryAmount
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl
    @Inject
    constructor(
        private val dao: ExpenseDao,
    ) : ExpenseRepository {
        override fun observeInRange(
            type: ExpenseType,
            startMs: Long,
            endMs: Long,
        ): Flow<List<Expense>> = dao.observeInRange(type, startMs, endMs).map { list -> list.map { it.toDomain() } }

        override fun observeRecent(
            type: ExpenseType,
            limit: Int,
        ): Flow<List<Expense>> = dao.observeRecent(type, limit).map { list -> list.map { it.toDomain() } }

        override fun observeTotalCents(
            type: ExpenseType,
            startMs: Long,
            endMs: Long,
        ): Flow<Long> = dao.observeTotalCents(type, startMs, endMs)

        override fun observeTotalByCategory(
            type: ExpenseType,
            startMs: Long,
            endMs: Long,
        ): Flow<List<CategoryAmount>> =
            dao.observeTotalByCategory(type, startMs, endMs).map { list ->
                list.map { row ->
                    CategoryAmount(
                        categoryId = row.categoryId,
                        categoryName = row.categoryName,
                        colorHex = row.colorHex,
                        iconName = row.iconName,
                        totalCents = row.totalCents,
                    )
                }
            }

        override fun observeTotalOfProject(
            projectId: Long,
            type: ExpenseType,
        ): Flow<Long> = dao.observeTotalOfProject(projectId, type)

        override fun observeOfProject(projectId: Long): Flow<List<Expense>> =
            dao.observeOfProject(projectId).map { list -> list.map { it.toDomain() } }

        override fun observeAll(): Flow<List<Expense>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun getById(id: Long): Expense? = dao.getById(id)?.toDomain()

        override suspend fun add(expense: Expense): Long = dao.insert(expense.toEntity())

        override suspend fun update(expense: Expense) = dao.update(expense.toEntity())

        override suspend fun delete(id: Long) {
            // 真删除 —— MVP 决策（见 ExpenseRepository.delete 注释）
            val entity = dao.getById(id) ?: return
            dao.delete(entity)
        }

        override suspend fun count(): Int = dao.count()
    }
