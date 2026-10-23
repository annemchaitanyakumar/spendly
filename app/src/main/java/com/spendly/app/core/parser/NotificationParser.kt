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
//  Strict Whitelist of Supported Financial Sources
// ─────────────────────────────────────────────────────────────────────

object SupportedPackages {
    // 1. SMS / Messaging Apps (Official Bank & UPI SMS)
    val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",        // Google Messages
        "com.samsung.android.messaging",            // Samsung Messages
        "com.oneplus.mms",                          // OnePlus Messages
        "com.coloros.mms",                          // Oppo Messages
        "com.heytap.mms",                           // Realme / Oppo HeyTap
        "com.oppo.mms",                             // Oppo Messages
        "com.android.mms",                          // Standard AOSP / Xiaomi MIUI MMS
        "com.miui.mms",                             // Xiaomi Messages
        "com.vivo.mms",                             // Vivo / iQOO Messages
        "com.truecaller",                           // Truecaller SMS
        "com.simplemobiletools.sms.messenger"       // Simple SMS
    )

    // 2. Email Apps (Bank & Payment confirmation emails)
    val EMAIL_PACKAGES = setOf(
        "com.google.android.gm",                    // Gmail
        "com.microsoft.office.outlook",             // Microsoft Outlook
        "com.yahoo.mobile.client.android.mail",     // Yahoo Mail
        "com.samsung.android.email.provider",       // Samsung Email
        "ch.protonmail.android",                    // Proton Mail
        "org.kman.AquaMail",                        // Aqua Mail
        "me.bluemail.mail"                          // Blue Mail
    )

    // 3. Official Bank Apps
    val BANK_PACKAGES = setOf(
        "com.snapwork.hdfc",                        // HDFC Bank
        "com.hdfcbank.android.now",                 // HDFC Bank Now / PayZapp
        "com.sbi.lotusintouch",                     // SBI YONO
        "com.spandana.yono",                        // SBI YONO Lite
        "com.sbi.SBIFreedomPlus",                   // SBI Anywhere / Freedom
        "com.csam.icici.bank.imobile",              // ICICI iMobile
        "com.axis.mobile",                          // Axis Mobile
        "com.msf.kbank.mobile",                     // Kotak Mahindra Bank
        "com.kotak.bank.mobile",                    // Kotak Bank Mobile
        "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge", // Kotak 811
        "com.kotak.mobile",                         // Kotak Mobile
        "com.msf.koko",                             // Kotak 811
        "com.pnb.pnbone",                           // PNB ONE
        "com.bankofbaroda.mconnect",                // bob World / BoB
        "com.canarabank.mobility",                  // Canara ai1
        "com.infrasofttech.uboi",                   // Union Bank of India
        "com.unionbank.mobility",                   // Union Bank
        "com.indusind.mpassbook",                   // IndusInd Bank
        "com.fedmobile",                            // Federal Bank FedMobile
        "com.idfcfirstbank.optimus",                // IDFC FIRST Bank
        "money.jupiter",                            // Jupiter Money
        "com.epifi.fincore",                        // Fi Money
        "com.yesbank",                              // YES Bank
        "com.rblbank.mobank",                       // RBL MoBank
        "com.infrasoft.indianbank",                 // IndOASIS Indian Bank
        "com.infrasoft.cboi",                       // Central Bank of India Cent Mobile
        "com.aubank.mobilebanking"                  // AU 0101
    )

    // 4. UPI & Payment Apps
    val UPI_PACKAGES = setOf(
        "com.phonepe.app",                          // PhonePe
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "com.google.android.apps.walletnfcrel",     // Google Wallet
        "net.one97.paytm",                          // Paytm
        "in.org.npci.upiapp",                       // BHIM UPI
        "com.dreamplug.androidapp",                 // CRED
        "com.cred.app",                             // CRED alternate
        "in.amazon.mShop.android.shopping",         // Amazon Pay India
        "com.amazon.mShop.android.shopping",        // Amazon Pay Global
        "com.whatsapp",                             // WhatsApp Pay
        "com.samsung.android.spay",                 // Samsung Wallet / Pay
        "com.samsung.android.raja.spay",            // Samsung Pay India
        "com.slicepay",                             // Slice UPI
        "com.fampay.in",                            // FamPay
        "com.famobi",                               // FamPay alt
        "com.mobikwik_new",                         // MobiKwik
        "com.freecharge.android",                   // Freecharge
        "com.navi.finance",                         // Navi
        "money.super.app",                          // super.money
        "com.tatadigital.tcp"                       // Tata Neu
    )

    val ALL_SUPPORTED: Set<String> = SMS_PACKAGES + EMAIL_PACKAGES + BANK_PACKAGES + UPI_PACKAGES

    fun isSupported(packageName: String): Boolean = packageName in ALL_SUPPORTED
    fun isEmail(packageName: String): Boolean = packageName in EMAIL_PACKAGES
    fun isSms(packageName: String): Boolean = packageName in SMS_PACKAGES
    fun isBank(packageName: String): Boolean = packageName in BANK_PACKAGES
    fun isUpi(packageName: String): Boolean = packageName in UPI_PACKAGES
}

