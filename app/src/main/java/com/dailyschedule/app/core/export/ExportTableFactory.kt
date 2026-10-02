package com.dailyschedule.app.core.export

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.time.DurationFormatter
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 领域数据 → 导出表的翻译层。
 *
 * **纯函数**（除了可注入的 [ZoneId]），不碰 IO、不碰 Context，
 * 于是「列有哪些、顺序对不对、合计算没算对、空数据长什么样」
 * 全都能在纯 JVM 单测里钉死。
 *
 * ## 两个刻意的取舍
 * 1. **不导出未结束的会话**。`RUNNING` / `PAUSED` 的 `durationMs` 是 null，
 *    强行导出会写出一个 0 分钟的假记录 —— 那是在往用户的账本里塞错误事实。
 *    正在进行的会话等它结束后自然会出现在下一次导出里。
 * 2. **合计只对数值列用公式，其余列留空**。合计行不是一行新数据，
 *    把「合计」两个字下面填满空字符串，看起来会像缺数据；留空格才对。
 */
object ExportTableFactory {

    const val FOCUS_SHEET_NAME = "专注记录"
    const val EXPENSE_SHEET_NAME = "花费记录"

    /** 项目被删除后 `projectId` 为 null，历史记录仍在。导出的文字要说清这件事 */
    const val NO_PROJECT = "（未归属项目）"

    private val FOCUS_HEADERS =
        listOf("序号", "日期", "开始", "结束", "时长", "时长(分钟)", "项目", "来源", "备注")
    private val FOCUS_WIDTHS = listOf(6, 12, 8, 8, 10, 12, 20, 8, 34)

    private val EXPENSE_HEADERS =
        listOf("序号", "日期", "时间", "类型", "金额(元)", "分类", "项目", "备注")
    private val EXPENSE_WIDTHS = listOf(6, 12, 8, 8, 12, 14, 20, 34)

    /** 时长(分钟) 列在 0 基下标下的位置，供合计公式定位 */
    private const val FOCUS_MINUTES_COLUMN = 5
    private const val EXPENSE_AMOUNT_COLUMN = 4

