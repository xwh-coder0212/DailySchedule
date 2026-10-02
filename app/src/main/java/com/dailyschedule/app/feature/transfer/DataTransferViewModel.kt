package com.dailyschedule.app.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.BuildConfig
import com.dailyschedule.app.core.export.ExportTableFactory
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.transfer.BackupCodec
import com.dailyschedule.app.core.transfer.BackupCounts
import com.dailyschedule.app.core.transfer.BackupDocument
import com.dailyschedule.app.core.transfer.BackupRejection
import com.dailyschedule.app.core.transfer.BackupValidation
import com.dailyschedule.app.core.transfer.BackupValidator
import com.dailyschedule.app.data.export.XlsxExportWriter
import com.dailyschedule.app.data.transfer.JsonBackupStore
import com.dailyschedule.app.data.transfer.PreImportBackupKeeper
import com.dailyschedule.app.domain.model.RestoreOutcome
import com.dailyschedule.app.domain.model.RestoreReport
import com.dailyschedule.app.domain.repository.BackupRepository
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
import kotlinx.coroutines.CancellationException
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

/**
 * 恢复结果。同样只描述**发生了什么**，具体措辞交给界面。
 */
sealed interface ImportEvent {

    /** 文件不合法。**数据库一个字节都没动** */
    data class Rejected(val reason: BackupRejection) : ImportEvent

    data class RestoreSucceeded(
        val report: RestoreReport,
        /** 导入前的自动备份文件名，界面据此告诉用户"还能退回到哪一份" */
        val preImportBackupName: String,
        /** true 表示这次是「撤销上次导入」，文案不同 */
        val isUndo: Boolean,
    ) : ImportEvent

    /** 当前正在计时，拒绝恢复（详见 RestoreOutcome.ActiveSessionRunning） */
    data object BlockedByRunningSession : ImportEvent

    /** 想撤销，但本机已经没有任何导入前备份了 */
    data object NoLocalBackup : ImportEvent

    /** 读文件或落库过程出错 */
    data class Failed(val reason: String) : ImportEvent
}

/** 正在进行的耗时动作。用单值而不是两个 boolean，从类型上排除"同时导出又导入" */
enum class TransferBusy {
    /** 导出（JSON 备份 或 Excel）。两者不会同时进行，共用同一个状态 */
    EXPORTING,
    READING_BACKUP,
    RESTORING,
}

/**
 * 待用户确认的导入。**只放界面需要展示的摘要**，
 * 完整的 [BackupDocument] 留在 ViewModel 私有字段里 ——
 * 一份全量备份是几 MB 的对象，塞进 StateFlow 会让每次状态变化都去比较它。
 */
data class PendingImport(
    val fileName: String,
    val counts: BackupCounts,
    /** 备份是哪台机器、什么时候导的，用来让用户确认"选对文件了没有" */
    val exportedAtIso: String,
    val appVersion: String,
    /** 文件里正在计时的会话数。> 0 时界面要额外警告这些记录不会被恢复 */
    val activeSessions: Int,
    val isUndo: Boolean,
)

