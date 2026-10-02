package com.dailyschedule.app.core.export

import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 最小 XLSX 写出器。**零第三方依赖**（只用 `java.util.zip`）。
 *
 * ## 为什么不引 Apache POI
 * POI 需要 `java.awt`（画图/字体度量），在 Android 上直接抛
 * `NoClassDefFoundError`；poi-ooxml-android 分支维护滞后且体积数 MB。
 * 而本项目要写的东西只有「若干行纯文本 + 几个数字 + 一行 SUM」，
 * 手写 OOXML 的代码量比引入 POI 的胶水代码还少。
 *
 * ## 一个 .xlsx 到底是什么
 * 就是一个 ZIP，里面装着：
 * ```
 * [Content_Types].xml            告诉 Excel 每种后缀是什么部件
 * _rels/.rels                    包根关系 → xl/workbook.xml
 * xl/workbook.xml                工作表清单（名字 + rId）
 * xl/_rels/workbook.xml.rels     每个 rId 指向哪个文件
 * xl/styles.xml                  字体/填充/边框/数字格式
 * xl/worksheets/sheetN.xml       真正的单元格数据
 * ```
 * 这六类部件齐了，Excel / WPS / LibreOffice 都能打开。**不写 `sharedStrings.xml`**：
 * 字符串用 `t="inlineStr"` 内联，省掉一张共享表，写法简单且所有阅读器都认。
 *
 * ## 日期为什么写成字符串
 * OOXML 的日期是「自 1899-12-30 起的天数」这种序列值，还要处理 1900 闰年 bug
 * 与时区。写错一点点，用户在 Excel 里看到的日期就整体偏移一天。
 * 导出场景下日期只需要「可读 + 可排序」，`2026-09-10` 这种 ISO 字符串
 * 在 Excel 里既看得懂，按文本排序也等于按时间排序。故一律写字符串。
 */
object XlsxWriter {

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_DOC_REL =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_CONTENT_TYPES =
        "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val CT_SHEET =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    /** 工作表名里不允许出现的字符（Excel 硬约束，写了会让文件直接打不开） */
    private val ILLEGAL_SHEET_CHARS = charArrayOf('[', ']', ':', '*', '?', '/', '\\')

    /**
     * 写出一份多工作表的 xlsx。
     *
     * 不关闭 [out]（由调用方负责）—— 这里只 `finish()`，方便调用方把
     * 同一个流再包别的处理，也避免「谁关流」这种扯不清的约定。
     */
    fun write(tables: List<ExportTable>, out: OutputStream) {
        require(tables.isNotEmpty()) { "至少要有一张工作表" }
        require(tables.size <= MAX_SHEETS) { "工作表数量超过 Excel 上限 $MAX_SHEETS" }
        tables.forEach(::validate)

        val zip = ZipOutputStream(out)
        zip.put("[Content_Types].xml", contentTypes(tables.size))
        zip.put("_rels/.rels", rootRels())
        zip.put("xl/workbook.xml", workbook(tables))
        zip.put("xl/_rels/workbook.xml.rels", workbookRels(tables.size))
        zip.put("xl/styles.xml", STYLES)
        tables.forEachIndexed { index, table ->
            zip.put("xl/worksheets/sheet${index + 1}.xml", sheet(table))
        }
        zip.finish()
    }

    private fun validate(table: ExportTable) {
        require(table.name.length in 1..MAX_SHEET_NAME) {
            "工作表名长度必须在 1..$MAX_SHEET_NAME 之间：'${table.name}'"
        }
        require(table.name.none { it in ILLEGAL_SHEET_CHARS }) {
            "工作表名含 Excel 禁止的字符：'${table.name}'"
        }
        require(table.headers.isNotEmpty()) { "工作表 '${table.name}' 没有表头" }
        require(table.columnWidths.size == table.headers.size) {
            "工作表 '${table.name}' 的列宽数量与表头不一致"
        }
        require(table.rows.all { it.size == table.headers.size }) {
            "工作表 '${table.name}' 存在与表头列数不一致的数据行"
        }
        require(table.dataRowCount in 0..table.rows.size) {
            "工作表 '${table.name}' 的 dataRowCount 超出数据行数"
        }
    }

    // ── 包部件 ──

