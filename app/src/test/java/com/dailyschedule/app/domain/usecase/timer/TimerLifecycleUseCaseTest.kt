package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimerLifecycleUseCaseTest {
    private val repos = InMemoryRepositories()
    private val clock = FakeClock(elapsedMs = 1_000L, wallMs = 100_000L)
    private val startUseCase = TimerStartUseCase(repos.sessionRepo, repos.preferencesRepo, clock)
    private val pauseUseCase = TimerPauseUseCase(repos.sessionRepo, clock)
    private val resumeUseCase = TimerResumeUseCase(repos.sessionRepo, clock)
    private val stopUseCase = TimerStopUseCase(repos.sessionRepo, clock)

    @Test
    fun `开始后 getActive 返回 RUNNING 会话`() =
        runTest {
            val result = startUseCase(projectId = null)
            assertThat(result.isSuccess).isTrue()
            val active = repos.sessionRepo.getActive()!!
            assertThat(active.status).isEqualTo(SessionStatus.RUNNING)
            assertThat(active.pauseStartElapsedMs).isNull()
        }

    @Test
    fun `已有活动会话时再次开始被拒`() =
        runTest {
            startUseCase(projectId = null)
            clock.advance(10_000L)
            val second = startUseCase(projectId = null)
            assertThat(second.isSuccess).isFalse()
        }

    @Test
    fun `暂停后 status 变 PAUSED 且记录 pauseStart`() =
        runTest {
            startUseCase(projectId = null)
            clock.advance(60_000L)
            val r = pauseUseCase()
            assertThat(r.isSuccess).isTrue()
            val active = repos.sessionRepo.getActive()!!
            assertThat(active.status).isEqualTo(SessionStatus.PAUSED)
            assertThat(active.pauseStartElapsedMs).isEqualTo(61_000L)
        }

    @Test
    fun `继续后累加 pause 时长并清空 pauseStart`() =
        runTest {
            startUseCase(projectId = null)
            clock.advance(60_000L)
            pauseUseCase()
            clock.advance(15_000L)
            resumeUseCase()
            val active = repos.sessionRepo.getActive()!!
            assertThat(active.status).isEqualTo(SessionStatus.RUNNING)
            assertThat(active.pauseStartElapsedMs).isNull()
            assertThat(active.accumulatedPauseMs).isEqualTo(15_000L)
        }

    @Test
    fun `结束按时长扣减暂停时段`() =
        runTest {
            startUseCase(projectId = null)
            clock.advance(60_000L)
            pauseUseCase()
            clock.advance(20_000L)
            resumeUseCase()
            clock.advance(120_000L)
            val result = stopUseCase()
            assertThat(result.isSuccess).isTrue()
            val active = repos.sessionRepo.getActive() // 已结束 → null
            assertThat(active).isNull()
            // 总跨度 60+20+120 = 200s，扣除 20s 暂停 → 180s。
            // 注意 60+120 已经是纯学习时长，不能再减一次暂停（否则会重复扣除）。
            val s = (result as com.dailyschedule.app.core.result.AppResult.Success).value
            assertThat(s.durationMs).isEqualTo(180_000L)
        }

    @Test
    fun `已结束后不能再结束`() =
        runTest {
            startUseCase(projectId = null)
            clock.advance(60_000L)
            stopUseCase()
            clock.advance(10_000L)
            val second = stopUseCase()
            assertThat(second.isSuccess).isFalse()
        }
}

private val com.dailyschedule.app.core.result.AppResult<*>.isSuccess: Boolean
    get() = this is com.dailyschedule.app.core.result.AppResult.Success
