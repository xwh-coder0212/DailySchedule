package com.dailyschedule.app.data.mapper

import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.domain.model.Expense

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    amountCents = amountCents,
    currency = currency,
    categoryId = categoryId,
    projectId = projectId,
    type = type,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    amountCents = amountCents,
    currency = currency,
    categoryId = categoryId,
    projectId = projectId,
    type = type,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
