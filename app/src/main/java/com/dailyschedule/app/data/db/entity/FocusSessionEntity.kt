package com.dailyschedule.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus

/**
 * 专注会话。两张事实表之一。
 *
 * ## 双时钟
 * - `startElapsedMs` / `endElapsedMs`：单调时钟，**算时长**（抗系统改时间）
 * - `startWallClockMs` / `endWallClockMs`：墙上时钟，**算归属日期与展示**
 *
 * ## 权威时长
 * `durationMs` 是权威值，时间戳是原始证据。用户手工修正过时长时，
 * 时长无法从时间戳重算。被禁止的冗余是"汇总表"，不是这个字段。
 *
 * ## 暂停
 * `accumulatedPauseMs` 累计已结束的暂停；`pauseStartElapsedMs != null` 表示此刻正在暂停。
 * 实时时长 = end - start - accumulatedPause - (now - pauseStart if 暂停中)。
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("startWallClockMs"),
        Index(value = ["projectId", "startWallClockMs"]),
        Index("status"),
    ],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 可空：项目被删除后置 NULL，历史投入永久保留 */
    val projectId: Long?,

    val status: SessionStatus,
    /** "是否在运行" 与 "要不要人工看一眼" 是两件事，故为独立标志而非第五个状态 */
    val needsReview: Boolean = false,

    // ── 双时钟 ──
    val startElapsedMs: Long,
    val startWallClockMs: Long,
    val endElapsedMs: Long? = null,
    val endWallClockMs: Long? = null,

    // ── 暂停 ──
    val accumulatedPauseMs: Long = 0,
    val pauseStartElapsedMs: Long? = null,

    val mode: SessionMode = SessionMode.STOPWATCH,
    val targetDurationMs: Long? = null,

    /**
     * 计时器产生还是用户补录。v2 新增列。
     *
     * ## 这里的 defaultValue 必须与 `MIGRATION_1_2` 里的 SQL 严格一致
     * Room 打开数据库时会把「实体声明的结构」与「库里实际的结构」逐项比对，
     * **默认值也参与比对**（Room 2.8 实测：实体不声明时，库里的 DEFAULT 会被
     * 判成不一致，报 `Migration didn't properly handle`）。
     *
     * 所以两边都写 `TIMER`：实体这边给的是 SQL 字面量，字符串要带单引号。
     * 这条约束由 `Migration1To2Test` 守着 —— 改任何一边而忘了另一边，测试会红。
     */
    @ColumnInfo(defaultValue = "'TIMER'")
    val source: SessionSource = SessionSource.DEFAULT,

    /** 权威时长；未结束时为 null */
    val durationMs: Long? = null,
    val note: String? = null,

    val createdAt: Long,
    val updatedAt: Long,
)
