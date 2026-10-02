package com.dailyschedule.app.domain.model

/**
 * 消费（领域模型）。
 *
 * `amountCents` 是整数分。领域层不提供 `amount: Double` 便捷属性 ——
 * 一旦有了，就会有地方用它做加法，然后误差就来了。
 */
data class Expense(
    val id: Long = 0,
    val amountCents: Long,
    val currency: String = "CNY",
    val categoryId: Long,
    val projectId: Long? = null,
    val type: com.dailyschedule.app.core.model.ExpenseType =
        com.dailyschedule.app.core.model.ExpenseType.EXPENSE,
    val note: String? = null,
    /** 用户可改（补录是常态），默认写入时刻 */
    val occurredAt: Long,
    val createdAt: Long = occurredAt,
    val updatedAt: Long = occurredAt,
)
