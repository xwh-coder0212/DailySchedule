package com.dailyschedule.app.core.transfer

import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.testutil.BackupFixtures
import com.dailyschedule.app.testutil.BackupFixtures.session
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 导入前校验测试。
 *
 * 这一层的每个用例都对应一种"如果放过它，用户会在数据已经没了之后才知道"的情形：
 * 截断的文件、重复的主键、不存在的外键、非法金额。
 * 所以这里不只断言"被拒绝"，还断言**拒绝理由是哪一类** ——
 * 理由搞错会让用户按错误的提示去修一个不存在的问题。
 */
class BackupValidatorTest {
    @Test
    fun `结构完整且引用自洽的备份通过`() {
        val document =
            BackupFixtures.document(
                categories = listOf(BackupFixtures.category(1), BackupFixtures.category(2)),
                projects = listOf(BackupFixtures.project(10, dailyTargetMinutes = 120)),
                sessions =
                    listOf(
                        session(100, projectId = 10),
                        // 项目被删过：projectId 为 null 是正常的历史数据，不是坏引用
                        session(101, projectId = null),
                    ),
                expenses =
                    listOf(
                        BackupFixtures.expense(200, categoryId = 1, projectId = 10),
                        BackupFixtures.expense(201, categoryId = 2, projectId = null),
                    ),
            )

        val result = BackupValidator.validate(document)

        assertThat(result).isInstanceOf(BackupValidation.Ok::class.java)
        assertThat((result as BackupValidation.Ok).activeSessions).isEqualTo(0)
    }

    @Test
    fun `活动会话被计数但不算拒绝`() {
        // 活动会话不会被恢复（理由见 BackupValidator 的注释），
        // 但文件本身是合法的 —— 报错会拦住一次本来完全正常的恢复。
        val document =
            BackupFixtures.document(
                sessions =
                    listOf(
                        session(1, projectId = 1),
                        session(2, projectId = 1, status = SessionStatus.RUNNING),
                        session(3, projectId = 1, status = SessionStatus.PAUSED),
                    ),
            )

        val result = BackupValidator.validate(document)

        assertThat((result as BackupValidation.Ok).activeSessions).isEqualTo(2)
    }

    @Test
    fun `format 不匹配时拒绝`() {
        val result =
            BackupValidator.validate(
                BackupFixtures.document(format = "some.other.app"),
            )

        assertThat(rejectionOf(result)).isEqualTo(BackupRejection.NOT_A_BACKUP)
    }

    @Test
    fun `结构版本比本版新时拒绝`() {
        // 放过它等于用旧代码去解释新字段：新版本加的非空字段会被当成缺失，
        // 或者更糟 —— 被静默丢掉。
        val result =
            BackupValidator.validate(
                BackupFixtures.document(schemaVersion = BackupCodec.SCHEMA_VERSION + 1),
            )

        assertThat(rejectionOf(result)).isEqualTo(BackupRejection.TOO_NEW)
    }

    @Test
    fun `结构版本小于 1 时拒绝`() {
        val result = BackupValidator.validate(BackupFixtures.document(schemaVersion = 0))

        assertThat(rejectionOf(result)).isEqualTo(BackupRejection.TOO_OLD)
    }

    @Test
    fun `声明的条数与实际不符时拒绝 —— 截断的文件`() {
        // 网盘同步到一半、传输中断，都会留下一个"仍是合法 JSON、只是短了一截"的文件。
        // 没有这条校验，用户会以为恢复成功，然后发现三个月的历史只剩一半。
        val document =
            BackupFixtures.document(
                sessions = listOf(session(1, projectId = 1), session(2, projectId = 1)),
                counts = BackupCounts(projects = 1, categories = 1, sessions = 5, expenses = 1),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.COUNT_MISMATCH)
    }

    @Test
    fun `同一张表里 id 重复时拒绝`() {
        // 主键冲突会在事务写到一半时发生。虽然会回滚，但那时候
        // 用户已经盯着进度条等了很久，而问题在动数据库之前就能查出来。
        val document =
            BackupFixtures.document(
                projects = listOf(BackupFixtures.project(7), BackupFixtures.project(7, name = "重名")),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.DUPLICATE_ID)
    }

    @Test
    fun `会话引用了不存在的项目时拒绝`() {
        val document =
            BackupFixtures.document(
                projects = listOf(BackupFixtures.project(1)),
                sessions = listOf(session(1, projectId = 999)),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.DANGLING_REFERENCE)
    }

    @Test
    fun `消费引用了不存在的分类时拒绝`() {
        val document =
            BackupFixtures.document(
                categories = listOf(BackupFixtures.category(1)),
                expenses = listOf(BackupFixtures.expense(1, categoryId = 999)),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.DANGLING_REFERENCE)
    }

    @Test
    fun `消费引用了不存在的项目时拒绝`() {
        val document =
            BackupFixtures.document(
                projects = listOf(BackupFixtures.project(1)),
                expenses = listOf(BackupFixtures.expense(1, categoryId = 1, projectId = 999)),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.DANGLING_REFERENCE)
    }

    @Test
    fun `金额不大于 0 时拒绝`() {
        // 整数分是权威值，0 分或负数没有业务含义。
        // 放过它的后果不是崩溃，而是统计里多一笔永远算不清的账。
        val document =
            BackupFixtures.document(
                expenses = listOf(BackupFixtures.expense(1, categoryId = 1, amountCents = 0L)),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.INVALID_VALUES)
    }

    @Test
    fun `已完成的会话没有时长时拒绝`() {
        // 夹具对 COMPLETED 会自动补上时长，所以这里手工构造一个矛盾组合，
        // 模拟被手改过的文件。
        val broken = BackupFixtures.session(1, projectId = 1).copy(durationMs = null)
        val document = BackupFixtures.document(sessions = listOf(broken))

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.INVALID_VALUES)
    }

    @Test
    fun `项目名称为空时拒绝`() {
        val document =
            BackupFixtures.document(
                projects = listOf(BackupFixtures.project(1, name = "  ")),
            )

        assertThat(rejectionOf(BackupValidator.validate(document)))
            .isEqualTo(BackupRejection.INVALID_VALUES)
    }

    @Test
    fun `拒绝时会带上可供排查的细节`() {
        val document =
            BackupFixtures.document(
                projects = listOf(BackupFixtures.project(1)),
                sessions = listOf(session(42, projectId = 999)),
            )

        val rejected = BackupValidator.validate(document) as BackupValidation.Rejected

        // detail 只进日志，但必须指得出是哪一条，否则排查等于大海捞针
        assertThat(rejected.detail).contains("42")
        assertThat(rejected.detail).contains("999")
    }

    private fun rejectionOf(result: BackupValidation): BackupRejection? = (result as? BackupValidation.Rejected)?.reason
}
