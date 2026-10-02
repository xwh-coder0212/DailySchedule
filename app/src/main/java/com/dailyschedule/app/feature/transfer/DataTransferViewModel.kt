package com.dailyschedule.app.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.export.ExportTableFactory
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.data.export.XlsxExportWriter
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 导出结果。用密封类而不是一段拼好的字符串，是为了让界面层自己决定文案（可换语言）。 */
sealed interface ExportOutcome {
    data class Success(val fileName: String) : ExportOutcome
    data class Failure(val reason: String) : ExportOutcome
}

data class DataTransferUiState(
    /** 将写入「专注记录」表的行数（只含已结束会话） */
    val focusCount: Int = 0,
    /** 将写入「花费记录」表的行数 */
    val expenseCount: Int = 0,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val outcome: ExportOutcome? = null,
    /** 建议文件名的时间戳部分，界面拼成完整文件名 */
    val timestamp: String = "",
)

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val expenseRepository: ExpenseRepository,
    private val projectRepository: ProjectRepository,
    private val categoryRepository: CategoryRepository,
    private val writer: XlsxExportWriter,
    private val clock: Clock,
) : ViewModel() {

    /** 导出前先让用户看到「有多少条」，避免点下去才发现是空的 */
    private val preview = combine(
        sessionRepository.observeInRange(0L, Long.MAX_VALUE),
        expenseRepository.observeAll(),
    ) { sessions, expenses -> sessions.size to expenses.size }

    private val internal = MutableStateFlow(DataTransferUiState(timestamp = stampOf(clock.wallClockMillis())))

    val state: StateFlow<DataTransferUiState> = combine(preview, internal) { counts, ui ->
        ui.copy(
            focusCount = counts.first,
            expenseCount = counts.second,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DataTransferUiState())

    fun exportTo(uri: Uri) {
        if (internal.value.isExporting) return
        internal.value = internal.value.copy(isExporting = true, outcome = null)

        viewModelScope.launch {
            val result = runCatching {
                // 一次性读取全部数据。导出是「点一下就写完」的动作，
                // 不需要订阅 —— 用 first() 拿当前快照，避免导到一半数据又变了，
                // 造成「专注记录 40 条但合计只算了 39 条」这种自相矛盾的文件。
                val sessions = sessionRepository.observeInRange(0L, Long.MAX_VALUE).first()
                val expenses = expenseRepository.observeAll().first()
                val projectNames = projectRepository.observeAll().first().associate { it.id to it.name }
                val categoryNames = categoryRepository.observeAll().first().associate { it.id to it.name }
                val zoneId = ZoneId.systemDefault()

                val tables = listOf(
                    ExportTableFactory.focusTable(sessions, projectNames, zoneId),
                    ExportTableFactory.expenseTable(expenses, categoryNames, projectNames, zoneId),
                )
                writer.write(uri, tables)
            }

            internal.value = internal.value.copy(
                isExporting = false,
                outcome = result.fold(
                    onSuccess = { ExportOutcome.Success(it) },
                    onFailure = { error ->
                        AppLogger.e(TAG, "导出 Excel 失败", error)
                        ExportOutcome.Failure(
                            error.message ?: error::class.java.simpleName,
                        )
                    },
                ),
            )
        }
    }

    private fun stampOf(wallClockMs: Long): String = LocalDateTime
        .ofInstant(Instant.ofEpochMilli(wallClockMs), ZoneId.systemDefault())
        .format(STAMP_FORMAT)

    private companion object {
        const val TAG = "DataTransfer"

        /** 文件名里的时间戳。用系统时区，与用户看到的日期一致 */
        val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    }
}
