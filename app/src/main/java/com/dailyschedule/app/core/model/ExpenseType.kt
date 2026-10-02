package com.dailyschedule.app.core.model

/**
 * 收支类型。
 *
 * MVP 界面只写 [EXPENSE]，但 DB 字段与枚举都预留 [INCOME]。
 * 成本接近零，将来加收入时不需要写数据库迁移。
 */
enum class ExpenseType {
    EXPENSE,
    INCOME,
    ;

    companion object {
        fun fromRaw(value: String?): ExpenseType? = entries.firstOrNull { it.name == value }
    }
}
