package com.spendly.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.app.core.backup.BackupManager
import com.spendly.app.core.database.entity.AppSourceEntity
import com.spendly.app.core.location.LocationHelper
import com.spendly.app.core.parser.NotificationParserRegistry
import com.spendly.app.core.parser.ParsedTransaction
import com.spendly.app.core.repository.ExpenseRepository
import com.spendly.app.core.settings.AppSettings
import com.spendly.app.core.settings.AppSettingsRepository
import com.spendly.app.core.settings.LocationCapturePref
import com.spendly.app.core.settings.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ParserDebugResult(
    val parserName: String,
    val amountMinor: Long?,
    val currencyCode: String,
    val merchant: String,
    val direction: String,
    val recordType: String,
    val confidence: Float,
    val isCandidate: Boolean,
    val summary: String
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val appSources: List<AppSourceEntity> = emptyList(),
    val isExporting: Boolean = false,
    val isRestoring: Boolean = false,
    val statusMessage: String? = null,
    val debugResult: ParserDebugResult? = null,
    val hasLocationPermission: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val expenseRepository: ExpenseRepository,
    private val backupManager: BackupManager,
    private val locationHelper: LocationHelper,
    private val parserRegistry: NotificationParserRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { s ->
                _uiState.update { it.copy(settings = s) }
            }
        }

        viewModelScope.launch {
            expenseRepository.appSources.collect { sources ->
                _uiState.update { it.copy(appSources = sources) }
            }
        }

        checkLocationPermission()
    }

    fun checkLocationPermission() {
        val hasPerm = locationHelper.hasLocationPermission()
        _uiState.update { it.copy(hasLocationPermission = hasPerm) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setCurrency(code) }
    }

    fun setLocationCapture(pref: LocationCapturePref) {
        viewModelScope.launch { settingsRepository.setLocationCapture(pref) }
    }

    fun toggleAppSource(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            expenseRepository.setSourceEnabled(packageName, enabled)
        }
    }

    fun exportCsv(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val ok = backupManager.writeCsvToUri(uri)
            _uiState.update { it.copy(isExporting = false) }
            onResult(ok)
        }
    }

    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val ok = backupManager.writeBackupToUri(uri)
            _uiState.update { it.copy(isExporting = false) }
            onResult(ok)
        }
    }

    fun restoreBackup(uri: Uri, onResult: (BackupManager.ImportResult) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true) }
            val res = backupManager.restoreFromJsonUri(uri)
            _uiState.update { it.copy(isRestoring = false) }
            onResult(res)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            expenseRepository.clearAllData()
            onComplete()
        }
    }

    fun testParser(packageName: String, title: String, text: String) {
        val parsed = parserRegistry.parse(packageName, title, text, null)
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    debugResult = ParserDebugResult(
                        parserName = "None",
                        amountMinor = null,
                        currencyCode = "INR",
                        merchant = "",
                        direction = "NONE",
                        recordType = "NONE",
                        confidence = 0f,
                        isCandidate = false,
                        summary = "No parser matched this notification or pattern was filtered out."
                    )
                )
            }
        } else {
            val isCandidate = parsed.confidence >= 0.4f &&
                    parsed.direction.name == "DEBIT" &&
                    parsed.recordType.name == "EXPENSE"

            _uiState.update {
                it.copy(
                    debugResult = ParserDebugResult(
                        parserName = parsed.parserName,
                        amountMinor = parsed.amountMinor,
                        currencyCode = parsed.currencyCode,
                        merchant = parsed.merchant,
                        direction = parsed.direction.name,
                        recordType = parsed.recordType.name,
                        confidence = parsed.confidence,
                        isCandidate = isCandidate,
                        summary = if (isCandidate) {
                            "Stage 1 (Filter): PASSED\nStage 2 (Parser): ${parsed.parserName}\nStage 3 (Extraction): Amount ₹${(parsed.amountMinor ?: 0) / 100.0}, Merchant '${parsed.merchant}'\nStage 4 (Confidence): ${(parsed.confidence * 100).toInt()}% -> QUALIFIES as candidate"
                        } else {
                            "Stage 1: Matched ${parsed.parserName}\nStage 2: Parsed as ${parsed.direction} / ${parsed.recordType} (Confidence: ${(parsed.confidence * 100).toInt()}%)\nResult: Does not qualify as debit expense candidate"
                        }
                    )
                )
            }
        }
    }

    fun setAiAgentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiAgentEnabled(enabled)
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setGeminiApiKey(key)
        }
    }

    fun testAiAgent(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val key = _uiState.value.settings.geminiApiKey
            if (key.isBlank()) {
                onResult("Please enter your Google AI Studio API key first.")
                return@launch
            }
            val res = com.spendly.app.core.ai.AiTransactionAgent.verifyTransaction(key, text)
            if (res.isTransaction) {
                onResult("AI Decision: REAL TRANSACTION\nType: ${res.recordType}\nAmount: ${res.amountMinor?.let { "₹${it / 100.0}" } ?: "N/A"}\nMerchant: ${res.merchant}\nReason: ${res.reason}")
            } else {
                onResult("AI Decision: IGNORED / NON-TRANSACTION\nReason: ${res.reason}")
            }
        }
    }

    fun triggerSimulatedNotification(context: android.content.Context, result: ParserDebugResult) {
        val amount = result.amountMinor ?: 45000L
        val merchant = result.merchant.ifBlank { "Starbucks" }
        com.spendly.app.core.notifications.ExpenseNotificationHelper.showAutoAddedExpenseNotification(
            context = context,
            expenseId = 999999L,
            candidateId = 999999L,
            amountMinor = amount,
            currencyCode = result.currencyCode,
            label = if (result.recordType == "TRANSFER") "Transfer" else "Paid",
            merchantOrRecipient = merchant,
            categoryName = if (result.recordType == "TRANSFER") "Transfers & UPI" else "Food & Dining",
            sourceDisplayName = result.parserName.ifBlank { "Test Notification" },
            transactionRef = "429612345678"
        )
    }
}
