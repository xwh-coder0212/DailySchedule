package com.dailyschedule.app.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailyschedule.app.core.transfer.BackupCodec
import com.dailyschedule.app.testutil.BackupFixtures
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 导入前自动备份的滚动保留测试。
 *
 * 这个类守的是一条**退路**：导入会清空全库，唯一能退回来的就是这些文件。
 * 所以它必须在两种情况下都对：一是留得够（至少能连撤两次），
 * 二是不能无限涨（用户不会去清理私有目录，涨起来就是白占空间）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreImportBackupKeeperTest {

    private lateinit var context: Context
    private lateinit var keeper: PreImportBackupKeeper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keeper = PreImportBackupKeeper(context)
        // Robolectric 会给每个测试方法一个干净的 filesDir，这里显式清一次，
        // 免得同进程内不同用例互相看到对方的文件。
        backupDir().deleteRecursively()
    }

    @Test
    fun `没有任何备份时返回 null`() = runTest {
        assertThat(keeper.latest()).isNull()
        assertThat(keeper.latestName()).isNull()
    }

    @Test
    fun `存下来的内容可以原样读回`() = runTest {
        val json = BackupCodec.encode(BackupFixtures.document())

        val name = keeper.keep(json, "20261002_1200")

        assertThat(keeper.latest()).isEqualTo(json)
        assertThat(keeper.latestName()).isEqualTo(name)
    }

    @Test
    fun `超过保留份数时删掉最旧的`() = runTest {
        repeat(7) { index ->
            keeper.keep("{\"index\":$index}", "2026100${index + 1}_1200")
        }

        assertThat(backupFiles()).hasSize(EXPECTED_KEEP)
        // 留下的必须是**最近**三份：最旧那几份的内容不该还能被读到。
        // 只数文件个数而不看内容，会让"删错了头"这种 bug 全绿通过。
        assertThat(keeper.latest()).isEqualTo("{\"index\":6}")
    }

    @Test
    fun `可以连续撤销多次`() = runTest {
        // 留 3 份的意义就在这里：导入 A 发现不对、导入 B 又不对，
        // 用户还能沿路退回去，而不是"撤销一次就没退路了"。
        keeper.keep("state-1", "20261001_1200")
        keeper.keep("state-2", "20261002_1200")
        keeper.keep("state-3", "20261003_1200")

        assertThat(keeper.latest()).isEqualTo("state-3")

        // 再存一份（"撤销"本身也会先留一份当前状态），最旧的被挤掉
        keeper.keep("state-4", "20261004_1200")

        assertThat(backupFiles()).hasSize(EXPECTED_KEEP)
        assertThat(keeper.latest()).isEqualTo("state-4")
        assertThat(backupFiles().map { it.name }).doesNotContain("pre-import-20261001_1200.json")
    }

    @Test
    fun `不碰目录里不属于自己的文件`() = runTest {
        val stray = File(backupDir().apply { mkdirs() }, "something-else.json")
        stray.writeText("not mine")

        keeper.keep("mine", "20261002_1200")

        // 前缀是"哪些文件由本类负责"的唯一依据。没有它，
        // 一次滚动清理就可能顺手删掉别人的东西。
        assertThat(stray.exists()).isTrue()
    }

    private fun backupDir(): File = File(context.filesDir, "pre-import-backup")

    private fun backupFiles(): List<File> =
        backupDir().listFiles { f -> f.isFile && f.name.startsWith("pre-import-") }?.toList()
            ?: emptyList()

    private companion object {
        /** 与 PreImportBackupKeeper.KEEP 对应。写死是刻意的：这个数字变了，
         *  上面几条用例的语义（"至少能连撤两次"）就变了，应该有人重新读一遍它们 */
        const val EXPECTED_KEEP = 3
    }
}
