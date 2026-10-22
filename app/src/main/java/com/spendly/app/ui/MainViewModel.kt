package com.spendly.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.repository.ExpenseRepository
import com.spendly.app.core.settings.AppSettings
import com.spendly.app.core.settings.AppSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    val settings = settingsRepository.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings()
        )

    fun undoExpense(expenseId: Long) {
        viewModelScope.launch {
            expenseRepository.deleteExpenseById(expenseId)
        }
    }
}
