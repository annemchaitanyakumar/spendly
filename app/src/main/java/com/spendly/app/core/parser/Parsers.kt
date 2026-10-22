package com.spendly.app.core.parser

import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection

// ─────────────────────────────────────────────────────────────────────
//  PhonePe Parser
// ─────────────────────────────────────────────────────────────────────

class PhonePeParser : NotificationParser {
    override val name = "PhonePe"
    override val supportedPackages = setOf("com.phonepe.app")

    // e.g. "₹150 paid to Swiggy"
    // e.g. "₹1,200.00 sent to John via UPI Ref No. 123456789012"
    private val paidToPattern = Regex(
        """(?:₹|Rs\.?\s*)([0-9,]+(?:\.[0-9]{1,2})?)\s+(?:paid|sent|transferred)\s+to\s+([A-Za-z0-9][A-Za-z0-9 &.'-]{1,40})""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText)
        val direction = CommonPatterns.detectDirection(fullText)
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val ref = CommonPatterns.extractRef(fullText)

        // Try specific merchant extraction
        val merchant = paidToPattern.find(fullText)?.groupValues?.get(2)?.trim()
            ?: CommonPatterns.extractMerchant(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = amount != null,
            directionKnown = direction != TransactionDirection.UNKNOWN,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        )

        return if (confidence > 0.3f) {
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
    override val supportedPackages = setOf("com.google.android.apps.nbu.paisa.user")

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        val amount = CommonPatterns.extractAmount(fullText)
        val direction = CommonPatterns.detectDirection(fullText)
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val merchant = CommonPatterns.extractMerchant(fullText)
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
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val merchant = CommonPatterns.extractMerchant(fullText)
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
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val merchant = CommonPatterns.extractMerchant(fullText)
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
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val merchant = CommonPatterns.extractMerchant(fullText)
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
        val recordType = CommonPatterns.detectRecordType(fullText, direction)
        val merchant = CommonPatterns.extractMerchant(fullText)
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
//  Generic Bank / UPI Parser (fallback)
// ─────────────────────────────────────────────────────────────────────

class GenericBankParser : NotificationParser {
    override val name = "Generic"
    override val supportedPackages = emptySet<String>() // fallback for all

    override fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        val fullText = listOfNotNull(title, text, bigText).joinToString(" ")
        if (CommonPatterns.isNonExpenseMessage(fullText)) return null

        // Must have an amount to even consider this
        val amount = CommonPatterns.extractAmount(fullText) ?: return null

        val direction = CommonPatterns.detectDirection(fullText)
        val recordType = CommonPatterns.detectRecordType(fullText, direction)

        // Generic parser requires a direction clue to avoid false positives
        if (direction == TransactionDirection.UNKNOWN) return null

        val merchant = CommonPatterns.extractMerchant(fullText)
        val ref = CommonPatterns.extractRef(fullText)

        val confidence = CommonPatterns.calculateConfidence(
            hasAmount = true,
            directionKnown = true,
            recordTypeKnown = recordType != RecordType.UNKNOWN,
            hasMerchant = merchant.isNotEmpty(),
            hasRef = ref != null
        ) * 0.8f // Reduce confidence for generic parser

        return ParsedTransaction(amount, "INR", direction, recordType, merchant, ref, confidence, fullText, name)
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Parser Registry
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
    private val fallbackParser = GenericBankParser()

    fun parse(sourcePackage: String, title: String, text: String, bigText: String?): ParsedTransaction? {
        // Try source-specific parser first
        val specific = specificParsers.firstOrNull { sourcePackage in it.supportedPackages }
        if (specific != null) {
            val result = specific.parse(sourcePackage, title, text, bigText)
            if (result != null) return result
        }
        // Fallback to generic parser
        return fallbackParser.parse(sourcePackage, title, text, bigText)
    }

    /** Get all parsers that support a given package for debug info */
    fun parsersForPackage(sourcePackage: String): List<String> {
        val names = mutableListOf<String>()
        specificParsers.filter { sourcePackage in it.supportedPackages }
            .forEach { names.add(it.name) }
        names.add(fallbackParser.name + " (fallback)")
        return names
    }
}
