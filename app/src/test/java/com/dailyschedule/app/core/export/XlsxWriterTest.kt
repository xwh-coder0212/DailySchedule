package com.dailyschedule.app.core.export

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XLSX 写出器的结构测试。
 *
 * 这些断言守的是「文件能不能被 Excel 打开」这件事 —— 它不是视觉效果，
 * 而是**结构契约**：少一个部件、样式下标对不上、数字写成科学计数法，
 * 结果都是用户那边弹一个「文件已损坏」，而 App 这边毫无异常。
 * 所以每个部件、每种单元格类型都必须在这里被钉住。
 */
class XlsxWriterTest {
    @Test
    fun `部件齐全 —— 六个必需部件一个都不能少`() {
        val entries = zipEntries(writeBytes(listOf(sampleTable())))

        assertThat(entries.keys).containsAtLeast(
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml",
        )
    }

    @Test
    fun `多张表时每张都有独立的 worksheet 部件与超出声明`() {
        val tables = listOf(sampleTable(), sampleTable().copy(name = "花费记录"))
        val entries = zipEntries(writeBytes(tables))
        val contentTypes = entries.getValue("[Content_Types].xml")
        val workbook = entries.getValue("xl/workbook.xml")
        val rels = entries.getValue("xl/_rels/workbook.xml.rels")

        assertThat(entries.keys).contains("xl/worksheets/sheet2.xml")
        assertThat(contentTypes).contains("/xl/worksheets/sheet1.xml")
        assertThat(contentTypes).contains("/xl/worksheets/sheet2.xml")
        assertThat(workbook).contains("name=\"花费记录\" sheetId=\"2\" r:id=\"rId2\"")
        assertThat(rels).contains("Id=\"rId2\"")
        // 样式关系用最后一个 rId，不能和任何工作表的 rId 撞号
        assertThat(rels).contains("Id=\"rId3\"")
    }

