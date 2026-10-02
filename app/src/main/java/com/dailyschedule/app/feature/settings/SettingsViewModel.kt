package com.dailyschedule.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.R
import com.dailyschedule.app.core.model.ThemeMode
import com.dailyschedule.app.domain.model.AppPreferences
import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferencesRepository: PreferencesRepository,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        val state: StateFlow<SettingsUiState> =
            combine(
                preferencesRepository.observe(),
                categoryRepository.observeAll(),
            ) { prefs, categories ->
                SettingsUiState(preferences = prefs, categories = categories)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SettingsUiState())

        private val _message = MutableStateFlow<Pair<Int, Int>?>(null)

        /** 一次性提示（如"该分类下还有 N 笔账"）。UI 显示后调 [consumeMessage] 清掉。 */
        val message: StateFlow<Pair<Int, Int>?> = _message.asStateFlow()

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
        }

        fun setDayStartHour(hour: Int) {
            viewModelScope.launch { preferencesRepository.setDayStartHour(hour) }
        }

        fun setWeekStartDay(day: Int) {
            viewModelScope.launch { preferencesRepository.setWeekStartDay(day) }
        }

        fun toggleCategory(category: Category) {
            viewModelScope.launch {
                categoryRepository.update(category.copy(isEnabled = !category.isEnabled))
            }
        }

        fun addCategory(
            name: String,
            colorHex: String,
            iconName: String,
        ) {
            viewModelScope.launch {
                categoryRepository.create(
                    Category(name = name, colorHex = colorHex, iconName = iconName),
                )
            }
        }

        /**
         * 有账的分类删不掉 —— 外键 RESTRICT 在 DB 层兜底，这里给出可操作的提示。
         *
         * 存的是 `(stringResId, 账目数)` 而不是拼好的中文，
         * 文案与参数分离，代码里不出现中文（编码规范）。
         */
        fun deleteCategory(category: Category) {
            viewModelScope.launch {
                if (category.isPreset) return@launch
                val used = categoryRepository.countExpenses(category.id)
                if (used > 0) {
                    _message.value = R.string.category_in_use to used
                    return@launch
                }
                categoryRepository.delete(category.id)
            }
        }

        fun consumeMessage() {
            _message.value = null
        }
    }
