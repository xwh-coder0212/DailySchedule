package com.dailyschedule.app.domain.model

import com.dailyschedule.app.core.model.SessionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [FocusSession.elapsedSinceStart] —— 计时卡上那个秒数。
 *
 * 这个方法从首页和记录页两份重复代码收进来的，所以重点守两件事：
 * 暂停期间不涨，以及暂停时段只扣一次。
 */
class FocusSessionTest {
    private fun running(
        startElapsedMs: Long = 1_000L,
        accumulatedPauseMs: Long = 0L,
        pauseStartElapsedMs: Long? = null,
    ) = FocusSession(
        status = SessionStatus.RUNNING,
        startElapsedMs = startElapsedMs,
        startWallClockMs = 1_700_000_000_000L,
        accumulatedPauseMs = accumulatedPauseMs,
        pauseStartElapsedMs = pauseStartElapsedMs,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `未暂停时等于经过时间`() {
        val session = running(startElapsedMs = 1_000L)
        assertThat(session.elapsedSinceStart(61_000L)).isEqualTo(60_000L)
    }

    @Test
    fun `已累计的暂停时段被扣除`() {
        val session = running(startElapsedMs = 1_000L, accumulatedPauseMs = 20_000L)
        // 经过 60s，其中 20s 是之前暂停掉的
        assertThat(session.elapsedSinceStart(61_000L)).isEqualTo(40_000L)
    }

    @Test
    fun `正处在暂停中时不涨 —— 暂停起点之后的每一毫秒都不算`() {
        val session =
            running(
                startElapsedMs = 1_000L,
                accumulatedPauseMs = 0L,
                pauseStartElapsedMs = 31_000L,
            )
        // 走到 45s，但 30s 起就暂停了 → 只有前 30s 算数
        assertThat(session.elapsedSinceStart(45_000L)).isEqualTo(30_000L)
        // 再等 10 秒，读数必须原地不动
        assertThat(session.elapsedSinceStart(55_000L)).isEqualTo(30_000L)
    }

    @Test
    fun `累计暂停与当前暂停同时存在时不重复扣`() {
        val session =
            running(
                startElapsedMs = 0L,
                accumulatedPauseMs = 10_000L,
                pauseStartElapsedMs = 50_000L,
            )
        // 经过 60s：扣已累计的 10s，再扣当前这段 60-50=10s
        assertThat(session.elapsedSinceStart(60_000L)).isEqualTo(40_000L)
    }

    @Test
    fun `时钟回拨也不会给出负时长`() {
        val session = running(startElapsedMs = 100_000L)
        assertThat(session.elapsedSinceStart(50_000L)).isEqualTo(0L)
    }

    @Test
    fun `补录出来的是已完成记录，isManual 为真`() {
        val recorded =
            FocusSession.recorded(
                projectId = 7L,
                durationMs = 90 * 60_000L,
                startWallClockMs = 1_700_000_000_000L,
                note = null,
                nowWallClockMs = 1_700_100_000_000L,
            )

        assertThat(recorded.isManual).isTrue()
        assertThat(recorded.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(recorded.endWallClockMs).isEqualTo(recorded.startWallClockMs + 90 * 60_000L)
    }
}
