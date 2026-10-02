package com.dailyschedule.app.domain.usecase.expense

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import javax.inject.Inject

/**
 * 记录一笔消费。
 *
 * 校验：
 * - 金额必须 > 0（DB 没建 CHECK，业务层兜底）
 * - 分类必须存在且启用中
 *
 * `occurredAt` 允许传入（补录场景），未传则取 now。`createdAt/updatedAt` 一律 = now。
 */
class AddExpenseUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        amountCents: Long,
        categoryId: Long,
        projectId: Long? = null,
        note: String? = null,
        occurredAt: Long? = null,
        type: ExpenseType = ExpenseType.EXPENSE,
    ): AppResult<Expense> {
        if (amountCents <= 0) {
            return AppError.Validation("金额必须大于 0").asFailure()
        }
        if (amountCents > MAX_AMOUNT_CENTS) {
            return AppError.Validation("金额过大，请检查").asFailure()
        }
        val category = categoryRepository.getById(categoryId)
            ?: return AppError.NotFound("分类不存在").asFailure()
        if (!category.isEnabled) {
            return AppError.Validation("分类已停用，请重新选择").asFailure()
        }
        val occurred = occurredAt ?: clock.wallClockMillis()
        val now = clock.wallClockMillis()
        val expense = Expense(
            amountCents = amountCents,
            currency = "CNY",
            categoryId = categoryId,
            projectId = projectId,
            type = type,
            note = note?.takeIf { it.isNotBlank() },
            occurredAt = occurred,
            createdAt = now,
            updatedAt = now,
        )
        val id = expenseRepository.add(expense)
        AppLogger.i(TAG, "记账 id=$id amountCents=$amountCents categoryId=$categoryId")
        return expense.copy(id = id).asSuccess()
    }

    private companion object {
        const val TAG = "AddExpense"

        /** 100 万元（整数分）—— 大于任何真实单笔消费 */
        const val MAX_AMOUNT_CENTS = 100_000_000_00L
    }
}
