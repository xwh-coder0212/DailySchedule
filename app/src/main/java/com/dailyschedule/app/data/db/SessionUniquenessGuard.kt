package com.dailyschedule.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.dailyschedule.app.core.log.AppLogger

/**
 * 「同一时刻全库最多一个活动会话」这条不变量的数据库层强制。
 *
 * ## 为什么从「部分唯一索引」改成「触发器」（v2 修的一个真实缺陷）
 * v1 用的是这条部分唯一索引：
 * ```sql
 * CREATE UNIQUE INDEX idx_single_active_session
 * ON focus_sessions(CASE WHEN status IN ('RUNNING','PAUSED') THEN 1 END)
 * ```
 * 它有个致命问题：Room 的 `@Entity(indices = [...])` **表达不了部分索引**，
 * 于是这条索引不在 Room 的 schema 元数据里。而 Room 打开数据库时会拿
 * 「元数据里的索引集合」与「库里实际存在的索引集合」做**相等**比对 ——
 * 于是只要这条索引存在，校验就判定「多了一个索引」，抛
 * `Migration didn't properly handle: focus_sessions`。
 *
 * 后果不是「有点小瑕疵」，而是**升级即打不开库**：
 * - 全新安装走 `onCreate`，不触发 schema 校验，所以一直没暴露；
 * - 已有数据的库走迁移，迁移后立刻校验，直接抛异常。
 * 这正是 AppDatabase 里那条「必须在真机上重启两次验证」的悬留项的真答案 ——
 * 答案是会崩。由 `Migration1To2Test` 抓到。
 *
 * ## 触发器为什么可行
 * Room 的 schema 校验只读 `PRAGMA table_info`（表与列）、`PRAGMA foreign_key_list`
 * （外键）、`PRAGMA index_list`（索引）。**它不读触发器** —— 触发器对校验完全不可见，
 * 因此既能在数据库层把不变量钉死，又不会让校验失败。
 *
 * ## 为什么不改成「哨兵列 + 唯一索引」
 * 也想过给活动行加一个 `activeSlot = 1`、非活动行为 NULL 的列，用 Room 能声明的
 * 唯一索引约束它。但那样一来「什么算活动」这件事就从 `status` 搬到了应用代码里，
 * 靠 mapper 每次写对；而这条不变量的价值恰恰在于**不依赖应用层写对**
 * （参见 `TimerController` 的注释：竞态、进程恢复、多入口连点都要挡得住）。
 * 触发器直接由 `status` 推导，语义没有任何转移。
 */
object SessionUniquenessGuard {
    /** 触发器中断时抛给上层的消息。测试直接断言它，确保是我们的守卫拦下的，而不是别的约束 */
    const val ABORT_MESSAGE = "已有正在进行的会话，数据库层拒绝写入第二个"

    /** v1 用的部分唯一索引名。已被触发器取代，迁移时必须删掉，否则校验失败 */
    const val LEGACY_INDEX_NAME = "idx_single_active_session"

    private const val TRIGGER_INSERT = "trg_single_active_session_insert"
    private const val TRIGGER_UPDATE = "trg_single_active_session_update"

    private const val ACTIVE_STATUSES = "'RUNNING', 'PAUSED'"

    /**
     * 安装守卫。**新建库与迁移都必须调用它**，两处只有这一个实现，
     * 否则「新装用户有守卫、升级用户没有」这种偏差不会有任何测试能发现。
     */
    fun install(db: SupportSQLiteDatabase) {
        runCatching {
            // 先清掉 v1 遗留的部分唯一索引：它会让 Room 的 schema 校验失败
            db.execSQL("DROP INDEX IF EXISTS $LEGACY_INDEX_NAME")

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS $TRIGGER_INSERT
                BEFORE INSERT ON focus_sessions
                WHEN NEW.status IN ($ACTIVE_STATUSES)
                BEGIN
                  SELECT RAISE(ABORT, '$ABORT_MESSAGE')
                  WHERE EXISTS (
                    SELECT 1 FROM focus_sessions WHERE status IN ($ACTIVE_STATUSES)
                  );
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS $TRIGGER_UPDATE
                BEFORE UPDATE ON focus_sessions
                WHEN NEW.status IN ($ACTIVE_STATUSES)
                BEGIN
                  SELECT RAISE(ABORT, '$ABORT_MESSAGE')
                  WHERE EXISTS (
                    SELECT 1 FROM focus_sessions
                    WHERE status IN ($ACTIVE_STATUSES) AND id != NEW.id
                  );
                END
                """.trimIndent(),
            )
        }.onFailure { t ->
            // 守卫没装上 = 唯一性只剩应用层保证。不能静默：
            // 这类问题只会在并发点击、进程恢复、通知栏与小组件同时触发时爆发，
            // 且表现为「库里出现两个 RUNNING」这种脏数据，而不是当次崩溃。
            AppLogger.e(TAG, "活动会话唯一性守卫安装失败", t)
        }
    }

    private const val TAG = "SessionUniquenessGuard"
}
