package com.spendly.app.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendly.app.core.database.entity.TransactionCandidateEntity
import com.spendly.app.core.model.*
import com.spendly.app.core.parser.CommonPatterns
import com.spendly.app.core.parser.NotificationParserRegistry
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android NotificationListenerService implementation.
 *
 * IMPORTANT:
 * - Requires the user to explicitly grant notification access via Android settings.
 * - Only reads notification content from packages the user has authorized.
 * - Does NOT access private app data, SMS inboxes, or financial account details.
 * - Automatically records expenses / transfers into the local Room database and
 *   posts high-priority system notifications in the Android notification drawer.
 */
class SpendlyNotificationListener : NotificationListenerService() {

    private val container by lazy {
        (applicationContext as com.spendly.app.SpendlyApplication).container
    }
    private val parserRegistry get() = container.parserRegistry
    private val repository get() = container.expenseRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cooldown map: prevent re-prompting the same notification key within a window
    private val recentlyProcessed = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 30_000L  // 30 seconds

    companion object {
        private const val TAG = "SpendlyNotifListener"
        private const val CONFIDENCE_THRESHOLD = 0.4f
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        scope.launch {
            try {
                processNotification(sbn, pkg)
            } catch (e: Exception) {
                // Never crash the listener
                Log.w(TAG, "Error processing notification from $pkg: ${e.message}")
            }
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification, pkg: String) {
        // Check if this source is user-enabled
        if (!repository.isSourceEnabled(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString()

        // Skip if both title and text are empty
        if (title.isBlank() && text.isBlank()) return

        // Cooldown: skip if same notification key was recently processed
        val notifKey = sbn.key ?: "${pkg}_${sbn.id}"
        val lastProcessed = recentlyProcessed[notifKey] ?: 0L
        if (System.currentTimeMillis() - lastProcessed < COOLDOWN_MS) return
        recentlyProcessed[notifKey] = System.currentTimeMillis()

        // Cleanup stale cooldown entries
        if (recentlyProcessed.size > 200) {
            val cutoff = System.currentTimeMillis() - COOLDOWN_MS * 5
            recentlyProcessed.entries.removeIf { it.value < cutoff }
        }

        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")

        // Parse the notification
        val parsed = parserRegistry.parse(pkg, title, text, bigText) ?: return

        // Require minimum confidence
        if (parsed.confidence < CONFIDENCE_THRESHOLD) return

        // Deduplication: check if we already have a similar candidate
        val existing = repository.findDuplicateCandidate(
            pkg, parsed.amountMinor, System.currentTimeMillis()
        )
        if (existing != null) {
            Log.d(TAG, "Duplicate candidate detected for $pkg, skipping")
            return
        }

        // Determine source display name
        val sourceDisplayName = knownAppNames[pkg] ?: pkg.substringAfterLast('.')

        // Distinguish Transfer vs Paid (Merchant) vs Refund vs Received
        val isTransfer = parsed.recordType == RecordType.TRANSFER ||
                CommonPatterns.TRANSFER_KEYWORDS.any { fullText.lowercase().contains(it) }
        val finalRecordType = if (isTransfer) RecordType.TRANSFER else parsed.recordType

        val label = when (finalRecordType) {
            RecordType.TRANSFER -> "Transfer"
            RecordType.INCOME -> "Received"
            RecordType.REFUND -> "Refund"
            else -> "Paid"
        }

        // Auto-categorize
        val suggestedCatId = repository.suggestCategoryId(parsed.merchant, finalRecordType)
        val categoryName = repository.getCategoryName(suggestedCatId)

        var createdExpenseId: Long? = null

        // Automatically record confirmed expenses/transfers if amount is valid
        if (parsed.amountMinor != null && parsed.amountMinor > 0L && parsed.direction == TransactionDirection.DEBIT) {
            val merchantOrRecipient = parsed.merchant.ifBlank { sourceDisplayName }
            val note = if (finalRecordType == RecordType.TRANSFER) {
                "Transfer to $merchantOrRecipient"
            } else {
                "Paid to $merchantOrRecipient"
            }

            val expense = Expense(
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                direction = parsed.direction,
                recordType = finalRecordType,
                merchant = parsed.merchant,
                categoryId = suggestedCatId,
                transactionAt = sbn.postTime,
                createdAt = System.currentTimeMillis(),
                sourcePackage = pkg,
                transactionRef = parsed.transactionRef,
                note = note,
                confirmationStatus = ConfirmationStatus.CONFIRMED
            )
            createdExpenseId = repository.saveExpense(expense)
        }

        // Build candidate entity
        val candidate = TransactionCandidateEntity(
            sourcePackage = pkg,
            notificationKey = notifKey,
            notificationText = sanitizeForStorage(text),
            amountMinor = parsed.amountMinor,
            currencyCode = parsed.currencyCode,
            direction = parsed.direction,
            recordType = finalRecordType,
            merchant = parsed.merchant,
            transactionRef = parsed.transactionRef,
            suggestedCategoryId = suggestedCatId,
            confidence = parsed.confidence,
            detectedAt = System.currentTimeMillis(),
            notificationPostedAt = sbn.postTime,
            expenseId = createdExpenseId
        )

        val candidateId = repository.saveCandidate(candidate)

        // Show rich status bar notification in the system drawer
        if (createdExpenseId != null && parsed.amountMinor != null) {
            ExpenseNotificationHelper.showAutoAddedExpenseNotification(
                context = this,
                expenseId = createdExpenseId,
                candidateId = candidateId,
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                label = label,
                merchantOrRecipient = parsed.merchant.ifBlank { sourceDisplayName },
                categoryName = categoryName,
                notificationId = createdExpenseId.toInt().coerceIn(1, Int.MAX_VALUE)
            )
        } else {
            ExpenseNotificationHelper.showExpenseDetectedNotification(
                context = this,
                candidateId = candidateId,
                amountMinor = parsed.amountMinor,
                merchant = parsed.merchant.takeIf { it.isNotBlank() },
                sourceDisplayName = sourceDisplayName,
                notificationId = candidateId.toInt().coerceIn(1, Int.MAX_VALUE)
            )
        }

        // Schedule cleanup of notification text after 10 minutes
        scope.launch {
            kotlinx.coroutines.delay(10 * 60 * 1000L)
            repository.clearCandidateNotificationText(candidateId)
        }
    }

    /** Minimal sanitization to remove potentially sensitive content */
    private fun sanitizeForStorage(text: String): String {
        return text.take(200)
    }

    // Simple lookup for known app display names
    private val knownAppNames = mapOf(
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.amazon.mShop.android.shopping" to "Amazon Pay"
    )

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotificationListenerService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "NotificationListenerService stopped")
    }
}
