package com.dailyschedule.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dailyschedule.app.core.model.ExpenseType

/**
 * 消费。两张事实表之一。
 *
 * `amountCents` 是**整数分**，永不使用浮点存钱。
 *
 * `projectId` 可选关联（D1-2）：默认不选，需要时手动挂到项目上，
 * 这样才能算出"考研总投入 = 128h32m + ¥680"。
 *
 * `categoryId` 外键 RESTRICT：**分类只能停用，不能删除**，
 * 删除前必须把该分类下的消费转移走，避免留下无分类的账。
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("occurredAt"),
        Index(value = ["categoryId", "occurredAt"]),
        Index("projectId"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 整数分。必须 > 0，由 UseCase 校验 */
    val amountCents: Long,
    val currency: String = "CNY",
    val categoryId: Long,
    val projectId: Long? = null,
    val type: ExpenseType = ExpenseType.EXPENSE,
    val note: String? = null,
    /** 用户可改（补录场景是常态），默认写入时刻 */
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
