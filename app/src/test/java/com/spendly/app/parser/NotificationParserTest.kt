package com.spendly.app.parser

import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection
import com.spendly.app.core.parser.NotificationParserRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationParserTest {

    private lateinit var registry: NotificationParserRegistry

    @Before
    fun setUp() {
        registry = NotificationParserRegistry()
    }

    // ── 1. Outgoing Payment Notifications ─────────────────────────────

    @Test
    fun testPhonePeOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Payment Successful",
            text = "Paid Rs. 450 to Starbucks on PhonePe",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(45000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertTrue(result!!.merchant.contains("Starbucks", ignoreCase = true))
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun testGooglePayOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "com.google.android.apps.nbu.paisa.user",
            title = "Google Pay Alert",
            text = "You paid ₹1,250.50 to Swiggy using UPI",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(125050L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertTrue(result!!.merchant.contains("Swiggy", ignoreCase = true))
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun testPaytmOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "net.one97.paytm",
            title = "Paid successfully",
            text = "Paid Rs 280 to Uber",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(28000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertTrue(result!!.merchant.contains("Uber", ignoreCase = true))
        assertTrue(result.confidence >= 0.8f)
    }

    // ── 2. Incoming Payment Notifications ─────────────────────────────

    @Test
    fun testIncomingPaymentNotification() {
        val result = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Money Received",
            text = "Received Rs. 1,500 from John Doe",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(150000L, result?.amountMinor)
        assertEquals(TransactionDirection.CREDIT, result?.direction)
        assertEquals(RecordType.INCOME, result?.recordType)
        // Incoming payment must NOT be marked as an EXPENSE
        assertNotEquals(RecordType.EXPENSE, result?.recordType)
    }

    // ── 3. Payment Failure Notifications ──────────────────────────────

    @Test
    fun testPaymentFailureNotification() {
        val result = registry.parse(
            sourcePackage = "net.one97.paytm",
            title = "Transaction Failed",
            text = "Payment of Rs 999 failed at Reliance Digital",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(RecordType.FAILED, result?.recordType)
        // Failed payment must NOT be marked as an EXPENSE
        assertNotEquals(RecordType.EXPENSE, result?.recordType)
    }

    // ── 4. Refund Notifications ───────────────────────────────────────

    @Test
    fun testRefundNotification() {
        val result = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Refund Processed",
            text = "Refund of Rs. 350.00 initiated from Amazon",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(35000L, result?.amountMinor)
        assertEquals(RecordType.REFUND, result?.recordType)
        // Refund must NOT be marked as an EXPENSE
        assertNotEquals(RecordType.EXPENSE, result?.recordType)
    }

    // ── 5. Multiple Monetary Amounts in One Notification ──────────────

    @Test
    fun testMultipleAmountsPicksPrimaryPaidAmount() {
        val result = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Payment Successful",
            text = "Paid Rs. 500 to Cafe Coffee Day. Cashback of Rs. 50 earned!",
            bigText = null
        )

        assertNotNull(result)
        // Must extract the paid amount of 500, not the cashback amount of 50
        assertEquals(50000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
    }

    // ── 6. Unknown Notification Format ────────────────────────────────

    @Test
    fun testUnknownNotificationFormatReturnsLowConfidenceOrNull() {
        val result = registry.parse(
            sourcePackage = "com.random.app",
            title = "Your daily horoscope",
            text = "Check out the stars today for free advice",
            bigText = null
        )

        // Horoscope contains no monetary indicators, so should return null or 0 amount
        if (result != null) {
            assertNull(result.amountMinor)
            assertTrue(result.confidence < 0.4f)
        }
    }

    // ── 7. Generic Fallback Parser for Bank SMS / App Alerts ───────────

    @Test
    fun testGenericFallbackParserForBankDebitAlert() {
        val result = registry.parse(
            sourcePackage = "com.android.mms",
            title = "HDFC Bank Alert",
            text = "Rs 2,340.00 debited from A/C **1234 on 22-Oct-26 at Zomato UPI ref 987654",
            bigText = null
        )

        assertNotNull(result)
        assertEquals(234000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertTrue(result!!.confidence >= 0.6f)
    }

    // ── 8. Non-Expense Exclusions (OTP, Promo Spam, Balance Inquiries) ─

    @Test
    fun testOtpMessageIsStrictlyExcluded() {
        val otpResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Bank OTP Alert",
            text = "Your OTP for transaction of Rs 1,500.00 at Flipkart is 482910. Do not share with anyone.",
            bigText = null
        )
        assertNull("OTP messages must be excluded from expense detection", otpResult)

        val phonePeOtp = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Verification Code",
            text = "492019 is your secret OTP for payment of ₹250. Valid for 5 minutes.",
            bigText = null
        )
        assertNull("App verification codes must be excluded", phonePeOtp)
    }

    @Test
    fun testPromotionalLoanSpamIsExcluded() {
        val promoResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Pre-approved Offer",
            text = "Congratulations! You are eligible for a pre-approved personal loan of Rs 5,00,000 at low EMI. Apply now!",
            bigText = null
        )
        assertNull("Promotional loan offers must be excluded", promoResult)
    }

    @Test
    fun testPureBalanceInquiryIsExcluded() {
        val balResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI Alert",
            text = "Dear Customer, Avl Bal in A/c XX1234 is Rs 45,210.00 on 22-Oct.",
            bigText = null
        )
        assertNull("Pure balance inquiry messages must be excluded", balResult)
    }

    @Test
    fun testBankSmsWithAvailableBalanceParsesDebitAmount() {
        val debitWithBal = registry.parse(
            sourcePackage = "com.android.mms",
            title = "ICICI Bank Alert",
            text = "Acct XX987 debited with INR 850.00 on 22-Oct-26 at Starbucks. Avl Bal: INR 24,150.00.",
            bigText = null
        )
        assertNotNull(debitWithBal)
        assertEquals(85000L, debitWithBal?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, debitWithBal?.direction)
        assertEquals(RecordType.EXPENSE, debitWithBal?.recordType)
    }

    // ── 9. BHIM, CRED, Amazon Pay Parsers ─────────────────────────────

    @Test
    fun testBhimOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "in.org.npci.upiapp",
            title = "BHIM UPI",
            text = "Payment of Rs. 350.00 to Chai Point successful. Ref: 234567890123",
            bigText = null
        )
        assertNotNull(result)
        assertEquals(35000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertEquals("BHIM", result?.parserName)
    }

    @Test
    fun testCredOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "com.dreamplug.androidapp",
            title = "CRED Alert",
            text = "₹1,499 paid to Electricity Board via CRED",
            bigText = null
        )
        assertNotNull(result)
        assertEquals(149900L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertEquals("CRED", result?.parserName)
    }

    @Test
    fun testAmazonPayOutgoingPayment() {
        val result = registry.parse(
            sourcePackage = "in.amazon.mShop.android.shopping",
            title = "Amazon Pay",
            text = "Paid ₹420 at Cafe Coffee Day via Amazon Pay",
            bigText = null
        )
        assertNotNull(result)
        assertEquals(42000L, result?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals(RecordType.EXPENSE, result?.recordType)
        assertEquals("Amazon Pay", result?.parserName)
    }
}