// ─────────────────────────────────────────────────────────────────────
//  Shared regex patterns
// ─────────────────────────────────────────────────────────────────────

internal object CommonPatterns {
    // Amount: ₹1,234.56 or Rs. 1234 or INR 500 or ₹ 1.00
    val AMOUNT_REGEX = Regex(
        """(?:₹\s*|Rs\.?\s*|INR\s*|inr\s*)([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    val SUFFIX_AMOUNT_REGEX = Regex(
        """([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:₹|Rs\.?|INR|\/-)""",
        RegexOption.IGNORE_CASE
    )
    val VERB_AMOUNT_REGEX = Regex(
        """(?:paid|debited|spent|transferred|sent|credited)\s+(?:(?:by|for|with|of|amount\s+of)\s+)?(?:₹\s*|Rs\.?\s*|INR\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    val PAID_TO_AMOUNT_REGEX = Regex(
        """(?:paid|sent|transferred)\s+to\s+[A-Za-z0-9 &.'-]{1,30}?\s+(?:₹\s*|Rs\.?\s*|INR\s*)?([0-9,]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // Debit keywords (must indicate actual money leaving)
    val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "paid", "payment of", "payment successful", "payment done",
        "spent", "transferred", "sent", "withdrawn", "purchase of", "charged",
        "auto-debit", "emi paid", "successful payment"
    )

    // Credit keywords (money coming in - must be confirmed verbs)
    val CREDIT_KEYWORDS = listOf(
        "payment received", "money received", "amount received", "credited",
        "received", "credited to", "has been credited", "was credited",
        "is credited", "credited with", "refund received", "refund credited"
    )

    // Failure keywords
    val FAILURE_KEYWORDS = listOf(
        "failed", "failure", "declined", "rejected", "unsuccessful",
        "could not", "reversal"
    )

    // Refund keywords (must be confirmed refund statements)
    val REFUND_KEYWORDS = listOf(
        "refund of", "refund received", "refund credited", "refund processed",
        "refund initiated", "reversal of", "reversed", "money back"
    )

    // Pending keywords
    val PENDING_KEYWORDS = listOf(
        "pending", "processing", "initiated", "in progress"
    )

    // 12-digit UPI reference number patterns (UTR / RRN / UPI Ref)
    val UPI_12_DIGIT_PATTERN = Regex(
        """\b(?:UPI\s*(?:Ref\.?\s*(?:No\.?)?|Transaction\s*ID|Txn\s*ID)?\s*[:/ -]\s*|RRN\s*[:.-]?\s*|UTR\s*[:.-]?\s*)([0-9]{12})\b""",
        RegexOption.IGNORE_CASE
    )
    val UPI_SLASH_PATTERN = Regex(
        """UPI/[0-9]{10,16}/([A-Za-z0-9][A-Za-z0-9 &.'-]{1,35}?)""",
        RegexOption.IGNORE_CASE
    )
    val UPI_REF_PATTERN = Regex("""\b(?:UPI\s*Ref\.?\s*(?:No\.?)?\s*[:.-]?\s*)([0-9]{10,20})\b""", RegexOption.IGNORE_CASE)
    val GENERIC_REF_PATTERN = Regex(
        """\b(?:Ref(?:erence)?\s*(?:No\.?|#)?\s*[:.-]?\s*|Txn\s*(?:ID|Ref|#)\.?\s*[:.-]?\s*|IMPS\s*/|NEFT\s*/)([A-Za-z0-9]{6,25})\b""",
        RegexOption.IGNORE_CASE
    )
    val TXN_REF_PATTERN = Regex("""\b(?:Txn\s*(?:ID|Ref)\.?\s*[:.-]?\s*)([A-Z0-9]{6,20})\b""", RegexOption.IGNORE_CASE)

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
        "exclusive offer", "win up to", "invite friends",
        "t&c apply", "t&c", "terms and conditions", "terms & conditions",
        "start earning", "earn cashback", "earning cashback", "super cashback",
        "win cashback", "get cashback", "cashback offer", "cashback on",
        "don't miss", "dont miss", "chance to win", "stand a chance",
        "add ₹", "add rs", "add money", "load wallet", "deposit to earn",
        "save more", "offer valid", "valid till", "hurry up", "limited period offer",
        "invest in", "open fd", "open account", "open 811", "start sip", "pre-qualified"
    )

    val BALANCE_ENQUIRY_KEYWORDS = listOf(
        "available balance", "avl bal", "balance is", "current balance",
        "balance enquiry", "acct bal"
    )

    // Cancellations (subscription cancelled, order cancelled, etc.)
    val CANCELLATION_KEYWORDS = listOf(
        "subscription cancelled", "subscription canceled", "cancelled subscription",
        "canceled subscription", "order cancelled", "order canceled", "cancelled your order",
        "booking cancelled", "ticket cancelled", "reservation cancelled",
        "cancellation confirmed", "cancelled successfully", "autopay cancelled",
        "auto-pay cancelled", "mandate cancelled", "mandate revoked"
    )

    // Suggested replacements, catalog recommendations, subscribe & save
    val REPLACEMENT_RECOMMENDATION_KEYWORDS = listOf(
        "suggested replacement", "suggested replacements", "choose as replacement",
        "recommended for you", "suggested for you", "recommendations for you",
        "reactivate subscription", "reactivate your subscription", "reactivate and edit",
        "reactivate", "subscribe & save", "subscribe and save",
        "customers who bought", "similar items", "top picks for you", "explore similar",
        "buy again", "items you might like", "more items like this"
    )

    // Cart & abandoned shopping reminders
    val CART_REMINDER_KEYWORDS = listOf(
        "items in your cart", "cart reminder", "your cart is waiting",
        "saved for later", "abandoned cart", "still interested in",
        "left in your cart", "continue shopping", "added to your cart"
    )

    // Delivery status notifications without actual payment
    val DELIVERY_STATUS_KEYWORDS = listOf(
        "out for delivery", "package delivered", "order delivered",
        "arriving today", "arriving tomorrow", "shipped with", "in transit",
        "package has been delivered", "shipment update"
    )

    // Feedback & review requests
    val FEEDBACK_KEYWORDS = listOf(
        "rate your purchase", "how was your delivery", "share your feedback",
        "write a review", "rate your order"
    )

    /**
     * Strict word-boundary matching so words like 'sent' don't match 'essentials' or 'consent',
     * while properly supporting symbols like '₹' and punctuation.
     */
    fun matchesWord(text: String, word: String): Boolean {
        if (!text.contains(word, ignoreCase = true)) return false
        val escaped = Regex.escape(word)
        val pattern = Regex("""(?<=^|[\s\p{Punct}])$escaped(?=[\s\p{Punct}]|$)""", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    /**
     * Find word index using strict word boundaries.
     */
    fun findWordPosition(text: String, word: String): Int {
        if (!text.contains(word, ignoreCase = true)) return -1
        val escaped = Regex.escape(word)
        val pattern = Regex("""(?<=^|[\s\p{Punct}])$escaped(?=[\s\p{Punct}]|$)""", RegexOption.IGNORE_CASE)
        return pattern.find(text)?.range?.first ?: -1
    }

    fun isNonExpenseMessage(text: String): Boolean {
        val lower = text.lowercase()
        // 1. Any OTP or verification message must NEVER be parsed as an expense transaction
        if (OTP_KEYWORDS.any { it in lower }) return true

        // 2. Cancellations (unless accompanied by an explicit refund keyword)
        if (CANCELLATION_KEYWORDS.any { lower.contains(it) } && !REFUND_KEYWORDS.any { lower.contains(it) }) return true

        // 3. Replacement suggestions & product catalog recommendations
        if (REPLACEMENT_RECOMMENDATION_KEYWORDS.any { lower.contains(it) }) return true

        // 4. Cart reminders
        if (CART_REMINDER_KEYWORDS.any { lower.contains(it) }) return true

        // 5. Delivery status updates without an explicit payment debit
        if (DELIVERY_STATUS_KEYWORDS.any { lower.contains(it) } && !CONFIRMED_PAST_DEBIT_KEYWORDS.any { lower.contains(it) }) return true

        // 6. Feedback / rating requests
        if (FEEDBACK_KEYWORDS.any { lower.contains(it) }) return true

        // 7. UPI collect requests or money requests are NOT completed expenses
        if (PAYMENT_REQUEST_KEYWORDS.any { it in lower }) return true

        // 8. Bill due notices, telecom/broadband bills, invoice reminders
        if (BILL_DUE_REMINDER_KEYWORDS.any { it in lower }) {
            val isConfirmedDebit = CONFIRMED_PAST_DEBIT_KEYWORDS.any { lower.contains(it) }
            if (!isConfirmedDebit) return true
        }

        // 9. Promotional and spam messages with financial terms but no actual debit
        if (PROMO_KEYWORDS.any { it in lower } && !CONFIRMED_PAST_DEBIT_KEYWORDS.any { lower.contains(it) }) return true

        // 10. Balance inquiry messages with no debit or credit action
        if (BALANCE_ENQUIRY_KEYWORDS.any { it in lower } &&
            !DEBIT_KEYWORDS.any { matchesWord(lower, it) } &&
            !CREDIT_KEYWORDS.any { matchesWord(lower, it) }) return true

        return false
    }

    // Official Bank & Financial institutions
    private val KNOWN_FINANCIAL_INSTITUTIONS = listOf(
        "hdfc", "sbi", "icici", "axis", "kotak", "indusind", "standard chartered",
        "amex", "american express", "citi", "citibank", "pnb", "punjab national",
        "bob", "bank of baroda", "canara", "union bank", "federal bank", "idfc",
        "jupiter", "fi money", "rbl", "yes bank", "hsbc", "dbs", "cred",
        "phonepe", "paytm", "google pay", "gpay", "bhim", "amazon pay", "paypal"
    )

    private val ACCOUNT_OR_CARD_MARKERS = listOf(
        "a/c", "acct", "account", "card ending", "ending in", "ending with",
        "xx", "vpa", "rrn", "utr", "ref no", "ref. no", "reference number", "txn id"
    )

    private val VERIFIED_INVOICE_PHRASES = listOf(
        "tax invoice", "invoice for order", "order confirmed", "your order has been placed",
        "payment received for order", "receipt for order", "payment confirmation for order"
    )

    /**
     * Strictly verifies whether an email notification is an authentic financial transaction.
     */
    fun isVerifiedFinancialEmail(title: String, text: String, bigText: String?): Boolean {
        val full = listOfNotNull(title, text, bigText).joinToString(" ").lowercase()

        // 1. Must not match any non-expense / promo / replacement / cancellation marker
        if (isNonExpenseMessage(full)) return false

        // 2. Legitimate bank alert email check:
        val hasBank = KNOWN_FINANCIAL_INSTITUTIONS.any { full.contains(it) }
        val hasAccountMarker = ACCOUNT_OR_CARD_MARKERS.any { full.contains(it) }
        val hasDebitCreditAction = CONFIRMED_PAST_DEBIT_KEYWORDS.any { full.contains(it) } ||
                CREDIT_KEYWORDS.any { matchesWord(full, it) }

        if (hasBank && hasAccountMarker && hasDebitCreditAction) {
            return true
        }

        // 3. Legitimate merchant order confirmation / tax invoice check:
        val hasVerifiedInvoice = VERIFIED_INVOICE_PHRASES.any { full.contains(it) }
        if (hasVerifiedInvoice) {
            return true
        }

        return false
    }

    fun extractAmount(text: String): Long? {
        val prefixMatch = AMOUNT_REGEX.find(text)
        if (prefixMatch != null) {
            val raw = prefixMatch.groupValues[1].replace(",", "")
            val parsed = raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            if (parsed != null && parsed > 0L) return parsed
        }
        val suffixMatch = SUFFIX_AMOUNT_REGEX.find(text)
        if (suffixMatch != null) {
            val raw = suffixMatch.groupValues[1].replace(",", "")
            val parsed = raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            if (parsed != null && parsed > 0L) return parsed
        }
        val verbMatch = VERB_AMOUNT_REGEX.find(text)
        if (verbMatch != null) {
            val raw = verbMatch.groupValues[1].replace(",", "")
            val parsed = raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            if (parsed != null && parsed > 0L) return parsed
        }
        val paidToMatch = PAID_TO_AMOUNT_REGEX.find(text)
        if (paidToMatch != null) {
            val raw = paidToMatch.groupValues[1].replace(",", "")
            val parsed = raw.toDoubleOrNull()?.let { (it * 100).toLong() }
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }

    fun extractAllAmounts(text: String): List<Long> {
        val results = mutableListOf<Long>()
        AMOUNT_REGEX.findAll(text).forEach { m ->
            m.groupValues[1].replace(",", "").toDoubleOrNull()?.let { (it * 100).toLong() }?.let { results.add(it) }
        }
        if (results.isEmpty()) {
            SUFFIX_AMOUNT_REGEX.findAll(text).forEach { m ->
                m.groupValues[1].replace(",", "").toDoubleOrNull()?.let { (it * 100).toLong() }?.let { results.add(it) }
            }
        }
        return results
    }

    fun detectDirection(text: String): TransactionDirection {
        val lower = text.lowercase()
        val sanitized = lower
            .replace("credit card", "cc")
            .replace("credit limit", "limit")
            .replace("credit score", "score")
            .replace("credit line", "line")
            .replace("avlbl credit", "limit")
            .replace("available credit", "limit")

        // Explicit strong phrases
        val strongCreditPhrases = listOf(
            "credit alert", "payment received", "money received", "amount received",
            "received from", "received in", "credited to", "credited with",
            "amount credited", "has been credited", "was credited", "is credited",
            "refund of", "refund initiated", "refund processed", "refund received"
        )

        val strongDebitPhrases = listOf(
            "debit alert", "was debited", "is debited", "has been debited",
            "debited by", "debited from", "debited for", "auto-debited",
            "paid successfully", "successfully paid", "payment successful",
            "payment done", "paid to", "paid at", "payment to", "payment of",
            "spent at", "spent on", "transferred to", "sent to",
            "paid rs", "paid ₹", "paid inr"
        )

        // Find earliest occurrence of strong indicators
        val firstStrongCredit = strongCreditPhrases
            .map { findWordPosition(sanitized, it) }
            .filter { it >= 0 }
            .minOrNull()

        val firstStrongDebit = strongDebitPhrases
            .map { findWordPosition(sanitized, it) }
            .filter { it >= 0 }
            .minOrNull()

        if (firstStrongCredit != null && firstStrongDebit != null) {
            return if (firstStrongCredit < firstStrongDebit) TransactionDirection.CREDIT else TransactionDirection.DEBIT
        }
        if (firstStrongCredit != null) return TransactionDirection.CREDIT
        if (firstStrongDebit != null) return TransactionDirection.DEBIT

        // Fallback to keyword sets with strict word-boundary position checking
        val firstDebit = DEBIT_KEYWORDS
            .map { findWordPosition(sanitized, it) }
            .filter { it >= 0 }
            .minOrNull()

        val firstCredit = CREDIT_KEYWORDS
            .map { findWordPosition(sanitized, it) }
            .filter { it >= 0 }
            .minOrNull()

        if (firstDebit != null && firstCredit != null) {
            return if (firstDebit < firstCredit) TransactionDirection.DEBIT else TransactionDirection.CREDIT
        }
        if (firstDebit != null) return TransactionDirection.DEBIT
        if (firstCredit != null) return TransactionDirection.CREDIT

        return TransactionDirection.UNKNOWN
    }

    // Transfer & self-transfer keywords
    val TRANSFER_KEYWORDS = listOf(
        "self", "to self", "self transfer", "own account", "own a/c",
        "between accounts", "to your account", "to your own",
        "transferred to", "transfer to", "transferred", "transfer of", "transfer", "trf to", "trf",
        "sent to", "sent money", "send money to", "send money", "sent",
        "to a/c", "to account", "fund transfer", "funds transfer",
        "p2p", "upi transfer", "to vpa", "towards upi", "towards", "imps", "neft", "rtgs"
    )

    fun isLikelyPersonPayee(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val lower = trimmed.lowercase()
        if (lower.contains("self") || lower.contains("account") || lower.contains("a/c")) return true

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

        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size in 2..4 && words.all { w -> w.all { it.isLetter() || it == '.' } }) {
            return true
        }
        return false
    }

    fun detectRecordType(text: String, direction: TransactionDirection, merchant: String = ""): RecordType {
        val lower = text.lowercase()
        return when {
            FAILURE_KEYWORDS.any { matchesWord(lower, it) } -> RecordType.FAILED
            REFUND_KEYWORDS.any { matchesWord(lower, it) } && direction == TransactionDirection.CREDIT -> RecordType.REFUND
            PENDING_KEYWORDS.any { matchesWord(lower, it) } -> RecordType.PENDING
            direction == TransactionDirection.CREDIT -> RecordType.INCOME
            isLikelyPersonPayee(merchant) -> RecordType.TRANSFER
            TRANSFER_KEYWORDS.any { matchesWord(lower, it) } -> RecordType.TRANSFER
            direction == TransactionDirection.DEBIT -> RecordType.EXPENSE
            else -> RecordType.UNKNOWN
        }
    }

    // Pattern: Received [Rs 10] from <Sender> or credited ... from <Sender>
    private val RECEIVED_FROM_PATTERN = Regex(
        """(?:(?:received|credited)(?:\s+(?:(?:₹|Rs\.?\s*|INR\s*)[0-9,.]+\s*)?|\s+money\s+|\s+funds\s+|\s+alert\s*:\s*(?:(?:₹|Rs\.?\s*|INR\s*)[0-9,.]+\s*)?)from)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,35}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|in|into|for|avl|amount|successfully|\.)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 1: Paid/Sent/Transfer [Rs 500] to/at <Name/Merchant>
    private val PAID_TO_PATTERN = Regex(
        """(?:paid|sent|transferred|transfer|payment(?:\s+of)?|paying)\s+(?:(?:₹|Rs\.?\s*|INR\s*)[0-9,.]+\s+)?(?:to|at)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,35}?)""" +
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

    // Pattern 4: trf to / transfer to / sent to
    private val TRF_PATTERN = Regex(
        """(?:trf\s+to|transfer\s+to|towards\s+transfer\s+to|sent\s+to)\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,30}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 5: at <Merchant> (requires explicit spending verb)
    private val AT_PATTERN = Regex(
        """\b(?:paid\s+at|spent\s+at|swiped\s+at|shopped\s+at|purchase\s+at|purchase\s+from)\s+([A-Za-z][A-Za-z0-9 &.'-]{1,30}?)""" +
        """(?:\s+(?:on|via|ref|upi|using|from|for|avl)\b|[\.,;]|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val GARBAGE_MERCHANT_WORDS = listOf(
        "check out", "details", "amount credite", "amount debite",
        "avl bal", "available balance", "account", "a/c", "acct",
        "upi ref", "ref no", "statement", "download", "yono", "click",
        "helpline", "terms", "charges", "beneficiary", "credited to",
        "debited from", "balance is", "not you", "call", "sms",
        "view the", "below e-mail", "e-mailer", "mailer", "email", "view",
        "account transfer", "alert", "notice", "update", "kotak811",
        "messages", "message", "bank sms", "samsung messages", "gmail",
        "outlook", "yahoo mail", "any time", "all times", "anytime",
        "this time", "your convenience", "reactivate", "subscription",
        "replacement", "replacements", "choose as", "schedule", "product",
        "products", "item", "items", "money transfer", "transfer", "expense", "general"
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
        // 1. Received from pattern: "₹10.00 received from ANNEM RAMA..." -> "ANNEM RAMA"
        val recvMatch = RECEIVED_FROM_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!recvMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(recvMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 2. UPI slash pattern: UPI/<ref>/<merchant> (e.g. UPI/429612345678/Sri Sai Store)
        val slashMatch = UPI_SLASH_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!slashMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(slashMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 3. VPA pattern (e.g. rahul@oksbi)
        val vpaMatch = VPA_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!vpaMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(vpaMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 4. Amount paid to pattern (e.g. ₹1,499 paid to Electricity Board)
        val amountPaidMatch = AMOUNT_PAID_TO_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!amountPaidMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(amountPaidMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 5. Paid/Sent to pattern (e.g. Paid Rs 280 to Uber)
        val paidMatch = PAID_TO_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!paidMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(paidMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 6. TRF to pattern
        val trfMatch = TRF_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!trfMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(trfMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        // 7. At pattern (e.g. at Starbucks, at Reliance)
        val atMatch = AT_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        if (!atMatch.isNullOrBlank()) {
            val cleaned = sanitizeMerchantName(atMatch)
            if (cleaned.isNotBlank()) return cleaned
        }

        return ""
    }

    fun extractRef(text: String): String? {
        val upi12 = UPI_12_DIGIT_PATTERN.find(text)?.groupValues?.get(1)
        if (!upi12.isNullOrBlank() && upi12.length >= 10) return upi12
        val upiRef = UPI_REF_PATTERN.find(text)?.groupValues?.get(1)
        if (!upiRef.isNullOrBlank() && upiRef.length >= 10) return upiRef
        val generic = GENERIC_REF_PATTERN.find(text)?.groupValues?.get(1)
        if (!generic.isNullOrBlank() && generic.length >= 6 &&
            !generic.equals("erence", ignoreCase = true) &&
            !generic.equals("number", ignoreCase = true) &&
            generic.any { it.isDigit() }) {
            return generic
        }
        val txn = TXN_REF_PATTERN.find(text)?.groupValues?.get(1)
        if (!txn.isNullOrBlank() && txn.any { it.isDigit() }) return txn
        return null
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
