package com.dailyschedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.core.model.ThemeMode
import com.dailyschedule.app.core.ui.theme.DailyScheduleTheme
import com.dailyschedule.app.feature.settings.ThemeViewModel
import com.dailyschedule.app.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val preferences by themeViewModel.preferences.collectAsStateWithLifecycle()

            // 三态：跟随系统 / 强制浅色 / 强制深色
            val darkTheme =
                when (preferences.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }

            DailyScheduleTheme(
                themePack = preferences.themePack,
                darkTheme = darkTheme,
                dynamicColor = preferences.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
