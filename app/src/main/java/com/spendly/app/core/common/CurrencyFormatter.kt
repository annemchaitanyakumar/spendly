package com.spendly.app.core.common

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val INR_FORMAT = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    /** Format minor units (paise) to INR string: e.g. 14950 → "₹149.50" */
    fun formatMinor(amountMinor: Long, currencyCode: String = "INR"): String {
        val major = amountMinor / 100.0
        return if (currencyCode == "INR") {
            // Use INR locale formatting
            "₹${String.format(Locale.US, "%,.2f", major)}"
        } else {
            "$currencyCode ${String.format(Locale.US, "%,.2f", major)}"
        }
    }

    /** Parse a string like "₹1,234.56" to minor units */
    fun parseToMinor(input: String): Long? {
        val cleaned = input
            .replace(Regex("[₹,\\s]"), "")
            .replace(Regex("[^0-9.]"), "")
        return cleaned.toDoubleOrNull()?.let { (it * 100).toLong() }
    }

    /** Format for compact display: e.g. 100000 paise → "₹1,000" */
    fun formatCompact(amountMinor: Long): String {
        val major = amountMinor / 100.0
        return when {
            major >= 100_000 -> "₹${String.format(Locale.US, "%.1f", major / 100_000)}L"
            major >= 1_000 -> "₹${String.format(Locale.US, "%.1f", major / 1_000)}K"
            else -> "₹${String.format(Locale.US, "%.0f", major)}"
        }
    }
}