    private fun contentTypes(sheetCount: Int): String = buildString {
        append(XML_DECL)
        append("<Types xmlns=\"$NS_CONTENT_TYPES\">")
        append("<Default Extension=\"rels\" ")
        append("ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append(
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/" +
                "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        )
        append(
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/" +
                "vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        )
        for (i in 1..sheetCount) {
            append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"$CT_SHEET\"/>")
        }
        append("</Types>")
    }

    private fun rootRels(): String = buildString {
        append(XML_DECL)
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        append(
            "<Relationship Id=\"rId1\" Type=\"$NS_DOC_REL/officeDocument\" " +
                "Target=\"xl/workbook.xml\"/>"
        )
        append("</Relationships>")
    }

    private fun workbook(tables: List<ExportTable>): String = buildString {
        append(XML_DECL)
        append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_DOC_REL\"><sheets>")
        tables.forEachIndexed { index, table ->
            val id = index + 1
            append(
                "<sheet name=\"${escape(table.name)}\" sheetId=\"$id\" r:id=\"rId$id\"/>"
            )
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append(XML_DECL)
        append("<Relationships xmlns=\"$NS_PKG_REL\">")
        for (i in 1..sheetCount) {
            append(
                "<Relationship Id=\"rId$i\" Type=\"$NS_DOC_REL/worksheet\" " +
                    "Target=\"worksheets/sheet$i.xml\"/>"
            )
        }
        // styles.xml 用最后一个 rId，避免与工作表 rId 撞号
        append(
            "<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$NS_DOC_REL/styles\" " +
                "Target=\"styles.xml\"/>"
        )
        append("</Relationships>")
    }

    // ── 工作表 ──

    private fun sheet(table: ExportTable): String = buildString {
        append(XML_DECL)
        append("<worksheet xmlns=\"$NS_MAIN\">")
        append("<sheetViews><sheetView workbookViewId=\"0\">")
        append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        append("</sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"15\"/>")

        append("<cols>")
        table.columnWidths.forEachIndexed { index, width ->
            val n = index + 1
            append("<col min=\"$n\" max=\"$n\" width=\"$width\" customWidth=\"1\"/>")
        }
        append("</cols>")

        append("<sheetData>")
        append("<row r=\"1\">")
        table.headers.forEachIndexed { index, header ->
            append(cellXml(index, rowNumber = 1, cell = Cell.Text(header), isHeader = true))
        }
        append("</row>")

        table.rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 2
            append("<row r=\"$r\">")
            row.forEachIndexed { index, cell ->
                append(cellXml(index, rowNumber = r, cell = cell, isHeader = false))
            }
            append("</row>")
        }
        append("</sheetData>")

        if (table.dataRowCount > 0) {
            val lastColumn = columnLetter(table.headers.size - 1)
            val lastRow = table.dataRowCount + 1
            append("<autoFilter ref=\"A1:$lastColumn$lastRow\"/>")
        }
        append("</worksheet>")
    }

    private fun cellXml(columnIndex: Int, rowNumber: Int, cell: Cell, isHeader: Boolean): String {
        val ref = "${columnLetter(columnIndex)}$rowNumber"
        if (isHeader) {
            val text = (cell as? Cell.Text)?.value.orEmpty()
            return inlineString(ref, text, STYLE_HEADER)
        }
        return when (cell) {
            is Cell.Text -> {
                // 空串直接省略整格：省体积，也让 Excel 的「定位空值」行为符合直觉
                if (cell.value.isEmpty()) {
                    ""
                } else {
                    inlineString(ref, cell.value, if (cell.bold) STYLE_TOTAL_TEXT else STYLE_DEFAULT)
                }
            }

            is Cell.Number ->
                "<c r=\"$ref\" s=\"${numberStyle(cell.format, cell.bold)}\"><v>" +
                    "${decimal(cell.value)}</v></c>"

            is Cell.Formula ->
                "<c r=\"$ref\" s=\"${numberStyle(cell.format, cell.bold)}\"><f>" +
                    "${escape(cell.formula)}</f><v>${decimal(cell.cached)}</v></c>"

            Cell.Blank -> ""
        }
    }

    private fun inlineString(ref: String, value: String, style: Int): String =
        "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\">" +
            "<is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"

