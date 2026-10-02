package com.dailyschedule.app.data.db.seed

import androidx.annotation.StringRes
import com.dailyschedule.app.R
import com.dailyschedule.app.data.db.entity.CategoryEntity

/**
 * 预置消费分类。
 *
 * 为什么预置而不是空表起步（D2-3）：首次打开就能记一笔，同时允许增删改。
 * 空表起步会让第一次使用卡在"我该建哪些分类"上，而多数人答不上来。
 *
 * `isPreset = true` 的分类在 UI 上不可删除，但可停用 —— 外键 RESTRICT 也在 DB 层兜底。
 *
 * ## 名字走 strings 而不是硬编码
 * 分类名写库后就是**数据**（用户可改名、可导出到 JSON），
 * 但它的初始来源是文案，所以从 `strings.xml` 解析，代码里不出现中文。
 * 解析在 Repository 层用 `resources.getString(res)` 完成。
 */
object PresetCategories {

    data class Seed(
        @param:StringRes val nameRes: Int,
        /** 图标名取自 Material Icons 命名，运行时映射到 ImageVector */
        val iconName: String,
        val colorHex: String,
    )

    val seeds: List<Seed> = listOf(
        Seed(R.string.preset_category_food, "restaurant", "#E0705A"),
        Seed(R.string.preset_category_transport, "directions_bus", "#3D84D6"),
        Seed(R.string.preset_category_shopping, "shopping_bag", "#E8A33D"),
        Seed(R.string.preset_category_entertainment, "sports_esports", "#9A6BD1"),
        Seed(R.string.preset_category_study, "menu_book", "#2FA37B"),
        Seed(R.string.preset_category_medical, "local_hospital", "#D9534F"),
        Seed(R.string.preset_category_other, "more_horiz", "#8A8F98"),
    )

    /** @param nameResolver 通常是 `resources::getString` */
    fun entities(nameResolver: (Int) -> String): List<CategoryEntity> =
        seeds.mapIndexed { index, seed ->
            CategoryEntity(
                name = nameResolver(seed.nameRes),
                iconName = seed.iconName,
                colorHex = seed.colorHex,
                isPreset = true,
                sortOrder = index,
            )
        }
}
