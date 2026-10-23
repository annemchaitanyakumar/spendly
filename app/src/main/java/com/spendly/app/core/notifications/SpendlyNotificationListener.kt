package com.spendly.app.core.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendly.app.core.database.entity.TransactionCandidateEntity
import com.spendly.app.core.model.*
import com.spendly.app.core.parser.CommonPatterns
import com.spendly.app.core.parser.ParsedTransaction
import com.spendly.app.core.parser.SupportedPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android NotificationListenerService implementation.
 *
 * Strictly processes financial transactions from:
 * 1. Bank and UPI SMS (Messages apps)
 * 2. Official Bank confirmation Emails (Gmail, Outlook, Yahoo)
 * 3. Official Bank Apps (HDFC, SBI, ICICI, Axis, Kotak, PNB, BoB, etc.)
 * 4. Verified UPI Apps (PhonePe, Google Pay, Paytm, BHIM, CRED, Amazon Pay, etc.)
 *
 * Implements strict 3-way deduplication (PhonePe + SMS + Email) via exact
 * 12-digit transaction reference matching and 5-minute fuzzy time-window matching.
 * Live alert popups are completely removed in favor of rich system drawer notifications.
 */
class SpendlyNotificationListener : NotificationListenerService() {

    private val container by lazy {
        (applicationContext as com.spendly.app.SpendlyApplication).container
    }
    private val parserRegistry get() = container.parserRegistry
    private val repository get() = container.expenseRepository
    private val settingsRepository get() = container.settingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Thread-safe Mutex to prevent concurrency race conditions when PhonePe, SMS, and Email fire concurrently
    private val dedupeMutex = Mutex()

    // Thread-safe Cooldown map: prevent re-prompting the same notification key or fingerprint within 5 minutes
    private val recentlyProcessed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val COOLDOWN_MS = 300_000L  // 5 minutes

