package com.dailyschedule.app.data.mapper

import com.dailyschedule.app.data.db.model.CategoryAmountRow
import com.dailyschedule.app.domain.model.CategoryAmount

fun CategoryAmountRow.toDomain(): CategoryAmount =
    CategoryAmount(
        categoryId = categoryId,
        categoryName = categoryName,
        colorHex = colorHex,
        iconName = iconName,
        totalCents = totalCents,
    )
