package com.spendly.app.core.parser

import com.spendly.app.core.common.CurrencyFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

data class ParsedNoteItem(
    val id: String = UUID.randomUUID().toString(),
    val merchant: String,
    val amountMinor: Long,
    val amountFormatted: String,
    val suggestedCategoryId: Long = 20L, // 20 = Other
    val suggestedCategoryName: String = "Other",
    val rawText: String,
    val isSelected: Boolean = true
)

data class ParsedNoteGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dateEpochMillis: Long,
    val dateFormatted: String,
    val items: List<ParsedNoteItem>,
    val isSelected: Boolean = true
) {
    val totalMinor: Long get() = items.filter { it.isSelected }.sumOf { it.amountMinor }
    val totalFormatted: String get() = CurrencyFormatter.formatMinor(totalMinor)
    val totalAllItemsMinor: Long get() = items.sumOf { it.amountMinor }
}

object NotesImportParser {

    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "sept", "oct", "nov", "dec"
    )

    // Matches "Oct 1", "October 24", "1st Oct", "10-10-2026", "2026-10-01", "24/10"
    private val DATE_HEADER_REGEX = Pattern.compile(
        """(?i)^(?:date[:\s]*)?(?:(\d{1,2})(?:st|nd|rd|th)?[\s\-_/]+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)|(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)[\s\-_/]+(\d{1,2})(?:st|nd|rd|th)?|(\d{4})[/-](\d{1,2})[/-](\d{1,2})|(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})|today|yesterday)(?:[\s,]+(\d{4}))?[:\s]*$"""
    )

    // Month Heading: e.g. "October spends", "Spends for Nov 2026"
    private val MONTH_TITLE_REGEX = Pattern.compile(
        """(?i)^(?:(?:my\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\s+(\d{4}))?[\s\-_]*(?:spends?|expenses?|notes?|budget|diary)?|(?:spends?|expenses?|notes?)[\s\-_]*(?:for\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\s+(\d{4}))?)$"""
    )

    // Matches expense lines like:
    // "Vegetables 100", "Milk: 35.50", "Petrol - Rs. 500", "Paid 200 for Tea", "Dinner ₹450"
    // "100 Vegetables", "₹ 500 Petrol"
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?:(?:rs\.?|inr|₹)\s*(\d+(?:[.,]\d{1,2})?)|(\d+(?:[.,]\d{1,2})?)\s*(?:rs\.?|inr|₹|/-)?)(?!\w)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses raw input text from a notes app, file, or share intent into a list of parsed note groups.
     */
    fun parseNotesText(rawContent: String): List<ParsedNoteGroup> {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Check if content is JSON (e.g. Google Keep export)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonResult = parseKeepJson(trimmed)
            if (jsonResult.isNotEmpty()) return jsonResult
        }

        // 2. Check if content is CSV
        if (trimmed.contains(",") && (trimmed.lines().firstOrNull()?.contains("amount", ignoreCase = true) == true ||
                    trimmed.lines().firstOrNull()?.contains("date", ignoreCase = true) == true)) {
            val csvResult = parseCsv(trimmed)
            if (csvResult.isNotEmpty()) return csvResult
        }

        // 3. Plain text parsing (handles standard notes, multi-day lists, etc.)
        return parsePlainTextNotes(trimmed)
    }

    private fun parsePlainTextNotes(text: String): List<ParsedNoteGroup> {
        val lines = text.lines()
        val groups = mutableListOf<ParsedNoteGroup>()

        var currentGroupTitle = ""
        var currentGroupDate = LocalDate.now()
        var currentItems = mutableListOf<ParsedNoteItem>()
        var defaultMonth = LocalDate.now().monthValue
        var defaultYear = LocalDate.now().year

        // First pass: look for top-level month heading e.g. "October spends"
        for (line in lines.take(5)) {
            val clean = line.trim()
            val m = MONTH_TITLE_REGEX.matcher(clean)
            if (m.matches()) {
                val monthStr = m.group(1) ?: m.group(3)
                val yearStr = m.group(2) ?: m.group(4)
                if (monthStr != null) {
                    val mVal = parseMonthString(monthStr)
                    if (mVal in 1..12) defaultMonth = mVal
                }
                if (yearStr != null) {
                    yearStr.toIntOrNull()?.let { defaultYear = it }
                }
                break
            }
        }

        fun finalizeCurrentGroup() {
            if (currentItems.isNotEmpty()) {
                val title = if (currentGroupTitle.isNotBlank()) currentGroupTitle else formatDateHeading(currentGroupDate)
                val epoch = currentGroupDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                groups.add(
                    ParsedNoteGroup(
                        title = title,
                        dateEpochMillis = epoch,
                        dateFormatted = formatDateHeading(currentGroupDate),
                        items = currentItems.toList(),
                        isSelected = true
                    )
                )
                currentItems.clear()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("# ") || line.startsWith("===") || line.startsWith("---")) {
                // If it's a markdown heading e.g. "# Groceries"
                if (line.startsWith("# ") && currentItems.isNotEmpty()) {
                    finalizeCurrentGroup()
                    currentGroupTitle = line.removePrefix("#").trim()
                }
                continue
            }

            // Check if line is a top month title (e.g. "October spends")
            if (MONTH_TITLE_REGEX.matcher(line).matches()) {
                finalizeCurrentGroup()
                currentGroupTitle = line
                continue
            }

            // Check if line is a Date Header (e.g. "Oct 1", "October 24", "24 Oct", "Today", etc.)
            val parsedDate = parseDateHeader(line, defaultMonth, defaultYear)
            if (parsedDate != null) {
                finalizeCurrentGroup()
                currentGroupDate = parsedDate
                currentGroupTitle = formatDateHeading(parsedDate)
                continue
            }

            // Check if line is an expense line
            val item = parseExpenseLine(line)
            if (item != null) {
                currentItems.add(item)
            }
        }

        finalizeCurrentGroup()

        // Fallback: If no date headers were found but items were parsed, create a single note group
        if (groups.isEmpty() && currentItems.isNotEmpty()) {
            val epoch = currentGroupDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            groups.add(
                ParsedNoteGroup(
                    title = if (currentGroupTitle.isNotBlank()) currentGroupTitle else "Imported Spends Note",
                    dateEpochMillis = epoch,
                    dateFormatted = formatDateHeading(currentGroupDate),
                    items = currentItems,
                    isSelected = true
                )
            )
        }

        return groups
    }

    /**
     * Parses an individual line into a ParsedNoteItem.
     * Examples:
     * - "Vegetables 100" -> merchant="Vegetables", amount=10000L
     * - "Milk 35.50" -> merchant="Milk", amount=3550L
     * - "Paid 500 to Petrol bunk" -> merchant="Petrol bunk", amount=50000L
     * - "₹450 Starbucks coffee" -> merchant="Starbucks coffee", amount=45000L
     * - "- Swiggy: 320" -> merchant="Swiggy", amount=32000L
     */
    fun parseExpenseLine(rawLine: String): ParsedNoteItem? {
        var clean = rawLine.trim()
            .removePrefix("- ")
            .removePrefix("* ")
            .removePrefix("• ")
            .trim()

        if (clean.isBlank()) return null

        val matcher = AMOUNT_PATTERN.matcher(clean)
        var amountMinor: Long? = null
        var amountStart = -1
        var amountEnd = -1

        if (matcher.find()) {
            val amt1 = matcher.group(1)
            val amt2 = matcher.group(2)
            val amtStr = (amt1 ?: amt2)?.replace(",", "")
            amtStr?.toDoubleOrNull()?.let {
                amountMinor = (it * 100).toLong()
                amountStart = matcher.start()
                amountEnd = matcher.end()
            }
        }

        if (amountMinor == null || amountMinor <= 0L) return null

        // Extract merchant/description by removing the matched amount and common artifacts
        val left = clean.substring(0, amountStart).trim()
        val right = clean.substring(amountEnd).trim()
        var desc = "$left $right".trim()
            .removePrefix(":")
            .removePrefix("-")
            .removeSuffix(":")
            .removeSuffix("-")
            .trim()

        // Clean up common filler words e.g. "paid to", "for", "bought"
        desc = desc.replace(Regex("""(?i)^(?:paid\s+(?:to\s+|for\s+)?|bought\s+|spent\s+(?:on\s+)?|to\s+)"""), "")
            .replace(Regex("""(?i)\s+(?:paid|done|spent)$"""), "")
            .trim()

        if (desc.isBlank()) {
            desc = "Spend Note"
        }

        val (catId, catName) = detectCategory(desc)

        return ParsedNoteItem(
            merchant = desc.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            amountMinor = amountMinor,
            amountFormatted = CurrencyFormatter.formatMinor(amountMinor),
            suggestedCategoryId = catId,
            suggestedCategoryName = catName,
            rawText = rawLine,
            isSelected = true
        )
    }

    private fun parseDateHeader(line: String, defaultMonth: Int, defaultYear: Int): LocalDate? {
        val clean = line.trim().lowercase(Locale.ROOT)
            .removePrefix("date:").removePrefix("date :").trim()

        if (clean == "today") return LocalDate.now()
        if (clean == "yesterday") return LocalDate.now().minusDays(1)

        val m = DATE_HEADER_REGEX.matcher(clean)
        if (m.matches()) {
            try {
                // Group 1 & 2: e.g. "1st Oct"
                val day1 = m.group(1)
                val mon1 = m.group(2)
                if (day1 != null && mon1 != null) {
                    val d = day1.toInt()
                    val mo = parseMonthString(mon1)
                    val yr = m.group(10)?.toIntOrNull() ?: defaultYear
                    return LocalDate.of(yr, mo, d)
                }

                // Group 3 & 4: e.g. "Oct 1", "October 24"
                val mon2 = m.group(3)
                val day2 = m.group(4)
                if (mon2 != null && day2 != null) {
                    val d = day2.toInt()
                    val mo = parseMonthString(mon2)
                    val yr = m.group(10)?.toIntOrNull() ?: defaultYear
                    return LocalDate.of(yr, mo, d)
                }

                // Group 5, 6, 7: "YYYY-MM-DD"
                val y3 = m.group(5)
                val mo3 = m.group(6)
                val d3 = m.group(7)
                if (y3 != null && mo3 != null && d3 != null) {
                    return LocalDate.of(y3.toInt(), mo3.toInt(), d3.toInt())
                }

                // Group 8, 9, 10: "DD/MM/YYYY" or "DD/MM"
                val d4 = m.group(8)
                val mo4 = m.group(9)
                val y4 = m.group(10)
                if (d4 != null && mo4 != null) {
                    val yr = y4?.toIntOrNull() ?: defaultYear
                    return LocalDate.of(yr, mo4.toInt(), d4.toInt())
                }
            } catch (_: Exception) {
                // Invalid calendar day (e.g. Feb 31)
            }
        }

        // Simpler fallback for "Oct 1" or "1 Oct"
        val parts = clean.split(Regex("""[\s,/\-_]+"""))
        if (parts.size in 2..3) {
            val numPart = parts.find { it.toIntOrNull() != null }?.toIntOrNull()
            val monthPart = parts.find { MONTH_NAMES.contains(it.lowercase()) }
            if (numPart != null && numPart in 1..31 && monthPart != null) {
                val mo = parseMonthString(monthPart)
                val yr = parts.getOrNull(2)?.toIntOrNull() ?: defaultYear
                return try { LocalDate.of(yr, mo, numPart) } catch (_: Exception) { null }
            }
        }

        return null
    }

    private fun parseMonthString(monthStr: String): Int {
        val s = monthStr.lowercase(Locale.ROOT)
        return when {
            s.startsWith("jan") -> 1
            s.startsWith("feb") -> 2
            s.startsWith("mar") -> 3
            s.startsWith("apr") -> 4
            s.startsWith("may") -> 5
            s.startsWith("jun") -> 6
            s.startsWith("jul") -> 7
            s.startsWith("aug") -> 8
            s.startsWith("sep") -> 9
            s.startsWith("oct") -> 10
            s.startsWith("nov") -> 11
            s.startsWith("dec") -> 12
            else -> 10
        }
    }

    private fun formatDateHeading(date: LocalDate): String {
        val now = LocalDate.now()
        val suffix = when {
            date == now -> " (Today)"
            date == now.minusDays(1) -> " (Yesterday)"
            else -> ""
        }
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
        return "${date.format(formatter)}$suffix"
    }

    private fun parseKeepJson(jsonText: String): List<ParsedNoteGroup> {
        val list = mutableListOf<ParsedNoteGroup>()

        // 1. Try robust regex extraction which works on both JVM unit tests and Android runtime
        val titleRegex = Regex(""""title"\s*:\s*"([^"]*)"""")
        val contentRegex = Regex(""""textContent"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val titleMatch = titleRegex.find(jsonText)?.groupValues?.get(1)
        val contentMatch = contentRegex.find(jsonText)?.groupValues?.get(1)

        if (!contentMatch.isNullOrBlank()) {
            val unescapedContent = contentMatch.replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val title = if (!titleMatch.isNullOrBlank()) titleMatch else "Notes App Export"
            val parsed = parsePlainTextNotes(unescapedContent)
            val allItems = parsed.flatMap { it.items }
            if (allItems.isNotEmpty()) {
                val date = LocalDate.now()
                val epoch = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                list.add(
                    ParsedNoteGroup(
                        title = title,
                        dateEpochMillis = epoch,
                        dateFormatted = formatDateHeading(date),
                        items = allItems,
                        isSelected = true
                    )
                )
                return list
            }
        }

        // 2. Fallback to org.json if available
        try {
            if (jsonText.startsWith("[")) {
                val arr = JSONArray(jsonText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    parseSingleKeepObject(obj)?.let { list.add(it) }
                }
            } else if (jsonText.startsWith("{")) {
                val obj = JSONObject(jsonText)
                parseSingleKeepObject(obj)?.let { list.add(it) }
            }
        } catch (_: Throwable) {}
        return list
    }

    private fun parseSingleKeepObject(obj: JSONObject): ParsedNoteGroup? {
        val title = obj.optString("title", "Notes App Export")
        val content = obj.optString("textContent", "")
        val timeUsec = obj.optLong("userEditedTimestampUsec", 0L)
        val epoch = if (timeUsec > 0) timeUsec / 1000L else System.currentTimeMillis()
        val date = java.time.Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()

        val parsed = parsePlainTextNotes(content)
        val allItems = parsed.flatMap { it.items }
        if (allItems.isEmpty()) return null

        return ParsedNoteGroup(
            title = if (title.isNotBlank()) title else formatDateHeading(date),
            dateEpochMillis = epoch,
            dateFormatted = formatDateHeading(date),
            items = allItems,
            isSelected = true
        )
    }

    private fun parseCsv(csvText: String): List<ParsedNoteGroup> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val items = mutableListOf<ParsedNoteItem>()
        for (line in lines.drop(1)) {
            val cols = line.split(",").map { it.trim().removeSurrounding("\"") }
            if (cols.size >= 2) {
                val desc = cols.getOrNull(1) ?: cols[0]
                val amtStr = cols.getOrNull(2) ?: cols.getOrNull(1) ?: ""
                val amtMinor = (amtStr.toDoubleOrNull() ?: 0.0) * 100
                if (amtMinor > 0) {
                    val (catId, catName) = detectCategory(desc)
                    items.add(
                        ParsedNoteItem(
                            merchant = desc,
                            amountMinor = amtMinor.toLong(),
                            amountFormatted = CurrencyFormatter.formatMinor(amtMinor.toLong()),
                            suggestedCategoryId = catId,
                            suggestedCategoryName = catName,
                            rawText = line
                        )
                    )
                }
            }
        }
        if (items.isEmpty()) return emptyList()

        val date = LocalDate.now()
        val epoch = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return listOf(
            ParsedNoteGroup(
                title = "Imported CSV Notes",
                dateEpochMillis = epoch,
                dateFormatted = formatDateHeading(date),
                items = items,
                isSelected = true
            )
        )
    }

    /**
     * Determines category based on item merchant name / description.
     */
    fun detectCategory(merchant: String): Pair<Long, String> {
        val s = merchant.lowercase(Locale.ROOT)
        return when {
            s.contains("veg") || s.contains("milk") || s.contains("fruit") || s.contains("egg") ||
                    s.contains("grocery") || s.contains("bread") || s.contains("curd") || s.contains("paneer") ||
                    s.contains("rice") || s.contains("dal") || s.contains("onion") || s.contains("potato") ||
                    s.contains("blinkit") || s.contains("zepto") || s.contains("instamart") || s.contains("supermarket") ->
                Pair(2L, "Groceries")

            s.contains("petrol") || s.contains("fuel") || s.contains("diesel") || s.contains("cng") ->
                Pair(10L, "Fuel & Vehicle")

            s.contains("uber") || s.contains("ola") || s.contains("rapido") || s.contains("auto") ||
                    s.contains("metro") || s.contains("bus") || s.contains("cab") || s.contains("taxi") ->
                Pair(3L, "Transport")

            s.contains("swiggy") || s.contains("zomato") || s.contains("tea") || s.contains("chai") ||
                    s.contains("coffee") || s.contains("starbucks") || s.contains("dosa") || s.contains("idli") ||
                    s.contains("lunch") || s.contains("dinner") || s.contains("breakfast") || s.contains("snack") ||
                    s.contains("pizza") || s.contains("burger") || s.contains("biryani") || s.contains("hotel") ||
                    s.contains("restaurant") || s.contains("cafe") ->
                Pair(1L, "Food & Dining")

            s.contains("recharge") || s.contains("bill") || s.contains("electricity") || s.contains("wifi") ||
                    s.contains("broadband") || s.contains("gas") || s.contains("cylinder") || s.contains("water") ->
                Pair(5L, "Bills & Utilities")

            s.contains("shirt") || s.contains("pant") || s.contains("clothes") || s.contains("dress") ||
                    s.contains("amazon") || s.contains("flipkart") || s.contains("myntra") || s.contains("shoes") ||
                    s.contains("shopping") ->
                Pair(4L, "Shopping")

            s.contains("movie") || s.contains("cinema") || s.contains("pvr") || s.contains("netflix") ||
                    s.contains("prime") || s.contains("hotstar") || s.contains("game") ->
                Pair(6L, "Entertainment")

            s.contains("medicine") || s.contains("doctor") || s.contains("hospital") || s.contains("pharmacy") ||
                    s.contains("tablet") || s.contains("clinic") || s.contains("medical") ->
                Pair(7L, "Health & Medical")

            s.contains("rent") || s.contains("maintenance") ->
                Pair(11L, "Rent & Housing")

            else -> Pair(20L, "Other")
        }
    }
}
