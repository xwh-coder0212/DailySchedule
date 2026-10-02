package com.dailyschedule.app.domain.usecase.timer

import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimerRebootRecoveryUseCaseTest {

    private val repos = InMemoryRepositories()
    private val clock = FakeClock(elapsedMs = 0L, wallMs = 1_700_000_000_000L)
    private val startUseCase = TimerStartUseCase(repos.sessionRepo, repos.preferencesRepo, clock)
    private val recovery = TimerRebootRecoveryUseCase(repos.sessionRepo, clock)

    @Test
    fun `无活动会话时返回 NoActive`() = runTest {
        val r = recovery()
        assertThat((r as com.dailyschedule.app.core.result.AppResult.Success).value)
            .isEqualTo(TimerRebootRecoveryUseCase.Outcome.NoActive)
    }

    @Test
    fun `正常情况下不做任何修改`() = runTest {
        startUseCase(projectId = null)
        val before = repos.sessionRepo.getActive()!!
        clock.advance(60_000L)
        val r = recovery()
        assertThat((r as com.dailyschedule.app.core.result.AppResult.Success).value)
            .isEqualTo(TimerRebootRecoveryUseCase.Outcome.NoActive) // Normal 折叠为 NoActive
        val after = repos.sessionRepo.getActive()!!
        assertThat(after.status).isEqualTo(before.status)
        assertThat(after.startWallClockMs).isEqualTo(before.startWallClockMs)
    }

    @Test
    fun `真重启时自动截断到开机时刻并标 COMPLETED`() = runTest {
        // 先让系统"运行"10 分钟再开始计时。
        // 真实手机上 App 启动时 elapsedRealtime 早已很大；若从 0 开始计时，
        // 重启后 startElapsed > nowElapsed 不成立，会命中「开机极短时间即开始计时」
        // 这个已知漏判分支（见 RebootResolver 注释）—— 属测试场景不真实，非实现错误。
        clock.advance(10 * 60 * 1000L)
        startUseCase(projectId = null)
        // 手机重启：单调时钟归零、墙上时钟继续走
        clock.simulateReboot(downtimeMs = 2 * 60 * 60 * 1000L)  // 关机 2 小时
        clock.advance(30_000L)  // 开机 30 秒后打开 App

        val r = recovery()
        val outcome = (r as com.dailyschedule.app.core.result.AppResult.Success).value
        assertThat(outcome).isInstanceOf(TimerRebootRecoveryUseCase.Outcome.AutoStoppedAtReboot::class.java)
        val session = (outcome as TimerRebootRecoveryUseCase.Outcome.AutoStoppedAtReboot).session
        assertThat(session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(session.needsReview).isFalse()
        // 活动会话已结束
        assertThat(repos.sessionRepo.getActive()).isNull()
    }

    @Test
    fun `系统时间被改后标 needsReview 不静默改数据`() = runTest {
        startUseCase(projectId = null)
        // 墙上时钟跳变 1 小时，单调时钟不变 —— 模拟用户改时间
        clock.jumpWallClock(60 * 60 * 1000L)
        clock.advance(10_000L)

        val r = recovery()
        val outcome = (r as com.dailyschedule.app.core.result.AppResult.Success).value
        assertThat(outcome).isInstanceOf(TimerRebootRecoveryUseCase.Outcome.ClockTampered::class.java)
        val session = (outcome as TimerRebootRecoveryUseCase.Outcome.ClockTampered).session
        // 仍在跑（未静默截断）
        assertThat(session.status).isEqualTo(SessionStatus.RUNNING)
        // 但标了 needsReview
        assertThat(session.needsReview).isTrue()
        // durationMs 没被偷偷写
        assertThat(session.durationMs).isNull()
    }
}
