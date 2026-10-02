package com.dailyschedule.app.data.repository

import com.dailyschedule.app.data.db.dao.CategoryDao
import com.dailyschedule.app.data.db.seed.PresetCategories
import com.dailyschedule.app.data.mapper.toDomain
import com.dailyschedule.app.data.mapper.toEntity
import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl
    @Inject
    constructor(
        private val dao: CategoryDao,
    ) : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override fun observeEnabled(): Flow<List<Category>> = dao.observeEnabled().map { list -> list.map { it.toDomain() } }

        override suspend fun getById(id: Long): Category? = dao.getById(id)?.toDomain()

        override suspend fun create(category: Category): Long = dao.insert(category.toEntity())

        override suspend fun update(category: Category) = dao.update(category.toEntity())

        override suspend fun reorder(orderedIds: List<Long>) {
            val all = dao.observeAll().first()
            val byId = all.associateBy { it.id }
            val reordered =
                orderedIds.mapIndexedNotNull { index, id ->
                    byId[id]?.copy(sortOrder = index)
                }
            if (reordered.isNotEmpty()) dao.updateAll(reordered)
        }

        override suspend fun delete(id: Long): Boolean {
            if (dao.countExpenses(id) > 0) return false
            val entity = dao.getById(id) ?: return false
            if (entity.isPreset) return false
            dao.delete(entity)
            return true
        }

        override suspend fun countExpenses(categoryId: Long): Int = dao.countExpenses(categoryId)

        override suspend fun ensureSeeded(nameResolver: (Int) -> String) {
            if (dao.count() > 0) return
            dao.insertAll(PresetCategories.entities(nameResolver))
        }
    }