    private fun numberStyle(format: NumberFormat, bold: Boolean): Int = when (format) {
        NumberFormat.PLAIN -> STYLE_DEFAULT
        NumberFormat.ONE_DECIMAL -> if (bold) STYLE_TOTAL_DECIMAL1 else STYLE_DECIMAL1
        NumberFormat.TWO_DECIMAL -> if (bold) STYLE_TOTAL_DECIMAL2 else STYLE_DECIMAL2
    }

    // ── 工具 ──

    /**
     * Double → OOXML 数字字面量。
     *
     * 不用 `toString()`：Kotlin/Java 对 Double 会用科学计数法（`1.0E7`），
     * OOXML 里这是非法数字。整数直接去小数点，其余定点六位再去尾零。
     * 全程 `Locale.ROOT`，否则某些地区会把小数点写成逗号，Excel 直接读成 0。
     */
    internal fun decimal(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val rounded = Math.rint(value)
        if (value == rounded && kotlin.math.abs(value) < 1e15) {
            return rounded.toLong().toString()
        }
        val fixed = String.format(Locale.ROOT, "%.6f", value)
        return fixed.trimEnd('0').trimEnd('.')
    }

    /** 0 → A，25 → Z，26 → AA。 */
    internal fun columnLetter(index: Int): String {
        require(index >= 0) { "列下标不能为负：$index" }
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.append(('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    /**
     * XML 文本转义。
     *
     * 控制字符必须剔除：XML 1.0 不允许 `\u0000`–`\u001F`（`\t \n` 除外），
     * 留着会让整个文件变成「无法解析」，而不是只坏一格。
     * 备注字段是自由文本，用户粘贴进任何东西都有可能，所以这一步不能省。
     */
    internal fun escape(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\n' -> sb.append("&#10;")
                ch == '\t' -> sb.append("&#9;")
                ch.code < 0x20 -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ── 样式下标，与下面 STYLES 里的 cellXfs 顺序一一对应 ──
    private const val STYLE_DEFAULT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_DECIMAL1 = 2
    private const val STYLE_DECIMAL2 = 3
    private const val STYLE_TOTAL_DECIMAL2 = 4
    private const val STYLE_TOTAL_DECIMAL1 = 5
    private const val STYLE_TOTAL_TEXT = 6

    private const val MAX_SHEETS = 255
    private const val MAX_SHEET_NAME = 31

    /** numFmtId 164 起是「自定义格式」区间，内置只到 163，不会撞车。 */
    private val STYLES = buildString {
        append(XML_DECL)
        append("<styleSheet xmlns=\"$NS_MAIN\">")
        append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.0\"/></numFmts>")
        append("<fonts count=\"2\">")
        append("<font><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/></font>")
        append("<font><b/><sz val=\"11\"/><color theme=\"1\"/><name val=\"Calibri\"/></font>")
        append("</fonts>")
        // index 0 必须是 none、index 1 必须是 gray125 —— 这是 Excel 的既有约定，
        // 顺序错了部分阅读器会报「文件已损坏」
        append("<fills count=\"3\">")
        append("<fill><patternFill patternType=\"none\"/></fill>")
        append("<fill><patternFill patternType=\"gray125\"/></fill>")
        append(
            "<fill><patternFill patternType=\"solid\">" +
                "<fgColor rgb=\"FFEDEDED\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        )
        append("</fills>")
        append("<borders count=\"2\">")
        append("<border><left/><right/><top/><bottom/><diagonal/></border>")
        append(
            "<border><left/><right/><top/>" +
                "<bottom style=\"thin\"><color rgb=\"FFB0B0B0\"/></bottom><diagonal/></border>"
        )
        append("</borders>")
        append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
        append("<cellXfs count=\"7\">")
        append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
        append(
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" " +
                "applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">" +
                "<alignment horizontal=\"center\"/></xf>"
        )
        append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>")
        append("<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>")
        append(
            "<xf numFmtId=\"2\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" " +
                "applyNumberFormat=\"1\" applyFont=\"1\" applyBorder=\"1\"/>"
        )
        append(
            "<xf numFmtId=\"164\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" " +
                "applyNumberFormat=\"1\" applyFont=\"1\" applyBorder=\"1\"/>"
        )
        append(
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"1\" xfId=\"0\" " +
                "applyFont=\"1\" applyBorder=\"1\"/>"
        )
        append("</cellXfs>")
        append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
        append("</styleSheet>")
    }
}
