package com.dailyschedule.app.core.transfer

import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.testutil.BackupFixtures
import com.dailyschedule.app.testutil.BackupFixtures.session
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Test

/**
 * 备份文件编解码测试。
 *
 * 重点全在**解码要严格**上。
 *
 * ## 为什么这里必须"不宽容"
 * 项目里其它地方读数据是宽容的：`Converters.toSessionSource` 遇到不认识的值
 * 回落到 TIMER，因为一条元数据读不出来不该让整行历史消失。
 * 备份解码正好相反 —— 它后面紧跟着一次**清库**。一个字段没读懂就被"宽容"过去，
 * 代价是用户的数据。所以未知字段、缺失字段、未知枚举值一律抛异常，
 * 而且抛在动数据库之前。
 */
class BackupCodecTest {
    @Test
    fun `编码再解码得到完全相同的文档`() {
        val original =
            BackupFixtures.document(
                categories = listOf(BackupFixtures.category(1), BackupFixtures.category(2)),
                projects =
                    listOf(
                        BackupFixtures.project(10, dailyTargetMinutes = 180),
                        BackupFixtures.project(11, isArchived = true, dailyTargetMinutes = null),
                    ),
                sessions =
                    listOf(
                        session(100, projectId = 10, note = "做题"),
                        session(101, projectId = null, status = SessionStatus.DISCARDED),
                        session(102, projectId = 11, source = SessionSource.MANUAL),
                    ),
                expenses =
                    listOf(
                        BackupFixtures.expense(200, categoryId = 1, projectId = 10, amountCents = 129_900L),
                        BackupFixtures.expense(201, categoryId = 2),
                    ),
            )

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `可空字段的 null 往返后仍然是 null 而不是被省略`() {
        // explicitNulls = false 时，null 会被整个字段省掉，
        // 于是"没有备注"和"这份文件里根本没有备注字段"长得一模一样。
        val original =
            BackupFixtures.document(
                sessions = listOf(session(1, projectId = null, note = null)),
            )

        val text = BackupCodec.encode(original)

        assertThat(text).contains("\"note\": null")
        assertThat(BackupCodec.decode(text)).isEqualTo(original)
    }

    @Test
    fun `输出是可读的多行文本`() {
        // 用户可能自己打开看。单行几 MB 的 JSON 谁也看不懂，
        // 也就无从判断"这份文件到底是哪天的、有没有我的数据"。
        val text = BackupCodec.encode(BackupFixtures.document())

        assertThat(text.lines().size).isGreaterThan(10)
        assertThat(text).contains("\"format\": \"${BackupCodec.FORMAT_ID}\"")
    }

    @Test
    fun `空对象解码失败 —— 绝不能被当成一份空备份`() {
        // 这是整个文件里最重要的一条断言。
        // 如果 DTO 的字段带默认值，`{}` 会解码成 counts 全 0、data 全空的文档，
        // 而校验会全过（没有记录就没有引用问题）—— 结果就是导入一个空 JSON
        // 把用户的数据清空，并提示"恢复成功"。
        val failure = runCatching { BackupCodec.decode("{}") }

        assertThat(failure.isFailure).isTrue()
        assertThat(failure.exceptionOrNull()).isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `缺少必需字段时解码失败`() {
        val text =
            BackupCodec.encode(BackupFixtures.document())
                .replaceFirst("\"schemaVersion\": ${BackupCodec.SCHEMA_VERSION},", "")

        val failure = runCatching { BackupCodec.decode(text) }

        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `出现未知字段时解码失败`() {
        // ignoreUnknownKeys = true 会让"别的 App 的 JSON"静默解码成一个
        // 字段残缺的对象。宁可当场报错，也不要猜。
        val text =
            BackupCodec.encode(BackupFixtures.document())
                .replaceFirst("{", "{\n  \"somethingElse\": 1,")

        val failure = runCatching { BackupCodec.decode(text) }

        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `未知的会话状态解码失败`() {
        // Dao 层遇到不认识的状态会回落到默认值（为了不让整行读不出来），
        // 备份层必须相反：一个出现在文件里、本版代码不认识的状态，
        // 说明这份文件来自别的版本或被人改过，写回去会变成第三个状态。
        val text =
            BackupCodec.encode(
                BackupFixtures.document(
                    sessions = listOf(session(1, projectId = 1, status = SessionStatus.COMPLETED)),
                ),
            ).replace("\"COMPLETED\"", "\"FINISHED\"")

        val failure = runCatching { BackupCodec.decode(text) }

        assertThat(failure.isFailure).isTrue()
    }

    @Test
    fun `中文与非法字符原样往返`() {
        // 备注里可能有换行、引号、emoji。编码器负责转义，解码器负责还原，
        // 中间任何一步丢一个字符，用户的记录就变了意思。
        val note = "第 1 章\n\"极限\"的定义 🚀"
        val original =
            BackupFixtures.document(
                sessions = listOf(session(1, projectId = 1, note = note)),
            )

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertThat(decoded.data.sessions.single().note).isEqualTo(note)
    }
}
