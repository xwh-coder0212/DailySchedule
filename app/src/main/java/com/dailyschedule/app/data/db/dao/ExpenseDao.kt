package com.dailyschedule.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.model.CategoryAmountRow
import kotlinx.coroutines.flow.Flow

/**
 * 消费 DAO。区间约定与 SessionDao 一致：只收 `[startMs, endMs)`，SQL 里没有日期函数。
 */
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExpenseEntity): Long

    @Update
    suspend fun update(entity: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query(
        """
        SELECT * FROM expenses
        WHERE type = :type
          AND occurredAt >= :startMs AND occurredAt < :endMs
        ORDER BY occurredAt DESC
        """
    )
    fun observeInRange(type: ExpenseType, startMs: Long, endMs: Long): Flow<List<ExpenseEntity>>

    /** 最近 N 笔（首页/小组件/历史页用） */
    @Query(
        """
        SELECT * FROM expenses
        WHERE type = :type
        ORDER BY occurredAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(type: ExpenseType, limit: Int): Flow<List<ExpenseEntity>>

    /** 区间总额（分） */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM expenses
        WHERE type = :type
          AND occurredAt >= :startMs AND occurredAt < :endMs
        """
    )
    fun observeTotalCents(type: ExpenseType, startMs: Long, endMs: Long): Flow<Long>

    /** 分类占比 */
    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, c.colorHex AS colorHex,
               c.iconName AS iconName, COALESCE(SUM(e.amountCents), 0) AS totalCents
        FROM expenses e
        INNER JOIN categories c ON e.categoryId = c.id
        WHERE e.type = :type
          AND e.occurredAt >= :startMs AND e.occurredAt < :endMs
        GROUP BY c.id
        ORDER BY totalCents DESC
        """
    )
    fun observeTotalByCategory(
        type: ExpenseType,
        startMs: Long,
        endMs: Long,
    ): Flow<List<CategoryAmountRow>>

    /** 某项目上的累计花费（"考研总投入 = 128h32m + ¥680" 的右半边，P1 启用 UI） */
    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM expenses
        WHERE projectId = :projectId AND type = :type
        """
    )
    fun observeTotalOfProject(projectId: Long, type: ExpenseType): Flow<Long>

    @Query("SELECT * FROM expenses WHERE projectId = :projectId ORDER BY occurredAt DESC")
    fun observeOfProject(projectId: Long): Flow<List<ExpenseEntity>>

    /**
     * 全部消费，**不分收支类型、不设区间**。只服务于导出。
     *
     * 日常统计一律走 [observeInRange]：那里必须显式给出 type 与区间，
     * 免得有人顺手拿这个「全量查询」去做统计，然后悄悄把收入和支出加到一起。
     */
    @Query("SELECT * FROM expenses ORDER BY occurredAt ASC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    /** [observeAll] 的一次性版本，导出时用（导出是点一下就走，不需要订阅） */
    @Query("SELECT * FROM expenses ORDER BY occurredAt ASC")
    suspend fun getAllOnce(): List<ExpenseEntity>

    @Query("UPDATE expenses SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Delete
    suspend fun delete(entity: ExpenseEntity)

    // ── 备份 / 恢复专用（理由见 SessionDao 同名方法） ──

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<ExpenseEntity>): List<Long>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeCount(): Flow<Int>
}
