package com.spendly.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.ai.AiAdvisorAgent
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import com.spendly.app.core.settings.AppSettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InsightsUiState(
    val isLoading: Boolean = true,
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val summary: MonthSummary? = null,
    val aiAnalysis: String? = null,
    val isAnalyzing: Boolean = false,
    val isAiEnabled: Boolean = false
)

class InsightsViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _month = MutableStateFlow(DateTimeUtils.currentYearMonth())
    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        // Observe settings for AI availability
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.update { it.copy(isAiEnabled = settings.aiAgentEnabled && settings.geminiApiKey.isNotBlank()) }
            }
        }

        _month.flatMapLatest { (year, month) ->
            repository.observeMonthSummary(year, month)
                .map { summary ->
                    _state.value.copy(
                        isLoading = false,
                        currentYear = year,
                        currentMonth = month,
                        summary = summary
                    )
                }
        }
        .onStart { emit(_state.value.copy(isLoading = true)) }
        .onEach { _state.value = it }
        .launchIn(viewModelScope)
    }

    fun setMonth(year: Int, month: Int) {
        _month.value = Pair(year, month)
        _state.update { it.copy(aiAnalysis = null) }
    }

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

    fun generateAiAnalysis() {
        val summary = _state.value.summary ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val settings = settingsRepository.settingsFlow.first()
            val monthName = DateTimeUtils.formatMonthYear(_state.value.currentYear, _state.value.currentMonth)
            
            val topCats = summary.categoryBreakdown
                .sortedByDescending { it.totalMinor }
                .take(3)
                .joinToString(", ") { "${it.category?.name ?: "Other"}: ${CurrencyFormatter.formatMinor(it.totalMinor, "INR")}" }

            val analysis = AiAdvisorAgent.generateMonthlyAnalysis(
                apiKey = settings.geminiApiKey,
                monthName = monthName,
                totalSpendsFormatted = CurrencyFormatter.formatMinor(summary.totalMinor, "INR"),
                totalIncomeFormatted = CurrencyFormatter.formatMinor(summary.totalCreditsMinor, "INR"),
                netCashFlowFormatted = CurrencyFormatter.formatMinor(summary.netBalanceMinor, "INR"),
                topCategoriesSummary = topCats
            )

            _state.update { it.copy(aiAnalysis = analysis, isAnalyzing = false) }
        }
    }
}
