package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.ProjectDuration
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓储。
 *
 * 所有 `startMs / endMs` 都必须由 `DayBoundary` 算出，**禁止**传 `LocalDate.now()`
 * 或让 SQL 出现 `date('now')`。累计口径 = `(0, Long.MAX_VALUE)`。
 */
interface SessionRepository {
    /** 当前活动会话（RUNNING 或 PAUSED），没有则发射 null */
    fun observeActive(): Flow<FocusSession?>

    suspend fun getActive(): FocusSession?

    suspend fun getById(id: Long): FocusSession?

    /** 时间轴：已完成会话，按开始时间升序 */
    fun observeTimeline(
        startMs: Long,
        endMs: Long,
    ): Flow<List<FocusSession>>

    /** 历史列表：已完成会话，按开始时间倒序 */
    fun observeInRange(
        startMs: Long,
        endMs: Long,
    ): Flow<List<FocusSession>>

    /** 已完成会话的总时长。**不含正在跑的会话** */
    fun observeTotalDuration(
        startMs: Long,
        endMs: Long,
    ): Flow<Long>

    fun observeCompletedCount(
        startMs: Long,
        endMs: Long,
    ): Flow<Int>

    /** 指定项目的已完成次数。累计口径同样是 `(0, Long.MAX_VALUE)` */
    fun observeCompletedCountOfProject(
        projectId: Long,
        startMs: Long,
        endMs: Long,
    ): Flow<Int>

    fun observeDurationByProject(
        startMs: Long,
        endMs: Long,
    ): Flow<List<ProjectDuration>>

    fun observeTotalDurationOfProject(
        projectId: Long,
        startMs: Long,
        endMs: Long,
    ): Flow<Long>

    fun observeNeedsReview(): Flow<List<FocusSession>>

    fun observeByProject(
        projectId: Long,
        limit: Int,
    ): Flow<List<FocusSession>>

    suspend fun insert(session: FocusSession): Long

    suspend fun update(session: FocusSession)

    /**
     * 删除一条会话。**返回是否真的删掉了**。
     *
     * 返回 Boolean 而不是 Unit：界面在删完之后要能区分"删成功了"和
     * "这条根本不在（或它是活动会话，按规则不许删）"。两者都不该弹"已删除"。
     */
    suspend fun delete(id: Long): Boolean

    /**
     * 体检：`durationMs` 与时间戳算出来的值不一致的行（用户手工改过时长，
     * 或曾经出过 bug）。导出与自测时用，不是日常查询。
     */
    suspend fun findInconsistent(): List<FocusSession>
}
