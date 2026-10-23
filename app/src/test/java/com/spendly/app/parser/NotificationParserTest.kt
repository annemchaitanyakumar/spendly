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

    // ── 10. Telecom / Utility Bill Due Reminders Must Be Excluded ─────

    @Test
    fun testAirtelWifiBillReminderIsStrictlyExcluded() {
        val airtelResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Airtel Alert",
            text = "Dear Customer, Airtel Wi-Fi bill for account 0401234567 of Rs 943 is generated. Due date is 25-Oct. Pay now to avoid disconnection: airtel.in/pay",
            bigText = null
        )
        assertNull("Airtel Wi-Fi bill generated notification must NOT be parsed as an expense", airtelResult)

        val airtelBroadband = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Airtel Thanks",
            text = "Your Airtel broadband bill of Rs 825 is due on 24-Oct. Pay now to avoid late fee.",
            bigText = null
        )
        assertNull("Broadband bill due reminder must NOT be parsed as an expense", airtelBroadband)
    }

    @Test
    fun testElectricityBillDueReminderIsExcluded() {
        val ebResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Electricity Board",
            text = "Your electricity bill of Rs 1,450 for CA 102938475 is due on 28-OCT-2026. Pay now.",
            bigText = null
        )
        assertNull("Electricity bill due reminder must be excluded", ebResult)
    }

    @Test
    fun testCreditCardBillDueReminderIsExcluded() {
        val ccResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "HDFC Bank Alert",
            text = "Total amount due for HDFC Credit Card ending 9876 is Rs 12,450.00. Minimum amount due: Rs 1,200. Due date: 24-Oct-26.",
            bigText = null
        )
        assertNull("Credit card bill due notice must be excluded", ccResult)
    }

    @Test
    fun testConfirmedPaidBillIsSuccessfullyParsed() {
        val paidBill = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Airtel Payment",
            text = "Payment of Rs 943 for Airtel Wi-Fi account 0401234567 was successfully paid. Debited from A/C XX1234.",
            bigText = null
        )
        assertNotNull(paidBill)
        assertEquals(94300L, paidBill?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, paidBill?.direction)
    }

    // ── 11. Bank Credits & Merchant Sanitization ───────────────────────

    @Test
    fun testBankCreditSmsDirectionAndNoGarbageMerchant() {
        val creditSms = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "Bank SMS",
            text = "Dear Customer, your A/c XX2636 is credited with Rs 1.00 on 22-Oct-26. Check out details. Amount credited to a/c XX2636.",
            bigText = null
        )
        assertNotNull(creditSms)
        assertEquals(100L, creditSms?.amountMinor)
        assertEquals(TransactionDirection.CREDIT, creditSms?.direction)
        assertEquals(RecordType.INCOME, creditSms?.recordType)
        // Must NOT extract "XX2636. Check out details. Amount credite" as merchant!
        assertFalse(creditSms!!.merchant.contains("Check out", ignoreCase = true))
        assertFalse(creditSms.merchant.contains("Amount credite", ignoreCase = true))
        assertFalse(creditSms.merchant.startsWith("XX", ignoreCase = true))
    }

    @Test
    fun testPersonTransferIsClassifiedAsTransfer() {
        val transferResult = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Payment Successful",
            text = "Paid Rs 500 to Rahul Sharma on PhonePe",
            bigText = null
        )
        assertNotNull(transferResult)
        assertEquals(50000L, transferResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, transferResult?.direction)
        assertEquals(RecordType.TRANSFER, transferResult?.recordType)
        assertEquals("Rahul Sharma", transferResult?.merchant)
    }

    // ── 12. Strict Whitelist: Non-financial apps must be completely ignored ─

    @Test
    fun testUnverifiedAppsAreStrictlyRejectedEvenWithFinancialKeywords() {
        // WhatsApp chat mentioning money
        val whatsappMsg = registry.parse(
            sourcePackage = "com.whatsapp",
            title = "Mom",
            text = "Please pay Rs. 500 to the grocery shop today",
            bigText = null
        )
        assertNull("WhatsApp messages must be strictly rejected", whatsappMsg)

        // Instagram message
        val instaMsg = registry.parse(
            sourcePackage = "com.instagram.android",
            title = "Direct Message",
            text = "Paid ₹1,200 for this jacket yesterday!",
            bigText = null
        )
        assertNull("Instagram messages must be strictly rejected", instaMsg)

        // Telegram message
        val telegramMsg = registry.parse(
            sourcePackage = "org.telegram.messenger",
            title = "Crypto Channel",
            text = "Credited $500 to your wallet",
            bigText = null
        )
        assertNull("Telegram messages must be strictly rejected", telegramMsg)
    }

    // ── 13. Bank Email Debit Notification Parsing ──────────────────────

    @Test
    fun testBankEmailDebitConfirmationParsing() {
        val emailResult = registry.parse(
            sourcePackage = "com.google.android.gm",
            title = "HDFC Bank: Debit Alert",
            text = "Alert: INR 50.00 was debited from your account **1234 for payment to Sri Sai Store. UPI Ref: 429612345678.",
            bigText = null
        )
        assertNotNull(emailResult)
        assertEquals(5000L, emailResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, emailResult?.direction)
        assertEquals(RecordType.EXPENSE, emailResult?.recordType)
        assertEquals("429612345678", emailResult?.transactionRef)
        assertTrue(emailResult!!.merchant.contains("Sri Sai Store", ignoreCase = true))
    }

    // ── 14. Official Bank App Notification Parsing ─────────────────────

    @Test
    fun testOfficialBankAppNotificationParsing() {
        val sbiResult = registry.parse(
            sourcePackage = "com.sbi.lotusintouch",
            title = "SBI YONO Alert",
            text = "Payment of Rs. 750.00 to DMart was successful. Txn ID: 987654321.",
            bigText = null
        )
        assertNotNull(sbiResult)
        assertEquals(75000L, sbiResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, sbiResult?.direction)
        assertEquals(RecordType.EXPENSE, sbiResult?.recordType)
        assertTrue(sbiResult!!.merchant.contains("DMart", ignoreCase = true))
    }

    // ── 15. 3-Way Multi-Source Matching: PhonePe + Bank SMS + Bank Email ─

    @Test
    fun testThreeWayMultiSourceConsistencyForShopPayment() {
        // 1. User pays ₹50 at shop via PhonePe
        val phonePeResult = registry.parse(
            sourcePackage = "com.phonepe.app",
            title = "Payment Successful",
            text = "Paid ₹50 to Sri Sai Store",
            bigText = null
        )
        assertNotNull(phonePeResult)
        assertEquals(5000L, phonePeResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, phonePeResult?.direction)
        assertEquals(RecordType.EXPENSE, phonePeResult?.recordType)
        assertTrue(phonePeResult!!.merchant.contains("Sri Sai Store", ignoreCase = true))

        // 2. Bank SMS arrives with ₹50 and 12-digit UPI reference
        val smsResult = registry.parse(
            sourcePackage = "com.google.android.apps.messaging",
            title = "SBI Alert",
            text = "Dear SBI User, your A/c XX2636 is debited by Rs 50.00 on 22Oct26 transfer to Sri Sai Store Ref No 429612345678 - SBI",
            bigText = null
        )
        assertNotNull(smsResult)
        assertEquals(5000L, smsResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, smsResult?.direction)
        assertEquals("429612345678", smsResult?.transactionRef)

        // 3. Bank confirmation Email arrives with ₹50 and UPI transaction ID
        val emailResult = registry.parse(
            sourcePackage = "com.google.android.gm",
            title = "Debit Alert - SBI",
            text = "Your account XX2636 was debited for Rs. 50.00 paid to Sri Sai Store. UPI Transaction ID: 429612345678.",
            bigText = null
        )
        assertNotNull(emailResult)
        assertEquals(5000L, emailResult?.amountMinor)
        assertEquals(TransactionDirection.DEBIT, emailResult?.direction)
        assertEquals("429612345678", emailResult?.transactionRef)

        // Both SMS and Email match the exact same amount and exact same UPI 12-digit reference
        assertEquals(smsResult?.amountMinor, emailResult?.amountMinor)
        assertEquals(smsResult?.transactionRef, emailResult?.transactionRef)
    }

    // ── 16. Real-world Kotak & PhonePe Incoming Payment Tests ───────────

    @Test
    fun testKotakAppReceivedPaymentNotification() {
        val kotakResult = registry.parse(
            sourcePackage = "com.kotak.bank.mobile",
            title = "Kotak811",
            text = "₹10.00 received from ANNEM RAMA... Amount credited to XX2636. Check out details.",
            bigText = null
        )
        assertNotNull(kotakResult)
        assertEquals(1000L, kotakResult?.amountMinor)
        assertEquals(TransactionDirection.CREDIT, kotakResult?.direction)
        assertEquals(RecordType.INCOME, kotakResult?.recordType)
        assertTrue(kotakResult!!.merchant.contains("ANNEM RAMA", ignoreCase = true))
        assertFalse(kotakResult.merchant.contains("Check out", ignoreCase = true))
    }

    @Test
    fun testKotakEmailNotificationParsing() {
        val emailResult = registry.parse(
            sourcePackage = "com.google.android.gm",
            title = "Kotak811",
            text = "UPI Credit Alert: ₹10.00 received in your Kotak A/c. Reference No: 429612345678. Click to view the below e-mailer.",
            bigText = null
        )
        assertNotNull(emailResult)
        assertEquals(1000L, emailResult?.amountMinor)
        assertEquals(TransactionDirection.CREDIT, emailResult?.direction)
        assertEquals(RecordType.INCOME, emailResult?.recordType)
        assertNotEquals("erence", emailResult?.transactionRef)
        assertEquals("429612345678", emailResult?.transactionRef)
        assertFalse(emailResult!!.merchant.contains("view the below", ignoreCase = true))
        assertFalse(emailResult.merchant.contains("e-mailer", ignoreCase = true))
    }

    @Test
    fun testAmazonSubscriptionCancelledWithReplacementSuggestionsRejected() {
        // Must reject cancellation emails with suggested replacement prices
        val amazonEmailResult = registry.parse(
            sourcePackage = "com.google.android.gm",
            title = "Subscription Cancelled",
            text = "Tugain Essentials Hair Fall Control Sham... Still want to subscribe to this product but change your schedule? You can reactivate and edit the subscription at any time. Suggested Replacements: Tugain Essentials Anti Dandruff Shampoo... ₹376.20 Choose as replacement.",
            bigText = "Tugain Essentials Hair Fall Control... ₹583.30 Choose as replacement"
        )
        assertNull(
            "Amazon suggested replacement / subscription cancelled emails must NEVER be parsed as expenses",
            amazonEmailResult
        )
    }

    @Test
    fun testEssentialsDoesNotMatchSentKeyword() {
        // "essentials" contains "sent" as a substring. Word-boundary checks must ensure it is not detected as DEBIT or TRANSFER.
        val nonFinancialEmail = registry.parse(
            sourcePackage = "com.google.android.gm",
            title = "Product Recommendation",
            text = "Check out Tugain Essentials Hair Oil for ₹299.00 today only.",
            bigText = null
        )
        assertNull(
            "Word 'essentials' must not falsely match keyword 'sent'",
            nonFinancialEmail
        )
    }

    @Test
    fun testAtAnyTimeDoesNotExtractMerchant() {
        val merchant = com.spendly.app.core.parser.CommonPatterns.extractMerchant(
            "You can reactivate and edit the subscription at any time. Tugain Essentials ₹376.20"
        )
        assertNotEquals("any time", merchant)
        assertEquals("", merchant)
    }
}
