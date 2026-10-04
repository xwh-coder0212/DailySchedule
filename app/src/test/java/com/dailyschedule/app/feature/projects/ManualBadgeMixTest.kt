package com.dailyschedule.app.feature.projects

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.data.db.AppDatabase
import com.dailyschedule.app.data.db.DatabaseCallback
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity
import com.dailyschedule.app.data.repository.ProjectRepositoryImpl
import com.dailyschedule.app.data.repository.SessionRepositoryImpl
import com.dailyschedule.app.domain.usecase.session.DeleteSessionUseCase
import com.dailyschedule.app.domain.usecase.session.RecordManualSessionUseCase
import com.dailyschedule.app.domain.usecase.session.UpdateSessionDurationUseCase
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 「补录」角标在**混排**列表里的归属 —— 第二轮真机验收里自认没覆盖的那一项。
 *
 * ## 真机验收当时验到了哪一步
 * 造一条 45 分钟补录后，角标数 1 → 2、累计次数 3 → 4，且角标只挂在补录记录上。
 * 没验的是：**同一个待办下多条补录与计时记录交错时，角标会不会串行**。
 *
 * ## 会怎么串
 * 角标的判定是逐条 `session.isManual`，看起来不会错。真正的风险在**数据层**：
 * `source` 列在查询/映射过程中一旦错位或被默认值覆盖，角标就会挂到隔壁那一条上 ——
 * 界面不会报错，只是某条计时记录莫名其妙带了「补录」两个字。
 * 所以这个测试从真实 Room 一路断到视图模型的 `sessions`，也就是界面真正渲染的那个列表。
 *
 * ## 为什么用「两种相反的时间顺序」而不是只测一种
 * 只测一种的话，「角标跟着记录走」和「角标按行号 0/2/4 走」会得出同样的结果，
 * 测试无法区分这两种实现。两组数据的时间顺序相反、来源模式也相反，
 * 任何按行号或按计数赋值的实现都会在其中一组上露馅。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ManualBadgeMixTest {
    private val zone: ZoneId = ZoneId.of("+08:00")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultTimeZone: TimeZone = TimeZone.getDefault()

    private lateinit var db: AppDatabase
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var projectRepository: ProjectRepositoryImpl
    private lateinit var clock: FakeClock

    private companion object {
        const val PROJECT_ID = 1L
        const val MIN = 60_000L
    }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Dispatchers.setMain(Dispatchers.Unconfined)

        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(DatabaseCallback())
                .allowMainThreadQueries()
                .build()
        sessionRepository = SessionRepositoryImpl(db.sessionDao())
        projectRepository = ProjectRepositoryImpl(db.projectDao())
        clock = FakeClock(wallMs = at("2026-06-10T12:00:00"))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(defaultTimeZone)
    }

    @Test
    fun `最新的一条是补录时 角标挂在第一行而不是按行号排`() =
        runBlocking {
            seedProject()
            // 按时间倒序会渲染成：MANUAL / TIMER / MANUAL / TIMER
            seedSessions(
                listOf(
                    "2026-06-10T09:00:00" to SessionSource.MANUAL,
                    "2026-06-10T08:00:00" to SessionSource.TIMER,
                    "2026-06-09T09:00:00" to SessionSource.MANUAL,
                    "2026-06-08T09:00:00" to SessionSource.TIMER,
                ),
            )

            val sessions = sessionsShown()

            assertThat(sessions.map { it.source })
                .containsExactly(
                    SessionSource.MANUAL,
                    SessionSource.TIMER,
                    SessionSource.MANUAL,
                    SessionSource.TIMER,
                ).inOrder()

            // 判定规则刻意写成字面量，不调用 session.isManual ——
            // 调用它等于拿被测代码去验被测代码，改成 `source == TIMER` 也照样绿
            assertThat(sessions.map { it.source == SessionSource.MANUAL })
                .containsExactly(true, false, true, false)
                .inOrder()
        }

    @Test
    fun `最新的一条是计时时 角标跟着记录走 不会因为换了位置就消失`() =
        runBlocking {
            seedProject()
            // 与上一个用例时间顺序相反，来源模式也相反：TIMER / MANUAL / TIMER / MANUAL
            seedSessions(
                listOf(
                    "2026-06-10T09:00:00" to SessionSource.TIMER,
                    "2026-06-10T08:00:00" to SessionSource.MANUAL,
                    "2026-06-09T09:00:00" to SessionSource.TIMER,
                    "2026-06-08T09:00:00" to SessionSource.MANUAL,
                ),
            )

            val sessions = sessionsShown()

            assertThat(sessions.map { it.source })
                .containsExactly(
                    SessionSource.TIMER,
                    SessionSource.MANUAL,
                    SessionSource.TIMER,
                    SessionSource.MANUAL,
                ).inOrder()
            assertThat(sessions.map { it.source == SessionSource.MANUAL })
                .containsExactly(false, true, false, true)
                .inOrder()

            // 角标数量必须等于补录条数。上面已经逐条钉死，这里再钉一次总数，
            // 是为了让「某处实现改成只给第一条加角标」这类错误有独立的失败信号
            assertThat(sessions.count { it.source == SessionSource.MANUAL }).isEqualTo(2)
        }

    @Test
    fun `列表只含已完成记录 与累计次数口径一致`() =
        runBlocking {
            seedProject()
            seedSessions(
                listOf(
                    "2026-06-10T09:00:00" to SessionSource.MANUAL,
                    "2026-06-09T09:00:00" to SessionSource.TIMER,
                ),
            )
            db.sessionDao().insert(session("2026-06-10T11:00:00", SessionStatus.RUNNING, SessionSource.TIMER))
            db.sessionDao().insert(session("2026-06-10T10:00:00", SessionStatus.DISCARDED, SessionSource.MANUAL))

            val state = stateAfterOpen()

            // 计时中的与被丢弃的不该出现在历史列表里。若它们混进来，
            // 那条 DISCARDED 还会顺带带出一个不该存在的「补录」角标
            assertThat(state.sessions).hasSize(2)
            assertThat(state.sessions.map { it.status }.toSet())
                .containsExactly(SessionStatus.COMPLETED)
            // 头部「累计次数」是 COUNT(*)，口径必须与列表一致
            assertThat(state.totalCount).isEqualTo(2)
            assertThat(state.totalMs).isEqualTo(90 * MIN)
        }

    // ── 脚手架 ──

    private suspend fun stateAfterOpen(): ProjectSessionsUiState {
        val viewModel = newViewModel()
        viewModel.open(PROJECT_ID)
        return viewModel.state.first { !it.isLoading && it.project != null }
    }

    private suspend fun sessionsShown() = stateAfterOpen().sessions

    private fun newViewModel(): ProjectSessionsViewModel {
        val preferences = InMemoryRepositories().preferencesRepo
        return ProjectSessionsViewModel(
            projectRepository = projectRepository,
            sessionRepository = sessionRepository,
            preferencesRepository = preferences,
            recordManual = RecordManualSessionUseCase(sessionRepository, projectRepository, clock),
            updateSessionDuration = UpdateSessionDurationUseCase(sessionRepository, clock),
            deleteSession = DeleteSessionUseCase(sessionRepository),
            clock = clock,
        )
    }

    private fun at(localDateTime: String): Long = LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()

    private suspend fun seedProject() {
        db.projectDao().insert(
            ProjectEntity(
                id = PROJECT_ID,
                name = "高等数学",
                iconName = "ic_math",
                colorHex = "#3366CC",
                createdAt = at("2026-01-01T00:00:00"),
                updatedAt = at("2026-01-01T00:00:00"),
            ),
        )
    }

    private suspend fun seedSessions(rows: List<Pair<String, SessionSource>>) {
        rows.forEach { (start, source) ->
            db.sessionDao().insert(session(start, SessionStatus.COMPLETED, source))
        }
    }

    /**
     * 计时记录的时间戳按不变量造。
     *
     * 补录记录满足 `end − start == durationMs`（两个时间戳都是事后按「时长 + 哪一天」
     * 推导出来的），计时记录满足 `end − start − accumulatedPauseMs == durationMs`。
     * 这里统一用「零累计暂停」的计时记录，两条不变量同时成立，
     * 因为设置页的「数据体检」会按各自的口径去查，造得不自洽会被它报成数据不一致。
     */
    private fun session(
        startLocal: String,
        status: SessionStatus,
        source: SessionSource,
    ): FocusSessionEntity {
        val startMs = at(startLocal)
        val isSettled = status == SessionStatus.COMPLETED
        val durationMs = if (isSettled) 45 * MIN else null
        return FocusSessionEntity(
            projectId = PROJECT_ID,
            status = status,
            startElapsedMs = startMs,
            startWallClockMs = startMs,
            endElapsedMs = durationMs?.let { startMs + it },
            endWallClockMs = durationMs?.let { startMs + it },
            accumulatedPauseMs = 0L,
            mode = SessionMode.STOPWATCH,
            source = source,
            durationMs = durationMs,
            createdAt = startMs,
            updatedAt = startMs,
        )
    }
}
