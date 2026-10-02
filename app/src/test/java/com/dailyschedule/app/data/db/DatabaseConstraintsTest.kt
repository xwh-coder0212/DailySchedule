package com.dailyschedule.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.data.db.entity.CategoryEntity
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity
import com.dailyschedule.app.data.db.seed.PresetCategories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库层约束测试。
 *
 * 这里测的都是**写出来不会崩、但会让数字悄悄变错**的东西：
 * - 归档项目消失 → 三年历史归零
 * - 分类被删 → 出现无分类的账
 * - 项目被删 → 历史投入一起没了
 * - 区间开闭搞错 → 边界那一天被算两次或漏掉
 *
 * 用 Robolectric 而非 instrumentation test：秒级反馈，不需要接设备。
 * 若 Robolectric 首次运行需要下载 android-all（约 100MB）导致很慢，
 * 这些用例也可以整体搬到 androidTest 里跑，逻辑一行不用改。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseConstraintsTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DatabaseCallback())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 约束 1：同一时刻最多一个活动会话 ──

    @Test
    fun `活动会话唯一性守卫已装上 —— 两条触发器`() {
        // 这两条触发器是「同一时刻最多一个活动会话」的 DB 层保障。
        // 它们由 SessionUniquenessGuard 手工创建、**不在 Room 的 schema 元数据里** ——
        // 这正是当初从「部分唯一索引」改成触发器的原因：索引会参与 Room 的
        // schema 校验并被判为多余，触发器不会（Room 只读表/列/外键/索引）。
        // 一旦没装上，下面那条「拒绝第二个活动会话」只会报 isFailure 断言失败而看不出原因，
        // 所以这里单独把它们的名字钉住。
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='trigger' " +
                "AND name IN " +
                "('trg_single_active_session_insert', 'trg_single_active_session_update')"
        )
        cursor.use { assertThat(it.count).isEqualTo(2) }
    }

    @Test
    fun `第二个活动会话被数据库拒绝`() = runTest {
        db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 1_000))
        val second = runCatching {
            db.sessionDao().insert(session(status = SessionStatus.PAUSED, startWall = 2_000))
        }

        assertThat(second.isFailure).isTrue()
        // 断言是**我们的守卫**拦下的，而不是碰巧撞上别的约束：
        // 这条消息来自触发器的 RAISE(ABORT, ...)，换任何别的失败原因都对不上
        assertThat(second.exceptionOrNull().allMessages())
            .contains(SessionUniquenessGuard.ABORT_MESSAGE)
        assertThat(db.sessionDao().getActiveOnce()).isNotNull()
    }

    @Test
    fun `把已完成会话改成活动状态同样被拒绝`() = runTest {
        // INSERT 与 UPDATE 是两条独立的写路径，只守 INSERT 会在「恢复历史会话」这类
        // 操作上漏掉 —— 于是专门为 UPDATE 也装一条触发器，这里守住它
        db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 1_000))
        val completedId = db.sessionDao().insert(
            session(status = SessionStatus.COMPLETED, startWall = 500, durationMs = 60_000)
        )

        val revived = runCatching {
            db.sessionDao().update(
                db.sessionDao().getById(completedId)!!.copy(status = SessionStatus.RUNNING)
            )
        }

        assertThat(revived.isFailure).isTrue()
        assertThat(revived.exceptionOrNull().allMessages())
            .contains(SessionUniquenessGuard.ABORT_MESSAGE)
    }

    @Test
    fun `结束当前会话后可以再开一个`() = runTest {
        val id = db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 1_000))
        val running = db.sessionDao().getById(id)!!
        db.sessionDao().update(
            running.copy(
                status = SessionStatus.COMPLETED,
                endElapsedMs = 1_000,
                endWallClockMs = 1_000,
                durationMs = 60_000,
            )
        )

        val nextId = db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 2_000))
        assertThat(nextId).isGreaterThan(id)
    }

    @Test
    fun `DISCARDED 会话不受唯一索引约束`() = runTest {
        db.sessionDao().insert(session(status = SessionStatus.DISCARDED, startWall = 1_000))
        db.sessionDao().insert(session(status = SessionStatus.DISCARDED, startWall = 2_000))

        assertThat(db.sessionDao().getActiveOnce()).isNull()
    }

    // ── 约束 2：归档项目必须继续参与统计 ──

    @Test
    fun `归档项目的时长仍然计入项目排行`() = runTest {
        val archivedId = db.projectDao().insert(project(name = "考研数学", archived = true))
        val activeId = db.projectDao().insert(project(name = "运动", archived = false))
        db.sessionDao().insert(session(archivedId, startWall = 100, durationMs = 3_600_000))
        db.sessionDao().insert(session(activeId, startWall = 200, durationMs = 600_000))

        val rows = db.sessionDao().observeDurationByProject(0, Long.MAX_VALUE).first()

        assertThat(rows.map { it.projectName }).containsExactly("考研数学", "运动").inOrder()
        assertThat(rows.first().totalDurationMs).isEqualTo(3_600_000)
    }

    @Test
    fun `归档项目不出现在计时选择器但仍可查到`() = runTest {
        val archivedId = db.projectDao().insert(project(name = "旧项目", archived = true))
        db.projectDao().insert(project(name = "在跑", archived = false))

        assertThat(db.projectDao().observeActive().first().map { it.name })
            .containsExactly("在跑")
        assertThat(db.projectDao().observeArchived().first().map { it.name })
            .containsExactly("旧项目")
        // 归档后仍能在项目详情页看到累计时长
        assertThat(db.sessionDao().observeTotalDurationOfProject(archivedId, 0, Long.MAX_VALUE).first())
            .isEqualTo(0)
    }

    // ── 约束 3：删除项目不删历史 ──

    @Test
    fun `删除项目后会话保留且 projectId 置 NULL`() = runTest {
        val pid = db.projectDao().insert(project(name = "英语"))
        val sid = db.sessionDao().insert(session(pid, startWall = 100, durationMs = 60_000))
        db.expenseDao().insert(expense(categoryId = seedCategory(), projectId = pid))

        db.projectDao().delete(db.projectDao().getById(pid)!!)

        assertThat(db.sessionDao().getById(sid)!!.projectId).isNull()
        assertThat(db.sessionDao().getById(sid)!!.durationMs).isEqualTo(60_000)
        assertThat(db.sessionDao().observeTotalDuration(0, Long.MAX_VALUE).first())
            .isEqualTo(60_000)
        assertThat(db.expenseDao().observeOfProject(pid).first()).isEmpty()
    }

    // ── 约束 4：分类不能被删除（RESTRICT） ──

    @Test
    fun `有消费的分类不能被删除`() = runTest {
        val cid = seedCategory()
        db.expenseDao().insert(expense(categoryId = cid))

        val result = runCatching { db.categoryDao().delete(db.categoryDao().getById(cid)!!) }

        assertThat(result.isFailure).isTrue()
        assertThat(db.categoryDao().getById(cid)).isNotNull()
    }

    @Test
    fun `没有消费的分类可以删除`() = runTest {
        val cid = seedCategory()

        db.categoryDao().delete(db.categoryDao().getById(cid)!!)

        assertThat(db.categoryDao().getById(cid)).isNull()
    }

    @Test
    fun `分类下的消费数可被统计用于删除前提示`() = runTest {
        val cid = seedCategory()
        db.expenseDao().insert(expense(categoryId = cid))
        db.expenseDao().insert(expense(categoryId = cid))

        assertThat(db.categoryDao().countExpenses(cid)).isEqualTo(2)
    }

    // ── 约束 5：区间左闭右开 + 累计口径 ──

    @Test
    fun `周期查询区间为左闭右开`() = runTest {
        db.sessionDao().insert(session(startWall = 1_000, durationMs = 10))
        db.sessionDao().insert(session(startWall = 2_000, durationMs = 20))
        db.sessionDao().insert(session(startWall = 3_000, durationMs = 30))

        assertThat(db.sessionDao().observeTotalDuration(1_000, 3_000).first()).isEqualTo(30)
        assertThat(db.sessionDao().observeCompletedCount(1_000, 3_000).first()).isEqualTo(2)
        assertThat(db.sessionDao().observeTotalDuration(0, Long.MAX_VALUE).first()).isEqualTo(60)
    }

    @Test
    fun `消费区间同样左闭右开`() = runTest {
        val cid = seedCategory()
        db.expenseDao().insert(expense(categoryId = cid, occurredAt = 1_000, cents = 800))
        db.expenseDao().insert(expense(categoryId = cid, occurredAt = 2_000, cents = 1_500))
        db.expenseDao().insert(expense(categoryId = cid, occurredAt = 3_000, cents = 2_000))

        assertThat(db.expenseDao().observeTotalCents(ExpenseType.EXPENSE, 1_000, 3_000).first())
            .isEqualTo(2_300)
    }

    // ── 口径：正在运行的会话不计入"已完成"统计 ──

    @Test
    fun `正在运行的会话不计入已完成总额`() = runTest {
        db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 1_000))
        db.sessionDao().insert(session(startWall = 2_000, durationMs = 5_000))

        // 这里必须是 5_000 而非 0：正在跑的会话没有 durationMs，
        // 首页"今日学习"需要由上层叠加实时 elapsed，DAO 只负责已完成部分。
        assertThat(db.sessionDao().observeTotalDuration(0, Long.MAX_VALUE).first()).isEqualTo(5_000)
        assertThat(db.sessionDao().observeCompletedCount(0, Long.MAX_VALUE).first()).isEqualTo(1)
    }

    @Test
    fun `DISCARDED 会话不计入统计`() = runTest {
        db.sessionDao().insert(session(status = SessionStatus.DISCARDED, startWall = 1_000, durationMs = 99_999))
        db.sessionDao().insert(session(startWall = 2_000, durationMs = 1_000))

        assertThat(db.sessionDao().observeTotalDuration(0, Long.MAX_VALUE).first()).isEqualTo(1_000)
    }

    // ── needsReview 与 status 正交 ──

    @Test
    fun `needsReview 独立于 status 查询`() = runTest {
        val id = db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 1_000))
        db.sessionDao().update(db.sessionDao().getById(id)!!.copy(needsReview = true))
        db.sessionDao().insert(session(status = SessionStatus.COMPLETED, startWall = 2_000, durationMs = 10))

        assertThat(db.sessionDao().observeNeedsReview().first()).hasSize(1)
        assertThat(db.sessionDao().getActiveOnce()?.needsReview).isTrue()
    }

    // ── 时间轴 ──

    @Test
    fun `时间轴按开始时间升序且只含已完成`() = runTest {
        db.sessionDao().insert(session(startWall = 3_000, durationMs = 30))
        db.sessionDao().insert(session(startWall = 1_000, durationMs = 10))
        db.sessionDao().insert(session(status = SessionStatus.RUNNING, startWall = 2_000))

        val timeline = db.sessionDao().observeTimeline(0, Long.MAX_VALUE).first()

        assertThat(timeline.map { it.startWallClockMs }).containsExactly(1_000L, 3_000L).inOrder()
    }

    // ── 数据体检 ──

    @Test
    fun `能查出 durationMs 与时间戳不一致的行`() = runTest {
        db.sessionDao().insert(
            session(startWall = 0, durationMs = 60_000).copy(endElapsedMs = 30_000)
        )

        assertThat(db.sessionDao().findInconsistentDurations()).hasSize(1)
    }

    // ── 种子数据 ──

    @Test
    fun `预置分类可写入且数量为 7`() = runTest {
        db.categoryDao().insertAll(PresetCategories.entities { "分类$it" })

        assertThat(db.categoryDao().count()).isEqualTo(7)
        assertThat(db.categoryDao().observeEnabled().first()).hasSize(7)
        assertThat(db.categoryDao().observeEnabled().first().first().sortOrder).isEqualTo(0)
    }

    // ── helpers ──

    private suspend fun seedCategory(): Long = db.categoryDao().insert(
        CategoryEntity(name = "测试分类", iconName = "more_horiz", colorHex = "#8A8F98")
    )

    private fun project(name: String, archived: Boolean = false) = ProjectEntity(
        name = name,
        iconName = "menu_book",
        colorHex = "#2FA37B",
        isArchived = archived,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun session(
        projectId: Long? = null,
        startWall: Long = 0,
        durationMs: Long = 0,
        status: SessionStatus = SessionStatus.COMPLETED,
    ) = FocusSessionEntity(
        projectId = projectId,
        status = status,
        startElapsedMs = startWall,
        startWallClockMs = startWall,
        endElapsedMs = if (status == SessionStatus.COMPLETED) startWall + durationMs else null,
        endWallClockMs = if (status == SessionStatus.COMPLETED) startWall + durationMs else null,
        durationMs = if (status == SessionStatus.COMPLETED) durationMs else null,
        createdAt = startWall,
        updatedAt = startWall,
    )

    private fun expense(
        categoryId: Long,
        projectId: Long? = null,
        occurredAt: Long = 1_000,
        cents: Long = 800,
    ) = ExpenseEntity(
        amountCents = cents,
        categoryId = categoryId,
        projectId = projectId,
        type = ExpenseType.EXPENSE,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )

    /**
     * 把整条异常链上的消息拼起来。
     *
     * 不能只看最外层：Room 与新的 `androidx.sqlite` 驱动都可能把底层
     * SQLite 错误包一层，我们真正要断言的触发器消息藏在里层。
     */
    private fun Throwable?.allMessages(): String =
        generateSequence(this) { it.cause }
            .joinToString(" | ") { it.message.orEmpty() }
}
