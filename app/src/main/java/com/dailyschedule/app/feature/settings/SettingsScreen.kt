package com.dailyschedule.app.feature.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.model.ThemeMode

/**
 * 设置页。
 *
 * 自己带 [Scaffold] + [TopAppBar]：它**不是**顶层 Tab（底栏只有待办/统计/记账），
 * 所以 `AppNavHost` 不会给它顶部栏。之前这里什么都没有 ——
 * 没有标题、没有返回箭头，用户只看到一屏开关，只能靠系统返回手势回去。
 *
 * [BackHandler] 与返回箭头指向同一个 [onBack]：既然页面上给了可见的返回入口，
 * 系统返回就必须做同一件事，否则两种"返回"行为不一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 外观 ──
            SettingsSection(title = stringResource(R.string.settings_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeMode.entries.forEach { mode ->
                    val labelRes =
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.preferences.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // 动态取色只在 Android 12+ 有意义；低版本显示禁用态 + 说明，
                // 而不是把这个开关整个藏起来 —— 藏起来用户会以为功能丢了。
                val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (supportsDynamic) {
                                        R.string.settings_dynamic_color_desc
                                    } else {
                                        R.string.settings_dynamic_color_unsupported
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = supportsDynamic && state.preferences.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                        enabled = supportsDynamic,
                    )
                }
            }

            // ── 日切与周起始 ──
            SettingsSection(title = stringResource(R.string.settings_day_start)) {
                Text(
                    text = stringResource(R.string.settings_day_start_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 日切只能选 0–6 点，超出这个范围的作息不需要日切
                    (0..6).forEach { hour ->
                        val selected = state.preferences.dayStartHour == hour
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { viewModel.setDayStartHour(hour) },
                            label = { Text("%02d:00".format(hour)) },
                        )
                    }
                }
            }

            // ── 数据 ──
            SettingsSection(title = stringResource(R.string.settings_data)) {
                SettingsRow(
                    text = stringResource(R.string.settings_data_transfer),
                    onClick = onOpenDataTransfer,
                )
                SettingsRow(
                    text = stringResource(R.string.settings_category_manage),
                    onClick = onOpenCategoryManager,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
