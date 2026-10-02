package com.dailyschedule.app.core.export

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 导出内容测试。
 *
 * 这一层是「用户拿到手的 Excel 到底长什么样」的唯一守门人。
 * 列的顺序、日期的时区、金额从「分」到「元」的换算、合计算的是哪几行 ——
 * 全都只在这里被验证，而且都必须是确定性的：给定时区、给定时间戳，
 * 结果只有一种。任何「看情况」都意味着换台手机导出的数字会变。
 */
class ExportTableFactoryTest {

    /** 固定时区，否则测试结果跟着 CI 机器的 TZ 走 */
    private val zone = ZoneId.of("Asia/Shanghai")

    // ── 专注记录 ──

    @Test
    fun `只导出已结束的会话 —— 未结束的没有权威时长`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 95),
                session(id = 2, startMs = wall(2026, 9, 10, 14, 0), minutes = 0, status = SessionStatus.RUNNING),
                session(id = 3, startMs = wall(2026, 9, 10, 16, 0), minutes = 0, status = SessionStatus.DISCARDED),
            ),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(table.dataRowCount).isEqualTo(1)
        // 1 行数据 + 1 行合计 = 2 行；dataRowCount 不把合计行算进筛选范围
        assertThat(table.rows).hasSize(2)
    }

    @Test
    fun `按开始时间升序且序号从 1 开始`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 14, 0), minutes = 30),
                session(id = 2, startMs = wall(2026, 9, 10, 8, 30), minutes = 95),
            ),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(numberAt(table, 0, 0)).isEqualTo(1.0)
        assertThat(numberAt(table, 1, 0)).isEqualTo(2.0)
        assertThat(textAt(table, 0, 2)).isEqualTo("08:30")
        assertThat(textAt(table, 1, 2)).isEqualTo("14:00")
    }

    @Test
    fun `日期与时刻按给定时区渲染`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 95)),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 1)).isEqualTo("2026-09-10")
        assertThat(textAt(table, 0, 2)).isEqualTo("08:30")
        assertThat(textAt(table, 0, 3)).isEqualTo("10:05")
    }

    @Test
    fun `时长列同时给出人读文本与可求和的分钟数`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 95)),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 4)).isEqualTo("1h 35m")
        assertThat(table.rows[0][5]).isEqualTo(Cell.Number(95.0, NumberFormat.ONE_DECIMAL))
    }

    @Test
    fun `项目名按 id 映射，项目已删或未挂项目都写明未归属`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 60, projectId = 1L),
                session(id = 2, startMs = wall(2026, 9, 10, 9, 30), minutes = 60, projectId = null),
                session(id = 3, startMs = wall(2026, 9, 10, 10, 30), minutes = 60, projectId = 99L),
            ),
            projectNames = mapOf(1L to "高等数学"),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 6)).isEqualTo("高等数学")
        assertThat(textAt(table, 1, 6)).isEqualTo(ExportTableFactory.NO_PROJECT)
        assertThat(textAt(table, 2, 6)).isEqualTo(ExportTableFactory.NO_PROJECT)
    }

    @Test
    fun `备注为空时写成空白而不是 null 字样`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 60, note = null),
                session(id = 2, startMs = wall(2026, 9, 10, 9, 30), minutes = 60, note = "错题整理"),
            ),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 8)).isEmpty()
        assertThat(textAt(table, 1, 8)).isEqualTo("错题整理")
    }

    @Test
    fun `来源列区分计时产生的记录与补录的记录`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 60),
                session(
                    id = 2,
                    startMs = wall(2026, 9, 10, 9, 30),
                    minutes = 60,
                    source = SessionSource.MANUAL,
                ),
            ),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 7)).isEqualTo(ExportTableFactory.SOURCE_TIMER)
        assertThat(textAt(table, 1, 7)).isEqualTo(ExportTableFactory.SOURCE_MANUAL)
    }

    @Test
    fun `合计行覆盖数据行且缓存值与明细一致`() {
        val table = ExportTableFactory.focusTable(
            sessions = listOf(
                session(id = 1, startMs = wall(2026, 9, 10, 8, 30), minutes = 95),
                session(id = 2, startMs = wall(2026, 9, 10, 14, 0), minutes = 30),
            ),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        // 表头 1 行 + 数据 2 行 → 合计行是第 4 行，公式只覆盖 F2:F3
        assertThat(table.rows[2][0]).isEqualTo(Cell.Text("合计", bold = true))
        assertThat(table.rows[2][5]).isEqualTo(
            Cell.Formula("SUM(F2:F3)", cached = 125.0, format = NumberFormat.ONE_DECIMAL, bold = true),
        )
        assertThat(table.dataRowCount).isEqualTo(2)
    }

    @Test
    fun `没有记录时表头仍在但没有数据行和合计行`() {
        val table = ExportTableFactory.focusTable(emptyList(), emptyMap(), zone)

        assertThat(table.headers).isNotEmpty()
        assertThat(table.rows).isEmpty()
        assertThat(table.dataRowCount).isEqualTo(0)
    }

    // ── 花费记录 ──

    @Test
    fun `金额从分换算成元并保留两位小数`() {
        val table = ExportTableFactory.expenseTable(
            expenses = listOf(
                expense(id = 1, occurredAt = wall(2026, 9, 10, 12, 0), cents = 4750, categoryId = 1L),
            ),
            categoryNames = mapOf(1L to "餐饮"),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(table.rows[0][4]).isEqualTo(Cell.Number(47.5, NumberFormat.TWO_DECIMAL))
        assertThat(textAt(table, 0, 5)).isEqualTo("餐饮")
        assertThat(textAt(table, 0, 3)).isEqualTo("支出")
    }

    @Test
    fun `按发生时间升序`() {
        val table = ExportTableFactory.expenseTable(
            expenses = listOf(
                expense(id = 1, occurredAt = wall(2026, 9, 10, 20, 0), cents = 100, categoryId = 1L),
                expense(id = 2, occurredAt = wall(2026, 9, 10, 8, 0), cents = 200, categoryId = 1L),
            ),
            categoryNames = mapOf(1L to "餐饮"),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 2)).isEqualTo("08:00")
        assertThat(textAt(table, 1, 2)).isEqualTo("20:00")
    }

    @Test
    fun `花费合计用 SUMIF 只算支出 —— 收入不能被算进花费`() {
        val table = ExportTableFactory.expenseTable(
            expenses = listOf(
                expense(id = 1, occurredAt = wall(2026, 9, 10, 8, 0), cents = 1000, categoryId = 1L),
                expense(
                    id = 2,
                    occurredAt = wall(2026, 9, 10, 9, 0),
                    cents = 5000,
                    categoryId = 1L,
                    type = ExpenseType.INCOME,
                ),
            ),
            categoryNames = mapOf(1L to "餐饮"),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(table.rows[0][3]).isEqualTo(Cell.Text("支出"))
        assertThat(table.rows[1][3]).isEqualTo(Cell.Text("收入"))

        val total = table.rows[2][4] as Cell.Formula
        assertThat(total.formula).isEqualTo("SUMIF(D\$2:D\$3,\"支出\",E\$2:E\$3)")
        // 1000 分为 10 元；5000 分的收入不进合计
        assertThat(total.cached).isEqualTo(10.0)
        assertThat(table.rows[2][0]).isEqualTo(Cell.Text("合计(支出)", bold = true))
    }

    @Test
    fun `分类被删导致查不到名字时写明未知分类`() {
        val table = ExportTableFactory.expenseTable(
            expenses = listOf(
                expense(id = 1, occurredAt = wall(2026, 9, 10, 8, 0), cents = 100, categoryId = 42L),
            ),
            categoryNames = emptyMap(),
            projectNames = emptyMap(),
            zoneId = zone,
        )

        assertThat(textAt(table, 0, 5)).isEqualTo("（未知分类）")
    }

    // ── 工具 ──

    private fun textAt(table: ExportTable, row: Int, column: Int): String =
        (table.rows[row][column] as Cell.Text).value

    private fun numberAt(table: ExportTable, row: Int, column: Int): Double =
        (table.rows[row][column] as Cell.Number).value

    private fun wall(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun session(
        id: Long,
        startMs: Long,
        minutes: Int,
        projectId: Long? = null,
        status: SessionStatus = SessionStatus.COMPLETED,
        note: String? = null,
        source: SessionSource = SessionSource.DEFAULT,
    ): FocusSession {
        val endMs = startMs + minutes * 60_000L
        val completed = status == SessionStatus.COMPLETED
        return FocusSession(
            id = id,
            projectId = projectId,
            status = status,
            source = source,
            startElapsedMs = startMs,
            startWallClockMs = startMs,
            endElapsedMs = if (completed) endMs else null,
            endWallClockMs = if (completed) endMs else null,
            durationMs = if (completed) minutes * 60_000L else null,
            note = note,
            createdAt = startMs,
            updatedAt = startMs,
        )
    }

    private fun expense(
        id: Long,
        occurredAt: Long,
        cents: Long,
        categoryId: Long,
        type: ExpenseType = ExpenseType.EXPENSE,
    ): Expense = Expense(
        id = id,
        amountCents = cents,
        categoryId = categoryId,
        type = type,
        occurredAt = occurredAt,
    )
}
