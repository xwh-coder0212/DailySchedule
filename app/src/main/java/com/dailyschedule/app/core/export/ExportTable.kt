package com.dailyschedule.app.core.export

/**
 * 导出表格的数据模型。
 *
 * 刻意**不含任何 Android / Room 类型** —— 于是「导哪些列、什么顺序、金额按时分还是按元、
 * 合计怎么算」全部可以在纯 JVM 单测里断言，不必装模拟器。
 *
 * 单元格只有四种，够用且不易写错。凡是这里没有的类型（日期、颜色、富文本）
 * 一律降级成 [Text] 字符串 —— 导出的第一要求是「能被 Excel 打开，
 * 且人看得懂」，不是「在 Excel 里还能继续算」。唯一例外是数值列，
 * 因为「按月求和」是导出后最常见的动作，必须保持数字类型。
 */
data class ExportTable(
    /** 工作表名。Excel 上限 31 字符，且不允许 `[ ] : * ? / \` */
    val name: String,
    val headers: List<String>,
    /** 含合计行。每行长度必须与 [headers] 等长 */
    val rows: List<List<Cell>>,
    /** 每列字符宽度，与 [headers] 等长 */
    val columnWidths: List<Int>,
    /** 数据行数（**不含**合计行），用于确定筛选范围 */
    val dataRowCount: Int = rows.size,
)

/** 数值单元格的显示格式。 */
enum class NumberFormat {
    /** 原样，用于序号这类整数 */
    PLAIN,

    /** 保留 1 位小数，用于「时长(分钟)」 */
    ONE_DECIMAL,

    /** 保留 2 位小数，用于「金额(元)」 */
    TWO_DECIMAL,
}

sealed interface Cell {

    /** 字符串。`bold` 用于合计行的标签 */
    data class Text(val value: String, val bold: Boolean = false) : Cell

    data class Number(
        val value: Double,
        val format: NumberFormat = NumberFormat.PLAIN,
        val bold: Boolean = false,
    ) : Cell

    /**
     * 公式。
     *
     * `cached` 是缓存值，**必须**与公式结果一致 —— 它是给不计算公式的阅读器
     * （WPS 的预览、部分手机端）看的兜底显示值，不是权威值。
     */
    data class Formula(
        val formula: String,
        val cached: Double,
        val format: NumberFormat = NumberFormat.PLAIN,
        val bold: Boolean = false,
    ) : Cell

    /** 空单元格。写出时整格省略 */
    data object Blank : Cell
}
