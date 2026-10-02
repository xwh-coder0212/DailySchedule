package com.dailyschedule.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dailyschedule.app.core.model.ThemeMode
import com.dailyschedule.app.core.model.ThemePack
import com.dailyschedule.app.domain.model.AppPreferences
import com.dailyschedule.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override fun observe(): Flow<AppPreferences> = dataStore.data.map(::read)

    override suspend fun get(): AppPreferences = dataStore.data.first().let(::read)

    override suspend fun setDayStartHour(hour: Int) {
        val safe = hour.coerceIn(
            AppPreferences.MIN_DAY_START_HOUR,
            AppPreferences.MAX_DAY_START_HOUR,
        )
        dataStore.edit { it[KEY_DAY_START_HOUR] = safe }
    }

    override suspend fun setWeekStartDay(day: Int) {
        val safe = day.coerceIn(1, 7)
        dataStore.edit { it[KEY_WEEK_START_DAY] = safe }
    }

    override suspend fun setLastUsedProjectId(projectId: Long?) {
        dataStore.edit { p ->
            if (projectId == null) p.remove(KEY_LAST_USED_PROJECT_ID)
            else p[KEY_LAST_USED_PROJECT_ID] = projectId
        }
    }

    override suspend fun setDefaultPomodoroMinutes(minutes: Int) {
        val safe = minutes.coerceIn(5, 120)
        dataStore.edit { it[KEY_POMODORO_MIN] = safe }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    override suspend fun setThemePack(pack: ThemePack) {
        dataStore.edit { it[KEY_THEME_PACK] = pack.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun updateKnownZoneId(zoneId: String): Boolean {
        var changed = false
        dataStore.edit { p ->
            val current = p[KEY_LAST_ZONE_ID]
            if (current != zoneId) {
                p[KEY_LAST_ZONE_ID] = zoneId
                changed = true
            }
        }
        return changed
    }

    override suspend fun replaceAll(preferences: AppPreferences) {
        dataStore.edit { p ->
            p[KEY_DAY_START_HOUR] = preferences.dayStartHour
            p[KEY_WEEK_START_DAY] = preferences.weekStartDay
            preferences.lastUsedProjectId?.let { p[KEY_LAST_USED_PROJECT_ID] = it }
                ?: p.remove(KEY_LAST_USED_PROJECT_ID)
            p[KEY_POMODORO_MIN] = preferences.defaultPomodoroMinutes
            p[KEY_THEME_MODE] = preferences.themeMode.name
            p[KEY_THEME_PACK] = preferences.themePack.name
            p[KEY_DYNAMIC_COLOR] = preferences.dynamicColor
            preferences.lastKnownZoneId?.let { p[KEY_LAST_ZONE_ID] = it }
                ?: p.remove(KEY_LAST_ZONE_ID)
        }
    }

    private fun read(p: Preferences): AppPreferences = AppPreferences(
        dayStartHour = (p[KEY_DAY_START_HOUR] ?: AppPreferences.DEFAULT_DAY_START_HOUR)
            .coerceIn(
                AppPreferences.MIN_DAY_START_HOUR,
                AppPreferences.MAX_DAY_START_HOUR,
            ),
        weekStartDay = (p[KEY_WEEK_START_DAY] ?: AppPreferences.DEFAULT_WEEK_START_DAY).coerceIn(1, 7),
        lastUsedProjectId = p[KEY_LAST_USED_PROJECT_ID],
        defaultPomodoroMinutes = (p[KEY_POMODORO_MIN] ?: AppPreferences.DEFAULT_POMODORO_MINUTES)
            .coerceIn(5, 120),
        themeMode = p[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        themePack = p[KEY_THEME_PACK]?.let { runCatching { ThemePack.valueOf(it) }.getOrNull() }
            ?: ThemePack.GRAPHITE_TEAL,
        dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
        lastKnownZoneId = p[KEY_LAST_ZONE_ID],
    )

    private companion object {
        val KEY_DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val KEY_WEEK_START_DAY = intPreferencesKey("week_start_day")
        val KEY_LAST_USED_PROJECT_ID = androidx.datastore.preferences.core.longPreferencesKey("last_used_project_id")
        val KEY_POMODORO_MIN = intPreferencesKey("default_pomodoro_minutes")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_PACK = stringPreferencesKey("theme_pack")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_LAST_ZONE_ID = stringPreferencesKey("last_known_zone_id")
    }
}
