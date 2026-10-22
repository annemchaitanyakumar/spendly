package com.spendly.app.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateTimeUtils {
    val systemZone: ZoneId get() = ZoneId.systemDefault()

    fun epochToLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(systemZone).toLocalDate()

    fun localDateToEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(systemZone).toInstant().toEpochMilli()

    fun epochToLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(systemZone).toLocalDateTime()

    fun monthStartMillis(year: Int, month: Int): Long {
        val ym = YearMonth.of(year, month)
        return ym.atDay(1).atStartOfDay(systemZone).toInstant().toEpochMilli()
    }

    fun monthEndMillis(year: Int, month: Int): Long {
        val ym = YearMonth.of(year, month)
        return ym.atEndOfMonth().plusDays(1).atStartOfDay(systemZone).toInstant().toEpochMilli()
    }

    fun formatDate(epochMillis: Long): String {
        val date = epochToLocalDate(epochMillis)
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    fun formatDateTime(epochMillis: Long): String {
        val dt = epochToLocalDateTime(epochMillis)
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))
    }

    fun formatTime(epochMillis: Long): String {
        val dt = epochToLocalDateTime(epochMillis)
        return dt.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    fun formatMonthYear(year: Int, month: Int): String {
        val ym = YearMonth.of(year, month)
        return "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $year"
    }

    fun formatRelativeDate(epochMillis: Long): String {
        val date = epochToLocalDate(epochMillis)
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }

    fun currentYearMonth(): Pair<Int, Int> {
        val now = LocalDate.now()
        return Pair(now.year, now.monthValue)
    }
}
