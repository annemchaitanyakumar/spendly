package com.spendly.app.feature.expenseform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

data class ExpenseFormUiState(
    val amountText: String = "",
    val merchant: String = "",
    val categoryId: Long? = null,
    val note: String = "",
    val transactionAt: Long = System.currentTimeMillis(),
    val categories: List<Category> = emptyList(),
    val merchantSuggestions: List<String> = emptyList(),
    val amountError: String? = null,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false
)

class AddExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseFormUiState())
    val state: StateFlow<ExpenseFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = repository.getAllCategories()
            _state.update { it.copy(categories = cats) }
        }
    }

    fun prefill(amountMinor: Long?, merchant: String?, categoryId: Long?) {
        _state.update { it.copy(
            amountText = if (amountMinor != null && amountMinor > 0)
                String.format("%.2f", amountMinor / 100.0) else "",
            merchant = merchant ?: "",
            categoryId = categoryId
        )}
    }

    fun setAmount(text: String) {
        _state.update { it.copy(amountText = text, amountError = null) }
    }

    fun setMerchant(merchant: String) {
        _state.update { it.copy(merchant = merchant) }
        if (merchant.length >= 2) {
            viewModelScope.launch {
                val suggestions = repository.searchMerchants(merchant)
                    .map { it.name }
                _state.update { it.copy(merchantSuggestions = suggestions) }
            }
        } else {
            _state.update { it.copy(merchantSuggestions = emptyList()) }
        }
    }

    fun setCategory(id: Long?) { _state.update { it.copy(categoryId = id) } }
    fun setNote(note: String) { _state.update { it.copy(note = note) } }
    fun setDate(millis: Long) { _state.update { it.copy(transactionAt = millis) } }

    fun save(onSuccess: () -> Unit) {
        val s = _state.value
        val amountMinor = s.amountText.replace(",", "").toDoubleOrNull()?.let { (it * 100).toLong() }

        if (amountMinor == null || amountMinor <= 0) {
            _state.update { it.copy(amountError = "Please enter a valid amount") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                val expense = Expense(
                    amountMinor = amountMinor,
                    merchant = s.merchant.trim(),
                    categoryId = s.categoryId,
                    note = s.note.trim(),
                    transactionAt = s.transactionAt,
                    direction = TransactionDirection.DEBIT,
                    recordType = RecordType.EXPENSE,
                    confirmationStatus = ConfirmationStatus.CONFIRMED
                )
                val id = repository.saveExpense(expense)
                val cat = s.categoryId?.let { repository.getCategoryById(it) }
                com.spendly.app.core.livealert.LiveAlertManager.postAlert(
                    com.spendly.app.core.livealert.LiveAlertData(
                        amountMinor = amountMinor,
                        currencyCode = "INR",
                        label = "Paid",
                        merchant = s.merchant.trim().ifBlank { "Expense" },
                        categoryName = cat?.name ?: "Expenses",
                        categoryIcon = cat?.icon,
                        sourceApp = "Manual Entry",
                        expenseId = id
                    )
                )
                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, amountError = "Failed to save: ${e.message}") }
            }
        }
    }
}
