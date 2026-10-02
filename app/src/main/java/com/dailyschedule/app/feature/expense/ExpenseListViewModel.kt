package com.dailyschedule.app.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.core.time.DayBoundary
import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.usecase.expense.DeleteExpenseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 列表里的一行：账目 + 它的分类。
 *
 * 分类**可空**：分类被停用后仍然出现在历史账目里，而管理页允许把它删掉
 * （前提是还没有账挂着它，所以这里可空的真正场景是"分类先被删、账目后来才被读到"）。
 * 界面拿到 null 时显示「分类已删除」，不崩、也不把那一行藏起来 —— 钱花了就是花了。
 */
data class ExpenseRow(
    val expense: Expense,
    val category: Category?,
)

data class ExpenseListUiState(
    /** 用年月两个数字而不是预格式化字符串：VM 里不该出现中文文案，交给界面走资源 */
    val year: Int = 0,
    val month: Int = 0,
    val monthTotalCents: Long = 0L,
    val lastMonthTotalCents: Long = 0L,
    val rows: List<ExpenseRow> = emptyList(),
    /** 是否停在当前月。「下一月」按钮据此置灰 —— 未来还没有账 */
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * 记账列表。
 *
 * ## 为什么按月翻页，而不是只显示本月
 * 只显示本月的话，上个月的账在 App 内就彻底看不到了（导出是另一条路）。
 * 「较上月」这个对比也隐含了"月"是有意义的单位，那它就该能翻。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpenseListViewModel
    @Inject
    constructor(
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository,
        private val deleteExpense: DeleteExpenseUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        /** 0 = 当前月，1 = 上一个月，以此类推 */
        private val _monthOffset = MutableStateFlow(0)
        val monthOffset: StateFlow<Int> = _monthOffset.asStateFlow()

        val state: StateFlow<ExpenseListUiState> =
            combine(_monthOffset, preferencesRepository.observe()) { offset, prefs -> offset to prefs }
                .flatMapLatest { (offset, prefs) ->
                    val boundary = DayBoundary(prefs.dayStartHour, prefs.weekStartDay)
                    val currentDate = boundary.businessDateOf(clock.wallClockMillis())
                    val anchor = currentDate.minusMonths(offset.toLong())
                    val range = boundary.monthRangeOf(anchor)
                    val prevRange = boundary.monthRangeOf(anchor.minusMonths(1))

                    combine(
                        expenseRepository.observeInRange(
                            ExpenseType.EXPENSE,
                            range.first,
                            range.last + 1,
                        ),
                        expenseRepository.observeTotalCents(
                            ExpenseType.EXPENSE,
                            range.first,
                            range.last + 1,
                        ),
                        expenseRepository.observeTotalCents(
                            ExpenseType.EXPENSE,
                            prevRange.first,
                            prevRange.last + 1,
                        ),
                        categoryRepository.observeAll(),
                    ) { rows, total, prevTotal, categories ->
                        val categoryById = categories.associateBy { it.id }
                        ExpenseListUiState(
                            year = anchor.year,
                            month = anchor.monthValue,
                            monthTotalCents = total,
                            lastMonthTotalCents = prevTotal,
                            rows = rows.map { ExpenseRow(it, categoryById[it.categoryId]) },
                            canGoNext = offset > 0,
                            isLoading = false,
                        )
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ExpenseListUiState())

        fun showPreviousMonth() {
            if (_monthOffset.value < MAX_LOOKBACK_MONTHS) _monthOffset.value += 1
        }

        fun showNextMonth() {
            if (_monthOffset.value > 0) _monthOffset.value -= 1
        }

        fun delete(id: Long) {
            viewModelScope.launch { deleteExpense(id) }
        }

        private companion object {
            /** 往前最多翻 10 年。纯粹是给一个下界，避免用户手抖翻到 1970 年 */
            const val MAX_LOOKBACK_MONTHS = 120
        }
    }
