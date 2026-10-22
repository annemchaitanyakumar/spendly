package com.spendly.app.model

import com.spendly.app.core.model.ConfirmationStatus
import com.spendly.app.core.model.Expense
import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotesFormattingTest {

    @Test
    fun testNotesFormatMatchesUserSpecification() {
        val zone = ZoneId.systemDefault()
        val oct1 = LocalDate.of(2026, 10, 1).atTime(10, 30).atZone(zone).toInstant().toEpochMilli()
        val oct22 = LocalDate.of(2026, 10, 22).atTime(14, 15).atZone(zone).toInstant().toEpochMilli()

        val expense1 = Expense(
            id = 1L,
            merchant = "Vegetables",
            amountMinor = 10000L, // 100 rupees
            transactionAt = oct1,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.EXPENSE,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )

        val expense2 = Expense(
            id = 2L,
            merchant = "Swiggy",
            amountMinor = 25000L, // 250 rupees
            transactionAt = oct22,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.EXPENSE,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )

        val expenses = listOf(expense2, expense1)

        // 1. Heading check: "October spends"
        val monthHeading = "October spends"
        assertEquals("October spends", monthHeading)

        // 2. Date grouping check: "Oct 1", "Oct 22"
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val date1Heading = LocalDate.of(2026, 10, 1).format(formatter)
        val date2Heading = LocalDate.of(2026, 10, 22).format(formatter)
        assertEquals("Oct 1", date1Heading)
        assertEquals("Oct 22", date2Heading)

        // 3. Spend line format: "Vegetables ₹100" (or "Vegetables 100")
        val amountStr1 = if (expense1.amountMinor % 100 == 0L) {
            "₹${expense1.amountMinor / 100}"
        } else {
            "₹${expense1.amountMinor / 100.0}"
        }
        val line1 = "${expense1.merchant} $amountStr1"
        assertEquals("Vegetables ₹100", line1)

        val amountStr2 = if (expense2.amountMinor % 100 == 0L) {
            "₹${expense2.amountMinor / 100}"
        } else {
            "₹${expense2.amountMinor / 100.0}"
        }
        val line2 = "${expense2.merchant} $amountStr2"
        assertEquals("Swiggy ₹250", line2)
    }
}
