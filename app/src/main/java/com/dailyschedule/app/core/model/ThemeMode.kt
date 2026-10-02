package com.dailyschedule.app.core.model

/**
 * 主题模式。默认 [SYSTEM]，用户可在设置页手动锁定浅色/深色。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromRaw(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
