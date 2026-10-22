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

        val outgoingDebitTransfer = Expense(
            amountMinor = 25000L,
            transactionAt = 1000L,
            direction = TransactionDirection.DEBIT,
            recordType = RecordType.TRANSFER,
            confirmationStatus = ConfirmationStatus.CONFIRMED
        )
        assertTrue("Confirmed outgoing debit transfer must count toward expense totals", outgoingDebitTransfer.isExpense)
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

    @Test
    fun testDayCashFlowCalculations() {
        val date = java.time.LocalDate.of(2026, 10, 22)
        val cashFlow = DayCashFlow(
            date = date,
            debitMinor = 25000L,   // 250
            creditMinor = 100000L, // 1000
            transactions = emptyList()
        )
        assertEquals(75000L, cashFlow.netMinor) // +750
        assertTrue(cashFlow.hasActivity)

        val idleDay = DayCashFlow(
            date = date,
            debitMinor = 0L,
            creditMinor = 0L,
            transactions = emptyList()
        )
        assertFalse(idleDay.hasActivity)
        assertEquals(0L, idleDay.netMinor)
    }

    @Test
    fun testMonthSummaryNetBalanceAndAppSpending() {
        val appPhonePe = AppSpending("com.phonepe.app", "PhonePe", "phonepe", 500000L, 5)
        val appGPay = AppSpending("com.google.android.apps.nbu.paisa.user", "Google Pay", "gpay", 300000L, 3)

        val summary = MonthSummary(
            year = 2026,
            month = 10,
            totalMinor = 800000L, // 8,000 debited total
            previousMonthTotalMinor = null,
            totalCreditsMinor = 2000000L, // 20,000 credited total
            totalTransfersMinor = 300000L, // 3,000 was transfers
            categoryBreakdown = emptyList(),
            dailyTotals = emptyList(),
            topMerchants = emptyList(),
            appSpends = listOf(appPhonePe, appGPay)
        )

        assertEquals(8000.0, summary.totalMajor, 0.001)
        assertEquals(20000.0, summary.totalCreditsMajor, 0.001)
        assertEquals(3000.0, summary.totalTransfersMajor, 0.001)
        assertEquals(1200000L, summary.netBalanceMinor) // 20,000 - 8,000 = 12,000
        assertEquals(12000.0, summary.netBalanceMajor, 0.001)
        assertEquals(2, summary.appSpends.size)
        assertEquals("PhonePe", summary.appSpends[0].appDisplayName)
        assertEquals(5000.0, summary.appSpends[0].totalMajor, 0.001)
    }
}
