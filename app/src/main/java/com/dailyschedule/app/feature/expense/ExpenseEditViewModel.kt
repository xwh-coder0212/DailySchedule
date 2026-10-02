package com.dailyschedule.app.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.money.AmountInput
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.usecase.expense.AddExpenseUseCase
import com.dailyschedule.app.domain.usecase.expense.UpdateExpenseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记账表单状态。
 *
 * `amountText` 是用户敲进去的原始字符串，"12.5" 而不是 1250。
 * 中间态保持用户看到的样子，避免"输入 12.5 显示 1250"这种错乱。
 */
data class ExpenseEditUiState(
    val amountText: String = "",
    val selectedCategoryId: Long? = null,
    val note: String = "",
    /**
     * 是否在编辑既有一笔。
     *
     * 这个布尔放在 state 里而不是写成 `original != null` 的 getter：
     * `original` 是个普通 var，改它不会触发重组，界面会一直停在「新增」的形态。
     */
    val isEditing: Boolean = false,
    /** "amount" / "category" / "unknown"，界面据此出提示文案 */
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    /** 新增模式：每成功落库一次 +1，界面弹一次「已记录」 */
    val savedTick: Int = 0,
    /** 编辑模式：保存成功，界面该退回列表了 */
    val finished: Boolean = false,
)

/**
 * 记账表单。新增与编辑共用。
 *
 * ## 两种模式的交互刻意不同
 * - **新增**：选分类即落库，没有保存按钮。这是 Phase 0 研究结论
 *   「记账失败的根因是摩擦」的直接落地，把 7 次点击压到 3 次。
 * - **编辑**：选分类只是选中，必须点「保存修改」。
 *   因为编辑常常要同时改金额和分类，点一下分类就写库会把中间态也写进去。
 */
@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val addExpense: AddExpenseUseCase,
    private val updateExpense: UpdateExpenseUseCase,
    private val clock: Clock,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _state = MutableStateFlow(ExpenseEditUiState())
    val state: StateFlow<ExpenseEditUiState> = _state.asStateFlow()

    /** 编辑模式下被改的那一笔。改的是它的副本，`id` / `createdAt` 原样带回去 */
    private var original: Expense? = null

    /** 进入编辑模式时把既有一笔读进表单。`id` 为 null 时什么都不做（新增模式） */
    fun load(expenseId: Long?) {
        if (expenseId == null) {
            original = null
            _state.value = ExpenseEditUiState()
            return
        }
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val existing = expenseRepository.getById(expenseId)
            if (existing == null) {
                original = null
                _state.value = ExpenseEditUiState(isLoading = false, notFound = true)
                return@launch
            }
            original = existing
            _state.value = ExpenseEditUiState(
                amountText = AmountInput.toEditableText(existing.amountCents),
                selectedCategoryId = existing.categoryId,
                note = existing.note.orEmpty(),
                isEditing = true,
                isLoading = false,
            )
        }
    }

    fun onKey(key: String) {
        val current = _state.value.amountText
        when {
            key == "." -> {
                // 不允许多个小数点，也不允许以小数点开头
                if (current.contains(".")) return
                _state.value = _state.value.copy(
                    amountText = if (current.isEmpty()) "0." else "$current.",
                    errorMessage = null,
                )
            }
            key == "del" -> {
                _state.value = _state.value.copy(
                    amountText = current.dropLast(1),
                    errorMessage = null,
                )
            }
            else -> {
                // 最多两位小数，总长不超过 9
                val dotIndex = current.indexOf('.')
                if (dotIndex >= 0 && current.length - dotIndex - 1 >= 2) return
                if (current.length >= 9) return
                // 避免 "0" 开头后直接跟数字（"05"）
                if (current == "0") {
                    _state.value = _state.value.copy(amountText = key, errorMessage = null)
                    return
                }
                _state.value = _state.value.copy(
                    amountText = current + key,
                    errorMessage = null,
                )
            }
        }
    }

    fun onNoteChange(note: String) {
        if (note.length <= MAX_NOTE_LENGTH) _state.value = _state.value.copy(note = note)
    }

    /**
     * 选中一个分类。
     *
     * 新增模式：直接落库。编辑模式：只改选中态，等「保存修改」。
     */
    fun onCategorySelected(categoryId: Long) {
        if (_state.value.isEditing) {
            _state.value = _state.value.copy(selectedCategoryId = categoryId, errorMessage = null)
            return
        }
        val amountCents = AmountInput.parseCents(_state.value.amountText)
        if (amountCents == null || amountCents <= 0L) {
            _state.value = _state.value.copy(errorMessage = ERROR_AMOUNT)
            return
        }
        viewModelScope.launch {
            val result = addExpense(
                amountCents = amountCents,
                categoryId = categoryId,
                note = _state.value.note.takeIf { it.isNotBlank() },
                occurredAt = clock.wallClockMillis(),
            )
            _state.value = when (result) {
                is AppResult.Success -> ExpenseEditUiState(
                    savedTick = _state.value.savedTick + 1,
                )
                is AppResult.Failure -> _state.value.copy(
                    errorMessage = result.error.toErrorKey(),
                )
            }
        }
    }

    /** 编辑模式下的「保存修改」 */
    fun saveEdits() {
        val current = original ?: return
        val amountCents = AmountInput.parseCents(_state.value.amountText)
        if (amountCents == null || amountCents <= 0L) {
            _state.value = _state.value.copy(errorMessage = ERROR_AMOUNT)
            return
        }
        val categoryId = _state.value.selectedCategoryId
        if (categoryId == null) {
            _state.value = _state.value.copy(errorMessage = ERROR_CATEGORY)
            return
        }
        viewModelScope.launch {
            val result = updateExpense(
                current.copy(
                    amountCents = amountCents,
                    categoryId = categoryId,
                    note = _state.value.note.takeIf { it.isNotBlank() },
                ),
            )
            _state.value = when (result) {
                is AppResult.Success -> _state.value.copy(finished = true, errorMessage = null)
                is AppResult.Failure -> _state.value.copy(errorMessage = result.error.toErrorKey())
            }
        }
    }

    private fun AppError.toErrorKey(): String = when (this) {
        is AppError.Validation -> ERROR_AMOUNT
        is AppError.NotFound -> ERROR_CATEGORY
        else -> ERROR_UNKNOWN
    }

    companion object {
        const val ERROR_AMOUNT = "amount"
        const val ERROR_CATEGORY = "category"
        const val ERROR_UNKNOWN = "unknown"

        private const val MAX_NOTE_LENGTH = 50
    }
}
