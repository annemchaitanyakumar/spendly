package com.spendly.app.core.parser

import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection

// ─────────────────────────────────────────────────────────────────────
//  PhonePe Parser
// ─────────────────────────────────────────────────────────────────────

class PhonePeParser : NotificationParser {
    override val name = "PhonePe"
    override val supportedPackages = setOf("com.phonepe.app")

    // e.g. "₹150 paid to Swiggy" or "Paid ₹1 to Chaithanya" or "₹1,200.00 sent to John via UPI Ref No. 123456789012"
    private val paidToPattern = Regex(
        """(?:(?:₹\s*|Rs\.?\s*|INR\s*)([0-9,]+(?:\.[0-9]{1,2})?)\s+(?:paid|sent|transferred)\s+to|(?:paid|sent|transferred)\s+(?:(?:₹\s*|Rs\.?\s*|INR\s*)([0-9,]+(?:\.[0-9]{1,2})?)\s+to|to))\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,40})""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val rawDirection = CommonPatterns.detectDirection(fullText)
        val direction = if (rawDirection != TransactionDirection.UNKNOWN) rawDirection else {
            when {
                fullText.contains("paid", ignoreCase = true) || fullText.contains("sent", ignoreCase = true) || fullText.contains("debited", ignoreCase = true) -> TransactionDirection.DEBIT
                fullText.contains("received", ignoreCase = true) || fullText.contains("credited", ignoreCase = true) -> TransactionDirection.CREDIT
                else -> TransactionDirection.UNKNOWN
            }
        }
        val match = paidToPattern.find(fullText)
        val rawMerchant = match?.groupValues?.get(3)?.trim()?.takeIf { it.isNotBlank() }
            ?: match?.groupValues?.get(2)?.trim()?.takeIf { it.isNotBlank() }
            ?: CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence >= 0.3f && direction != TransactionDirection.UNKNOWN) {
            ParsedTransaction(
                amountMinor = amount,
                direction = direction,
                recordType = recordType,
                merchant = merchant,
                transactionRef = ref,
                confidence = confidence,
                rawText = fullText,
                parserName = name
            )
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Google Pay Parser
// ─────────────────────────────────────────────────────────────────────

class GooglePayParser : NotificationParser {
    override val name = "Google Pay"
    override val supportedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel"
    )

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val rawDirection = CommonPatterns.detectDirection(fullText)
        val direction = if (rawDirection != TransactionDirection.UNKNOWN) rawDirection else {
            when {
                fullText.contains("paid", ignoreCase = true) || fullText.contains("sent", ignoreCase = true) || fullText.contains("debited", ignoreCase = true) -> TransactionDirection.DEBIT
                fullText.contains("received", ignoreCase = true) || fullText.contains("credited", ignoreCase = true) -> TransactionDirection.CREDIT
                else -> TransactionDirection.UNKNOWN
            }
        }
        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence >= 0.3f && direction != TransactionDirection.UNKNOWN) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Paytm Parser
// ─────────────────────────────────────────────────────────────────────

class PaytmParser : NotificationParser {
    override val name = "Paytm"
    override val supportedPackages = setOf("net.one97.paytm")

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText)
        val direction = CommonPatterns.detectDirection(fullText)
        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = amount != null,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  BHIM UPI Parser
// ─────────────────────────────────────────────────────────────────────

class BhimParser : NotificationParser {
    override val name = "BHIM"
    override val supportedPackages = setOf("in.org.npci.upiapp")

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  CRED Parser
// ─────────────────────────────────────────────────────────────────────

class CredParser : NotificationParser {
    override val name = "CRED"
    override val supportedPackages = setOf("com.dreamplug.androidapp")

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Amazon Pay Parser
// ─────────────────────────────────────────────────────────────────────

class AmazonPayParser : NotificationParser {
    override val name = "Amazon Pay"
    override val supportedPackages = setOf(
        "in.amazon.mShop.android.shopping",
        "com.amazon.mShop.android.shopping"
    )

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Other UPI Apps Parser (MobiKwik, Freecharge, Navi, Super.money, Tata Neu)
// ─────────────────────────────────────────────────────────────────────

class OtherUpiParser : NotificationParser {
    override val name = "UPI App"
    override val supportedPackages = SupportedPackages.UPI_PACKAGES

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        if (direction == TransactionDirection.UNKNOWN) return null

        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = true,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Email Parser (Gmail, Outlook, Yahoo Mail, Samsung Email receiving Bank alerts)
// ─────────────────────────────────────────────────────────────────────

class EmailParser : NotificationParser {
    override val name = "Bank Email"
    override val supportedPackages = SupportedPackages.EMAIL_PACKAGES

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        // Strictly verify that this email is from an authentic bank or verified order invoice
        if (!CommonPatterns.isVerifiedFinancialEmail(title, text, bigText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        // Email must strictly have verified debit or credit
        if (direction == TransactionDirection.UNKNOWN) return null

        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = true,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Official Bank App Parser (HDFC, SBI YONO, ICICI iMobile, Axis, Kotak, etc.)
// ─────────────────────────────────────────────────────────────────────

class BankAppParser : NotificationParser {
    override val name = "Bank App"
    override val supportedPackages = SupportedPackages.BANK_PACKAGES

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        if (direction == TransactionDirection.UNKNOWN) return null

        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = true,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  SMS / Messages Parser (Google Messages, Samsung Messages, OnePlus MMS, etc.)
// ─────────────────────────────────────────────────────────────────────

class SmsParser : NotificationParser {
    override val name = "Bank SMS"
    override val supportedPackages = SupportedPackages.SMS_PACKAGES

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText) ?: return null
        val direction = CommonPatterns.detectDirection(fullText)
        if (direction == TransactionDirection.UNKNOWN) return null

        val rawMerchant = CommonPatterns.extractMerchant(fullText)
        val merchant = CommonPatterns.sanitizeMerchantName(rawMerchant)
        val recordType = CommonPatterns.detectRecordType(fullText, direction, merchant)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = true,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
            ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
        } else null
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Parser Registry (Strict Whitelist + Multi-Source Architecture)
// ─────────────────────────────────────────────────────────────────────

class NotificationParserRegistry {
    private val specificParsers: List<NotificationParser> = listOf(
        PhonePeParser(),
        GooglePayParser(),
        PaytmParser(),
        BhimParser(),
        CredParser(),
        AmazonPayParser()
    )

    private val otherUpiParser = OtherUpiParser()
    private val emailParser = EmailParser()
    private val bankAppParser = BankAppParser()
    private val smsParser = SmsParser()

    /**
     * Parse notification text strictly from authorized financial sources.
     * Returns null immediately if source is not in the verified whitelist.
     */
    fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        // STRICT WHITELIST: Only process emails, messages, official bank apps, and UPI apps
        if (!SupportedPackages.isSupported(sourcePackage)) {
            return null
        }

        // 1. Try dedicated UPI app parsers (PhonePe, GPay, Paytm, BHIM, CRED, Amazon Pay)
        val specific = specificParsers.firstOrNull { sourcePackage in it.supportedPackages }
        if (specific != null) {
            val result = specific.parse(sourcePackage, title, text, bigText)
            if (result != null && isValidFinancialTransaction(result)) return result
        }

        // 2. Try appropriate category parser based on package type
        val candidate = when {
            SupportedPackages.isEmail(sourcePackage) -> emailParser.parse(sourcePackage, title, text, bigText)
            SupportedPackages.isSms(sourcePackage) -> smsParser.parse(sourcePackage, title, text, bigText)
            SupportedPackages.isBank(sourcePackage) -> bankAppParser.parse(sourcePackage, title, text, bigText)
            SupportedPackages.isUpi(sourcePackage) -> otherUpiParser.parse(sourcePackage, title, text, bigText)
            else -> null
        }

        if (candidate != null && isValidFinancialTransaction(candidate)) {
            return candidate
        }

        return null
    }

    /**
     * Strictly verify that this is a valid debit or credit transaction with known direction and amount.
     */
    private fun isValidFinancialTransaction(tx: ParsedTransaction): Boolean {
        if (tx.amountMinor == null || tx.amountMinor <= 0L) return false
        if (tx.direction == TransactionDirection.UNKNOWN) return false
        if (tx.recordType == RecordType.UNKNOWN) return false
        return true
    }

    /** Get all parsers that support a given package for debug info */
    fun parsersForPackage(sourcePackage: String): List<String> {
        val names = mutableListOf<String>()
        specificParsers.filter { sourcePackage in it.supportedPackages }.forEach { names.add(it.name) }
        when {
            SupportedPackages.isEmail(sourcePackage) -> names.add(emailParser.name)
            SupportedPackages.isSms(sourcePackage) -> names.add(smsParser.name)
            SupportedPackages.isBank(sourcePackage) -> names.add(bankAppParser.name)
            SupportedPackages.isUpi(sourcePackage) -> names.add(otherUpiParser.name)
        }
        return names
    }
}
