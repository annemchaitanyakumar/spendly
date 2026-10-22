package com.spendly.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.settings.AppSettings
import com.spendly.app.core.settings.AppSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    val settings = settingsRepository.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings()
        )
}
