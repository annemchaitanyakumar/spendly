package com.spendly.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class InsightsUiState(
    val isLoading: Boolean = true,
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val summary: MonthSummary? = null
)

class InsightsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _month = MutableStateFlow(DateTimeUtils.currentYearMonth())
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        _month.flatMapLatest { (year, month) ->
            repository.observeMonthSummary(year, month)
                .map { summary ->
                    InsightsUiState(isLoading = false, currentYear = year, currentMonth = month, summary = summary)
                }
        }
        .onStart { emit(InsightsUiState(isLoading = true)) }
        .onEach { _state.value = it }
        .launchIn(viewModelScope)
    }

    fun setMonth(year: Int, month: Int) { _month.value = Pair(year, month) }
    fun previousMonth() {
        val (y, m) = _month.value
        if (m == 1) setMonth(y - 1, 12) else setMonth(y, m - 1)
    }
    fun nextMonth() {
        val (y, m) = _month.value
        val (ny, nm) = DateTimeUtils.currentYearMonth()
        if (y > ny || (y == ny && m >= nm)) return
        if (m == 12) setMonth(y + 1, 1) else setMonth(y, m + 1)
    }
}
