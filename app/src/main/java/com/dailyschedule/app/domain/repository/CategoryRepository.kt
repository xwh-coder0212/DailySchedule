package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    /** 全部分类（含停用），设置页分类管理用 */
    fun observeAll(): Flow<List<Category>>

    /** 启用中的分类，记账页选择器用 */
    fun observeEnabled(): Flow<List<Category>>

    suspend fun getById(id: Long): Category?

    suspend fun create(category: Category): Long

    suspend fun update(category: Category)

    suspend fun reorder(orderedIds: List<Long>)

    /**
     * 删除分类。调用方必须先确认该分类下没有消费，或已把消费转移走 ——
     * 外键 RESTRICT 会在 DB 层拒绝，此处返回 false。
     */
    suspend fun delete(id: Long): Boolean

    suspend fun countExpenses(categoryId: Long): Int

    /** 首次启动时写入预置分类；已有数据则不做事 */
    suspend fun ensureSeeded(nameResolver: (Int) -> String)
}
