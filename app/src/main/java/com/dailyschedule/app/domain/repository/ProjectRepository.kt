package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    /** 全部项目（含归档），项目页列表用 */
    fun observeAll(): Flow<List<Project>>

    /** 未归档项目，计时页选择器用 */
    fun observeActive(): Flow<List<Project>>

    fun observeArchived(): Flow<List<Project>>

    suspend fun getById(id: Long): Project?

    suspend fun create(project: Project): Long

    suspend fun update(project: Project)

    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun reorder(orderedIds: List<Long>)

    /** 彻底删除。会话与消费保留（projectId 置 NULL） */
    suspend fun delete(id: Long)
}
