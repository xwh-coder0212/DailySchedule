package com.dailyschedule.app.domain.model

import com.dailyschedule.app.core.model.ThemeMode
import com.dailyschedule.app.core.model.ThemePack

/**
 * 用户偏好。**不建表**（Phase 4 §3.5），存 DataStore。
 *
 * `dayStartHour` 是全 App 最重要的一个数字：所有"今日/本周/本月"的口径都由它决定。
 * 导出 JSON 必须带上它，否则换机后历史统计会整体漂移。
 */
data class AppPreferences(
    /** 日切时刻。凌晨 4 点前的记录归属前一天 */
    val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    /** 1 = 周一 */
    val weekStartDay: Int = DEFAULT_WEEK_START_DAY,
    val lastUsedProjectId: Long? = null,
    val defaultPomodoroMinutes: Int = DEFAULT_POMODORO_MINUTES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePack: ThemePack = ThemePack.GRAPHITE_TEAL,
    /**
     * 是否用系统动态取色（Monet，Android 12+）。默认开。
     *
     * 与 [themePack] 是两个正交的轴，别混：
     * - [themePack] 决定**风格**（圆角、字号、以及动态取色关闭时的色板）
     * - 本开关只决定**颜色从哪来**（壁纸 or 主题包）
     *
     * 于是"卡通风 + 跟随壁纸配色"这种组合天然成立，不需要为它加枚举。
     * 低于 Android 12 时该值恒为无效，[com.dailyschedule.app.core.ui.theme.DailyScheduleTheme]
     * 会自动回落到 [themePack] 的色板。
     */
    val dynamicColor: Boolean = true,
    /** 用于检测时区变化并提示一次（D4-3） */
    val lastKnownZoneId: String? = null,
) {
    companion object {
        const val DEFAULT_DAY_START_HOUR = 4
        const val DEFAULT_WEEK_START_DAY = 1
        const val DEFAULT_POMODORO_MINUTES = 25

        /** 偏好的合法范围，越界即回落默认值，避免脏数据把日期计算搞崩 */
        const val MIN_DAY_START_HOUR = 0
        const val MAX_DAY_START_HOUR = 6
    }
}
