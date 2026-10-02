package com.dailyschedule.app.domain.usecase.expense

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.repository.ExpenseRepository
import javax.inject.Inject

class UpdateExpenseUseCase
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(updated: Expense): AppResult<Unit> {
            if (updated.amountCents <= 0) {
                return AppError.Validation("金额必须大于 0").asFailure()
            }
            val existing =
                expenseRepository.getById(updated.id)
                    ?: return AppError.NotFound("账目不存在或已被删除").asFailure()

            val toSave = updated.copy(updatedAt = clock.wallClockMillis())
            expenseRepository.update(toSave)
            AppLogger.i(TAG, "改账 id=${existing.id}")
            return Unit.asSuccess()
        }

        private companion object {
            const val TAG = "UpdateExpense"
        }
    }