    fun focusTable(
        sessions: List<FocusSession>,
        projectNames: Map<Long, String>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ExportTable {
        val ordered = sessions
            .filter { it.status == SessionStatus.COMPLETED }
            .sortedBy { it.startWallClockMs }

        val rows = ArrayList<List<Cell>>(ordered.size + 1)
        var totalMinutes = 0.0

        ordered.forEachIndexed { index, session ->
            val durationMs = session.durationMs ?: 0L
            val minutes = durationMs / MS_PER_MINUTE
            totalMinutes += minutes
            rows += listOf(
                Cell.Number((index + 1).toDouble()),
                Cell.Text(dateOf(session.startWallClockMs, zoneId)),
                Cell.Text(timeOf(session.startWallClockMs, zoneId)),
                Cell.Text(session.endWallClockMs?.let { timeOf(it, zoneId) }.orEmpty()),
                Cell.Text(DurationFormatter.duration(durationMs)),
                Cell.Number(minutes, NumberFormat.ONE_DECIMAL),
                Cell.Text(session.projectId?.let { projectNames[it] } ?: NO_PROJECT),
                Cell.Text(sourceLabel(session)),
                Cell.Text(session.note.orEmpty()),
            )
        }

        if (ordered.isNotEmpty()) {
            rows += totalRow(
                label = "合计",
                columnCount = FOCUS_HEADERS.size,
                sumColumnIndex = FOCUS_MINUTES_COLUMN,
                formula = "SUM(${columnLetter(FOCUS_MINUTES_COLUMN)}2:" +
                    "${columnLetter(FOCUS_MINUTES_COLUMN)}${ordered.size + 1})",
                total = totalMinutes,
                format = NumberFormat.ONE_DECIMAL,
            )
        }

        return ExportTable(
            name = FOCUS_SHEET_NAME,
            headers = FOCUS_HEADERS,
            rows = rows,
            columnWidths = FOCUS_WIDTHS,
            dataRowCount = ordered.size,
        )
    }

    fun expenseTable(
        expenses: List<Expense>,
        categoryNames: Map<Long, String>,
        projectNames: Map<Long, String>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ExportTable {
        val ordered = expenses.sortedBy { it.occurredAt }

        val rows = ArrayList<List<Cell>>(ordered.size + 1)
        var totalExpenseYuan = 0.0

        ordered.forEachIndexed { index, expense ->
            val yuan = expense.amountCents / CENTS_PER_YUAN
            if (expense.type == ExpenseType.EXPENSE) totalExpenseYuan += yuan
            rows += listOf(
                Cell.Number((index + 1).toDouble()),
                Cell.Text(dateOf(expense.occurredAt, zoneId)),
                Cell.Text(timeOf(expense.occurredAt, zoneId)),
                Cell.Text(typeLabel(expense.type)),
                Cell.Number(yuan, NumberFormat.TWO_DECIMAL),
                Cell.Text(categoryNames[expense.categoryId] ?: UNKNOWN_CATEGORY),
                Cell.Text(expense.projectId?.let { projectNames[it] } ?: NO_PROJECT),
                Cell.Text(expense.note.orEmpty()),
            )
        }

        if (ordered.isNotEmpty()) {
            val amountColumn = columnLetter(EXPENSE_AMOUNT_COLUMN)
            val typeColumn = columnLetter(TYPE_COLUMN)
            // 用 SUMIF 而不是 SUM：数据库里预留了 INCOME，
            // 直接全列求和会把收入也加进「花费合计」，是个不会报错的错误答案
            rows += totalRow(
                label = "合计(支出)",
                columnCount = EXPENSE_HEADERS.size,
                sumColumnIndex = EXPENSE_AMOUNT_COLUMN,
                formula = "SUMIF($typeColumn\$2:$typeColumn\$${ordered.size + 1}," +
                    "\"${typeLabel(ExpenseType.EXPENSE)}\",$amountColumn\$2:$amountColumn\$${ordered.size + 1})",
                total = totalExpenseYuan,
                format = NumberFormat.TWO_DECIMAL,
            )
        }

        return ExportTable(
            name = EXPENSE_SHEET_NAME,
            headers = EXPENSE_HEADERS,
            rows = rows,
            columnWidths = EXPENSE_WIDTHS,
            dataRowCount = ordered.size,
        )
    }

    /** 「类型」列在 0 基下标下的位置，SUMIF 条件区要用 */
    private const val TYPE_COLUMN = 3

    private const val UNKNOWN_CATEGORY = "（未知分类）"

    /**
     * 「来源」列的两种取值。用户看导出表时关心的是「这条是我坐下来计时的，
     * 还是事后凭记忆补的」——「正常/补录」比「TIMER/MANUAL」更接近这个问法。
     */
    const val SOURCE_TIMER = "正常"
    const val SOURCE_MANUAL = "补录"

    private const val MS_PER_MINUTE = 60_000.0
    private const val CENTS_PER_YUAN = 100.0

    private fun totalRow(
        label: String,
        columnCount: Int,
        sumColumnIndex: Int,
        formula: String,
        total: Double,
        format: NumberFormat,
    ): List<Cell> = List(columnCount) { index ->
        when (index) {
            0 -> Cell.Text(label, bold = true)
            sumColumnIndex -> Cell.Formula(formula, cached = total, format = format, bold = true)
            else -> Cell.Blank
        }
    }

    private fun columnLetter(index: Int): String = XlsxWriter.columnLetter(index)

    private fun dateOf(wallClockMs: Long, zoneId: ZoneId): String {
        val local = localOf(wallClockMs, zoneId)
        return "%04d-%02d-%02d".format(local.year, local.monthValue, local.dayOfMonth)
    }

    private fun timeOf(wallClockMs: Long, zoneId: ZoneId): String {
        val local = localOf(wallClockMs, zoneId)
        return "%02d:%02d".format(local.hour, local.minute)
    }

    private fun localOf(wallClockMs: Long, zoneId: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(wallClockMs), zoneId)

    private fun typeLabel(type: ExpenseType): String = when (type) {
        ExpenseType.EXPENSE -> "支出"
        ExpenseType.INCOME -> "收入"
    }

    /** 来源列：补录的记录标出来，纯计时产生的标「正常」 */
    private fun sourceLabel(session: FocusSession): String =
        if (session.isManual) SOURCE_MANUAL else SOURCE_TIMER
}
