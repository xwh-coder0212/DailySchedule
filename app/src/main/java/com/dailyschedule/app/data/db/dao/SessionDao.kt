package com.dailyschedule.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.model.ProjectDurationRow
import kotlinx.coroutines.flow.Flow

/**
 * 会话 DAO。
 *
 * ## 区间约定（关键）
 * 所有周期查询只接收 `[startMs, endMs)` 两个 Long，**SQL 里禁止出现 `date('now')` / `strftime`**。
 * 日期换算全部在 Kotlin 层由 DayBoundary 完成，这样才能用纯 JVM 单测覆盖日切/跨月/跨年边界。
 *
 * "累计"不是另一个查询，就是 `startMs = 0, endMs = Long.MAX_VALUE`。
 *
 * ## 状态字面量
 * 下面 SQL 里的 `'RUNNING'` / `'PAUSED'` / `'COMPLETED'` 是枚举 `name()` 的存储值，
 * 由 Converters 保证一致。改枚举名必须同步改这里，或改用参数绑定。
 */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FocusSessionEntity): Long

    @Update
    suspend fun update(entity: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): FocusSessionEntity?

    /**
     * 当前活动会话。全库最多一行 —— 由 `SessionUniquenessGuard` 装的两条
     * BEFORE INSERT/UPDATE 触发器在数据库层强制，比应用层加锁可靠
     * （竞态、进程恢复、多入口连点都挡得住）。
     *
     * 注意**不是**用部分唯一索引：Room 在打开库时会比对 `index_list`，
     * 裸 SQL 建的部分索引不在它的元数据里，会被判成"多余索引"而抛
     * `Migration didn't properly handle` —— 已有数据的库升级时直接打不开。
     * 触发器不在 Room 的校验范围里，所以能做这件事。
     */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status IN ('RUNNING', 'PAUSED')
        LIMIT 1
        """,
    )
    fun observeActive(): Flow<FocusSessionEntity?>

    /** 非 Flow 版本，供前台服务、BootReceiver、Widget 等一次性读取 */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status IN ('RUNNING', 'PAUSED')
        LIMIT 1
        """,
    )
    suspend fun getActiveOnce(): FocusSessionEntity?

    /** 今日时间轴：只含已完成会话，按开始时间升序。消费不进时间轴（Phase 3 已定） */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        ORDER BY startWallClockMs ASC
        """,
    )
    fun observeTimeline(
        startMs: Long,
        endMs: Long,
    ): Flow<List<FocusSessionEntity>>

    /** 历史列表（倒序） */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        ORDER BY startWallClockMs DESC
        """,
    )
    fun observeCompletedInRange(
        startMs: Long,
        endMs: Long,
    ): Flow<List<FocusSessionEntity>>

    /**
     * 已完成会话的时长总和。
     *
     * **不含正在运行的会话** —— 它还没有 durationMs。首页"今日学习"必须
     * 另外叠加活动会话的实时 elapsed（内存计算），否则用户会觉得"我明明在学，为什么是 0"。
     */
    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        """,
    )
    fun observeTotalDuration(
        startMs: Long,
        endMs: Long,
    ): Flow<Long>

    /** 专注次数 */
    @Query(
        """
        SELECT COUNT(*) FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        """,
    )
    fun observeCompletedCount(
        startMs: Long,
        endMs: Long,
    ): Flow<Int>

    /**
     * 指定项目的专注次数。"累计专注 90 次"那个数字。
     *
     * 不写成"把该项目所有会话读出来再数一遍"：一个用了三年的项目会有几千条，
     * 为了显示一个数字把整行都读进内存，越到后面越慢，而慢在这里毫无意义。
     */
    @Query(
        """
        SELECT COUNT(*) FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND projectId = :projectId
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        """,
    )
    fun observeCompletedCountOfProject(
        projectId: Long,
        startMs: Long,
        endMs: Long,
    ): Flow<Int>

    /** 指定项目在指定区间内的时长（项目的今日/本周/本月/累计共用此查询） */
    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND projectId = :projectId
          AND startWallClockMs >= :startMs AND startWallClockMs < :endMs
        """,
    )
    fun observeTotalDurationOfProject(
        projectId: Long,
        startMs: Long,
        endMs: Long,
    ): Flow<Long>

    /**
     * 项目排行。
     *
     * **不加 `p.isArchived = 0` 过滤** —— 归档项目必须继续参与历史统计，
     * 否则归档考研项目的瞬间，累计投入就归零了。
     */
    @Query(
        """
        SELECT p.id AS projectId, p.name AS projectName, p.colorHex AS colorHex,
               p.iconName AS iconName, COALESCE(SUM(f.durationMs), 0) AS totalDurationMs
        FROM focus_sessions f
        INNER JOIN projects p ON f.projectId = p.id
        WHERE f.status = 'COMPLETED'
          AND f.startWallClockMs >= :startMs AND f.startWallClockMs < :endMs
        GROUP BY p.id
        ORDER BY totalDurationMs DESC
        """,
    )
    fun observeDurationByProject(
        startMs: Long,
        endMs: Long,
    ): Flow<List<ProjectDurationRow>>

    /**
     * 待确认会话。与 status 无关 —— "是否在运行"和"要不要人工看一眼"是两件事。
     * 重启截断不走这里（那是可确证的事实，直接发通知），只有 ClockTampered 等真歧义才标。
     */
    @Query("SELECT * FROM focus_sessions WHERE needsReview = 1 ORDER BY startWallClockMs DESC")
    fun observeNeedsReview(): Flow<List<FocusSessionEntity>>

    /** 项目详情页的最近会话 */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE projectId = :projectId AND status = 'COMPLETED'
        ORDER BY startWallClockMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentOfProject(
        projectId: Long,
        limit: Int,
    ): Flow<List<FocusSessionEntity>>

    /**
     * 把某个项目的会话 projectId 置空（"删除项目但保留历史"的另一条路径）。
     * 外键 SET_NULL 在 delete 时会自动做，这里供显式调用。
     */
    @Query("UPDATE focus_sessions SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)

    /** 自测/导出前的数据体检：找出 durationMs 与时间戳算出来不一致的行 */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND endElapsedMs IS NOT NULL
          AND durationMs IS NOT NULL
          AND durationMs != (endElapsedMs - startElapsedMs - accumulatedPauseMs)
        """,
    )
    suspend fun findInconsistentDurations(): List<FocusSessionEntity>

    /** 会话状态统计（设置页"数据体检"用） */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status = :status")
    suspend fun countByStatus(status: SessionStatus): Int

    /**
     * 删除一条已完成会话。
     *
     * 只允许删 COMPLETED：活动会话有前台服务与通知挂着，删掉它会让通知
     * 永远停在"专注中"而没有任何数据可停 —— 那是状态不一致，不是删除。
     */
    @Query("DELETE FROM focus_sessions WHERE id = :id AND status = 'COMPLETED'")
    suspend fun deleteCompleted(id: Long): Int

    // ── 备份 / 恢复专用 ──
    //
    // 下面三个方法**只服务于 JSON 备份与恢复**，日常读写一律走上面的查询。
    // 放在同一个 DAO 而不是另开一个，是因为它们操作的是同一张表、
    // 用的是同一套状态字面量约定，拆开只会让"哪个查询属于备份"变得不明显。

    /**
     * 全量会话，**排除活动会话**（RUNNING / PAUSED），按 id 升序。
     *
     * 排除它们有两个独立的理由，任一成立就够了：
     * 1. 活动会话的单调时钟（`*ElapsedMs`）来自本次开机，换设备/重启后
     *    不对应任何真实时刻 —— 恢复回去会显示成任意数字。
     * 2. 它还不是一条事实：没有 `durationMs`，导出的 Excel 里也没有它。
     *
     * 用 `status NOT IN (...)` 而不是"读全部再在 Kotlin 里过滤"：过滤条件
     * 与 [SessionUniquenessGuard] 里那条触发器用的是同一个字面量集合，
     * 写在 SQL 里更容易被一起看到。
     */
    @Query(
        """
        SELECT * FROM focus_sessions
        WHERE status NOT IN ('RUNNING', 'PAUSED')
        ORDER BY id ASC
        """,
    )
    suspend fun getAllSettledOnce(): List<FocusSessionEntity>

    /**
     * 恢复时批量写回，**带显式 id**。
     *
     * Room 对 `autoGenerate = true` 的主键：传入非 0 的 id 就用传入值，
     * 传 0 才自增。所以这里必须传原 id —— 会话与项目的关联就靠它。
     * 写完之后 SQLite 的 `sqlite_sequence` 会自动抬到最大 id，
     * 后续新增记录不会撞上恢复进来的 id。
     *
     * `ABORT` 而不是 `REPLACE`：冲突说明校验漏了东西（重复 id、外键不存在），
     * 这种时候应该整个事务回滚，而不是悄悄覆盖一行。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<FocusSessionEntity>): List<Long>

    /** 恢复前清空。放在事务里，失败会一起回滚 */
    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()

    /**
     * 会写进备份的会话数。界面在导出/导入前显示"这次涉及多少条"。
     *
     * 与 [getAllSettledOnce] 用**同一个过滤条件**，否则界面说 42 条、
     * 文件里 40 条，用户会以为丢了数据。这正是把它做成 Flow 而不是
     * 让界面自己去数字的原因：条件只有一处。
     */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status NOT IN ('RUNNING', 'PAUSED')")
    fun observeSettledCount(): Flow<Int>
}
