package com.dailyschedule.app.data.transfer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.core.transfer.BackupCodec
import com.dailyschedule.app.data.db.AppDatabase
import com.dailyschedule.app.data.db.DatabaseCallback
import com.dailyschedule.app.data.db.entity.CategoryEntity
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity
import com.dailyschedule.app.data.repository.BackupRepositoryImpl
import com.dailyschedule.app.domain.model.RestoreOutcome
import com.dailyschedule.app.testutil.BackupFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 备份往返测试。**这是"导出 + 导入"这个功能唯一的验收标准**：
 * 导出一份、把库改乱、再导回去，四张表必须逐字段回到导出时的样子。
 *
 * 放在 Robolectric 里跑真实的 Room（内存库）而不是 mock DAO：
 * 这个功能的风险全在"SQLite 里到底发生了什么" ——
 * 外键顺序、主键自增序列、触发器、事务边界。
 * mock 掉 DAO 就等于把要验的东西全 mock 没了，测试会全绿而线上照样丢数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupRoundTripTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepositoryImpl
    private val clock = FakeClock(wallMs = 1_759_400_000_000L)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(DatabaseCallback())
                .allowMainThreadQueries()
                .build()
        repository = BackupRepositoryImpl(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `导出再导入后四张表逐字段一致`() =
        runTest {
            seedRealisticData()
            val before = repository.snapshot(appVersion = "1.0.0")

            // 把当前数据搞乱：删一个项目（它的会话 projectId 会被置空）、
            // 再加一条今天的新记录。导入之后这些"乱"必须全部消失。
            val doomed = db.projectDao().getAllOnce().first()
            db.projectDao().delete(doomed)
            db.sessionDao().insert(session(id = 0, projectId = null, startWall = 999_000L))

            val outcome = repository.replaceAll(BackupCodec.decode(BackupCodec.encode(before)))

            assertThat(outcome).isInstanceOf(RestoreOutcome.Restored::class.java)
            val after = repository.snapshot(appVersion = "1.0.0")
            // 逐字段比对，而不是只比条数：条数对了但某条记录的 projectId 错位，
            // 正是"看起来恢复了、统计却全变了"的典型。
            assertThat(after.data).isEqualTo(before.data)
        }

    @Test
    fun `恢复会清掉库里多出来的数据`() =
        runTest {
            seedRealisticData()
            val before = repository.snapshot(appVersion = "1.0.0")

            db.projectDao().insert(project(id = 0, name = "多余的项目"))
            db.expenseDao().insert(expense(id = 0, categoryId = 1, cents = 9_999))

            repository.replaceAll(before)

            val after = repository.snapshot(appVersion = "1.0.0")
            assertThat(after.counts).isEqualTo(before.counts)
            assertThat(db.projectDao().getAllOnce().map { it.name }).doesNotContain("多余的项目")
        }

    @Test
    fun `活动会话不进备份`() =
        runTest {
            seedRealisticData()
            // 一条正在跑的会话：还没有 durationMs，双时钟只对本次开机有效
            db.sessionDao().insert(
                session(id = 0, projectId = null, startWall = 500_000L, status = SessionStatus.RUNNING),
            )

            val document = repository.snapshot(appVersion = "1.0.0")

            assertThat(document.data.sessions.map { it.status })
                .containsNoneOf(SessionStatus.RUNNING, SessionStatus.PAUSED)
            assertThat(document.counts.sessions).isEqualTo(document.data.sessions.size)
        }

    @Test
    fun `文件里的活动会话被跳过并回报条数`() =
        runTest {
            seedRealisticData()
            val document = repository.snapshot(appVersion = "1.0.0")
            // 手工往文件里塞一条活动会话（真实导出不会产生，但文件可能被改过，
            // 或来自将来某个把活动会话也导出的版本）
            val contaminated =
                document.copy(
                    counts = document.counts.copy(sessions = document.counts.sessions + 1),
                    data =
                        document.data.copy(
                            sessions =
                                document.data.sessions +
                                    BackupFixtures.session(9_999, projectId = null, status = SessionStatus.RUNNING),
                        ),
                )

            val outcome = repository.replaceAll(contaminated) as RestoreOutcome.Restored

            assertThat(outcome.report.skippedActiveSessions).isEqualTo(1)
            assertThat(db.sessionDao().getById(9_999L)).isNull()
        }

    @Test
    fun `正在计时时拒绝恢复且数据库一个字节都不动`() =
        runTest {
            seedRealisticData()
            val document = repository.snapshot(appVersion = "1.0.0")
            val before = repository.snapshot(appVersion = "1.0.0")

            // 用户点开确认框的过程中去开了个计时 —— 这就是为什么这条检查
            // 必须在事务里再做一次，而不能只信界面上的检查。
            val runningId =
                db.sessionDao().insert(
                    session(id = 0, projectId = null, startWall = 700_000L, status = SessionStatus.RUNNING),
                )

            val outcome = repository.replaceAll(document)

            assertThat(outcome).isEqualTo(RestoreOutcome.ActiveSessionRunning)
            assertThat(db.sessionDao().getById(runningId)).isNotNull()
            assertThat(repository.snapshot(appVersion = "1.0.0").data).isEqualTo(before.data)
        }

    @Test
    fun `恢复之后新增的记录不会撞上恢复进来的 id`() =
        runTest {
            seedRealisticData()
            val document = repository.snapshot(appVersion = "1.0.0")
            val maxSessionId = document.data.sessions.maxOf { it.id }

            repository.replaceAll(document)

            // SQLite 的 AUTOINCREMENT 会跟着显式写入的大 id 一起抬升。
            // 没抬升的话，下一条新记录会拿到一个已经被占用的 id，
            // 报主键冲突 —— 而用户只是正常地结束了一次专注。
            val newId = db.sessionDao().insert(session(id = 0, projectId = null, startWall = 888_000L))
            assertThat(newId).isGreaterThan(maxSessionId)
        }

    @Test
    fun `恢复不会破坏活动会话唯一性守卫`() =
        runTest {
            seedRealisticData()
            val document = repository.snapshot(appVersion = "1.0.0")

            repository.replaceAll(document)

            // 守卫是触发器，不在 Room 的 schema 元数据里，因此"恢复有没有把它删掉"
            // 没有任何自动检查能发现。这里单独钉一次。
            val cursor =
                db.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='trigger' " +
                        "AND name IN " +
                        "('trg_single_active_session_insert', 'trg_single_active_session_update')",
                )
            cursor.use { assertThat(it.count).isEqualTo(2) }

            db.sessionDao().insert(session(id = 0, projectId = null, startWall = 1_000L, status = SessionStatus.RUNNING))
            val second =
                runCatching {
                    db.sessionDao().insert(session(id = 0, projectId = null, startWall = 2_000L, status = SessionStatus.PAUSED))
                }
            assertThat(second.isFailure).isTrue()
        }

    @Test
    fun `计数与实际条数同源 —— 界面上的预览就是文件里的内容`() =
        runTest {
            seedRealisticData()
            db.sessionDao().insert(
                session(id = 0, projectId = null, startWall = 400_000L, status = SessionStatus.RUNNING),
            )

            val document = repository.snapshot(appVersion = "1.0.0")

            assertThat(document.counts.sessions).isEqualTo(db.sessionDao().getAllSettledOnce().size)
            assertThat(document.counts.expenses).isEqualTo(db.expenseDao().getAllOnce().size)
            assertThat(document.counts.projects).isEqualTo(db.projectDao().getAllOnce().size)
            assertThat(document.counts.categories).isEqualTo(db.categoryDao().getAllOnce().size)
        }

    @Test
    fun `导出的文件带有可辨认的身份信息`() =
        runTest {
            val document = repository.snapshot(appVersion = "1.0.0-debug")

            assertThat(document.format).isEqualTo(BackupCodec.FORMAT_ID)
            assertThat(document.schemaVersion).isEqualTo(BackupCodec.SCHEMA_VERSION)
            assertThat(document.dbVersion).isEqualTo(AppDatabase.DB_VERSION)
            assertThat(document.appVersion).isEqualTo("1.0.0-debug")
            assertThat(document.exportedAt).isEqualTo(clock.wallMs)
            assertThat(document.exportedAtIso).isNotEmpty()
        }

    // ── 夹具 ──

    private suspend fun seedRealisticData() {
        db.categoryDao().insertAll(
            listOf(
                CategoryEntity(id = 1, name = "餐饮", iconName = "ic_food", colorHex = "#F4A261", isPreset = true, sortOrder = 0),
                CategoryEntity(id = 2, name = "交通", iconName = "ic_transit", colorHex = "#2A9D8F", isPreset = true, sortOrder = 1),
                CategoryEntity(
                    id = 3,
                    name = "自建",
                    iconName = "more_horiz",
                    colorHex = "#8A8F98",
                    isPreset = false,
                    isEnabled = false,
                    sortOrder = 2,
                ),
            ),
        )
        db.projectDao().insertAll(
            listOf(
                ProjectEntity(
                    id = 10,
                    name = "考研数学",
                    iconName = "menu_book",
                    colorHex = "#2FA37B",
                    dailyTargetMinutes = 180,
                    sortOrder = 0,
                    createdAt = 1,
                    updatedAt = 1,
                ),
                ProjectEntity(
                    id = 11,
                    name = "旧项目",
                    iconName = "menu_book",
                    colorHex = "#D9534F",
                    isArchived = true,
                    sortOrder = 1,
                    createdAt = 2,
                    updatedAt = 2,
                ),
            ),
        )
        db.sessionDao().insertAll(
            listOf(
                FocusSessionEntity(
                    id = 100,
                    projectId = 10,
                    status = SessionStatus.COMPLETED,
                    startElapsedMs = 1_000,
                    startWallClockMs = 10_000,
                    endElapsedMs = 3_601_000,
                    endWallClockMs = 3_610_000,
                    durationMs = 3_600_000,
                    note = "第一章",
                    createdAt = 10_000,
                    updatedAt = 10_000,
                ),
                // 补录：单调时钟是用墙上时钟代填的近似值，往返之后必须原样保留
                FocusSessionEntity(
                    id = 101,
                    projectId = 11,
                    status = SessionStatus.COMPLETED,
                    source = SessionSource.MANUAL,
                    startElapsedMs = 20_000,
                    startWallClockMs = 20_000,
                    endElapsedMs = 1_820_000,
                    endWallClockMs = 1_820_000,
                    durationMs = 1_800_000,
                    createdAt = 20_000,
                    updatedAt = 20_000,
                ),
                // 暂停过：累计暂停必须跟着还原，否则时长算法立刻就错
                FocusSessionEntity(
                    id = 102,
                    projectId = null,
                    status = SessionStatus.COMPLETED,
                    startElapsedMs = 30_000,
                    startWallClockMs = 30_000,
                    endElapsedMs = 660_000,
                    endWallClockMs = 690_000,
                    accumulatedPauseMs = 30_000,
                    durationMs = 600_000,
                    createdAt = 30_000,
                    updatedAt = 30_000,
                ),
                FocusSessionEntity(
                    id = 103,
                    projectId = 10,
                    status = SessionStatus.DISCARDED,
                    startElapsedMs = 40_000,
                    startWallClockMs = 40_000,
                    createdAt = 40_000,
                    updatedAt = 40_000,
                ),
            ),
        )
        db.expenseDao().insertAll(
            listOf(
                ExpenseEntity(
                    id = 200,
                    amountCents = 129_900,
                    categoryId = 1,
                    projectId = 10,
                    type = ExpenseType.EXPENSE,
                    note = "教材",
                    occurredAt = 50_000,
                    createdAt = 50_000,
                    updatedAt = 50_000,
                ),
                ExpenseEntity(
                    id = 201,
                    amountCents = 300,
                    categoryId = 2,
                    projectId = null,
                    type = ExpenseType.EXPENSE,
                    occurredAt = 60_000,
                    createdAt = 60_000,
                    updatedAt = 60_000,
                ),
                ExpenseEntity(
                    id = 202,
                    amountCents = 100_000,
                    categoryId = 3,
                    projectId = null,
                    type = ExpenseType.INCOME,
                    occurredAt = 70_000,
                    createdAt = 70_000,
                    updatedAt = 70_000,
                ),
            ),
        )
    }

    private fun project(
        id: Long,
        name: String,
    ) = ProjectEntity(
        id = id,
        name = name,
        iconName = "menu_book",
        colorHex = "#2FA37B",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun session(
        id: Long,
        projectId: Long?,
        startWall: Long,
        status: SessionStatus = SessionStatus.COMPLETED,
    ) = FocusSessionEntity(
        id = id,
        projectId = projectId,
        status = status,
        startElapsedMs = startWall,
        startWallClockMs = startWall,
        endElapsedMs = if (status == SessionStatus.COMPLETED) startWall + 60_000 else null,
        endWallClockMs = if (status == SessionStatus.COMPLETED) startWall + 60_000 else null,
        durationMs = if (status == SessionStatus.COMPLETED) 60_000 else null,
        createdAt = startWall,
        updatedAt = startWall,
    )

    private fun expense(
        id: Long,
        categoryId: Long,
        cents: Long,
    ) = ExpenseEntity(
        id = id,
        amountCents = cents,
        categoryId = categoryId,
        occurredAt = 0,
        createdAt = 0,
        updatedAt = 0,
    )
}
