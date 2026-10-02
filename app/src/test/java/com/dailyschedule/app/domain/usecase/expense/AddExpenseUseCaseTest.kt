package com.dailyschedule.app.domain.usecase.expense

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.time.FakeClock
import com.dailyschedule.app.testutil.InMemoryRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddExpenseUseCaseTest {
    private val repos = InMemoryRepositories()
    private val clock = FakeClock(elapsedMs = 1000L, wallMs = 1_700_000_000_000L)
    private val useCase = AddExpenseUseCase(repos.expenseRepo, repos.categoryRepo, clock)

    init {
        runTest { repos.categoryRepo.ensureSeeded { it.toString() } }
    }

    @Test
    fun `金额为 0 被拒`() =
        runTest {
            val r = useCase(amountCents = 0, categoryId = 1L)
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `金额为负数被拒`() =
        runTest {
            val r = useCase(amountCents = -100, categoryId = 1L)
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `分类不存在被拒`() =
        runTest {
            val r = useCase(amountCents = 100, categoryId = 9999L)
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `已停用分类被拒`() =
        runTest {
            val cat = repos.categoryRepo.getById(1L)!!
            repos.categoryRepo.update(cat.copy(isEnabled = false))
            val r = useCase(amountCents = 100, categoryId = 1L)
            assertThat(r.isSuccess).isFalse()
        }

    @Test
    fun `合法输入成功写入并回填 id`() =
        runTest {
            val before = repos.expenseRepo.count()
            val r =
                useCase(
                    // 8.00 元
                    amountCents = 800,
                    categoryId = 1L,
                    note = "早餐",
                )
            assertThat(r.isSuccess).isTrue()
            val saved = (r as com.dailyschedule.app.core.result.AppResult.Success).value
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(saved.amountCents).isEqualTo(800L)
            assertThat(saved.type).isEqualTo(ExpenseType.EXPENSE)
            assertThat(saved.note).isEqualTo("早餐")
            assertThat(repos.expenseRepo.count()).isEqualTo(before + 1)
        }

    @Test
    fun `note 为空白串会被规范成 null`() =
        runTest {
            val r = useCase(amountCents = 100, categoryId = 1L, note = "   ")
            val saved = (r as com.dailyschedule.app.core.result.AppResult.Success).value
            assertThat(saved.note).isNull()
        }
}

private val com.dailyschedule.app.core.result.AppResult<*>.isSuccess: Boolean
    get() = this is com.dailyschedule.app.core.result.AppResult.Success
