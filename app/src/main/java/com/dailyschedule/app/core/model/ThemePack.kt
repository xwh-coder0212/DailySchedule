package com.dailyschedule.app.core.model

/**
 * 主题风格包。MVP 只有 [GRAPHITE_TEAL]（石墨青），
 * 未来新增卡通风 / 简洁风 / 少女风 = 新增枚举 + 一个 ThemePack 实现，不动任何页面。
 */
enum class ThemePack {
    GRAPHITE_TEAL,
    ;

    companion object {
        fun fromRaw(value: String?): ThemePack = entries.firstOrNull { it.name == value } ?: GRAPHITE_TEAL
    }
}
