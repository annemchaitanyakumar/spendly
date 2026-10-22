package com.spendly.app.common

import com.spendly.app.core.common.CurrencyFormatter
import org.junit.Assert.*
import org.junit.Test

class CurrencyFormatterTest {

    @Test
    fun testFormatMinorInr() {
        assertEquals("₹149.50", CurrencyFormatter.formatMinor(14950L, "INR"))
        assertEquals("₹0.00", CurrencyFormatter.formatMinor(0L, "INR"))
        assertEquals("₹1,000.00", CurrencyFormatter.formatMinor(100000L, "INR"))
    }

    @Test
    fun testFormatMinorOtherCurrency() {
        assertEquals("USD 25.50", CurrencyFormatter.formatMinor(2550L, "USD"))
        assertEquals("EUR 100.00", CurrencyFormatter.formatMinor(10000L, "EUR"))
    }

    @Test
    fun testParseToMinor() {
        assertEquals(123456L, CurrencyFormatter.parseToMinor("₹1,234.56"))
        assertEquals(50000L, CurrencyFormatter.parseToMinor("500"))
        assertEquals(9999L, CurrencyFormatter.parseToMinor("99.99"))
        assertNull(CurrencyFormatter.parseToMinor("invalid"))
    }

    @Test
    fun testFormatCompact() {
        assertEquals("₹500", CurrencyFormatter.formatCompact(50000L))
        assertEquals("₹1.5K", CurrencyFormatter.formatCompact(150000L))
        assertEquals("₹2.5L", CurrencyFormatter.formatCompact(25000000L))
    }
}