    private fun isRecentlyProcessed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recentlyProcessed[key] ?: 0L
        if (now - last < COOLDOWN_MS) return true
        recentlyProcessed[key] = now
        if (recentlyProcessed.size > 300) {
            val cutoff = now - COOLDOWN_MS
            recentlyProcessed.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    companion object {
        private const val TAG = "SpendlyNotifListener"
        private const val CONFIDENCE_THRESHOLD = 0.35f
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return

        // Skip our own notifications
        if (pkg == packageName || pkg == "com.spendly.app" || pkg == "com.spendly.app.debug") return

        // STRICT REQUIREMENT: Only process notifications from verified SMS, Email, Bank, and UPI packages
        if (!SupportedPackages.isSupported(pkg)) {
            return
        }

        scope.launch {
            try {
                processNotification(sbn, pkg)
            } catch (e: Exception) {
                Log.w(TAG, "Error processing notification from $pkg: ${e.message}")
            }
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification, pkg: String) {
        // Check if user disabled this source in app settings
        if (!repository.isSourceEnabled(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString()

        if (title.isBlank() && text.isBlank()) return

        val notifKey = sbn.key ?: "${pkg}_${sbn.id}"
        if (isRecentlyProcessed(notifKey)) return
        if (repository.findCandidateByNotifKey(notifKey) != null) return

        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")

        // Parse notification text strictly using parser registry
        val parsed = parserRegistry.parse(pkg, title, text, bigText) ?: return

        if (parsed.confidence < CONFIDENCE_THRESHOLD) return
        if (parsed.amountMinor == null || parsed.amountMinor <= 0L) return
        if (parsed.direction == TransactionDirection.UNKNOWN) return
        if (parsed.recordType == RecordType.UNKNOWN || parsed.recordType == RecordType.FAILED) return

        // Optional AI Agent Verification & Professional Formatting (Gemini Free Tier)
        var aiCustomTitle: String? = null
        var aiCustomSubtitle: String? = null
        var aiMerchant: String? = null

        try {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.aiAgentEnabled && settings.geminiApiKey.isNotBlank()) {
                val aiResult = com.spendly.app.core.ai.AiTransactionAgent.verifyAndFormat(
                    apiKey = settings.geminiApiKey,
                    text = fullText
                )
                if (!aiResult.isTransaction) {
                    Log.d(TAG, "AI Agent rejected notification as non-transaction: ${aiResult.reason}")
                    return
                }
                if (aiResult.professionalTitle.isNotBlank()) {
                    aiCustomTitle = aiResult.professionalTitle
                }
                if (aiResult.professionalSubtitle.isNotBlank()) {
                    aiCustomSubtitle = aiResult.professionalSubtitle
                }
                if (aiResult.merchant.isNotBlank()) {
                    aiMerchant = aiResult.merchant
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI Agent check failed; falling back to local engine: ${e.message}")
        }

        val contentFingerprint = "${parsed.amountMinor}_${parsed.transactionRef ?: ""}"
        if (contentFingerprint.isNotBlank() && isRecentlyProcessed(contentFingerprint)) {
            Log.d(TAG, "Duplicate fingerprint $contentFingerprint detected, skipping")
            return
        }

        val sourceDisplayName = knownAppNames[pkg] ?: when {
            SupportedPackages.isEmail(pkg) -> {
                when {
                    title.contains("Kotak", ignoreCase = true) -> "Kotak Bank"
                    title.contains("SBI", ignoreCase = true) -> "SBI"
                    title.contains("HDFC", ignoreCase = true) -> "HDFC Bank"
                    title.contains("ICICI", ignoreCase = true) -> "ICICI Bank"
                    title.contains("Axis", ignoreCase = true) -> "Axis Bank"
                    title.contains("PhonePe", ignoreCase = true) -> "PhonePe"
                    title.contains("Google Pay", ignoreCase = true) || title.contains("GPay", ignoreCase = true) -> "Google Pay"
                    title.contains("Paytm", ignoreCase = true) -> "Paytm"
                    else -> "Bank Email"
                }
            }
            SupportedPackages.isSms(pkg) -> "Bank SMS"
            SupportedPackages.isBank(pkg) -> "Bank App"
            else -> pkg.substringAfterLast('.')
        }

        // ─────────────────────────────────────────────────────────────────
        //  Critical Section: Multi-Source Deduplication & Record Insertion
        // ─────────────────────────────────────────────────────────────────
        dedupeMutex.withLock {
            // 1. Transaction Reference Matching (UPI Ref / UTR / RRN / Bank Txn ID)
            if (!parsed.transactionRef.isNullOrBlank()) {
                val existingByRef = repository.findByTransactionRef(parsed.transactionRef)
                if (existingByRef != null) {
                    Log.d(TAG, "Exact transactionRef ${parsed.transactionRef} already in DB. Enriching and skipping duplicate.")
                    // Enrich merchant if existing was empty
                    if (existingByRef.merchant.isBlank() && parsed.merchant.isNotBlank()) {
                        repository.updateExpenseMerchantAndRef(
                            existingByRef.id,
                            parsed.merchant,
                            parsed.transactionRef
                        )
                    }
                    // Upgrade source app if new is a dedicated UPI app (e.g. PhonePe) and existing was SMS/Email
                    if (SupportedPackages.isUpi(pkg) && !SupportedPackages.isUpi(existingByRef.sourcePackage ?: "")) {
                        repository.updateExpenseSourcePackage(existingByRef.id, pkg)
                    }
                    recordCandidate(pkg, notifKey, fullText, parsed, existingByRef.id, sbn.postTime)
                    return@withLock
                }
            }

            // 2. Cross-Source 5-Minute Time Window Matching (PhonePe + SMS + Email)
            // If user paid ₹50 on PhonePe, PhonePe notification arrives, then Bank SMS arrives, then Email arrives.
            val recentDuplicate = repository.findRecentDuplicateExpenseAnySource(
                amountMinor = parsed.amountMinor,
                transactionAt = System.currentTimeMillis(),
                windowMs = 300_000L // 5-minute deduplication window
            )

            if (recentDuplicate != null) {
                Log.d(TAG, "Recent duplicate spend of ${parsed.amountMinor} detected within 5-min window across sources. Enriching.")
                // If the new notification has a transactionRef (e.g. from SMS/Email) and existing didn't, enrich it!
                if (recentDuplicate.transactionRef.isNullOrBlank() && !parsed.transactionRef.isNullOrBlank()) {
                    repository.updateExpenseMerchantAndRef(
                        recentDuplicate.id,
                        if (recentDuplicate.merchant.isBlank()) parsed.merchant else recentDuplicate.merchant,
                        parsed.transactionRef
                    )
                } else if (recentDuplicate.merchant.isBlank() && parsed.merchant.isNotBlank()) {
                    repository.updateExpenseMerchantAndRef(
                        recentDuplicate.id,
                        parsed.merchant,
                        recentDuplicate.transactionRef
                    )
                }
                // Upgrade source package to UPI app if available
                if (SupportedPackages.isUpi(pkg) && !SupportedPackages.isUpi(recentDuplicate.sourcePackage ?: "")) {
                    repository.updateExpenseSourcePackage(recentDuplicate.id, pkg)
                }
                recordCandidate(pkg, notifKey, fullText, parsed, recentDuplicate.id, sbn.postTime)
                return@withLock
            }

            // ─────────────────────────────────────────────────────────────
            //  Separate Record Types: Shopping vs Debit Transfers vs Credited
            // ─────────────────────────────────────────────────────────────
            val isCredit = parsed.direction == TransactionDirection.CREDIT ||
                    parsed.recordType == RecordType.INCOME ||
                    parsed.recordType == RecordType.REFUND

            val rawMerchant = parsed.merchant.trim()
            val isCarrierOrSystem = rawMerchant.equals("Messages", ignoreCase = true) ||
                    rawMerchant.equals("Message", ignoreCase = true) ||
                    rawMerchant.equals("Bank SMS", ignoreCase = true) ||
                    rawMerchant.equals("SMS", ignoreCase = true) ||
                    rawMerchant.equals("Gmail", ignoreCase = true) ||
                    rawMerchant.equals("Outlook", ignoreCase = true) ||
                    rawMerchant.equals("Yahoo Mail", ignoreCase = true) ||
                    rawMerchant.equals("PhonePe", ignoreCase = true) ||
                    rawMerchant.equals("Google Pay", ignoreCase = true) ||
                    rawMerchant.equals("Paytm", ignoreCase = true) ||
                    rawMerchant.equals("GPay", ignoreCase = true) ||
                    rawMerchant.equals("Android", ignoreCase = true) ||
                    rawMerchant.equals("System", ignoreCase = true) ||
                    rawMerchant.equals("any time", ignoreCase = true) ||
                    rawMerchant.equals("all times", ignoreCase = true) ||
                    rawMerchant.equals("anytime", ignoreCase = true)

            val cleanMerchant = if (isCarrierOrSystem) "" else rawMerchant

            val isSelf = !isCredit && (
                    CommonPatterns.matchesWord(fullText, "self") ||
                    CommonPatterns.matchesWord(cleanMerchant, "self") ||
                    cleanMerchant.contains("Annem", ignoreCase = true) ||
                    cleanMerchant.contains("Chaitanya", ignoreCase = true)
            )

            val isTransfer = !isCredit && (
                    isSelf ||
                    parsed.recordType == RecordType.TRANSFER ||
                    CommonPatterns.TRANSFER_KEYWORDS.any { CommonPatterns.matchesWord(fullText, it) } ||
                    CommonPatterns.isLikelyPersonPayee(cleanMerchant) ||
                    (cleanMerchant.isBlank() && (
                        SupportedPackages.isSms(pkg) ||
                        SupportedPackages.isUpi(pkg) ||
                        CommonPatterns.matchesWord(fullText, "upi") ||
                        CommonPatterns.matchesWord(fullText, "vpa") ||
                        CommonPatterns.matchesWord(fullText, "p2p") ||
                        CommonPatterns.matchesWord(fullText, "sent") ||
                        CommonPatterns.matchesWord(fullText, "transfer")
                    ))
            )

            val finalRecordType = when {
                isCredit -> if (parsed.recordType == RecordType.REFUND) RecordType.REFUND else RecordType.INCOME
                isTransfer -> RecordType.TRANSFER
                else -> RecordType.EXPENSE // Shopping / Merchant payment
            }

            val effectiveDirection = when {
                isCredit -> TransactionDirection.CREDIT
                else -> TransactionDirection.DEBIT
            }

            val label = when {
                isSelf -> "Self Transfer"
                finalRecordType == RecordType.TRANSFER -> "Transfer"
                finalRecordType == RecordType.REFUND -> "Refund"
                finalRecordType == RecordType.INCOME -> "Credited"
                else -> "Paid" // Shopping / merchant spend
            }

            // Auto-categorize
            val suggestedCatId = repository.suggestCategoryId(cleanMerchant, finalRecordType)
            val categoryName = repository.getCategoryName(suggestedCatId)

            val displayPayee = when {
                cleanMerchant.isNotBlank() -> cleanMerchant
                isSelf -> "Self Account"
                isTransfer -> ""
                finalRecordType == RecordType.INCOME -> ""
                finalRecordType == RecordType.REFUND -> ""
                else -> ""
            }

            val note = when {
                isSelf -> if (cleanMerchant.isNotBlank()) "Self Transfer to $cleanMerchant" else "Self Transfer"
                finalRecordType == RecordType.TRANSFER -> if (cleanMerchant.isNotBlank()) "Transfer to $cleanMerchant" else "Transfer"
                finalRecordType == RecordType.REFUND -> if (cleanMerchant.isNotBlank()) "Refund from $cleanMerchant" else "Refund"
                effectiveDirection == TransactionDirection.CREDIT -> if (cleanMerchant.isNotBlank()) "Credited from $cleanMerchant" else "Credited"
                else -> if (cleanMerchant.isNotBlank()) "Paid to $cleanMerchant" else "Paid"
            }

            val expenseMerchant = when {
                !aiMerchant.isNullOrBlank() -> aiMerchant
                cleanMerchant.isNotBlank() -> cleanMerchant
                isSelf -> "Self Account"
                finalRecordType == RecordType.TRANSFER -> "Money Transfer"
                finalRecordType == RecordType.INCOME -> "Income"
                finalRecordType == RecordType.REFUND -> "Refund"
                else -> "Expense"
            }

            val expense = Expense(
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                direction = effectiveDirection,
                recordType = finalRecordType,
                merchant = expenseMerchant,
                categoryId = suggestedCatId,
                transactionAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                sourcePackage = pkg,
                transactionRef = parsed.transactionRef,
                note = note,
                confirmationStatus = ConfirmationStatus.CONFIRMED
            )

            val createdExpenseId = repository.saveExpense(expense)
            val candidateId = recordCandidate(pkg, notifKey, fullText, parsed, createdExpenseId, sbn.postTime)

            // Post improved, high-visibility status bar notification (only latest notification kept)
            ExpenseNotificationHelper.showAutoAddedExpenseNotification(
                context = this@SpendlyNotificationListener,
                expenseId = createdExpenseId,
                candidateId = candidateId,
                amountMinor = parsed.amountMinor,
                currencyCode = parsed.currencyCode,
                label = label,
                merchantOrRecipient = if (!aiMerchant.isNullOrBlank()) aiMerchant else displayPayee,
                categoryName = categoryName,
                sourceDisplayName = sourceDisplayName,
                transactionRef = parsed.transactionRef,
                notificationId = ExpenseNotificationHelper.LATEST_SPEND_NOTIFICATION_ID,
                customTitle = aiCustomTitle,
                customSubtitle = aiCustomSubtitle
            )

            // Schedule candidate text cleanup after 10 minutes
            scope.launch {
                kotlinx.coroutines.delay(10 * 60 * 1000L)
                repository.clearCandidateNotificationText(candidateId)
            }
        }
    }

    private suspend fun recordCandidate(
        pkg: String,
        notifKey: String,
        text: String,
        parsed: ParsedTransaction,
        expenseId: Long?,
        postTime: Long
    ): Long {
        val candidate = TransactionCandidateEntity(
            sourcePackage = pkg,
            notificationKey = notifKey,
            notificationText = text.take(200),
            amountMinor = parsed.amountMinor,
            currencyCode = parsed.currencyCode,
            direction = parsed.direction,
            recordType = parsed.recordType,
            merchant = parsed.merchant,
            transactionRef = parsed.transactionRef,
            suggestedCategoryId = null,
            confidence = parsed.confidence,
            detectedAt = System.currentTimeMillis(),
            notificationPostedAt = postTime,
            expenseId = expenseId
        )
        return repository.saveCandidate(candidate)
    }

    private val knownAppNames = mapOf(
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM UPI",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.mobikwik_new" to "MobiKwik",
        "com.freecharge.android" to "Freecharge",
        "com.navi.finance" to "Navi",
        "money.super.app" to "super.money",
        "com.tatadigital.tcp" to "Tata Neu",
        "com.google.android.gm" to "Gmail",
        "com.microsoft.office.outlook" to "Outlook",
        "com.yahoo.mobile.client.android.mail" to "Yahoo Mail",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.sbi.lotusintouch" to "SBI YONO",
        "com.csam.icici.bank.imobile" to "ICICI Bank",
        "com.axis.mobile" to "Axis Bank",
        "com.msf.kbank.mobile" to "Kotak Bank",
        "com.kotak.bank.mobile" to "Kotak Bank",
        "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge" to "Kotak 811",
        "com.kotak.mobile" to "Kotak Bank",
        "com.msf.koko" to "Kotak 811",
        "com.pnb.pnbone" to "PNB ONE",
        "com.bankofbaroda.mconnect" to "Bank of Baroda"
    )

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SpendlyNotificationListener started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "SpendlyNotificationListener stopped")
    }
}
