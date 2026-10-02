package com.dailyschedule.app.data.repository

import androidx.room.withTransaction
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.transfer.BackupCodec
import com.dailyschedule.app.core.transfer.BackupCounts
import com.dailyschedule.app.core.transfer.BackupData
import com.dailyschedule.app.core.transfer.BackupDocument
import com.dailyschedule.app.data.db.AppDatabase
import com.dailyschedule.app.data.transfer.toEntity
import com.dailyschedule.app.data.transfer.toRecord
import com.dailyschedule.app.domain.model.RestoreOutcome
import com.dailyschedule.app.domain.model.RestoreReport
import com.dailyschedule.app.domain.repository.BackupRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : BackupRepository {

    override fun observeCounts(): Flow<BackupCounts> = combine(
        db.projectDao().observeCount(),
        db.categoryDao().observeCount(),
        db.sessionDao().observeSettledCount(),
        db.expenseDao().observeCount(),
    ) { projects, categories, sessions, expenses ->
        BackupCounts(
            projects = projects,
            categories = categories,
            sessions = sessions,
            expenses = expenses,
        )
    }

    override suspend fun snapshot(appVersion: String): BackupDocument = withContext(Dispatchers.IO) {
        // 顺序：维度表在前。读的时候无所谓，但生成的文件里"先有项目和分类、
        // 再有挂在它们下面的记录"，人打开看的时候不用来回翻。
        val categories = db.categoryDao().getAllOnce()
        val projects = db.projectDao().getAllOnce()
        val sessions = db.sessionDao().getAllSettledOnce()
        val expenses = db.expenseDao().getAllOnce()

        val now = clock.wallClockMillis()
        BackupDocument(
            format = BackupCodec.FORMAT_ID,
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            appVersion = appVersion,
            dbVersion = AppDatabase.DB_VERSION,
            exportedAt = now,
            exportedAtIso = isoOf(now),
            counts = BackupCounts(
                projects = projects.size,
                categories = categories.size,
                sessions = sessions.size,
                expenses = expenses.size,
            ),
            data = BackupData(
                categories = categories.map { it.toRecord() },
                projects = projects.map { it.toRecord() },
                sessions = sessions.map { it.toRecord() },
                expenses = expenses.map { it.toRecord() },
            ),
        )
    }

    override suspend fun replaceAll(document: BackupDocument): RestoreOutcome =
        withContext(Dispatchers.IO) {
            // 先把要写的东西算出来（纯内存，不会失败），再进事务。
            // 事务里少做一分计算，就少一分"回滚到一半"的可能。
            val data = document.data
            val restorable = data.sessions.filterNot { it.status in SessionStatus.ACTIVE }
            val skipped = data.sessions.size - restorable.size

            return@withContext db.withTransaction {
                // 事务内再查一次活动会话，而不是只信调用方在界面上的检查。
                // 界面上的检查是为了给出好看的提示，这里的检查才是正确性保障：
                // 用户完全可以在"确认导入"对话框弹着的时候去计时页开一个计时。
                if (db.sessionDao().getActiveOnce() != null) {
                    return@withTransaction RestoreOutcome.ActiveSessionRunning
                }

                // 删除顺序是被外键约束**强制**的，不能调换：
                // expenses 挂着 categories（RESTRICT），还挂着 projects（SET_NULL）；
                // sessions 挂着 projects。先删子表再删父表。
                db.expenseDao().deleteAll()
                db.sessionDao().deleteAll()
                db.projectDao().deleteAll()
                db.categoryDao().deleteAll()

                // 写入顺序与删除相反：先父表再子表，否则外键立刻失败。
                // 这里不检查返回值条数 —— 事务里 insertAll 抛异常就会整体回滚，
                // 能走到下面这行，就说明每一条都真的写进去了。
                val categories = db.categoryDao().insertAll(data.categories.map { it.toEntity() })
                val projects = db.projectDao().insertAll(data.projects.map { it.toEntity() })
                val sessions = db.sessionDao().insertAll(restorable.map { it.toEntity() })
                val expenses = db.expenseDao().insertAll(data.expenses.map { it.toEntity() })

                RestoreOutcome.Restored(
                    RestoreReport(
                        counts = BackupCounts(
                            projects = projects.size,
                            categories = categories.size,
                            sessions = sessions.size,
                            expenses = expenses.size,
                        ),
                        skippedActiveSessions = skipped,
                    ),
                )
            }
        }

    /**
     * ISO-8601 带时区偏移，例如 `2026-10-02T12:43:19.412+08:00`。
     *
     * 单独写一个可读字段而不是让用户去心算 epoch millis：这份文件的目标之一
     * 就是"三年后打开还能看懂"。
     */
    private fun isoOf(wallClockMs: Long): String = Instant.ofEpochMilli(wallClockMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
