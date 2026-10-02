package com.dailyschedule.app.data.repository

import com.dailyschedule.app.data.db.dao.SessionDao
import com.dailyschedule.app.data.mapper.toDomain
import com.dailyschedule.app.data.mapper.toEntity
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.ProjectDuration
import com.dailyschedule.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl
    @Inject
    constructor(
        private val dao: SessionDao,
    ) : SessionRepository {
        override fun observeActive(): Flow<FocusSession?> = dao.observeActive().map { it?.toDomain() }

        override suspend fun getActive(): FocusSession? = dao.getActiveOnce()?.toDomain()

        override suspend fun getById(id: Long): FocusSession? = dao.getById(id)?.toDomain()

        override fun observeTimeline(
            startMs: Long,
            endMs: Long,
        ): Flow<List<FocusSession>> = dao.observeTimeline(startMs, endMs).map { list -> list.map { it.toDomain() } }

        override fun observeInRange(
            startMs: Long,
            endMs: Long,
        ): Flow<List<FocusSession>> = dao.observeCompletedInRange(startMs, endMs).map { list -> list.map { it.toDomain() } }

        override fun observeTotalDuration(
            startMs: Long,
            endMs: Long,
        ): Flow<Long> = dao.observeTotalDuration(startMs, endMs)

        override fun observeCompletedCount(
            startMs: Long,
            endMs: Long,
        ): Flow<Int> = dao.observeCompletedCount(startMs, endMs)

        override fun observeCompletedCountOfProject(
            projectId: Long,
            startMs: Long,
            endMs: Long,
        ): Flow<Int> = dao.observeCompletedCountOfProject(projectId, startMs, endMs)

        override fun observeDurationByProject(
            startMs: Long,
            endMs: Long,
        ): Flow<List<ProjectDuration>> =
            dao.observeDurationByProject(startMs, endMs).map { list ->
                list.map { row -> row.toDomain() }
            }

        override fun observeTotalDurationOfProject(
            projectId: Long,
            startMs: Long,
            endMs: Long,
        ): Flow<Long> = dao.observeTotalDurationOfProject(projectId, startMs, endMs)

        override fun observeNeedsReview(): Flow<List<FocusSession>> = dao.observeNeedsReview().map { list -> list.map { it.toDomain() } }

        override fun observeByProject(
            projectId: Long,
            limit: Int,
        ): Flow<List<FocusSession>> = dao.observeRecentOfProject(projectId, limit).map { list -> list.map { it.toDomain() } }

        override suspend fun insert(session: FocusSession): Long = dao.insert(session.toEntity())

        override suspend fun update(session: FocusSession) = dao.update(session.toEntity())

        override suspend fun delete(id: Long): Boolean = dao.deleteCompleted(id) > 0

        override suspend fun findInconsistent(): List<FocusSession> = dao.findInconsistentDurations().map { it.toDomain() }
    }
