package com.spendly.app.parser

import com.spendly.app.core.parser.NotesImportParser
import org.junit.Assert.*
import org.junit.Test

class NotesImportParserTest {

    @Test
    fun testParseMultiDayNotes() {
        val notesText = """
            October spends
            Oct 1
            Vegetables 100
            Milk 35.50
            Oct 2
            Petrol 500
            Swiggy 320
        """.trimIndent()

        val groups = NotesImportParser.parseNotesText(notesText)
        assertEquals(2, groups.size)

        // Group 1: Oct 1
        val group1 = groups[0]
        assertTrue(group1.title.contains("01 Oct"))
        assertEquals(2, group1.items.size)
        assertEquals("Vegetables", group1.items[0].merchant)
        assertEquals(10000L, group1.items[0].amountMinor)
        assertEquals("Groceries", group1.items[0].suggestedCategoryName)
        assertEquals("Milk", group1.items[1].merchant)
        assertEquals(3550L, group1.items[1].amountMinor)
        assertEquals(13550L, group1.totalMinor)

        // Group 2: Oct 2
        val group2 = groups[1]
        assertTrue(group2.title.contains("02 Oct"))
        assertEquals(2, group2.items.size)
        assertEquals("Petrol", group2.items[0].merchant)
        assertEquals(50000L, group2.items[0].amountMinor)
        assertEquals("Fuel & Vehicle", group2.items[0].suggestedCategoryName)
        assertEquals("Swiggy", group2.items[1].merchant)
        assertEquals(32000L, group2.items[1].amountMinor)
        assertEquals("Food & Dining", group2.items[1].suggestedCategoryName)
        assertEquals(82000L, group2.totalMinor)
    }

    @Test
    fun testParseKeepJsonFormat() {
        val keepJson = """
            {
              "title": "Weekend Trip Expenses",
              "textContent": "Cab to airport 450\nBreakfast ₹220\nHotel booking 2500"
            }
        """.trimIndent()

        val groups = NotesImportParser.parseNotesText(keepJson)
        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals("Weekend Trip Expenses", group.title)
        assertEquals(3, group.items.size)
        assertEquals("Cab to airport", group.items[0].merchant)
        assertEquals(45000L, group.items[0].amountMinor)
        assertEquals(317000L, group.totalMinor)
    }

    @Test
    fun testParseIndividualExpenseLine() {
        val item1 = NotesImportParser.parseExpenseLine("- Groceries ₹ 450.00")
        assertNotNull(item1)
        assertEquals("Groceries", item1?.merchant)
        assertEquals(45000L, item1?.amountMinor)

        val item2 = NotesImportParser.parseExpenseLine("Paid 1200 for Electricity bill")
        assertNotNull(item2)
        assertEquals("Electricity bill", item2?.merchant)
        assertEquals(120000L, item2?.amountMinor)
        assertEquals("Bills & Utilities", item2?.suggestedCategoryName)
    }
}
