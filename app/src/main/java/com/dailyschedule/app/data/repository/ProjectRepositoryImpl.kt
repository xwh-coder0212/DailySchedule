package com.dailyschedule.app.data.repository

import com.dailyschedule.app.data.db.dao.ProjectDao
import com.dailyschedule.app.data.mapper.toDomain
import com.dailyschedule.app.data.mapper.toEntity
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl
    @Inject
    constructor(
        private val dao: ProjectDao,
    ) : ProjectRepository {
        override fun observeAll(): Flow<List<Project>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override fun observeActive(): Flow<List<Project>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

        override fun observeArchived(): Flow<List<Project>> = dao.observeArchived().map { list -> list.map { it.toDomain() } }

        override suspend fun getById(id: Long): Project? = dao.getById(id)?.toDomain()

        override suspend fun create(project: Project): Long {
            val nextOrder = (dao.maxSortOrder() + 1).coerceAtLeast(0)
            return dao.insert(project.copy(sortOrder = nextOrder).toEntity())
        }

        override suspend fun update(project: Project) = dao.update(project.toEntity())

        override suspend fun setArchived(
            id: Long,
            archived: Boolean,
        ) {
            val current = dao.getById(id) ?: return
            dao.update(current.copy(isArchived = archived))
        }

        override suspend fun reorder(orderedIds: List<Long>) {
            val all = dao.observeAll().first()
            val byId = all.associateBy { it.id }
            val reordered =
                orderedIds.mapIndexedNotNull { index, id ->
                    byId[id]?.copy(sortOrder = index)
                }
            if (reordered.isNotEmpty()) dao.updateAll(reordered)
        }

        override suspend fun delete(id: Long) {
            // 外键 SET_NULL 会自动把相关会话与消费的 projectId 置空，保留历史
            val entity = dao.getById(id) ?: return
            dao.delete(entity)
        }
    }
