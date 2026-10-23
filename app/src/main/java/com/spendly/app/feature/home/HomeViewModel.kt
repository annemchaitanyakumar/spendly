package com.spendly.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val monthSummary: MonthSummary? = null,
    val recentExpenses: List<Expense> = emptyList(),
    val pendingCandidates: List<TransactionCandidate> = emptyList(),
    val pendingCandidateCount: Int = 0,
    val cashFlowDays: List<DayCashFlow> = emptyList(),
    val error: String? = null,
    val aiCopilotTip: String? = null,
    val isGeneratingCopilotTip: Boolean = false
)

class HomeViewModel(
    private val repository: ExpenseRepository,
    private val settingsRepository: com.spendly.app.core.settings.AppSettingsRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(DateTimeUtils.currentYearMonth())
    private val _aiCopilotTip = MutableStateFlow<String?>(null)
    private val _isGeneratingCopilotTip = MutableStateFlow(false)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        combine(
            _selectedMonth,
            _aiCopilotTip,
            _isGeneratingCopilotTip
        ) { monthPair, tip, isGenerating ->
            Triple(monthPair, tip, isGenerating)
        }.flatMapLatest { (monthPair, tip, isGenerating) ->
            val (year, month) = monthPair
            combine(
                repository.observeMonthSummary(year, month),
                repository.observeRecentExpenses(12),
                repository.observePendingCandidates(),
                repository.observeMonthlyCashFlow(year, month)
            ) { summary, recent, candidates, cashFlow ->
                HomeUiState(
                    isLoading = false,
                    currentYear = year,
                    currentMonth = month,
                    monthSummary = summary,
                    recentExpenses = recent,
                    pendingCandidates = candidates,
                    pendingCandidateCount = candidates.size,
                    cashFlowDays = cashFlow,
                    aiCopilotTip = tip,
                    isGeneratingCopilotTip = isGenerating
                )
            }
        }
            .onStart {
                val (y, m) = _selectedMonth.value
                emit(HomeUiState(isLoading = true, currentYear = y, currentMonth = m))
            }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun dismissCandidate(candidateId: Long) {
        viewModelScope.launch {
            repository.dismissCandidate(candidateId)
        }
    }

    fun confirmCandidate(candidate: TransactionCandidate) {
        viewModelScope.launch {
            val isTransfer = candidate.recordType == RecordType.TRANSFER ||
                    candidate.merchant.contains("transfer", ignoreCase = true)
            val effectiveDirection = if (candidate.direction != TransactionDirection.UNKNOWN) {
                candidate.direction
            } else {
                TransactionDirection.DEBIT
            }
            val effectiveRecordType = if (isTransfer) {
                RecordType.TRANSFER
            } else if (candidate.recordType != RecordType.UNKNOWN) {
                candidate.recordType
            } else {
                RecordType.EXPENSE
            }

            val expense = Expense(
                amountMinor = candidate.amountMinor ?: 0L,
                currencyCode = candidate.currencyCode,
                direction = effectiveDirection,
                recordType = effectiveRecordType,
                merchant = candidate.merchant,
                categoryId = candidate.suggestedCategoryId ?: (if (effectiveRecordType == RecordType.TRANSFER) 13L else 20L),
                transactionAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                sourcePackage = candidate.sourcePackage,
                transactionRef = candidate.transactionRef,
                note = if (candidate.merchant.isNotBlank()) "Paid to ${candidate.merchant}" else "Detected payment",
                confirmationStatus = ConfirmationStatus.CONFIRMED
            )
            repository.confirmCandidate(candidate.id, expense)
        }
    }

    fun undoAutoLoggedExpense(candidate: TransactionCandidate) {
        viewModelScope.launch {
            candidate.expenseId?.let { expId ->
                repository.deleteExpenseById(expId)
            }
            repository.dismissCandidate(candidate.id)
        }
    }

    fun setMonth(year: Int, month: Int) {
        _selectedMonth.value = Pair(year, month)
    }

    fun previousMonth() {
        val (year, month) = _selectedMonth.value
        if (month == 1) setMonth(year - 1, 12)
        else setMonth(year, month - 1)
    }

    fun nextMonth() {
        val (year, month) = _selectedMonth.value
        val now = DateTimeUtils.currentYearMonth()
        if (year > now.first || (year == now.first && month >= now.second)) return
        if (month == 12) setMonth(year + 1, 1)
        else setMonth(year, month + 1)
    }

    fun canGoToNextMonth(): Boolean {
        val (year, month) = _selectedMonth.value
        val (nowYear, nowMonth) = DateTimeUtils.currentYearMonth()
        return year < nowYear || (year == nowYear && month < nowMonth)
    }

    fun refreshAiCopilotTip() {
        val summary = _state.value.monthSummary ?: return
        viewModelScope.launch {
            _isGeneratingCopilotTip.value = true
            val settings = settingsRepository.settingsFlow.first()
            val monthName = DateTimeUtils.formatMonthYear(_state.value.currentYear, _state.value.currentMonth)
            val topCats = summary.categoryBreakdown
                .sortedByDescending { it.totalMinor }
                .take(3)
                .joinToString(", ") { "${it.category?.name ?: "Other"}: ${com.spendly.app.core.common.CurrencyFormatter.formatMinor(it.totalMinor, "INR")}" }

            val tip = com.spendly.app.core.ai.AiAdvisorAgent.generateMonthlyAnalysis(
                apiKey = settings.geminiApiKey,
                monthName = monthName,
                totalSpendsFormatted = com.spendly.app.core.common.CurrencyFormatter.formatMinor(summary.totalMinor, "INR"),
                totalIncomeFormatted = com.spendly.app.core.common.CurrencyFormatter.formatMinor(summary.totalCreditsMinor, "INR"),
                netCashFlowFormatted = com.spendly.app.core.common.CurrencyFormatter.formatMinor(summary.netBalanceMinor, "INR"),
                topCategoriesSummary = topCats
            )
            _aiCopilotTip.value = tip
            _isGeneratingCopilotTip.value = false
        }
    }

    fun dismissCopilotTip() {
        _aiCopilotTip.value = null
    }
}
