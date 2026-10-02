package com.dailyschedule.app.domain.repository

import com.dailyschedule.app.domain.model.AppPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observe(): Flow<AppPreferences>

    suspend fun get(): AppPreferences

    suspend fun setDayStartHour(hour: Int)

    suspend fun setWeekStartDay(day: Int)

    suspend fun setLastUsedProjectId(projectId: Long?)

    suspend fun setDefaultPomodoroMinutes(minutes: Int)

    suspend fun setThemeMode(mode: com.dailyschedule.app.core.model.ThemeMode)

    suspend fun setThemePack(pack: com.dailyschedule.app.core.model.ThemePack)

    /** 动态取色（Monet，Android 12+）。低版本写入也不报错，只是不生效。 */
    suspend fun setDynamicColor(enabled: Boolean)

    /** 写入前必须调用方先比对当前时区；返回是否发生了变化（D4-3） */
    suspend fun updateKnownZoneId(zoneId: String): Boolean

    /** 整体替换，供"导入备份"使用 */
    suspend fun replaceAll(preferences: AppPreferences)
}
