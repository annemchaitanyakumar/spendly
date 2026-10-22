package com.spendly.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransactionsFilter(
    val year: Int = LocalDate.now().year,
    val month: Int = LocalDate.now().monthValue,
    val categoryId: Long? = null,
    val sourcePackage: String? = null,
    val searchQuery: String = ""
)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val groupedExpenses: Map<String, List<Expense>> = emptyMap(), // date label -> expenses
    val dailyTotals: Map<String, Long> = emptyMap(),
    val filter: TransactionsFilter = TransactionsFilter(),
    val categories: List<Category> = emptyList(),
    val deletedExpense: Expense? = null, // For undo
    val error: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionsFilter())
    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    init {
        observeData()
        viewModelScope.launch {
            val cats = repository.getAllCategories()
            _state.update { it.copy(categories = cats) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeData() {
        _filter
            .debounce(150)
            .flatMapLatest { filter ->
                val start = DateTimeUtils.monthStartMillis(filter.year, filter.month)
                val end = DateTimeUtils.monthEndMillis(filter.year, filter.month)

                if (filter.searchQuery.isNotBlank()) {
                    repository.searchExpenses(filter.searchQuery)
                } else {
                    repository.observeFiltered(
                        startMillis = start,
                        endMillis = end,
                        categoryId = filter.categoryId,
                        sourcePackage = filter.sourcePackage
                    )
                }
            }
            .map { expenses -> groupByDate(expenses) }
            .onStart { emit(Pair(emptyMap(), emptyMap())) }
            .onEach { (grouped, totals) ->
                _state.update { it.copy(
                    isLoading = false,
                    groupedExpenses = grouped,
                    dailyTotals = totals,
                    filter = _filter.value
                )}
            }
            .launchIn(viewModelScope)
    }

    private fun groupByDate(expenses: List<Expense>): Pair<Map<String, List<Expense>>, Map<String, Long>> {
        val grouped = expenses.groupBy { DateTimeUtils.formatRelativeDate(it.transactionAt) }
        val totals = grouped.mapValues { (_, list) ->
            list.filter { it.direction == TransactionDirection.DEBIT && it.recordType == RecordType.EXPENSE }
                .sumOf { it.amountMinor }
        }
        return Pair(grouped, totals)
    }

    fun setFilter(filter: TransactionsFilter) { _filter.value = filter }
    fun setSearchQuery(q: String) { _filter.update { it.copy(searchQuery = q) } }
    fun setMonth(year: Int, month: Int) { _filter.update { it.copy(year = year, month = month) } }
    fun setCategoryFilter(id: Long?) { _filter.update { it.copy(categoryId = id) } }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            _state.update { it.copy(deletedExpense = expense) }
            repository.deleteExpense(expense)
        }
    }

    fun undoDelete() {
        val expense = _state.value.deletedExpense ?: return
        viewModelScope.launch {
            repository.saveExpense(expense)
            _state.update { it.copy(deletedExpense = null) }
        }
    }
}

// Expose observeFiltered in repository
fun ExpenseRepository.observeFiltered(
    startMillis: Long,
    endMillis: Long,
    categoryId: Long?,
    sourcePackage: String?
): kotlinx.coroutines.flow.Flow<List<Expense>> {
    // Delegate to the DAO via the repository's existing exposed flows
    return observeMonthlyExpenses(
        year = java.time.Instant.ofEpochMilli(startMillis)
            .atZone(DateTimeUtils.systemZone).year,
        month = java.time.Instant.ofEpochMilli(startMillis)
            .atZone(DateTimeUtils.systemZone).monthValue
    ).map { list ->
        list.filter { expense ->
            (categoryId == null || expense.categoryId == categoryId) &&
            (sourcePackage == null || expense.sourcePackage == sourcePackage)
        }
    }
}
