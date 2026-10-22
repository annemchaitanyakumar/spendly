package com.spendly.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendly.app.SpendlyApplication
import com.spendly.app.feature.expenseform.AddExpenseViewModel
import com.spendly.app.feature.home.HomeViewModel
import com.spendly.app.feature.insights.InsightsViewModel
import com.spendly.app.feature.settings.SettingsViewModel
import com.spendly.app.feature.transactions.TransactionsViewModel

class SpendlyViewModelFactory(
    private val app: SpendlyApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = app.container
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(container.settingsRepository) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(container.expenseRepository) as T
            modelClass.isAssignableFrom(TransactionsViewModel::class.java) ->
                TransactionsViewModel(container.expenseRepository) as T
            modelClass.isAssignableFrom(InsightsViewModel::class.java) ->
                InsightsViewModel(container.expenseRepository) as T
            modelClass.isAssignableFrom(AddExpenseViewModel::class.java) ->
                AddExpenseViewModel(container.expenseRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    container.settingsRepository,
                    container.expenseRepository,
                    container.backupManager,
                    container.locationHelper,
                    container.parserRegistry
                ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> spendlyViewModel(): VM {
    val app = LocalContext.current.applicationContext as SpendlyApplication
    return viewModel(factory = SpendlyViewModelFactory(app))
}
