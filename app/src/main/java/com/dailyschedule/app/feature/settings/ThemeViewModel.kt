package com.dailyschedule.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.model.ThemeMode
import com.dailyschedule.app.core.model.ThemePack
import com.dailyschedule.app.domain.model.AppPreferences
import com.dailyschedule.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 只管主题，给 MainActivity 用。
 *
 * 与 [SettingsViewModel] 分开的理由：MainActivity 每次冷启动都会创建它，
 * 而设置页的 ViewModel 要管分类、导出这些重依赖。让冷启动路径上的对象
 * 只依赖 PreferencesRepository，别把 CategoryRepository 也拖进来。
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setThemePack(pack: ThemePack) {
        viewModelScope.launch { preferencesRepository.setThemePack(pack) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }
}
