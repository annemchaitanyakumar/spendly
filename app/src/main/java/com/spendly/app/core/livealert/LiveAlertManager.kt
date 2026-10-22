package com.spendly.app.core.livealert

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveAlertData(
    val id: Long = System.currentTimeMillis(),
    val amountMinor: Long,
    val currencyCode: String = "INR",
    val label: String, // "Paid", "Transfer", "Received", "Refund"
    val merchant: String,
    val categoryName: String,
    val categoryIcon: String? = null,
    val sourceApp: String? = null,
    val transactionRef: String? = null,
    val expenseId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Global singleton event bus for triggering OxygenOS Live Alerts / Dynamic Island
 * floating capsule notifications across background services, foreground activities, and debug tools.
 */
object LiveAlertManager {
    private val _currentAlert = MutableStateFlow<LiveAlertData?>(null)
    val currentAlert: StateFlow<LiveAlertData?> = _currentAlert.asStateFlow()

    fun postAlert(alert: LiveAlertData) {
        _currentAlert.value = alert
    }

    fun dismiss() {
        _currentAlert.value = null
    }
}
