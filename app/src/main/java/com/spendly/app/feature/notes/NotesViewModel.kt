package com.spendly.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.Expense
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class SpendNoteItem(
    val id: Long,
    val title: String,
    val amountMinor: Long,
    val amountFormatted: String,
    val fullTextLine: String,
    val categoryName: String,
    val categoryIcon: String,
    val sourceAppName: String,
    val timeFormatted: String,
    val note: String,
    val rawExpense: Expense
)

data class DayNotesGroup(
    val dateHeading: String,
    val relativeLabel: String?,
    val dateLocalDate: LocalDate,
    val dayTotalMinor: Long,
    val dayTotalFormatted: String,
    val items: List<SpendNoteItem>
)

data class NotesUiState(
    val currentYear: Int = LocalDate.now().year,
    val currentMonth: Int = LocalDate.now().monthValue,
    val monthHeading: String = "",
    val searchQuery: String = "",
    val totalSpentMinor: Long = 0L,
    val totalSpendsCount: Int = 0,
    val dayGroups: List<DayNotesGroup> = emptyList(),
    val filteredDayGroups: List<DayNotesGroup> = emptyList(),
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(DateTimeUtils.currentYearMonth())
    private val _searchQuery = MutableStateFlow("")

    val state: StateFlow<NotesUiState> = combine(
        _selectedMonth,
        _searchQuery
    ) { monthPair, query ->
        Pair(monthPair, query)
    }.flatMapLatest { (monthPair, query) ->
        val (year, month) = monthPair
        repository.observeMonthlyExpenses(year, month).map { expenses ->
            buildUiState(year, month, query, expenses)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotesUiState(
            currentYear = DateTimeUtils.currentYearMonth().first,
            currentMonth = DateTimeUtils.currentYearMonth().second,
            monthHeading = formatMonthHeading(DateTimeUtils.currentYearMonth().first, DateTimeUtils.currentYearMonth().second),
            isLoading = true
        )
    )

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

    fun setMonth(year: Int, month: Int) {
        _selectedMonth.value = Pair(year, month)
    }

    fun canGoToNextMonth(): Boolean {
        val (year, month) = _selectedMonth.value
        val (nowYear, nowMonth) = DateTimeUtils.currentYearMonth()
        return year < nowYear || (year == nowYear && month < nowMonth)
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    /**
     * Generates clean formatted plain text notes matching user specification:
     *
     * October spends
     * Total: ₹1,599 (5 spends)
     *
     * Oct 22
     * Vegetables 100
     * Swiggy 250
     *
     * Oct 1
     * Vegetables 100
     */
    fun generatePlainTextNotes(): String {
        val currentState = state.value
        val sb = StringBuilder()

        sb.append(currentState.monthHeading).append("\n")
        sb.append("Total: ${CurrencyFormatter.formatMinor(currentState.totalSpentMinor)}")
        sb.append(" (${currentState.totalSpendsCount} spends)\n\n")

        val groupsToExport = if (currentState.searchQuery.isBlank()) {
            currentState.dayGroups
        } else {
            currentState.filteredDayGroups
        }

        if (groupsToExport.isEmpty()) {
            sb.append("No spends recorded.")
            return sb.toString()
        }

        groupsToExport.forEachIndexed { index, group ->
            sb.append(group.dateHeading)
            if (!group.relativeLabel.isNullOrBlank()) {
                sb.append(" (${group.relativeLabel})")
            }
            sb.append("\n")

            group.items.forEach { item ->
                // Clean user format: "Vegetables 100" (or "Vegetables ₹100")
                sb.append("${item.title} ${item.amountFormatted}\n")
            }

            if (index < groupsToExport.size - 1) {
                sb.append("\n")
            }
        }

        return sb.toString()
    }

    private fun buildUiState(
        year: Int,
        month: Int,
        query: String,
        expenses: List<Expense>
    ): NotesUiState {
        val monthHeading = formatMonthHeading(year, month)

        // Only include debit / spends (purchases and transfers)
        val spends = expenses.filter { it.isExpense }
        val totalSpent = spends.sumOf { it.amountMinor }
        val count = spends.size

        // Group by LocalDate
        val groupedByDate = spends.groupBy { DateTimeUtils.epochToLocalDate(it.transactionAt) }

        // Sort dates descending (latest day first: Oct 22, Oct 21, ..., Oct 1)
        val sortedDates = groupedByDate.keys.sortedDescending()

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        val dayGroups = sortedDates.map { date ->
            val dateExpenses = groupedByDate[date].orEmpty().sortedByDescending { it.transactionAt }
            val dayTotal = dateExpenses.sumOf { it.amountMinor }

            val dateHeading = date.format(dateFormatter) // e.g. "Oct 1", "Oct 22"
            val relativeLabel = when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> null
            }

            val items = dateExpenses.map { exp ->
                val title = exp.merchant.ifBlank { exp.note.ifBlank { "Spend" } }
                val amountStr = formatSpendAmount(exp.amountMinor)
                val fullTextLine = "$title $amountStr"
                val sourceName = resolveSourceApp(exp.sourcePackage)

                SpendNoteItem(
                    id = exp.id,
                    title = title,
                    amountMinor = exp.amountMinor,
                    amountFormatted = amountStr,
                    fullTextLine = fullTextLine,
                    categoryName = exp.categoryName ?: "General",
                    categoryIcon = exp.categoryIcon ?: "💸",
                    sourceAppName = sourceName,
                    timeFormatted = DateTimeUtils.formatTime(exp.transactionAt),
                    note = exp.note,
                    rawExpense = exp
                )
            }

            DayNotesGroup(
                dateHeading = dateHeading,
                relativeLabel = relativeLabel,
                dateLocalDate = date,
                dayTotalMinor = dayTotal,
                dayTotalFormatted = CurrencyFormatter.formatMinor(dayTotal),
                items = items
            )
        }

        val filteredGroups = if (query.isBlank()) {
            dayGroups
        } else {
            val q = query.trim().lowercase()
            dayGroups.mapNotNull { group ->
                val filteredItems = group.items.filter { item ->
                    item.title.lowercase().contains(q) ||
                    item.note.lowercase().contains(q) ||
                    item.categoryName.lowercase().contains(q) ||
                    item.amountFormatted.contains(q)
                }
                if (filteredItems.isNotEmpty()) {
                    group.copy(
                        items = filteredItems,
                        dayTotalMinor = filteredItems.sumOf { it.amountMinor },
                        dayTotalFormatted = CurrencyFormatter.formatMinor(filteredItems.sumOf { it.amountMinor })
                    )
                } else null
            }
        }

        return NotesUiState(
            currentYear = year,
            currentMonth = month,
            monthHeading = monthHeading,
            searchQuery = query,
            totalSpentMinor = totalSpent,
            totalSpendsCount = count,
            dayGroups = dayGroups,
            filteredDayGroups = filteredGroups,
            isLoading = false
        )
    }

    private fun formatMonthHeading(year: Int, month: Int): String {
        val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$monthName spends" // e.g. "October spends"
    }

    private fun formatSpendAmount(amountMinor: Long): String {
        val amount = amountMinor / 100.0
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.getDefault(), "₹%.0f", amount)
        } else {
            String.format(Locale.getDefault(), "₹%.2f", amount)
        }
    }

    private fun resolveSourceApp(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Manual"
        val lower = pkg.lowercase()
        return when {
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("nbu.paisa") || lower.contains("google") -> "Google Pay"
            lower.contains("paytm") -> "Paytm"
            lower.contains("kotak") || lower.contains("kbank") -> "Kotak"
            lower.contains("sbi") || lower.contains("lotus") -> "SBI"
            lower.contains("hdfc") -> "HDFC"
            lower.contains("icici") -> "ICICI"
            lower.contains("axis") -> "Axis"
            lower.contains("cred") -> "CRED"
            lower.contains("amazon") -> "Amazon Pay"
            lower.contains("messaging") || lower.contains("mms") -> "SMS"
            lower.contains("gm") || lower.contains("mail") -> "Gmail"
            else -> "Bank/App"
        }
    }
}
