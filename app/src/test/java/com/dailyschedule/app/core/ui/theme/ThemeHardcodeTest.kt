package com.dailyschedule.app.core.ui.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * 源码扫描：页面里不许硬编码颜色。
 *
 * ## 为什么需要它
 * 将来要换主题风格（卡通 / 简洁 / 少女）。只要有一处颜色写死在页面里，
 * 那处就不会跟着变，界面会出现"一半新风格一半旧风格"。
 * 靠人自觉守不住，所以让测试来守。
 *
 * ## 规则
 * - `core/ui/theme` 包内**允许**硬编码（那里就是定义色板的地方）
 * - 其它任何地方出现 `Color(0x…)` 即失败
 * - 用户自定义的项目色/分类色走 `ProjectColors.parse(hex)`，
 *   它的兜底色定义在 theme 包内，不违反本规则
 */
class ThemeHardcodeTest {
    @Test
    fun `页面不得硬编码颜色字面量`() {
        val srcRoot = File("src/main/java")
        assertWithMessage("找不到源码目录，测试的工作目录可能不对：${srcRoot.absolutePath}")
            .that(srcRoot.exists())
            .isTrue()

        val violations =
            srcRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.invariantSeparatorsPath.contains("core/ui/theme") }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                        if (!isComment && COLOR_LITERAL.containsMatchIn(trimmed)) {
                            "${file.invariantSeparatorsPath}:${index + 1}: $trimmed"
                        } else {
                            null
                        }
                    }
                }
                .toList()

        assertWithMessage(
            "发现 ${violations.size} 处硬编码颜色。请改用 MaterialTheme.colorScheme.* " +
                "语义 token，或把色值移入 core/ui/theme：\n" +
                violations.joinToString("\n"),
        )
            .that(violations)
            .isEmpty()
    }

    @Test
    fun `ThemePack 枚举与实现一一对应`() {
        // 新增枚举值但忘了写实现时，spec() 会抛异常 —— 这条测试提前抓住它
        com.dailyschedule.app.core.model.ThemePack.entries.forEach { pack ->
            val spec = pack.spec()
            assertThat(spec.light).isNotNull()
            assertThat(spec.dark).isNotNull()
        }
    }

    private companion object {
        val COLOR_LITERAL = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}""")
    }
}
