package com.dailyschedule.app.feature.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.stats.StatsAggregator
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.data.db.AppDatabase
import com.dailyschedule.app.data.db.DatabaseCallback
import com.dailyschedule.app.data.db.entity.CategoryEntity
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity
import com.dailyschedule.app.data.repository.ExpenseRepositoryImpl
import com.dailyschedule.app.data.repository.SessionRepositoryImpl
import com.dailyschedule.app.domain.repository.PreferencesRepository
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * 统计页的**数值**断言 —— 第二轮真机验收里自认没覆盖的那一项。
 *
 * ## 为什么值得单独存在
 * 第一轮验收只做了「三张图渲染正常、占比合计 100%」这种目视核对。
 * 目视核对能发现「图画不出来」，发现不了「柱子画对了位置但数字是错的」——
 * 后者才是真正的数据缺陷，而且它不会崩、不会报错，只会让人慢慢不再相信这个页面。
 *
 * ## 这个测试刻意走的三条路
 * 1. **真实 Room，不是假仓储**。统计的源头是 DAO 里那些 `SUM` / `COUNT` / `GROUP BY`，
 *    用内存列表模拟仓储等于绕过了最可能出错的一段。
 * 2. **真实 [DayBoundary]，用的是同一份日切与周起始配置**。区间换算写错会让
 *    一笔账跑到隔壁的柱子上，而这种错在不跨天的时候完全看不出来。
 * 3. **真实 `StatsViewModel`**。它是「哪段时间配哪张图、纵轴标签从哪来」的落点。
 *
 * ## 期望值是手算的常量，不是生产代码算出来的
 * 如果把期望值写成「和 [StatsAggregator] 的结果比」，那这个测试只能证明
 * 「代码等于它自己」。下面所有数字都是按业务规则手工推出来的：
 *
 * ```
 * 时间轴（Asia/Shanghai，日切 4 点，周一为一周之始）
 *   06-01 10:00  专注 120min   → 六月、不在本周
 *   06-08 10:00  专注  30min   → 周一
 *   06-10 02:00  专注  15min   → 凌晨 2 点 < 4 点 → 业务日期是 06-09（周二）
 *   06-10 09:00  专注  45min   → 周三
 *   06-08 05:00  支出   ¥5.00  → 分类 1
 *   06-09 20:00  支出   ¥8.00  → 分类 2
 *   06-10 12:30  支出  ¥12.50  → 分类 1
 *   05-20 12:00  支出  ¥99.99  → 分类 1（只进本年）
 * ```
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class StatsNumbersEndToEndTest {
    private val zone: ZoneId = ZoneId.of("+08:00")
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val defaultTimeZone: TimeZone = TimeZone.getDefault()

    private lateinit var db: AppDatabase
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var expenseRepository: ExpenseRepositoryImpl
    private lateinit var preferences: PreferencesRepository
    private lateinit var clock: FakeClock

    private companion object {
        const val PROJECT_NAME = "高等数学"
        const val CATEGORY_STUDY = "学习"
        const val CATEGORY_FOOD = "餐饮"
        const val DAY_START_HOUR = 4
        const val WEEK_START_DAY = 1

        const val MIN = 60_000L
    }

    @Before
    fun setUp() {
        // 期望值是按东八区手算的，就必须把 JVM 默认时区钉死。
        // 不钉的话，换一台机器跑同一份断言会得出不同结果 —— 那是测试的缺陷，不是代码的。
        // DayBoundary 默认取 ZoneId.systemDefault()，所以这一行是真的在控制被测对象。
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        Dispatchers.setMain(Dispatchers.Unconfined)

        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(DatabaseCallback())
                .allowMainThreadQueries()
                .build()
        sessionRepository = SessionRepositoryImpl(db.sessionDao())
        expenseRepository = ExpenseRepositoryImpl(db.expenseDao())

        // 只用它的偏好假实现，不碰它的内存仓储 —— 数据一律走真实 Room
        preferences = InMemoryRepositories().preferencesRepo
        clock = FakeClock(wallMs = at("2026-06-10T12:00:00"))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(defaultTimeZone)
    }

    // ── 专注 ──

    @Test
    fun `本周专注的总时长 次数 项目排行 星期分布都对得上手算值`() =
        runBlocking {
            seed()

            val state = stateAfter(StatsPeriod.WEEK)

            // 06-01 那条不在本周，所以是 30 + 15 + 45 分钟
            assertThat(state.studyMs).isEqualTo(90 * MIN)
            assertThat(state.sessionCount).isEqualTo(3)
            assertThat(state.averageMs).isEqualTo(30 * MIN)

            val byProject = state.byProject.single()
            assertThat(byProject.projectId).isEqualTo(1L)
            assertThat(byProject.projectName).isEqualTo(PROJECT_NAME)
            assertThat(byProject.totalDurationMs).isEqualTo(90 * MIN)

            // 星期分布：key 是 ISO 星期号（1=周一），空的那几天必须是 0 而不是缺项
            assertThat(state.focusByWeekday.map { it.key }).containsExactly(1, 2, 3, 4, 5, 6, 7).inOrder()
            assertThat(state.focusByWeekday.map { it.value })
                .containsExactly(
                    30 * MIN,
                    // 06-10 凌晨 2 点那条，日切 4 点 → 算 06-09（周二）
                    15 * MIN,
                    45 * MIN,
                    0L,
                    0L,
                    0L,
                    0L,
                ).inOrder()
        }

    @Test
    fun `跨月与日切边界：今日只算落在 06-10 04点之后的那条`() =
        runBlocking {
            seed()

            val today = stateAfter(StatsPeriod.TODAY)
            // 只有 06-10 09:00 那条。凌晨 2 点那条归属 06-09，不该混进来
            assertThat(today.studyMs).isEqualTo(45 * MIN)
            assertThat(today.sessionCount).isEqualTo(1)

            val month = stateAfter(StatsPeriod.MONTH)
            // 六月四条全在：120 + 30 + 15 + 45
            assertThat(month.studyMs).isEqualTo(210 * MIN)
            assertThat(month.sessionCount).isEqualTo(4)

            // 月的起点是「1 号 04:00」而不是「1 号 00:00」——
            // 06-01 10:00 属于六月，而 06-01 02:00 会属于五月
            val boundary = DayBoundary(DAY_START_HOUR, WEEK_START_DAY, zone)
            assertThat(boundary.monthRangeOf(LocalDate.of(2026, 6, 10)).first)
                .isEqualTo(at("2026-06-01T04:00:00"))
        }

    // ── 消费 ──

    @Test
    fun `消费的本周 本月 本年三个口径与分类占比都是手算值`() =
        runBlocking {
            seed()

            val week = stateAfter(StatsPeriod.WEEK)
            assertThat(week.expenseCents).isEqualTo(2_550L)

            val month = stateAfter(StatsPeriod.MONTH)
            assertThat(month.expenseCents).isEqualTo(2_550L)

            val year = stateAfter(StatsPeriod.YEAR)
            // 五月那笔 ¥99.99 只进本年：2550 + 9999
            assertThat(year.expenseCents).isEqualTo(12_549L)

            val byCategory = week.byCategory.associate { it.categoryId to it.totalCents }
            assertThat(byCategory).containsExactly(1L, 1_750L, 2L, 800L)

            // 占比用最大余数法：1750/2550 与 800/2550 逐项四舍五入会得到 69 + 31 = 100 没错，
            // 但换成三等分就只剩 99，所以「合计恒为 100」必须现在钉住
            val shares = StatsAggregator.percentShares(week.byCategory.map { it.totalCents })
            assertThat(shares).containsExactly(69, 31).inOrder()
            assertThat(shares.sum()).isEqualTo(100)
        }

    @Test
    fun `趋势图：刻度数量 每天的值 与横轴标签抽稀后的占位数`() =
        runBlocking {
            seed()

            val week = stateAfter(StatsPeriod.WEEK)
            assertThat(week.trendUnit).isEqualTo(TrendUnit.DAY)
            // 一周必须是 7 个刻度，哪怕只有 3 天有数据 —— 刻度少了柱子会整体左移
            assertThat(week.expenseTrend).hasSize(7)
            assertThat(week.expenseTrend.map { it.value })
                .containsExactly(500L, 800L, 1_250L, 0L, 0L, 0L, 0L)
                .inOrder()
            // 标签抽稀只把文字清空，列表长度不动。长度一变，折线点就会错位
            assertThat(week.trendLabels).containsExactly("8", "", "10", "", "12", "", "14").inOrder()

            val month = stateAfter(StatsPeriod.MONTH)
            assertThat(month.expenseTrend).hasSize(30)
            assertThat(month.trendLabels).hasSize(30)
            assertThat(month.expenseTrend.map { it.value }.sum()).isEqualTo(2_550L)

            val year = stateAfter(StatsPeriod.YEAR)
            assertThat(year.trendUnit).isEqualTo(TrendUnit.MONTH)
            assertThat(year.expenseTrend).hasSize(12)
            // 五月一笔、六月一笔，其余月份是 0
            assertThat(year.expenseTrend.map { it.value })
                .containsExactly(0L, 0L, 0L, 0L, 9_999L, 2_550L, 0L, 0L, 0L, 0L, 0L, 0L)
                .inOrder()
        }

    // ── 脚手架 ──

    /**
     * 取一次界面状态。
     *
     * ## 为什么必须传 period，而不是读 `.value`
     * `stateIn` 用的是 `WhileSubscribed`，订阅建立后上游才启动。
     * 直接读 `.value` 拿到的永远是 `isLoading = true` 的初始值，
     * 断言会全部退化成「和默认值比」，看起来绿、实际什么都没测。
     *
     * ## 为什么 segment 不影响这里的数字
     * 视图模型是 `combine(focusPart, expensePart)` —— 两段**始终都算**，
     * `segment` 只决定界面显示哪一段。所以下面断言消费数字时不需要切到消费分段，
     * 但为了和真机上的操作顺序一致，仍然显式设一次。
     */
    private suspend fun stateAfter(period: StatsPeriod): StatsUiState {
        val viewModel =
            StatsViewModel(
                sessionRepository = sessionRepository,
                expenseRepository = expenseRepository,
                preferencesRepository = preferences,
                clock = clock,
            )
        viewModel.onPeriodSelected(period)
        return viewModel.state.first { !it.isLoading && it.period == period }
    }

    private fun at(localDateTime: String): Long = LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli()

    private suspend fun seed() {
        db.projectDao().insert(
            ProjectEntity(
                id = 1L,
                name = PROJECT_NAME,
                iconName = "ic_math",
                colorHex = "#3366CC",
                createdAt = at("2026-01-01T00:00:00"),
                updatedAt = at("2026-01-01T00:00:00"),
            ),
        )
        db.categoryDao().insert(
            CategoryEntity(
                id = 1L,
                name = CATEGORY_STUDY,
                iconName = "ic_book",
                colorHex = "#3366CC",
                sortOrder = 0,
            ),
        )
        db.categoryDao().insert(
            CategoryEntity(
                id = 2L,
                name = CATEGORY_FOOD,
                iconName = "ic_food",
                colorHex = "#CC6633",
                sortOrder = 1,
            ),
        )

        listOf(
            "2026-06-01T10:00:00" to 120,
            "2026-06-08T10:00:00" to 30,
            "2026-06-10T02:00:00" to 15,
            "2026-06-10T09:00:00" to 45,
        ).forEach { (start, minutes) ->
            db.sessionDao().insert(session(start, minutes))
        }

        listOf(
            Triple("2026-06-08T05:00:00", 1L, 500L),
            Triple("2026-06-09T20:00:00", 2L, 800L),
            Triple("2026-06-10T12:30:00", 1L, 1_250L),
            Triple("2026-05-20T12:00:00", 1L, 9_999L),
        ).forEach { (occurredAt, categoryId, cents) ->
            db.expenseDao().insert(expense(occurredAt, categoryId, cents))
        }
    }

    /**
     * 已完成的计时记录。
     *
     * 时间戳按计时记录的语义造：`end - start - 累计暂停 == durationMs`。
     * 造一条不满足这个不变量的记录，设置页的「数据体检」会把它报成数据不一致。
     */
    private fun session(
        startLocal: String,
        minutes: Int,
    ): FocusSessionEntity {
        val startMs = at(startLocal)
        val durationMs = minutes * MIN
        return FocusSessionEntity(
            projectId = 1L,
            status = SessionStatus.COMPLETED,
            startElapsedMs = startMs,
            startWallClockMs = startMs,
            endElapsedMs = startMs + durationMs,
            endWallClockMs = startMs + durationMs,
            accumulatedPauseMs = 0L,
            mode = SessionMode.STOPWATCH,
            source = SessionSource.TIMER,
            durationMs = durationMs,
            createdAt = startMs,
            updatedAt = startMs + durationMs,
        )
    }

    private fun expense(
        occurredAtLocal: String,
        categoryId: Long,
        amountCents: Long,
    ): ExpenseEntity {
        val occurredAt = at(occurredAtLocal)
        return ExpenseEntity(
            amountCents = amountCents,
            categoryId = categoryId,
            projectId = null,
            type = ExpenseType.EXPENSE,
            occurredAt = occurredAt,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )
    }
}
