package com.spendly.app.core.parser

import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection

/**
 * Result from parsing a notification.
 */
data class ParsedTransaction(
    val amountMinor: Long?,           // null = amount not found
    val currencyCode: String = "INR",
    val direction: TransactionDirection,
    val recordType: RecordType,
    val merchant: String = "",
    val transactionRef: String? = null,
    val confidence: Float,            // 0.0 – 1.0
    val rawText: String,              // original text (for debugging)
    val parserName: String = "Unknown"
)

/**
 * Modular transaction parser interface.
 * Each known payment app can have its own parser.
 */
interface NotificationParser {
    /** Package names this parser handles; empty = generic fallback */
    val supportedPackages: Set<String>
    val name: String

    /**
     * Attempt to parse the notification text.
     * @return null if this parser cannot confidently handle the text.
     */
    fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction?
}

// ─────────────────────────────────────────────────────────────────────
//  Shared regex patterns
// ─────────────────────────────────────────────────────────────────────

internal object CommonPatterns {
    // Amount: ₹1,234.56 or Rs. 1234 or INR 500
    val AMOUNT_REGEX = Regex(
        """(?:₹|Rs\.?\s*|INR\s*|inr\s*)([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Debit keywords
    val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "paid", "payment of", "payment successful",
        "spent", "transferred", "sent", "withdrawn", "transaction of",
        "purchase of", "charged", "auto-debit", "emi paid"
    )

    // Credit keywords
    val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "refund", "cashback",
        "added to", "money received", "amount received", "deposit"
    )

    // Failure keywords
    val FAILURE_KEYWORDS = listOf(
        "failed", "failure", "declined", "rejected", "unsuccessful",
        "could not", "reversal"
    )

    // Refund keywords
    val REFUND_KEYWORDS = listOf(
        "refund", "reversal", "reversed", "cashback", "money back"
    )

    // Pending keywords
    val PENDING_KEYWORDS = listOf(
        "pending", "processing", "initiated", "in progress"
    )

    // Common merchant patterns
    val MERCHANT_AT_PATTERN = Regex("""(?:at|to)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,40})""", RegexOption.IGNORE_CASE)
    val UPI_REF_PATTERN = Regex("""(?:UPI\s*Ref\.?\s*(?:No\.?)?\s*:?\s*)([0-9]{10,20})""", RegexOption.IGNORE_CASE)
    val TXN_REF_PATTERN = Regex("""(?:Txn\s*(?:ID|Ref)\.?\s*:?\s*)([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)

    // Non-expense exclusion keywords (OTP, balance inquiries, promotional, spam)
    val OTP_KEYWORDS = listOf(
        "otp", "one time password", "verification code", "security code",
        "login code", "auth code", "do not share", "secret code", "valid for"
    )

    val PROMO_KEYWORDS = listOf(
        "apply now", "pre-approved", "loan approved", "personal loan",
        "eligible for a loan", "credit limit increased", "congratulations! you",
        "reward points", "scratch card", "claim your prize", "flat 50% off",
        "exclusive offer", "win up to"
    )

    val BALANCE_ENQUIRY_KEYWORDS = listOf(
        "available balance", "avl bal", "balance is", "current balance",
        "balance enquiry", "acct bal"
    )

    fun isNonExpenseMessage(text: String): Boolean {
        val lower = text.lowercase()
        // 1. Any OTP or verification message must NEVER be parsed as an expense transaction
        if (OTP_KEYWORDS.any { it in lower }) return true

        // 2. Promotional and spam messages with financial terms but no actual debit
        if (PROMO_KEYWORDS.any { it in lower } && !DEBIT_KEYWORDS.any { lower.contains(it) }) return true

        // 3. Balance inquiry messages with no debit or credit action
        if (BALANCE_ENQUIRY_KEYWORDS.any { it in lower } &&
            !DEBIT_KEYWORDS.any { lower.contains(it) } &&
            !CREDIT_KEYWORDS.any { lower.contains(it) }) return true

        return false
    }

    fun extractAmount(text: String): Long? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        val raw = match.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()?.let { (it * 100).toLong() }
    }

    fun extractAllAmounts(text: String): List<Long> {
        return AMOUNT_REGEX.findAll(text)
            .mapNotNull { match ->
                match.groupValues[1].replace(",", "").toDoubleOrNull()?.let { (it * 100).toLong() }
            }
            .toList()
    }

    fun detectDirection(text: String): TransactionDirection {
        val lower = text.lowercase()
        val debitIndex = DEBIT_KEYWORDS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val creditIndex = CREDIT_KEYWORDS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()

        return when {
            debitIndex != null && creditIndex == null -> TransactionDirection.DEBIT
            creditIndex != null && debitIndex == null -> TransactionDirection.CREDIT
            debitIndex != null && creditIndex != null -> {
                // If debit action appears first in the message (e.g. "Paid ... Cashback earned")
                if (debitIndex <= creditIndex) TransactionDirection.DEBIT
                else TransactionDirection.CREDIT
            }
            else -> TransactionDirection.UNKNOWN
        }
    }

    // Transfer keywords
    val TRANSFER_KEYWORDS = listOf(
        "transferred to", "transfer to", "sent to", "sent money",
        "to a/c", "to account", "transfer of", "fund transfer",
        "p2p", "upi transfer", "imps", "neft"
    )

    fun detectRecordType(text: String, direction: TransactionDirection): RecordType {
        val lower = text.lowercase()
        return when {
            FAILURE_KEYWORDS.any { it in lower } -> RecordType.FAILED
            REFUND_KEYWORDS.any { it in lower } && direction == TransactionDirection.CREDIT -> RecordType.REFUND
            PENDING_KEYWORDS.any { it in lower } -> RecordType.PENDING
            TRANSFER_KEYWORDS.any { it in lower } -> RecordType.TRANSFER
            direction == TransactionDirection.DEBIT -> RecordType.EXPENSE
            direction == TransactionDirection.CREDIT -> RecordType.INCOME
            else -> RecordType.UNKNOWN
        }
    }

    fun extractMerchant(text: String): String {
        return MERCHANT_AT_PATTERN.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractRef(text: String): String? {
        return UPI_REF_PATTERN.find(text)?.groupValues?.get(1)
            ?: TXN_REF_PATTERN.find(text)?.groupValues?.get(1)
    }

    fun calculateConfidence(
        hasAmount: Boolean,
        directionKnown: Boolean,
        recordTypeKnown: Boolean,
        hasMerchant: Boolean,
        hasRef: Boolean
    ): Float {
        var score = 0f
        if (hasAmount) score += 0.4f
        if (directionKnown) score += 0.3f
        if (recordTypeKnown) score += 0.1f
        if (hasMerchant) score += 0.1f
        if (hasRef) score += 0.1f
        return score.coerceIn(0f, 1f)
    }
}
