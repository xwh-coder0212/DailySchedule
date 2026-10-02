package com.dailyschedule.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.dailyschedule.app.data.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProjectEntity): Long

    @Upsert
    suspend fun upsert(entity: ProjectEntity): Long

    @Update
    suspend fun update(entity: ProjectEntity)

    @Update
    suspend fun updateAll(entities: List<ProjectEntity>)

    /**
     * 真删除。外键 SET_NULL 保证历史会话与消费被保留（projectId 置 NULL）。
     * 正常流程走归档（isArchived = 1），只有设置页的"彻底删除"才调这里。
     */
    @Delete
    suspend fun delete(entity: ProjectEntity)

    /** 项目页列表：归档的排在后面 */
    @Query("SELECT * FROM projects ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** 计时页选择器：不含归档 */
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<ProjectEntity>>

    /**
     * 归档列表。注意：这里**只**用于"项目页分组展示"，
     * 任何统计聚合都不得使用本查询，也不得加 isArchived 过滤。
     */
    @Query("SELECT * FROM projects WHERE isArchived = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeArchived(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM projects")
    suspend fun maxSortOrder(): Int

    // ── 备份 / 恢复专用（理由见 SessionDao 同名方法） ──

    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getAllOnce(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ProjectEntity>): List<Long>

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM projects")
    fun observeCount(): Flow<Int>
}