data class DataTransferUiState(
    /** 将写入「专注记录」表的行数（只含已结束会话） */
    val focusCount: Int = 0,
    /** 将写入「花费记录」表的行数 */
    val expenseCount: Int = 0,
    /** 会写进 JSON 备份的各表条数 */
    val backupCounts: BackupCounts = BackupCounts(0, 0, 0, 0),
    val isLoading: Boolean = true,
    val busy: TransferBusy? = null,
    val exportOutcome: ExportOutcome? = null,
    val importEvent: ImportEvent? = null,
    val pendingImport: PendingImport? = null,
    /** 本机可撤销的自动备份文件名，null 表示没有可撤销的东西 */
    val undoBackupName: String? = null,
    /** 建议文件名的时间戳部分，界面拼成完整文件名 */
    val timestamp: String = "",
) {
    /**
     * 有耗时动作在跑。
     *
     * 界面用它统一禁用所有按钮，而不是每个按钮各判一次 ——
     * 漏掉任何一个都会出现"一边恢复一边又点了导出"这种自相矛盾的操作。
     */
    val isBusy: Boolean get() = busy != null
}

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val expenseRepository: ExpenseRepository,
    private val projectRepository: ProjectRepository,
    private val categoryRepository: CategoryRepository,
    private val backupRepository: BackupRepository,
    private val writer: XlsxExportWriter,
    private val backupStore: JsonBackupStore,
    private val keeper: PreImportBackupKeeper,
    private val clock: Clock,
) : ViewModel() {

    /** 导出前先让用户看到「有多少条」，避免点下去才发现是空的 */
    private val preview = combine(
        sessionRepository.observeInRange(0L, Long.MAX_VALUE),
        expenseRepository.observeAll(),
    ) { sessions, expenses -> sessions.size to expenses.size }

    private val internal = MutableStateFlow(DataTransferUiState(timestamp = stampOf(clock.wallClockMillis())))

    /**
     * 待确认的备份文档。**不进 UI 状态**，理由见 [PendingImport] 的注释。
     * 用户取消确认时会被清掉，所以它不会跨会话一直占着内存。
     */
    private var pendingDocument: BackupDocument? = null

    val state: StateFlow<DataTransferUiState> = combine(
        preview,
        backupRepository.observeCounts(),
        internal,
    ) { counts, backupCounts, ui ->
        ui.copy(
            focusCount = counts.first,
            expenseCount = counts.second,
            backupCounts = backupCounts,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DataTransferUiState())

    init {
        viewModelScope.launch { refreshUndoName() }
    }

    // ── Excel 导出 ──

    fun exportTo(uri: Uri) {
        if (internal.value.busy != null) return
        internal.value = internal.value.copy(
            busy = TransferBusy.EXPORTING,
            exportOutcome = null,
            importEvent = null,
        )

        viewModelScope.launch {
            val result = catching {
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
                busy = null,
                exportOutcome = result.fold(
                    onSuccess = { ExportOutcome.Success(it) },
                    onFailure = { error ->
                        AppLogger.e(TAG, "导出 Excel 失败", error)
                        ExportOutcome.Failure(error.message ?: error::class.java.simpleName)
                    },
                ),
            )
        }
    }

    // ── JSON 备份：导出 ──

    fun exportBackupTo(uri: Uri) {
        if (internal.value.busy != null) return
        internal.value = internal.value.copy(
            busy = TransferBusy.EXPORTING,
            exportOutcome = null,
            importEvent = null,
        )

        viewModelScope.launch {
            val result = catching {
                val document = backupRepository.snapshot(BuildConfig.VERSION_NAME)
                backupStore.write(uri, BackupCodec.encode(document))
            }
            internal.value = internal.value.copy(
                busy = null,
                exportOutcome = result.fold(
                    onSuccess = { ExportOutcome.Success(it) },
                    onFailure = { error ->
                        AppLogger.e(TAG, "导出备份失败", error)
                        ExportOutcome.Failure(error.message ?: error::class.java.simpleName)
                    },
                ),
            )
        }
    }

    // ── JSON 备份：导入 ──

    /**
     * 用户选好了备份文件。这一步**只读、只校验，不动数据库**。
     *
     * 通过校验就转成"待确认"状态，由界面弹二次确认 ——
     * 导入是破坏性操作，不能在文件读完之后直接开干。
     */
    fun selectImportFile(uri: Uri) {
        if (internal.value.busy != null) return
        internal.value = internal.value.copy(
            busy = TransferBusy.READING_BACKUP,
            exportOutcome = null,
            importEvent = null,
            pendingImport = null,
        )

        viewModelScope.launch {
            val read = catching { backupStore.readText(uri) }
            val text = read.getOrElse { error ->
                failImport("读取备份文件失败", error)
                return@launch
            }
            prepare(text = text, fileName = uri.lastPathSegment.orEmpty(), isUndo = false)
        }
    }

    /** 用户确认替换。走到这里文件已经校验通过 */
    fun confirmImport() {
        val document = pendingDocument ?: return
        if (internal.value.busy != null) return
        val isUndo = internal.value.pendingImport?.isUndo == true
        internal.value = internal.value.copy(
            pendingImport = null,
            busy = TransferBusy.RESTORING,
            importEvent = null,
        )
        viewModelScope.launch { restore(document, isUndo) }
    }

    /** 用户在确认框里点了取消。什么都没发生，把待确认状态丢掉即可 */
    fun cancelImport() {
        pendingDocument = null
        internal.value = internal.value.copy(pendingImport = null)
    }

    /**
     * 用「导入前的自动备份」把数据退回去。
     *
     * 走的还是同一条路径（读取 → 校验 → 确认 → 替换），所以撤销本身
     * 也会先留一份当前状态的备份。多按两次可以沿着历史往回走 [KEEP] 步，
     * 而不是"撤销一次就没退路了"。
     */
    fun undoLastImport() {
        if (internal.value.busy != null) return
        internal.value = internal.value.copy(
            busy = TransferBusy.READING_BACKUP,
            exportOutcome = null,
            importEvent = null,
            pendingImport = null,
        )

        viewModelScope.launch {
            val name = internal.value.undoBackupName
            val read = catching { keeper.latest() }
            val text = read.getOrElse { error ->
                failImport("读取本机自动备份失败", error)
                return@launch
            }
            if (text == null) {
                internal.value = internal.value.copy(
                    busy = null,
                    importEvent = ImportEvent.NoLocalBackup,
                )
                return@launch
            }
            prepare(text = text, fileName = name.orEmpty(), isUndo = true)
        }
    }

    // ── 内部 ──

    /**
     * 解码 + 校验，通过就进入待确认状态。
     *
     * 解码失败一律报 [BackupRejection.NOT_A_BACKUP]：对用户来说
     * 「这个文件不是备份」和「这个文件坏了」是同一件事，
     * 而且这一层的失败**不会**碰数据库，措辞不需要区分得太细。
     */
    private suspend fun prepare(text: String, fileName: String, isUndo: Boolean) {
        val document = catching { BackupCodec.decode(text) }.getOrElse { error ->
            AppLogger.w(TAG, "备份文件解码失败：${error::class.java.simpleName}")
            internal.value = internal.value.copy(
                busy = null,
                importEvent = ImportEvent.Rejected(BackupRejection.NOT_A_BACKUP),
            )
            return
        }

        when (val validation = BackupValidator.validate(document)) {
            is BackupValidation.Rejected -> {
                // detail 只进日志：具体是哪个 id 重复了，对用户没有意义
                AppLogger.w(TAG, "备份校验未通过：${validation.reason} / ${validation.detail}")
                internal.value = internal.value.copy(
                    busy = null,
                    importEvent = ImportEvent.Rejected(validation.reason),
                )
            }

            is BackupValidation.Ok -> {
                pendingDocument = document
                internal.value = internal.value.copy(
                    busy = null,
                    pendingImport = PendingImport(
                        fileName = fileName,
                        counts = document.counts,
                        exportedAtIso = document.exportedAtIso,
                        appVersion = document.appVersion,
                        activeSessions = validation.activeSessions,
                        isUndo = isUndo,
                    ),
                )
            }
        }
    }

    /**
     * 真正动数据库：先给当前数据留一份本地备份，再整体替换。
     *
     * 顺序不能颠倒 —— 备份写失败就必须中止（[PreImportBackupKeeper.keep] 会抛错），
     * 否则用户会在一个没有退路的状态下完成清库。
     */
    private suspend fun restore(document: BackupDocument, isUndo: Boolean) {
        val result = catching {
            val snapshot = backupRepository.snapshot(BuildConfig.VERSION_NAME)
            val backupName = keeper.keep(BackupCodec.encode(snapshot), stampOf(clock.wallClockMillis()))
            backupName to backupRepository.replaceAll(document)
        }

        result.fold(
            onSuccess = { (backupName, outcome) ->
                when (outcome) {
                    is RestoreOutcome.Restored -> {
                        pendingDocument = null
                        internal.value = internal.value.copy(
                            busy = null,
                            importEvent = ImportEvent.RestoreSucceeded(
                                report = outcome.report,
                                preImportBackupName = backupName,
                                isUndo = isUndo,
                            ),
                        )
                        refreshUndoName()
                    }

                    RestoreOutcome.ActiveSessionRunning -> {
                        // 备份已经写了，但数据库没动。多一份自动备份无害
                        // （滚动保留 3 份），所以这里不做补偿删除 ——
                        // 补偿本身才是多余的风险。
                        internal.value = internal.value.copy(
                            busy = null,
                            importEvent = ImportEvent.BlockedByRunningSession,
                        )
                    }
                }
            },
            onFailure = { error -> failImport("恢复失败", error) },
        )
    }

    private fun failImport(what: String, error: Throwable) {
        AppLogger.e(TAG, "$what：${error.message}", error)
        internal.value = internal.value.copy(
            busy = null,
            importEvent = ImportEvent.Failed(error.message ?: error::class.java.simpleName),
        )
    }

    private suspend fun refreshUndoName() {
        val name = catching { keeper.latestName() }.getOrNull()
        internal.value = internal.value.copy(undoBackupName = name)
    }

    private fun stampOf(wallClockMs: Long): String = LocalDateTime
        .ofInstant(Instant.ofEpochMilli(wallClockMs), ZoneId.systemDefault())
        .format(STAMP_FORMAT)

    /**
     * 把「预期内的失败」收成 [Result]，但**不吞协程取消**。
     *
     * `runCatching` 会连 `CancellationException` 一起吞掉：界面退出时
     * 协程被取消，它会变成一个"失败结果"，然后我们可能还去写一次状态
     * （甚至弹一条"导出失败"给已经离开的用户）。取消不是失败，必须继续往外抛。
     */
    private inline fun <T> catching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val TAG = "DataTransfer"

        /** 文件名里的时间戳。用系统时区，与用户看到的日期一致 */
        val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    }
}
