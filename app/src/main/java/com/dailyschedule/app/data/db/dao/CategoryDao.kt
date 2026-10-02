package com.dailyschedule.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailyschedule.app.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(entity: CategoryEntity)

    @Update
    suspend fun updateAll(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /** 记账页分类选择器：只显示启用中的 */
    @Query("SELECT * FROM categories WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeEnabled(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Delete
    suspend fun delete(entity: CategoryEntity)

    /**
     * 该分类下有多少笔账。用于"停用/删除前"确认。
     * 外键 RESTRICT 已经禁止真删除，这里是为了在 UI 层给出可操作的提示。
     */
    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countExpenses(categoryId: Long): Int
}
