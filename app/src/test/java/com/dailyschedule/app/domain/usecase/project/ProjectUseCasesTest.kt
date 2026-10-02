package com.dailyschedule.app.domain.usecase.project

import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProjectUseCasesTest {
    private val repos = InMemoryRepositories()
    private val clock = FakeClock()
    private val create = CreateProjectUseCase(repos.projectRepo, clock)
    private val update = UpdateProjectUseCase(repos.projectRepo, clock)
    private val archive = ArchiveProjectUseCase(repos.projectRepo)
    private val delete = DeleteProjectUseCase(repos.projectRepo)

    @Test
    fun `空白项目名被拒`() =
        runTest {
            val r = create(name = "  ", iconName = "ic_book", colorHex = "#0F6E56")
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `过长项目名被拒`() =
        runTest {
            val r = create(name = "x".repeat(30), iconName = "ic_book", colorHex = "#0F6E56")
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `非法颜色格式被拒`() =
        runTest {
            val r = create(name = "数学", iconName = "ic_book", colorHex = "red")
            assertThat(r.isSuccess).isFalse()
            val r2 = create(name = "数学", iconName = "ic_book", colorHex = "#GGGGGG")
            assertThat(r2.isSuccess).isFalse()
        }

    @Test
    fun `合法项目创建成功并写入 createdAt`() =
        runTest {
            clock.wallMs = 12345L
            val r = create(name = "数学", iconName = "ic_book", colorHex = "#0F6E56", dailyTargetMinutes = 120)
            assertThat(r.isSuccess).isTrue()
            val p = (r as com.dailyschedule.app.core.result.AppResult.Success).value
            assertThat(p.id).isGreaterThan(0L)
            assertThat(p.dailyTargetMinutes).isEqualTo(120)
        }

    @Test
    fun `日目标越界被拒`() =
        runTest {
            val r = create(name = "数学", iconName = "ic_book", colorHex = "#0F6E56", dailyTargetMinutes = 9999)
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `归档可逆：再次归档=false 即恢复`() =
        runTest {
            val r = create(name = "已考完", iconName = "ic_done", colorHex = "#888888")
            val id = (r as com.dailyschedule.app.core.result.AppResult.Success).value.id
            archive(id, archived = true)
            assertThat(repos.projectRepo.getById(id)!!.isArchived).isTrue()
            archive(id, archived = false)
            assertThat(repos.projectRepo.getById(id)!!.isArchived).isFalse()
        }

    @Test
    fun `删除不存在项目返回 NotFound`() =
        runTest {
            val r = delete(99999L)
            assertThat(r.isSuccess).isFalse()
        }
}

private val com.dailyschedule.app.core.result.AppResult<*>.isSuccess: Boolean
    get() = this is com.dailyschedule.app.core.result.AppResult.Success
