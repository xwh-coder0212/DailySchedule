package com.dailyschedule.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.model.SessionSource
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1 → v2 迁移测试。
 *
 * ## 为什么这个测试值得单独存在
 * 迁移失败有两种形态，而**只有一种会被人发现**：
 * - 大声失败：`ALTER TABLE` 报错，App 起不来。用户立刻反馈。
 * - 安静失败：迁移「成功」了，但 Room 打开库时 schema 校验不通过，
 *   或者更糟 —— 校验被跳过、列默认值不对，于是历史记录的来源永远是错的。
 *
 * 所以这里做的是**端到端**验证，而不是只跑一句 SQL：
 * 1. 用 `schemas/1.json` 里真实的 v1 建表语句造一个 v1 库（不手抄 DDL，
 *    手抄迟早会和线上跑的不一样）；
 * 2. 塞一行真实的 v1 记录进去；
 * 3. 用**真实的 `AppDatabase` + `MIGRATION_1_2`** 打开它 ——
 *    打开不抛异常，就等于 Room 官方生成的 `onValidateSchema` 认可了迁移结果；
 * 4. 断言那行记录还在、`source` 是 `TIMER`、时长没被改动。
 *
 * 第 3 步是关键：它覆盖的正是「有没有漏写迁移」和「迁移后的表和实体对不对得上」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration1To2Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-1-2-test.db"

    private companion object {
        /** 迁移前那条记录的时长：90 分钟。用来证明迁移没有碰过数据 */
        const val LEGACY_DURATION_MS = 90 * 60_000L
        const val LEGACY_SESSION_ID = 1L
    }

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `v1 库升到 v2：Room 校验通过、旧记录保留、来源标为计时`() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            // 打开这个库本身就会跑 migration + Room 的 schema 校验。
            // 走到这里没抛异常，说明「迁移后的表结构」与「实体声明的结构」互相认可。
            val sessions = db.sessionDao().observeCompletedInRange(0L, Long.MAX_VALUE).first()

            assertThat(sessions).hasSize(1)
            val legacy = sessions.single()
            assertThat(legacy.id).isEqualTo(LEGACY_SESSION_ID)
            assertThat(legacy.projectId).isEqualTo(1L)
            assertThat(legacy.durationMs).isEqualTo(LEGACY_DURATION_MS)
            assertThat(legacy.note).isEqualTo("旧版本的记录")
            // 迁移前不存在补录功能，存量记录只能是计时器产生的
            assertThat(legacy.source).isEqualTo(SessionSource.TIMER)
        } finally {
            db.close()
        }
    }

    @Test
    fun `v2 新写入的行在没显式指定来源时默认为计时`() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            // 直接走 SQL 插入且不写 source 列，验证「列的默认值」确实落到了库里 ——
            // 只看实体默认值是不够的，那条路不经过 SQLite 的 DEFAULT
            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO focus_sessions
                  (projectId, status, needsReview, startElapsedMs, startWallClockMs,
                   accumulatedPauseMs, mode, durationMs, createdAt, updatedAt)
                VALUES (1, 'COMPLETED', 0, 2000, 2000, 0, 'STOPWATCH', 60000, 2000, 2000)
                """.trimIndent()
            )

            val sessions = db.sessionDao().observeCompletedInRange(0L, Long.MAX_VALUE).first()
            assertThat(sessions).hasSize(2)
            assertThat(sessions.map { it.source }.toSet()).containsExactly(SessionSource.TIMER)
        } finally {
            db.close()
        }
    }

    @Test
    fun `迁移删掉了 v1 的旧索引并装上触发器`() = runTest {
        createV1Database()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val raw = db.openHelper.readableDatabase

            // 旧的部分唯一索引必须消失：它会让 Room 的 schema 校验判为「多了个索引」
            raw.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
                arrayOf(SessionUniquenessGuard.LEGACY_INDEX_NAME),
            ).use { assertThat(it.count).isEqualTo(0) }

            // 取而代之的两条触发器必须到位，否则升级用户会比新装用户少一层保障
            raw.query(
                "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'trg_single_active_session%'",
            ).use { assertThat(it.count).isEqualTo(2) }
        } finally {
            db.close()
        }
    }

    // ── 造一个 v1 库 ──

    private fun createV1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            v1Ddl().forEach(sqlite::execSQL)
            sqlite.execSQL(
                """
                INSERT INTO projects
                  (id, name, iconName, colorHex, isArchived, sortOrder, createdAt, updatedAt)
                VALUES (1, '高等数学', 'ic_math', '#3366CC', 0, 0, 1000, 1000)
                """.trimIndent()
            )
            sqlite.execSQL(
                """
                INSERT INTO focus_sessions
                  (id, projectId, status, needsReview, startElapsedMs, startWallClockMs,
                   accumulatedPauseMs, mode, durationMs, note, createdAt, updatedAt)
                VALUES ($LEGACY_SESSION_ID, 1, 'COMPLETED', 0, 1000, 1000,
                        0, 'STOPWATCH', $LEGACY_DURATION_MS, '旧版本的记录', 1000, 1000)
                """.trimIndent()
            )
            // 真实设备上这条部分唯一索引由 DatabaseCallback.onCreate 建出来，
            // 造假库时也要补上，否则测的就不是线上那个库的形状
            sqlite.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_single_active_session
                ON focus_sessions(CASE WHEN status IN ('RUNNING', 'PAUSED') THEN 1 END)
                """.trimIndent()
            )
            sqlite.version = 1
        } finally {
            sqlite.close()
        }
    }

    /**
     * 从导出的 schema JSON 里读 v1 的建表语句。
     *
     * 不手抄 DDL 的原因很实在：手抄的 DDL 与真实 v1 库一旦有细微差别
     * （少一个索引、少一个外键），Room 的 `onValidateSchema` 会直接判定
     * 「迁移后的表和实体不一致」，而这个测试会以「迁移有 bug」的名义失败 ——
     * 排查方向从一开始就被带偏。
     */
    private fun v1Ddl(): List<String> {
        val schemaFile = listOf(
            File("schemas/com.dailyschedule.app.data.db.AppDatabase/1.json"),
            File("app/schemas/com.dailyschedule.app.data.db.AppDatabase/1.json"),
        ).firstOrNull { it.exists() }

        assertThat(schemaFile).isNotNull()
        val root = Json.parseToJsonElement(schemaFile!!.readText()).jsonObject
        val entities = root.getValue("database").jsonObject.getValue("entities").jsonArray

        return entities.flatMap { element ->
            val entity = element.jsonObject
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            fun String.resolve() = replace("\${TABLE_NAME}", tableName)

            buildList {
                add(entity.getValue("createSql").jsonPrimitive.content.resolve())
                entity["indices"]?.jsonArray?.forEach { index ->
                    add(index.jsonObject.getValue("createSql").jsonPrimitive.content.resolve())
                }
            }
        }
    }
}
