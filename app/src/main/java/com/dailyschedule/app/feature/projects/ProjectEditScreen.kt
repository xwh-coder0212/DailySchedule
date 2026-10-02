package com.dailyschedule.app.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dailyschedule.app.R
import com.dailyschedule.app.core.ui.theme.ProjectColors

/** 预设图标名。MVP 只给 12 个通用图形，够用且不制造选择过载。 */
private val PRESET_ICONS = listOf(
    "book", "school", "code", "fitness_center",
    "language", "science", "draw", "music_note",
    "sports_esports", "work", "self_improvement", "flight_takeoff",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectEditScreen(
    projectId: Long?,
    onBack: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(ProjectColors.Palette.first()) }
    var iconName by remember { mutableStateOf(PRESET_ICONS.first()) }
    var targetText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(projectId == null) }

    // 编辑模式：载入既有项目
    LaunchedEffect(projectId) {
        if (projectId == null) return@LaunchedEffect
        val existing = viewModel.findProject(projectId) ?: return@LaunchedEffect
        name = existing.name
        colorHex = existing.colorHex
        iconName = existing.iconName
        targetText = existing.dailyTargetMinutes?.toString().orEmpty()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (projectId == null) R.string.project_create else R.string.project_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                label = { Text(stringResource(R.string.project_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            SectionTitle(stringResource(R.string.project_color_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProjectColors.Palette.forEach { hex ->
                    val selected = hex == colorHex
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                } else Modifier
                            )
                            .clickable { colorHex = hex },
                    )
                }
            }

            SectionTitle(stringResource(R.string.project_icon_label))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PRESET_ICONS.forEach { icon ->
                    val selected = icon == iconName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { iconName = icon }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = icon,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = targetText,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) targetText = it },
                label = { Text(stringResource(R.string.project_target_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Button(
                onClick = {
                    val target = targetText.toIntOrNull()?.takeIf { it in 1..1440 }
                    if (projectId == null) {
                        viewModel.create(name, iconName, colorHex, target)
                    } else {
                        viewModel.update(
                            com.dailyschedule.app.domain.model.Project(
                                id = projectId,
                                name = name,
                                iconName = iconName,
                                colorHex = colorHex,
                                dailyTargetMinutes = target,
                            )
                        )
                    }
                    onBack()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
