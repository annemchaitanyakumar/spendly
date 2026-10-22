package com.spendly.app.common

import com.spendly.app.core.common.DateTimeUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DateTimeUtilsTest {

    @Test
    fun testEpochToLocalDateAndBack() {
        val today = LocalDate.now()
        val epoch = DateTimeUtils.localDateToEpochMillis(today)
        val converted = DateTimeUtils.epochToLocalDate(epoch)
        assertEquals(today, converted)
    }

    @Test
    fun testMonthStartAndEndMillis() {
        val start = DateTimeUtils.monthStartMillis(2026, 10)
        val end = DateTimeUtils.monthEndMillis(2026, 10)
        assertTrue("Month end must be strictly after month start", end > start)

        val startDate = DateTimeUtils.epochToLocalDate(start)
        assertEquals(2026, startDate.year)
        assertEquals(10, startDate.monthValue)
        assertEquals(1, startDate.dayOfMonth)
    }

    @Test
    fun testFormatRelativeDateTodayAndYesterday() {
        val now = System.currentTimeMillis()
        assertEquals("Today", DateTimeUtils.formatRelativeDate(now))

        val yesterday = LocalDate.now().minusDays(1)
        val yesterdayMillis = DateTimeUtils.localDateToEpochMillis(yesterday) + 3600_000L
        assertEquals("Yesterday", DateTimeUtils.formatRelativeDate(yesterdayMillis))
    }

    @Test
    fun testFormatMonthYear() {
        val formatted = DateTimeUtils.formatMonthYear(2026, 10)
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("October", ignoreCase = true))
    }
}
