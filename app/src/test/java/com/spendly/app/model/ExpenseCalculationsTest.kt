package com.spendly.app.model

import com.spendly.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ExpenseCalculationsTest {

    @Test
    fun testOnlyConfirmedDebitExpenseCountsTowardsTotal() {
        val validExpense = Expense(
            amountMinor = 50000L,
            transactionAt = 1000L,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.EXPENSE,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )
        assertTrue(validExpense.isExpense)

        val unconfirmedCandidate = Expense(
            amountMinor = 50000L,
            transactionAt = 1000L,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.EXPENSE,
            confirmationStatus = ConfirmationStatus.PENDING_REVIEW
        )
        assertFalse("Unconfirmed candidate must NOT count toward expense totals", unconfirmedCandidate.isExpense)

        val incomingCredit = Expense(
            amountMinor = 100000L,
            transactionAt = 1000L,
            direction = TransactionDirection.CREDIT,
            recordType = RecordType.TRANSFER,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )
        assertFalse("Incoming credit must NOT count toward expense totals", incomingCredit.isExpense)

        val refund = Expense(
            amountMinor = 35000L,
            transactionAt = 1000L,
            direction = TransactionDirection.CREDIT,
            recordType = RecordType.REFUND,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )
        assertFalse("Refund must NOT count toward expense totals", refund.isExpense)

        val failedPayment = Expense(
            amountMinor = 50000L,
            transactionAt = 1000L,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.FAILED,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )
        assertFalse("Failed payment must NOT count toward expense totals", failedPayment.isExpense)
    }

    @Test
    fun testMonthSummaryChangePercentCalculation() {
        val summaryIncrease = MonthSummary(
            year = 2026,
            month = 10,
            totalMinor = 1500000L, // 15,000
            previousMonthTotalMinor = 1000000L, // 10,000
            categoryBreakdown = emptyList(),
            dailyTotals = emptyList(),
            topMerchants = emptyList()
        )
        assertNotNull(summaryIncrease.changePercent)
        assertEquals(50.0, summaryIncrease.changePercent!!, 0.01)

        val summaryDecrease = MonthSummary(
            year = 2026,
            month = 10,
            totalMinor = 800000L, // 8,000
            previousMonthTotalMinor = 1000000L, // 10,000
            categoryBreakdown = emptyList(),
            dailyTotals = emptyList(),
            topMerchants = emptyList()
        )
        assertNotNull(summaryDecrease.changePercent)
        assertEquals(-20.0, summaryDecrease.changePercent!!, 0.01)

        val summaryNoPrevious = MonthSummary(
            year = 2026,
            month = 10,
            totalMinor = 800000L,
            previousMonthTotalMinor = null,
            categoryBreakdown = emptyList(),
            dailyTotals = emptyList(),
            topMerchants = emptyList()
        )
        assertNull(summaryNoPrevious.changePercent)
    }

    @Test
    fun testCategorySpendingMajorCalculation() {
        val cat = Category(1L, "Food", "restaurant", "#E53935")
        val spending = CategorySpending(category = cat, totalMinor = 1245000L) // 12,450.00
        assertEquals(12450.0, spending.totalMajor, 0.001)
    }
}
