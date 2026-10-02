package com.dailyschedule.app.domain.usecase.session

import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 补录用例测试。
 *
 * 补录是本项目里唯一「用户直接指定时长」的入口，因此它也是唯一可能写进
 * 不可能数据的地方。这里守的就是那几种不可能：零/负时长、比一天还长、
 * 落在未来、挂到已经删掉的待办上。
 */
class RecordManualSessionUseCaseTest {
    private val repos = InMemoryRepositories()
    private val clock = FakeClock(elapsedMs = 5_000L, wallMs = 1_700_000_000_000L)
    private val useCase =
        RecordManualSessionUseCase(
            sessionRepository = repos.sessionRepo,
            projectRepository = repos.projectRepo,
            clock = clock,
        )

    @Test
    fun `补录写入的是已结束的手动记录，时间戳与时长自洽`() =
        runTest {
            val duration = 90 * 60_000L
            val result =
                useCase(
                    request(durationMs = duration, note = "昨天忘了开计时"),
                )

            assertThat(result).isInstanceOf(AppResult.Success::class.java)
            val saved = allSessions().single()

            assertThat(saved.status).isEqualTo(SessionStatus.COMPLETED)
            assertThat(saved.source).isEqualTo(SessionSource.MANUAL)
            assertThat(saved.durationMs).isEqualTo(duration)
            // 补录出来的记录同样必须满足「结束 - 开始 = 时长」，
            // 否则设置页的「数据体检」会把它们全报成不一致
            assertThat(saved.endWallClockMs).isEqualTo(saved.startWallClockMs + duration)
            assertThat(saved.note).isEqualTo("昨天忘了开计时")
        }

    @Test
    fun `补录不会产生活动会话`() =
        runTest {
            useCase(request(durationMs = 30 * 60_000L))

            assertThat(repos.sessionRepo.getActive()).isNull()
        }

    @Test
    fun `时长为 0 被拒`() =
        runTest {
            assertThat(useCase(request(durationMs = 0L)).isFailure).isTrue()
        }

    @Test
    fun `时长为负被拒`() =
        runTest {
            assertThat(useCase(request(durationMs = -1L)).isFailure).isTrue()
        }

    @Test
    fun `超过 24 小时被拒，恰好 24 小时放行`() =
        runTest {
            // 超过一天只可能是位数填错了，不是真实投入
            assertThat(useCase(request(durationMs = 25 * 60 * 60_000L)).isFailure).isTrue()
            assertThat(useCase(request(durationMs = 24 * 60 * 60_000L)).isSuccess).isTrue()
        }

    @Test
    fun `结束时刻落在未来被拒`() =
        runTest {
            // 开始在现在、时长 3 小时 → 结束在 3 小时后
            val result =
                useCase(
                    request(durationMs = 3 * 60 * 60_000L, startWallClockMs = clock.wallMs),
                )

            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun `一分钟以内的未来容差被放行 —— 界面算开始时刻与保存之间时钟会走`() =
        runTest {
            val duration = 60 * 60_000L
            val result =
                useCase(
                    request(durationMs = duration, startWallClockMs = clock.wallMs + 30_000L - duration),
                )

            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `挂到不存在的待办上被拒`() =
        runTest {
            assertThat(useCase(request(projectId = 999L, durationMs = 60_000L)).isFailure).isTrue()
        }

    @Test
    fun `挂到存在的待办上可以补录`() =
        runTest {
            val projectId =
                repos.projectRepo.create(
                    Project(name = "高等数学", iconName = "ic_math", colorHex = "#3366CC"),
                )

            val result = useCase(request(projectId = projectId, durationMs = 45 * 60_000L))

            assertThat(result.isSuccess).isTrue()
            assertThat(allSessions().single().projectId).isEqualTo(projectId)
        }

    @Test
    fun `全是空白的备注被归一成 null，正常备注原样保存`() =
        runTest {
            useCase(request(durationMs = 60_000L, note = "   \n  "))
            useCase(request(durationMs = 60_000L, note = "含 空格 的备注"))

            assertThat(allSessions().map { it.note })
                .containsExactly(null, "含 空格 的备注")
        }

    // ── 工具 ──

    private fun request(
        projectId: Long? = null,
        durationMs: Long,
        startWallClockMs: Long = clock.wallMs - durationMs,
        note: String? = null,
    ) = RecordManualSessionUseCase.Request(
        projectId = projectId,
        durationMs = durationMs,
        startWallClockMs = startWallClockMs,
        note = note,
    )

    private suspend fun allSessions(): List<FocusSession> = repos.sessionRepo.observeInRange(0L, Long.MAX_VALUE).first()
}

/**
 * [AppResult] 是本项目自定义的 sealed 接口（不是 Kotlin 标准库的 `Result`），
 * 因此没有现成的 `isSuccess` / `isFailure`。
 *
 * 这里补两个文件级扩展，与 `ProjectUseCasesTest` / `AddExpenseUseCaseTest` 的写法保持一致，
 * 让断言能写成 `assertThat(result.isSuccess).isTrue()`。
 */
private val AppResult<*>.isSuccess: Boolean
    get() = this is AppResult.Success

private val AppResult<*>.isFailure: Boolean
    get() = this is AppResult.Failure
