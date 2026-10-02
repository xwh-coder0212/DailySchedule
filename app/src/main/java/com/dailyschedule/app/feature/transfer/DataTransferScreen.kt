package com.dailyschedule.app.feature.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyschedule.app.R
import com.dailyschedule.app.core.transfer.BackupCounts
import com.dailyschedule.app.core.transfer.BackupRejection

/** 系统文件选择器要认的 MIME。用标准的 xlsx 类型，用户能直接看到「另存为 Excel」 */
private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * 备份文件的 MIME。
 *
 * 导出时用它建议类型；导入时**不能只按它过滤** —— 国内网盘、文件管理器
 * 与聊天软件转发出来的 JSON 经常被标成 `text/plain` 甚至 `application/octet-stream`，
 * 只按 `application/json` 过滤会让用户"找不到自己刚导出的那个文件"。
 * 所以导入用 [IMPORT_MIMES]，把通配符也放进去，靠格式校验兜底。
 */
private const val JSON_MIME = "application/json"

private val IMPORT_MIMES = arrayOf(JSON_MIME, "text/plain", "*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(
    onBack: () -> Unit,
    viewModel: DataTransferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupFileName = stringResource(R.string.backup_export_file_name, state.timestamp)
    val excelFileName = stringResource(R.string.export_file_name, state.timestamp)

    // SAF：让用户自己决定存哪儿。App 不申请任何存储权限，
    // 拿到的是一次性的 content:// 写入授权，写完即失效。
    val exportBackupLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(JSON_MIME),
        ) { uri -> uri?.let(viewModel::exportBackupTo) }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::selectImportFile) }

    val exportExcelLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(XLSX_MIME),
        ) { uri -> uri?.let(viewModel::exportTo) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
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
            BusyRow(state.busy)

            // ── 1. 完整备份（可恢复） ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.backup_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    BackupCountsRows(state.backupCounts)

                    Button(
                        onClick = { exportBackupLauncher.launch(backupFileName) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_export_action))
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(IMPORT_MIMES) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_import_action))
                    }

                    // 撤销入口只在真有备份时出现。灰着显示一个永远点不动的按钮，
                    // 会让用户以为"功能坏了"；不显示则完全看不出有这个能力。
                    state.undoBackupName?.let { backupName ->
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.backup_undo_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = viewModel::undoLastImport,
                            enabled = !state.isBusy,
                        ) {
                            Text(
                                text =
                                    stringResource(R.string.backup_undo_action) +
                                        "（$backupName）",
                            )
                        }
                    }
                }
            }

            // ── 2. 最近一次操作的结果 ──
            state.exportOutcome?.let { outcome ->
                val success = outcome is ExportOutcome.Success
                OutcomeCard(
                    isSuccess = success,
                    text =
                        when (outcome) {
                            is ExportOutcome.Success -> stringResource(R.string.export_success, outcome.fileName)
                            is ExportOutcome.Failure -> stringResource(R.string.export_failed, outcome.reason)
                        },
                )
            }

            state.importEvent?.let { event ->
                when (event) {
                    is ImportEvent.Rejected ->
                        OutcomeCard(
                            isSuccess = false,
                            text =
                                stringResource(
                                    R.string.backup_rejected,
                                    stringResource(rejectionRes(event.reason)),
                                ),
                        )

                    is ImportEvent.RestoreSucceeded -> {
                        val counts = event.report.counts
                        OutcomeCard(
                            isSuccess = true,
                            text =
                                buildString {
                                    append(
                                        stringResource(
                                            if (event.isUndo) {
                                                R.string.backup_undo_success
                                            } else {
                                                R.string.backup_restore_success
                                            },
                                            counts.projects,
                                            counts.categories,
                                            counts.sessions,
                                            counts.expenses,
                                        ),
                                    )
                                    if (event.report.skippedActiveSessions > 0) {
                                        append("\n")
                                        append(
                                            stringResource(
                                                R.string.backup_restore_skipped,
                                                event.report.skippedActiveSessions,
                                            ),
                                        )
                                    }
                                    append("\n")
                                    append(
                                        stringResource(
                                            R.string.backup_restore_backup_hint,
                                            event.preImportBackupName,
                                        ),
                                    )
                                },
                        )
                    }

                    ImportEvent.BlockedByRunningSession ->
                        OutcomeCard(
                            isSuccess = false,
                            text = stringResource(R.string.backup_restore_blocked),
                        )

                    ImportEvent.NoLocalBackup ->
                        OutcomeCard(
                            isSuccess = false,
                            text = stringResource(R.string.backup_no_local_backup),
                        )

                    is ImportEvent.Failed ->
                        OutcomeCard(
                            isSuccess = false,
                            text = stringResource(R.string.backup_restore_failed, event.reason),
                        )
                }
            }

            // ── 3. Excel 报表 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.export_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.export_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.isLoading) {
                        Text(
                            text = stringResource(R.string.common_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CountRow(
                            label = stringResource(R.string.export_focus_sheet),
                            count = state.focusCount,
                            countFormat = R.string.export_count,
                        )
                        CountRow(
                            label = stringResource(R.string.export_expense_sheet),
                            count = state.expenseCount,
                            countFormat = R.string.export_count,
                        )
                    }

                    Button(
                        onClick = { exportExcelLauncher.launch(excelFileName) },
                        enabled = !state.isBusy && !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.export_action))
                    }
                }
            }

            // 必须显式说清 Excel 不是备份。否则用户很容易把「导出成功」
            // 理解成「数据安全了」，然后在换机时丢掉全部历史。
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.export_notice_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.export_notice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    // 二次确认。放在 Scaffold 外层：它是覆盖在整页之上的模态，
    // 不需要参与页面的滚动布局。
    state.pendingImport?.let { pending ->
        ImportConfirmDialog(
            pending = pending,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }
}

