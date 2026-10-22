package com.spendly.app.feature.expenseform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseFormUiState(
    val editingExpenseId: Long? = null,
    val amountText: String = "",
    val merchant: String = "",
    val categoryId: Long? = null,
    val recordType: RecordType = RecordType.EXPENSE,
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val note: String = "",
    val transactionAt: Long = System.currentTimeMillis(),
    val sourcePackage: String? = null,
    val transactionRef: String? = null,
    val categories: List<Category> = emptyList(),
    val merchantSuggestions: List<String> = emptyList(),
    val amountError: String? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
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

    fun loadExpense(id: Long) {
        viewModelScope.launch {
            val expense = repository.getExpenseById(id) ?: return@launch
            _state.update {
                it.copy(
                    editingExpenseId = expense.id,
                    amountText = String.format("%.2f", expense.amountMinor / 100.0),
                    merchant = expense.merchant,
                    categoryId = expense.categoryId,
                    recordType = expense.recordType,
                    direction = expense.direction,
                    note = expense.note,
                    transactionAt = expense.transactionAt,
                    sourcePackage = expense.sourcePackage,
                    transactionRef = expense.transactionRef
                )
            }
        }
    }

    fun prefill(amountMinor: Long?, merchant: String?, categoryId: Long?) {
        if (_state.value.editingExpenseId != null) return
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

    fun setRecordType(type: RecordType) {
        val dir = when (type) {
            RecordType.INCOME, RecordType.REFUND -> TransactionDirection.CREDIT
            else -> TransactionDirection.DEBIT
        }
        val catId = if (type == RecordType.TRANSFER && _state.value.categoryId == null) 13L else _state.value.categoryId
        _state.update { it.copy(recordType = type, direction = dir, categoryId = catId) }
    }

    fun deleteExpense(onSuccess: () -> Unit) {
        val id = _state.value.editingExpenseId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            try {
                repository.deleteExpenseById(id)
                _state.update { it.copy(isDeleting = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isDeleting = false, amountError = "Failed to delete: ${e.message}") }
            }
        }
    }

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
                if (s.editingExpenseId != null) {
                    val existing = repository.getExpenseById(s.editingExpenseId)
                    val updated = Expense(
                        id = s.editingExpenseId,
                        amountMinor = amountMinor,
                        currencyCode = existing?.currencyCode ?: "INR",
                        direction = s.direction,
                        recordType = s.recordType,
                        merchant = s.merchant.trim(),
                        categoryId = s.categoryId,
                        transactionAt = s.transactionAt,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        sourcePackage = existing?.sourcePackage,
                        transactionRef = existing?.transactionRef,
                        note = s.note.trim(),
                        tags = existing?.tags ?: emptyList(),
                        confirmationStatus = ConfirmationStatus.CONFIRMED,
                        candidateId = existing?.candidateId
                    )
                    repository.updateExpense(updated)
                } else {
                    val expense = Expense(
                        amountMinor = amountMinor,
                        merchant = s.merchant.trim(),
                        categoryId = s.categoryId,
                        note = s.note.trim(),
                        transactionAt = s.transactionAt,
                        direction = s.direction,
                        recordType = s.recordType,
                        confirmationStatus = ConfirmationStatus.CONFIRMED
                    )
                    val id = repository.saveExpense(expense)
                    val cat = s.categoryId?.let { repository.getCategoryById(it) }
                    com.spendly.app.core.livealert.LiveAlertManager.postAlert(
                        com.spendly.app.core.livealert.LiveAlertData(
                            amountMinor = amountMinor,
                            currencyCode = "INR",
                            label = if (s.recordType == RecordType.TRANSFER) "Transfer" else "Paid",
                            merchant = s.merchant.trim().ifBlank { "Expense" },
                            categoryName = cat?.name ?: "Expenses",
                            categoryIcon = cat?.icon,
                            sourceApp = "Manual Entry",
                            expenseId = id
                        )
                    )
                }
                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, amountError = "Failed to save: ${e.message}") }
            }
        }
    }
}
