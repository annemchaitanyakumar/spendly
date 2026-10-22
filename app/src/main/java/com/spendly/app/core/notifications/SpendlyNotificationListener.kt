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

    // Thread-safe Cooldown map: prevent re-prompting the same notification key or fingerprint within a 5-minute window
    private val recentlyProcessed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val COOLDOWN_MS = 300_000L  // 5 minutes

    private fun isRecentlyProcessed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recentlyProcessed[key] ?: 0L
        if (now - last < COOLDOWN_MS) return true
        recentlyProcessed[key] = now
        if (recentlyProcessed.size > 200) {
            val cutoff = now - COOLDOWN_MS
            recentlyProcessed.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    companion object {
        private const val TAG = "SpendlyNotifListener"
        private const val CONFIDENCE_THRESHOLD = 0.4f
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        // Skip our own notifications to avoid any recursion
        if (pkg == packageName || pkg == "com.spendly.app" || pkg == "com.spendly.app.debug") return

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
        if (isRecentlyProcessed(notifKey)) return

        // Check if this notification key was already stored in DB
        if (repository.findCandidateByNotifKey(notifKey) != null) return

        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")

        // Parse the notification
        val parsed = parserRegistry.parse(pkg, title, text, bigText) ?: return

        // Require minimum confidence
        if (parsed.confidence < CONFIDENCE_THRESHOLD) return

        // Fingerprint deduplication: package + amount + merchant
        val contentFingerprint = "${pkg}_${parsed.amountMinor}_${parsed.merchant.lowercase().trim()}"
        if (isRecentlyProcessed(contentFingerprint)) {
            Log.d(TAG, "Duplicate fingerprint $contentFingerprint detected, skipping")
            return
        }

        // 1. Cross-app candidate deduplication: check if similar candidate exists within 3 minutes across ANY app
        val existingCandidate = repository.findDuplicateCandidate(
            sourcePackage = null, // Check across ALL packages
            amountMinor = parsed.amountMinor,
            detectedAt = System.currentTimeMillis()
        )
        if (existingCandidate != null) {
            Log.d(TAG, "Duplicate candidate detected across apps, skipping")
            return
        }

        // 2. Cross-app expense deduplication: check if an identical expense was already created within 3 minutes
        if (parsed.amountMinor != null && parsed.amountMinor > 0L) {
            val duplicateExpense = repository.findRecentDuplicateExpenseAnySource(
                parsed.amountMinor,
                System.currentTimeMillis()
            )
            if (duplicateExpense != null) {
                Log.d(TAG, "Duplicate expense already recorded across apps, skipping")
                if (duplicateExpense.merchant.isBlank() && parsed.merchant.isNotBlank()) {
                    repository.updateExpenseMerchantAndRef(
                        duplicateExpense.id,
                        parsed.merchant,
                        parsed.transactionRef ?: duplicateExpense.transactionRef
                    )
                }
                return
            }
            if (!parsed.transactionRef.isNullOrBlank() && repository.hasTransactionRef(parsed.transactionRef)) {
                Log.d(TAG, "Duplicate txn ref ${parsed.transactionRef} already in DB, skipping")
                return
            }
        }

        // Determine source display name
        val sourceDisplayName = knownAppNames[pkg] ?: pkg.substringAfterLast('.')

        // Distinguish Transfer vs Paid (Merchant) vs Self Transfer vs Refund vs Received
        val isSelf = fullText.lowercase().contains("self") ||
                parsed.merchant.lowercase().contains("self") ||
                parsed.merchant.contains("Annem", ignoreCase = true) ||
                parsed.merchant.contains("Chaitanya", ignoreCase = true)

        val isCredit = parsed.direction == TransactionDirection.CREDIT ||
                parsed.recordType == RecordType.INCOME ||
                parsed.recordType == RecordType.REFUND

        val isTransfer = !isCredit && (isSelf ||
                parsed.recordType == RecordType.TRANSFER ||
                CommonPatterns.TRANSFER_KEYWORDS.any { fullText.lowercase().contains(it) } ||
                CommonPatterns.isLikelyPersonPayee(parsed.merchant))

        val finalRecordType = when {
            isCredit -> parsed.recordType
            isTransfer -> RecordType.TRANSFER
            else -> parsed.recordType
        }

        val effectiveDirection = when {
            isCredit -> TransactionDirection.CREDIT
            isTransfer -> TransactionDirection.DEBIT
            else -> parsed.direction
        }

        val label = when {
            isSelf -> "Self Transfer"
            finalRecordType == RecordType.TRANSFER -> "Transfer"
            finalRecordType == RecordType.INCOME -> "Received"
            finalRecordType == RecordType.REFUND -> "Refund"
            else -> "Paid"
        }

        // Auto-categorize
        val suggestedCatId = repository.suggestCategoryId(parsed.merchant, finalRecordType)
        val categoryName = repository.getCategoryName(suggestedCatId)

        val displayPayee = when {
            parsed.merchant.isNotBlank() -> parsed.merchant
            isSelf -> "Self Account"
            isTransfer -> "Account Transfer"
            finalRecordType == RecordType.INCOME -> "Received Money"
            else -> sourceDisplayName
        }

        var createdExpenseId: Long? = null

        // Automatically record confirmed debits and credits if amount is valid
        if (parsed.amountMinor != null && parsed.amountMinor > 0L) {
            val note = when {
                isSelf -> "Self Transfer to $displayPayee"
                finalRecordType == RecordType.TRANSFER -> "Transfer to $displayPayee"
                effectiveDirection == TransactionDirection.CREDIT -> "Received from $displayPayee"
                else -> "Paid to $displayPayee"
            }

            val expense = Expense(
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                direction = effectiveDirection,
                recordType = finalRecordType,
                merchant = parsed.merchant.ifBlank { displayPayee },
                categoryId = suggestedCatId,
                transactionAt = System.currentTimeMillis(),
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
            direction = effectiveDirection,
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
                merchantOrRecipient = displayPayee,
                categoryName = categoryName,
                notificationId = createdExpenseId.toInt().coerceIn(1, Int.MAX_VALUE)
            )
        } else {
            ExpenseNotificationHelper.showExpenseDetectedNotification(
                context = this,
                candidateId = candidateId,
                amountMinor = parsed.amountMinor,
                merchant = displayPayee,
                sourceDisplayName = sourceDisplayName,
                notificationId = candidateId.toInt().coerceIn(1, Int.MAX_VALUE)
            )
        }

        // Trigger OxygenOS Live Alerts & Dynamic Island capsule alert if app is open
        if (parsed.amountMinor != null && parsed.amountMinor > 0) {
            val catIcon = repository.getCategoryIcon(suggestedCatId)
            com.spendly.app.core.livealert.LiveAlertManager.postAlert(
                com.spendly.app.core.livealert.LiveAlertData(
                    amountMinor = parsed.amountMinor,
                    currencyCode = parsed.currencyCode,
                    label = label,
                    merchant = displayPayee,
                    categoryName = categoryName,
                    categoryIcon = catIcon,
                    sourceApp = sourceDisplayName,
                    transactionRef = parsed.transactionRef,
                    expenseId = createdExpenseId
                )
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
