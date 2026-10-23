package com.spendly.app.core.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InAppNotificationEvent(
    val expenseId: Long = 0L,
    val candidateId: Long = 0L,
    val amountMinor: Long,
    val currencyCode: String = "INR",
    val label: String = "Paid",
    val merchantOrRecipient: String,
    val categoryName: String,
    val sourceDisplayName: String,
    val transactionRef: String? = null
)

object InAppNotificationManager {
    private val _events = MutableSharedFlow<InAppNotificationEvent>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun emit(event: InAppNotificationEvent) {
        _events.tryEmit(event)
    }
}