@Composable
private fun BusyRow(busy: TransferBusy?) {
    if (busy == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text =
                stringResource(
                    when (busy) {
                        TransferBusy.EXPORTING -> R.string.export_in_progress
                        TransferBusy.READING_BACKUP -> R.string.backup_reading
                        TransferBusy.RESTORING -> R.string.backup_restoring
                    },
                ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * 恢复前的二次确认。
 *
 * 这个对话框存在的唯一理由是：**导入是破坏性操作**。
 * 所以它必须回答用户三个问题，缺一个都不够：
 * 1. 我选的是哪个文件？（备份时间 + 版本号 + 文件名）
 * 2. 会写进去多少东西？（四张表的条数）
 * 3. 我现在的东西会怎样？（会被清空，但能撤销）
 */
@Composable
private fun ImportConfirmDialog(
    pending: PendingImport,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val counts = pending.counts
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (pending.isUndo) {
                        R.string.backup_confirm_undo_title
                    } else {
                        R.string.backup_confirm_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        if (pending.isUndo) {
                            stringResource(R.string.backup_confirm_undo_source, pending.fileName)
                        } else {
                            stringResource(
                                R.string.backup_confirm_source,
                                pending.exportedAtIso,
                                pending.appVersion,
                                pending.fileName,
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            R.string.backup_confirm_counts,
                            counts.projects,
                            counts.categories,
                            counts.sessions,
                            counts.expenses,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pending.activeSessions > 0) {
                    Text(
                        text =
                            stringResource(
                                R.string.backup_confirm_active_warning,
                                pending.activeSessions,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(R.string.backup_confirm_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_confirm_cancel))
            }
        },
    )
}

@Composable
private fun BackupCountsRows(counts: BackupCounts) {
    CountRow(
        label = stringResource(R.string.backup_count_projects),
        count = counts.projects,
        countFormat = R.string.backup_count_unit,
    )
    CountRow(
        label = stringResource(R.string.backup_count_categories),
        count = counts.categories,
        countFormat = R.string.backup_count_unit,
    )
    CountRow(
        label = stringResource(R.string.backup_count_sessions),
        count = counts.sessions,
        countFormat = R.string.backup_count_unit,
    )
    CountRow(
        label = stringResource(R.string.backup_count_expenses),
        count = counts.expenses,
        countFormat = R.string.backup_count_unit,
    )
}

/**
 * 一行「标签 —— 条数」。
 *
 * [countFormat] 由调用方给：Excel 那一段说的是「共 40 条」（对着一张完整的表），
 * 备份这一段说的是「40 条」（四行并列的清单）。用同一个格式串会让其中一段读起来别扭，
 * 而两段各写一个字符串、各写一份布局，又是同一段代码抄两遍。
 */
@Composable
private fun CountRow(
    label: String,
    count: Int,
    @StringRes countFormat: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(countFormat, count),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
        )
    }
}

@Composable
private fun OutcomeCard(
    isSuccess: Boolean,
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSuccess) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (isSuccess) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@StringRes
private fun rejectionRes(reason: BackupRejection): Int =
    when (reason) {
        BackupRejection.NOT_A_BACKUP -> R.string.backup_reject_not_a_backup
        BackupRejection.TOO_NEW -> R.string.backup_reject_too_new
        BackupRejection.TOO_OLD -> R.string.backup_reject_too_old
        BackupRejection.COUNT_MISMATCH -> R.string.backup_reject_count_mismatch
        BackupRejection.DUPLICATE_ID -> R.string.backup_reject_duplicate_id
        BackupRejection.DANGLING_REFERENCE -> R.string.backup_reject_dangling_reference
        BackupRejection.INVALID_VALUES -> R.string.backup_reject_invalid_values
    }
