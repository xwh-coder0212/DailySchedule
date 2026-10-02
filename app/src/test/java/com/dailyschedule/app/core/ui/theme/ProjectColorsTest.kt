package com.dailyschedule.app.core.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 卡片底色与字色的对比度判定。
 *
 * 待办卡片整块刷项目色之后，"字用什么颜色"从常量变成了函数。
 * 这一条守的是可读性下限：浅底配浅字等于没有字，而用户永远可能挑到浅色。
 */
class ProjectColorsTest {
    @Test
    fun `深色底用浅色字`() {
        assertThat(ProjectColors.prefersDarkContentOn("#0F6E56")).isFalse()
        assertThat(ProjectColors.prefersDarkContentOn("#185FA5")).isFalse()
        assertThat(ProjectColors.prefersDarkContentOn("#444441")).isFalse()
        assertThat(ProjectColors.prefersDarkContentOn("#993C1D")).isFalse()
        assertThat(ProjectColors.prefersDarkContentOn("#000000")).isFalse()
    }

    @Test
    fun `浅色底用深色字 —— 白字压白底等于没写字`() {
        assertThat(ProjectColors.prefersDarkContentOn("#EAF3DE")).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("#FFFFFF")).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("#FFFFFF00")).isTrue()
    }

    @Test
    fun `相对亮度看感知而不是 HSL —— 纯绿的 HSL 亮度与纯蓝相同，判定却相反`() {
        // HSL 的 L 对这两个都是 50%，但人眼对绿敏感得多，所以绿该配深字、蓝该配浅字
        assertThat(ProjectColors.prefersDarkContentOn("#00FF00")).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("#0000FF")).isFalse()
    }

    @Test
    fun `解析不出来时给深色字，浅色兜底底上至少读得见`() {
        assertThat(ProjectColors.prefersDarkContentOn(null)).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("")).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("#GGGGGG")).isTrue()
        assertThat(ProjectColors.prefersDarkContentOn("#XYZXYZ")).isTrue()
    }

    @Test
    fun `contentOn 与 prefersDarkContentOn 结论一致且落在 theme 定义的两个色上`() {
        val dark = ProjectColors.contentOn("#0F6E56")
        val light = ProjectColors.contentOn("#EAF3DE")

        assertThat(dark).isEqualTo(ProjectColors.ContentOnDark)
        assertThat(light).isEqualTo(ProjectColors.ContentOnLight)
    }
}
