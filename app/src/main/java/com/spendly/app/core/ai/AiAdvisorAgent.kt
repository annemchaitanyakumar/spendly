package com.spendly.app.core.ai

import android.util.Log
import com.spendly.app.core.model.Expense
import com.spendly.app.core.model.MonthSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class NaturalExpenseResult(
    val amountMinor: Long? = null,
    val merchant: String = "",
    val categoryName: String = "General",
    val note: String = ""
)

/**
 * Universal AI Financial Intelligence Agent powered by Google Gemini (Free Tier).
 * Provides intelligent capabilities across Spendly:
 * 1. AI Financial Insights & Monthly Analytics Advisor.
 * 2. Natural Language Quick-Add for expenses.
 * 3. Daily Notes & Spends Journal Summarizer.
 */
object AiAdvisorAgent {
    private const val TAG = "AiAdvisorAgent"

    private val candidateModels = listOf(
        "gemini-2.0-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-flash",
        "gemini-2.5-flash",
        "gemini-pro"
    )

    private suspend fun callGemini(apiKey: String, prompt: String, temperature: Double = 0.2): String? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().trim('"', '\'')
        if (cleanKey.isBlank()) return@withContext null

        for (modelName in candidateModels) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", temperature)
                    })
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val resultText = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                    if (resultText.isNotBlank()) return@withContext resultText
                } else if (responseCode == 404) {
                    continue
                } else {
                    Log.w(TAG, "Gemini $modelName returned HTTP $responseCode")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception calling Gemini $modelName: ${e.message}")
            }
        }
        null
    }

    /**
     * Generates a concise, executive monthly financial analysis for the Insights / Home screen.
     */
    suspend fun generateMonthlyAnalysis(
        apiKey: String,
        monthName: String,
        totalSpendsFormatted: String,
        totalIncomeFormatted: String,
        netCashFlowFormatted: String,
        topCategoriesSummary: String
    ): String {
        val prompt = """
            You are Spendly's AI Personal Financial Analyst.
            Analyze this monthly spending summary for $monthName:
            - Total Outflow / Spends: $totalSpendsFormatted
            - Total Inflow / Income: $totalIncomeFormatted
            - Net Cash Flow: $netCashFlowFormatted
            - Category Breakdown: $topCategoriesSummary
            
            Provide a brief, high-value financial review (under 120 words) with:
            1. An executive financial health verdict (Healthy / Moderate / High Burn).
            2. Top spending observation.
            3. Two actionable, realistic tips to optimize savings.
            
            Keep the tone clean, encouraging, minimal, and professional. Avoid emojis overload.
        """.trimIndent()

        val aiResponse = callGemini(apiKey, prompt, temperature = 0.3)
        if (!aiResponse.isNullOrBlank()) {
            return aiResponse
        }

        // Local Smart Fallback if offline
        return buildString {
            append("Executive Summary ($monthName):\n")
            append("Total outflow is $totalSpendsFormatted against $totalIncomeFormatted income.\n")
            if (topCategoriesSummary.isNotBlank()) {
                append("Top expense categories: $topCategoriesSummary.\n")
            }
            append("\nSavings Tip: Setting category spending caps for high-velocity merchants can reduce discretionary burn by up to 15%.")
        }
    }

    /**
     * Parses freeform natural language text into a structured Expense for quick entry.
     */
    suspend fun parseNaturalExpense(apiKey: String, input: String): NaturalExpenseResult {
        val prompt = """
            Extract transaction details from this quick expense note:
            "$input"
            
            Respond ONLY with a valid JSON object matching this schema:
            {
               "amount": number or null,
               "merchant": string,
               "category": string,
               "note": string
            }
            Available Categories: Food & Dining, Shopping, Transport, Groceries, Bills & Utilities, Entertainment, Health & Fitness, Transfers & UPI, Travel, General.
        """.trimIndent()

        val aiResponse = callGemini(apiKey, prompt, temperature = 0.1)
        if (!aiResponse.isNullOrBlank()) {
            try {
                val cleanJson = aiResponse.removeSurrounding("```json", "```").trim()
                val json = JSONObject(cleanJson)
                val amt = if (!json.isNull("amount")) (json.optDouble("amount") * 100).toLong() else null
                val merch = json.optString("merchant", "")
                val cat = json.optString("category", "General")
                val note = json.optString("note", input)
                return NaturalExpenseResult(amt, merch, cat, note)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse JSON from AI natural expense: ${e.message}")
            }
        }

        // Local Fallback Regex extraction
        val amountPattern = Regex("""(?:₹|Rs\.?\s*|INR\s*)?([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = amountPattern.find(input)
        val detectedAmount = match?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 100).toLong() }
        val remaining = if (match != null) input.replace(match.value, "").trim() else input

        return NaturalExpenseResult(
            amountMinor = detectedAmount,
            merchant = remaining.take(30),
            categoryName = "General",
            note = input
        )
    }

    /**
     * Summarizes daily spending notes from the Notes journal into an executive digest.
     */
    suspend fun summarizeNotesDiary(apiKey: String, monthTitle: String, rawNotesText: String): String {
        val prompt = """
            You are Spendly's AI Journal Assistant.
            Analyze these daily expense notes for $monthTitle:
            $rawNotesText
            
            Generate a clean, structured summary:
            1. Total estimated spends mentioned.
            2. Grouped summary by date/item.
            3. One key savings recommendation.
            
            Format cleanly in markdown bullet points. Keep it professional.
        """.trimIndent()

        val response = callGemini(apiKey, prompt, temperature = 0.2)
        if (!response.isNullOrBlank()) return response

        return "Notes Digest for $monthTitle:\n${rawNotesText.take(200)}...\n\nKeep tracking your daily spends regularly for deeper insights."
    }

    /**
     * Parses free-form text from external notes apps into structured note groups.
     */
    suspend fun parseNotesWithAi(apiKey: String, rawNotesText: String): List<com.spendly.app.core.parser.ParsedNoteGroup> {
        val cleanKey = apiKey.trim().trim('"', '\'')
        if (cleanKey.isBlank() || rawNotesText.isBlank()) {
            return com.spendly.app.core.parser.NotesImportParser.parseNotesText(rawNotesText)
        }

        val prompt = """
            You are Spendly's Intelligent Notes Parser.
            Extract all spending/expense entries from the following raw notes into a JSON array:
            [
              {
                "title": "Date or Note Title (e.g. 01 Oct 2026)",
                "date": "YYYY-MM-DD",
                "items": [
                  {
                    "merchant": "Item or Merchant name (e.g. Vegetables)",
                    "amount": 100.0,
                    "category": "Groceries"
                  }
                ]
              }
            ]
            
            Valid categories: "Food & Dining", "Groceries", "Transport", "Shopping", "Bills & Utilities", "Entertainment", "Health & Medical", "Education", "Travel & Trips", "Fuel & Vehicle", "Rent & Housing", "Other".
            
            Raw Notes:
            $rawNotesText
            
            Output ONLY valid JSON array.
        """.trimIndent()

        val response = callGemini(cleanKey, prompt, temperature = 0.1)
        if (!response.isNullOrBlank()) {
            try {
                val cleanJson = response.substringAfter("[").substringBeforeLast("]")
                val arr = JSONArray("[$cleanJson]")
                val groups = mutableListOf<com.spendly.app.core.parser.ParsedNoteGroup>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val title = obj.optString("title", "Notes Group ${i + 1}")
                    val dateStr = obj.optString("date", "")
                    val date = try {
                        java.time.LocalDate.parse(dateStr)
                    } catch (_: Exception) {
                        java.time.LocalDate.now()
                    }
                    val epoch = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                    val items = mutableListOf<com.spendly.app.core.parser.ParsedNoteItem>()
                    for (j in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(j)
                        val merchant = itemObj.optString("merchant", "Expense")
                        val amountMajor = itemObj.optDouble("amount", 0.0)
                        val catName = itemObj.optString("category", "Other")
                        val (catId, finalCatName) = com.spendly.app.core.parser.NotesImportParser.detectCategory(merchant)
                        val amountMinor = (amountMajor * 100).toLong()
                        if (amountMinor > 0L) {
                            items.add(
                                com.spendly.app.core.parser.ParsedNoteItem(
                                    merchant = merchant,
                                    amountMinor = amountMinor,
                                    amountFormatted = com.spendly.app.core.common.CurrencyFormatter.formatMinor(amountMinor),
                                    suggestedCategoryId = catId,
                                    suggestedCategoryName = if (catName != "Other") catName else finalCatName,
                                    rawText = "$merchant $amountMajor"
                                )
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        groups.add(
                            com.spendly.app.core.parser.ParsedNoteGroup(
                                title = title,
                                dateEpochMillis = epoch,
                                dateFormatted = com.spendly.app.core.common.DateTimeUtils.formatDate(epoch),
                                items = items,
                                isSelected = true
                            )
                        )
                    }
                }
                if (groups.isNotEmpty()) return groups
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse AI notes json: ${e.message}")
            }
        }

        return com.spendly.app.core.parser.NotesImportParser.parseNotesText(rawNotesText)
    }
}
