package com.dailyschedule.app.domain.usecase.expense

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.domain.repository.ExpenseRepository
import javax.inject.Inject

class DeleteExpenseUseCase
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
    ) {
        suspend operator fun invoke(id: Long): AppResult<Unit> {
            if (expenseRepository.getById(id) == null) {
                return AppError.NotFound("账目不存在或已被删除").asFailure()
            }
            expenseRepository.delete(id)
            AppLogger.i(TAG, "删账 id=$id")
            return Unit.asSuccess()
        }

        private companion object {
            const val TAG = "DeleteExpense"
        }
    }
