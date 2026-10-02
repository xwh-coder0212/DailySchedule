package com.dailyschedule.app.feature.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.ui.component.DsNumberPad
import com.dailyschedule.app.domain.model.Category

/**
 * 记账表单：新增与编辑共用。
 *
 * 布局顺序刻意是「金额 → 分类 → 键盘」：手指从键盘抬起后，
 * 最近的下一个可点区域就是分类。
 *
 * 新增模式下点完分类即完成，没有"保存"按钮；
 * 编辑模式下点分类只是选中，底部多一个「保存修改」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    expenseId: Long?,
    onBack: () -> Unit,
    viewModel: ExpenseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(expenseId) { viewModel.load(expenseId) }

    // 编辑保存成功即退回列表 —— 编辑是"处理某一笔"，办完就该走。
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    val savedText = stringResource(R.string.record_saved)
    LaunchedEffect(state.savedTick) {
        // savedTick 从 0 起步，所以首次进入不会弹；之后每落库一次弹一次。
        if (state.savedTick > 0) snackbarHostState.showSnackbar(savedText)
    }

    val errorText =
        state.errorMessage?.let { key ->
            stringResource(
                when (key) {
                    ExpenseEditViewModel.ERROR_AMOUNT -> R.string.expense_error_amount
                    ExpenseEditViewModel.ERROR_CATEGORY -> R.string.expense_error_category
                    else -> R.string.expense_error_unknown
                },
            )
        }
    LaunchedEffect(errorText) {
        if (errorText != null) snackbarHostState.showSnackbar(errorText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) {
                                R.string.expense_edit_title
                            } else {
                                R.string.expense_add
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.notFound -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.expense_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.record_amount_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (state.amountText.isEmpty()) "0" else state.amountText,
                            style =
                                MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                ),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = viewModel::onNoteChange,
                            label = { Text(stringResource(R.string.record_note_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    Text(
                        text =
                            stringResource(
                                if (state.isEditing) {
                                    R.string.expense_pick_category
                                } else {
                                    R.string.record_tap_category
                                },
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(categories, key = { it.id }) { category ->
                            CategoryTile(
                                category = category,
                                selected = state.isEditing && state.selectedCategoryId == category.id,
                                onClick = { viewModel.onCategorySelected(category.id) },
                            )
                        }
                    }

                    if (state.isEditing) {
                        Button(
                            onClick = viewModel::saveEdits,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.expense_save_changes))
                        }
                    }

                    DsNumberPad(
                        onKey = viewModel::onKey,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            runCatching {
                                Color(android.graphics.Color.parseColor(category.colorHex))
                            }.getOrDefault(MaterialTheme.colorScheme.primary),
                        ),
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}
