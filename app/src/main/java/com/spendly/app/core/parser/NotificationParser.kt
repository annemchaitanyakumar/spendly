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
        "debited", "debit", "paid", "payment of", "payment successful", "payment done",
        "spent", "transferred", "sent", "withdrawn", "purchase of", "charged",
        "auto-debit", "emi paid", "successful payment", "payment"
    )

    // Credit keywords (money coming in)
    val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "refund", "cashback",
        "added to", "money received", "amount received", "deposit", "deposited"
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

    val UPI_REF_PATTERN = Regex("""(?:UPI\s*Ref\.?\s*(?:No\.?)?\s*:?\s*)([0-9]{10,20})""", RegexOption.IGNORE_CASE)
    val TXN_REF_PATTERN = Regex("""(?:Txn\s*(?:ID|Ref)\.?\s*:?\s*)([A-Z0-9]{6,20})""", RegexOption.IGNORE_CASE)

    // Non-expense exclusion keywords (OTP, balance inquiries, promotional, spam)
    val OTP_KEYWORDS = listOf(
        "otp", "one time password", "verification code", "security code",
        "login code", "auth code", "do not share", "secret code", "valid for"
    )

    // Bill due reminders, telecom/broadband bills, upcoming invoices (e.g. Airtel Wi-Fi, Jio, Electricity, CC due)
    val BILL_DUE_REMINDER_KEYWORDS = listOf(
        "due date", "due on", "bill due", "bill is due", "bill generated",
        "bill has been generated", "bill of rs", "bill for rs", "e-bill",
        "amount due", "total amount due", "minimum amount due", "min amount due",
        "pay your bill", "pay bill", "pay now to avoid", "pay to avoid",
        "avoid disconnection", "late fee", "payable by", "payment due",
        "bill reminder", "reminder: your bill", "outstanding bill", "total outstanding",
        "pack expiring", "validity expiring", "plan expiring", "recharge due",
        "recharge your", "recharge now", "statement generated", "broadband bill",
        "wifi bill", "fiber bill", "electricity bill", "water bill", "gas bill"
    )

    // Explicit past-tense debit phrases that confirm an actual executed payment (not a future bill)
    val CONFIRMED_PAST_DEBIT_KEYWORDS = listOf(
        "debited by", "debited from", "debited for", "debited with", "has been debited",
        "is debited", "was debited", "paid successfully", "payment successful",
        "successfully paid", "auto-debited", "withdrawn from"
    )

    // UPI collect requests / money requests (NOT completed transactions)
    val PAYMENT_REQUEST_KEYWORDS = listOf(
        "requested money", "requesting money", "has requested", "requested rs",
        "requested inr", "requested ₹", "collect request", "approve request",
        "pay request"
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

        // 2. UPI collect requests or money requests are NOT completed expenses
        if (PAYMENT_REQUEST_KEYWORDS.any { it in lower }) return true

        // 3. Bill due notices, telecom/broadband bills, invoice reminders
        // If it's a bill due reminder without an explicit past-tense debit ("debited by", "paid successfully"), ignore it!
        if (BILL_DUE_REMINDER_KEYWORDS.any { it in lower }) {
            val isConfirmedDebit = CONFIRMED_PAST_DEBIT_KEYWORDS.any { lower.contains(it) }
            if (!isConfirmedDebit) return true
        }

        // 4. Promotional and spam messages with financial terms but no actual debit
        if (PROMO_KEYWORDS.any { it in lower } && !CONFIRMED_PAST_DEBIT_KEYWORDS.any { lower.contains(it) }) return true

        // 5. Balance inquiry messages with no debit or credit action
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
        // Sanitize out non-credit uses of "credit" to prevent false CREDIT detection
        val sanitized = lower
            .replace("credit card", "cc")
            .replace("credit limit", "limit")
            .replace("credit score", "score")
            .replace("credit line", "line")
            .replace("avlbl credit", "limit")
            .replace("available credit", "limit")

        val hasDebit = DEBIT_KEYWORDS.any { sanitized.contains(it) }
        val hasCredit = CREDIT_KEYWORDS.any { sanitized.contains(it) }

        if (hasDebit && !hasCredit) return TransactionDirection.DEBIT
        if (hasCredit && !hasDebit) return TransactionDirection.CREDIT

        if (hasDebit && hasCredit) {
            // E.g. "Your A/c XX2636 debited by Rs 1.00 ... amount credited to recipient" -> user account was DEBITED
            if (sanitized.contains("debited") && (sanitized.contains("to a/c") || sanitized.contains("to vpa") || sanitized.contains("transferred to"))) {
                return TransactionDirection.DEBIT
            }
            val debitIndex = DEBIT_KEYWORDS.map { sanitized.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
            val creditIndex = CREDIT_KEYWORDS.map { sanitized.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
            return if (debitIndex <= creditIndex) TransactionDirection.DEBIT else TransactionDirection.CREDIT
        }

        return TransactionDirection.UNKNOWN
    }

    // Transfer & self-transfer keywords
    val TRANSFER_KEYWORDS = listOf(
        "self", "to self", "self transfer", "own account", "own a/c",
        "between accounts", "to your account", "to your own",
        "transferred to", "transfer to", "transferred", "transfer of",
        "sent to", "sent money", "send money to", "send money",
        "to a/c", "to account", "fund transfer", "funds transfer",
        "p2p", "upi transfer", "imps", "neft", "rtgs"
    )

    fun isLikelyPersonPayee(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val lower = trimmed.lowercase()
        if (lower.contains("self") || lower.contains("account") || lower.contains("a/c")) return true

        // Business terms indicate commercial merchants
        val businessKeywords = listOf(
            "store", "shop", "mart", "cafe", "restaurant", "bakery", "pharmacy",
            "ltd", "pvt", "limited", "enterprises", "solutions", "services",
            "bazaar", "hotel", "clinic", "hospital", "petrol", "fuel", "bunk",
            "retail", "digital", "foods", "tech", "com", "agency", "station",
            "stationery", "mall", "supermarket", "cinema", "theatre", "travels",
            "board", "electricity", "broadband", "telecom", "fiber", "uber", "swiggy",
            "zomato", "starbucks", "chai point", "amazon", "flipkart"
        )
        if (businessKeywords.any { lower.contains(it) }) return false

        // 2 to 4 name parts (e.g. Annem Chaitanya Kumar, Rahul Sharma)
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size in 2..4 && words.all { w -> w.all { it.isLetter() || it == '.' } }) {
            return true
        }
        return false
    }

    fun detectRecordType(text: String, direction: TransactionDirection, merchant: String = ""): RecordType {
        val lower = text.lowercase()
        return when {
            FAILURE_KEYWORDS.any { it in lower } -> RecordType.FAILED
            REFUND_KEYWORDS.any { it in lower } && direction == TransactionDirection.CREDIT -> RecordType.REFUND
            PENDING_KEYWORDS.any { it in lower } -> RecordType.PENDING
            direction == TransactionDirection.CREDIT -> RecordType.INCOME
            isLikelyPersonPayee(merchant) -> RecordType.TRANSFER
            TRANSFER_KEYWORDS.any { it in lower } -> RecordType.TRANSFER
            direction == TransactionDirection.DEBIT -> RecordType.EXPENSE
            else -> RecordType.UNKNOWN
        }
    }

    // Pattern 1: Paid/Sent/Transfer [Rs 500] to/at <Name/Merchant>
    private val PAID_TO_PATTERN = Regex(
        """(?:paid|sent|transferred|transfer|payment of)\s+(?:(?:₹|Rs\.?\s*|INR\s*)[0-9,.]+\s+)?(?:to|at)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,35}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|for|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 2: [Rs 500] paid/sent/transferred to/at <Name/Merchant>
    private val AMOUNT_PAID_TO_PATTERN = Regex(
        """(?:(?:₹|Rs\.?\s*|INR\s*)[0-9,.]+\s+)(?:paid|sent|transferred)\s+(?:to|at)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,35}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|for|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 3: to VPA <vpa>
    private val VPA_PATTERN = Regex(
        """(?:to\s+VPA|VPA)\s+([a-zA-Z0-9.\-_@]+)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 4: trf to / transfer to
    private val TRF_PATTERN = Regex(
        """(?:trf\s+to|transfer\s+to)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,30}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 5: at/to <Merchant> (e.g. at Starbucks, at Reliance)
    private val AT_TO_PATTERN = Regex(
        """(?:at|to)\s+([A-Za-z][A-Za-z0-9 &.'-]{1,30}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|for|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val GARBAGE_MERCHANT_WORDS = listOf(
        "check out", "details", "amount credite", "amount debite",
        "avl bal", "available balance", "account", "a/c", "acct",
        "upi ref", "ref no", "statement", "download", "yono", "click",
        "helpline", "terms", "charges", "beneficiary", "credited to",
        "debited from", "balance is", "not you", "call", "sms"
    )

    fun sanitizeMerchantName(name: String): String {
        var result = name.trim().trimEnd('.', ',', '-', ':', ';', ' ')
        val lower = result.lowercase()

        // Reject if matches account numbers (a/c XX2636, *1234, etc.)
        if (lower.startsWith("a/c") || lower.startsWith("acct") || lower.startsWith("account") ||
            lower.matches(Regex("""^(?:xx|\*+)?[0-9]{3,}$""")) ||
            (lower.contains("xx") && lower.any { it.isDigit() })) {
            return ""
        }

        // Reject if contains garbage banking phrases
        if (GARBAGE_MERCHANT_WORDS.any { lower.contains(it) }) {
            return ""
        }

        // Must have at least 2 characters and contain at least one letter
        if (result.length < 2 || !result.any { it.isLetter() }) {
            return ""
        }

        return result
    }

    fun extractMerchant(text: String): String {
        // Try VPA first (e.g. rahul@oksbi)
        val vpaMatch = VPA_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!vpaMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(vpaMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // Try Amount paid to pattern (e.g. ₹1,499 paid to Electricity Board)
        val amountPaidMatch = AMOUNT_PAID_TO_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!amountPaidMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(amountPaidMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // Try Paid/Sent to pattern (e.g. Paid Rs 280 to Uber)
        val paidMatch = PAID_TO_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!paidMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(paidMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // Try TRF to pattern
        val trfMatch = TRF_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!trfMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(trfMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // Try at/to pattern
        val atMatch = AT_TO_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!atMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(atMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        return ""
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