    @Test
    fun `表头写在第一行且使用表头样式`() {
        val sheet = zipEntries(writeBytes(listOf(sampleTable()))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("<row r=\"1\">")
        assertThat(sheet).contains("<c r=\"A1\" t=\"inlineStr\" s=\"1\">")
        assertThat(sheet).contains("<t xml:space=\"preserve\">标题</t>")
        // 冻结首行：长列表滚下去以后仍知道每列是什么
        assertThat(sheet).contains("state=\"frozen\"")
    }

    @Test
    fun `数据行从第 2 行开始且列号按 A B C 递增`() {
        val sheet = zipEntries(writeBytes(listOf(sampleTable()))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("<row r=\"2\">")
        assertThat(sheet).contains("<c r=\"A2\"")
        assertThat(sheet).contains("<c r=\"B2\"")
    }

    @Test
    fun `筛选范围只覆盖数据行 —— 不含合计行`() {
        val table = sampleTable().copy(dataRowCount = 1)
        val sheet = zipEntries(writeBytes(listOf(table))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("<autoFilter ref=\"A1:B2\"/>")
    }

    @Test
    fun `没有数据行时不写筛选也不写合计`() {
        val table =
            ExportTable(
                name = "空表",
                headers = listOf("A", "B"),
                rows = emptyList(),
                columnWidths = listOf(8, 8),
                dataRowCount = 0,
            )
        val sheet = zipEntries(writeBytes(listOf(table))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).doesNotContain("autoFilter")
    }

    @Test
    fun `公式同时写出表达式与缓存值`() {
        val table =
            ExportTable(
                name = "表",
                headers = listOf("A"),
                rows = listOf(listOf(Cell.Formula("SUM(A2:A3)", cached = 95.0))),
                columnWidths = listOf(8),
                dataRowCount = 1,
            )
        val sheet = zipEntries(writeBytes(listOf(table))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("<f>SUM(A2:A3)</f>")
        assertThat(sheet).contains("<v>95</v>")
    }

    @Test
    fun `空单元格整格省略`() {
        val table =
            ExportTable(
                name = "表",
                headers = listOf("A", "B", "C"),
                rows = listOf(listOf(Cell.Text(""), Cell.Blank, Cell.Text("有值"))),
                columnWidths = listOf(8, 8, 8),
                dataRowCount = 1,
            )
        val sheet = zipEntries(writeBytes(listOf(table))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("<row r=\"2\"><c r=\"C2\"")
    }

    @Test
    fun `特殊字符被转义`() {
        val table =
            ExportTable(
                name = "表",
                headers = listOf("H"),
                rows = listOf(listOf(Cell.Text("a<b&c\"d'e"))),
                columnWidths = listOf(8),
                dataRowCount = 1,
            )
        val sheet = zipEntries(writeBytes(listOf(table))).getValue("xl/worksheets/sheet1.xml")

        assertThat(sheet).contains("a&lt;b&amp;c&quot;d&apos;e")
    }

    @Test
    fun `XML 不允许的控制字符被剔除 —— 备注是自由文本，粘进什么都可能`() {
        assertThat(XlsxWriter.escape("x\u0000y\u0001z")).isEqualTo("xyz")
        assertThat(XlsxWriter.escape("保\n留\n换行")).isEqualTo("保&#10;留&#10;换行")
    }

    @Test
    fun `数字用定点写法 —— 不能出现科学计数法`() {
        assertThat(XlsxWriter.decimal(1.0E7)).isEqualTo("10000000")
        assertThat(XlsxWriter.decimal(95.0)).isEqualTo("95")
        assertThat(XlsxWriter.decimal(47.5)).isEqualTo("47.5")
        assertThat(XlsxWriter.decimal(0.0)).isEqualTo("0")
        assertThat(XlsxWriter.decimal(Double.NaN)).isEqualTo("0")
    }

    @Test
    fun `列号换算覆盖单字母与多字母`() {
        assertThat(XlsxWriter.columnLetter(0)).isEqualTo("A")
        assertThat(XlsxWriter.columnLetter(7)).isEqualTo("H")
        assertThat(XlsxWriter.columnLetter(25)).isEqualTo("Z")
        assertThat(XlsxWriter.columnLetter(26)).isEqualTo("AA")
        assertThat(XlsxWriter.columnLetter(27)).isEqualTo("AB")
        assertThat(XlsxWriter.columnLetter(701)).isEqualTo("ZZ")
        assertThat(XlsxWriter.columnLetter(702)).isEqualTo("AAA")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `工作表名超过 31 字符被拒`() {
        writeBytes(listOf(sampleTable().copy(name = "超".repeat(32))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `工作表名含 Excel 禁止字符被拒`() {
        writeBytes(listOf(sampleTable().copy(name = "专注/记录")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `列宽数量与表头不一致被拒`() {
        writeBytes(listOf(sampleTable().copy(columnWidths = listOf(8))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `数据行列数与表头不一致被拒`() {
        writeBytes(
            listOf(sampleTable().copy(rows = listOf(listOf(Cell.Text("只有一个"))))),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `一张表都没有被拒`() {
        writeBytes(emptyList())
    }

    // ── 更深一层的结构不变量 ──

    @Test
    fun `每个部件都是合法 XML —— 结构错一处 Excel 就报文件损坏`() {
        val entries =
            zipEntries(
                writeBytes(listOf(sampleTable(), sampleTable().copy(name = "花费记录"))),
            )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        entries.forEach { (name, content) ->
            val parsed =
                runCatching {
                    factory.newDocumentBuilder().parse(
                        ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
                    )
                }
            assertWithMessage("部件 $name 不是合法 XML：${parsed.exceptionOrNull()}")
                .that(parsed.isSuccess)
                .isTrue()
        }
    }

    @Test
    fun `workbook 里引用的每个 rId 都在关系表里有定义 —— 缺一个就整包打不开`() {
        val entries =
            zipEntries(
                writeBytes(listOf(sampleTable(), sampleTable().copy(name = "花费记录"))),
            )
        val workbook = entries.getValue("xl/workbook.xml")
        val rels = entries.getValue("xl/_rels/workbook.xml.rels")

        val referenced = Regex("r:id=\"(rId\\d+)\"").findAll(workbook).map { it.groupValues[1] }.toSet()
        val defined = Regex("Id=\"(rId\\d+)\"").findAll(rels).map { it.groupValues[1] }.toSet()

        assertThat(referenced).isNotEmpty()
        assertThat(defined).containsAtLeastElementsIn(referenced)
    }

    @Test
    fun `样式下标表与声明数量一致 —— 下标漂了 Excel 不报错，只是显示错`() {
        val entries = zipEntries(writeBytes(listOf(sampleTable())))
        val styles = entries.getValue("xl/styles.xml")

        val declared =
            Regex("""<cellXfs count="(\d+)">""").find(styles)!!
                .groupValues[1].toInt()
        val actual = Regex("<xf ").findAll(styles.substringAfter("<cellXfs count=")).count()
        assertWithMessage("cellXfs 的 count 声明必须等于实际 xf 数量")
            .that(actual)
            .isEqualTo(declared)

        val used =
            Regex("<c r=\"[A-Z]+\\d+\"[^>]*?\\ss=\"(\\d+)\"")
                .findAll(entries.getValue("xl/worksheets/sheet1.xml"))
                .map { it.groupValues[1].toInt() }
                .toSet()
        assertThat(used).isNotEmpty()
        used.forEach { index ->
            assertWithMessage("工作表引用了不存在的样式下标 $index")
                .that(index)
                .isLessThan(declared)
        }
    }

    // ── 工具 ──

    private fun sampleTable() =
        ExportTable(
            name = "专注记录",
            headers = listOf("标题", "时长"),
            rows =
                listOf(
                    listOf(Cell.Text("高数"), Cell.Number(95.0, NumberFormat.ONE_DECIMAL)),
                    listOf(Cell.Text("英语"), Cell.Number(30.0, NumberFormat.ONE_DECIMAL)),
                ),
            columnWidths = listOf(8, 10),
            dataRowCount = 2,
        )

    private fun writeBytes(tables: List<ExportTable>): ByteArray {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(tables, out)
        return out.toByteArray()
    }

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return result
    }
}
