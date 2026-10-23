package com.spendly.app.core.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiAnalysisResult(
    val isTransaction: Boolean,
    val amountMinor: Long? = null,
    val merchant: String = "",
    val categoryName: String = "",
    val recordType: String = "EXPENSE",
    val professionalTitle: String = "",
    val professionalSubtitle: String = "",
    val reason: String = ""
)

/**
 * Free AI Transaction Agent supporting Google Gemini 2.0 Flash / 1.5 Flash.
 *
 * Uses the free Google AI Studio API tier (15 requests/min, zero cost, no credit card required).
 * Performs deep semantic inspection to:
 * 1. Eliminate false positives (e.g. Amazon replacement prices, newsletter discounts, cancellations).
 * 2. Professionalize notification titles (e.g. "Paid ₹10 for Coffee", "Transferred ₹1 to Ramesh").
 */
object AiTransactionAgent {
    private const val TAG = "AiTransactionAgent"

    @Volatile
    private var activeModel: String = "gemini-2.0-flash"

    suspend fun verifyAndFormat(
        apiKey: String,
        text: String
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim().trim('"', '\'')
        if (cleanKey.isBlank()) {
            return@withContext AiAnalysisResult(
                isTransaction = true,
                reason = "No API key configured; using local engine"
            )
        }

        // List of candidate models in priority order
        val modelsToTry = listOf(
            activeModel,
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-2.5-flash",
            "gemini-pro"
        ).distinct()

        var lastError = ""

        for (modelName in modelsToTry) {
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

                val prompt = """
                    You are the AI Transaction Intelligence assistant for Spendly (Android personal finance app).
                    Analyze this smartphone notification or email snippet.
                    
                    Tasks:
                    1. Is this a REAL, COMPLETED financial transaction where money was debited, credited, or transferred?
                       - If this is a promotional email, suggested replacement, product catalogue, cart reminder, bill due reminder, subscription cancellation, or OTP, set "is_transaction" to FALSE.
                    2. If true:
                       - amount: the exact amount in rupees (e.g. 10.0, 150.0, 1.0)
                       - type: "EXPENSE" (shopping/purchases), "TRANSFER" (UPI/P2P transfer or transfer to person/account), "INCOME" (money credited/received), "REFUND" (refund from store)
                       - clean_merchant: clean recipient/store name (e.g. "Starbucks", "Swiggy", "Amazon", "Mom", "Ramesh"). If unknown or generic transfer, use "". NEVER output "Messages", "Gmail", "SMS", "Bank SMS", "PhonePe", "Google Pay", "Paytm".
                       - category: e.g. "Food & Dining", "Shopping", "Transfers & UPI", "Groceries", "Bills & Utilities", "Entertainment", "Income", "General"
                       - professional_title: a minimal, professional notification title, e.g.:
                         * If expense with merchant: "Paid ₹150.00 to Starbucks"
                         * If expense without merchant: "Paid ₹10.00"
                         * If transfer with person: "Transferred ₹1.00 to Ramesh"
                         * If transfer without recipient: "Transferred ₹1.00"
                         * If income: "Received ₹500.00 from Mom" or "Received ₹500.00"
                         * If refund: "Refund ₹250.00 from Amazon"
                       - professional_subtitle: e.g. "Shopping • Spendly" or "Transfers & UPI • Spendly"
                    
                    Snippet to analyze:
                    $text
                    
                    Respond ONLY with a valid JSON object matching this schema:
                    {
                      "is_transaction": boolean,
                      "amount": number or null,
                      "type": "EXPENSE" | "TRANSFER" | "INCOME" | "REFUND" | "NONE",
                      "clean_merchant": string,
                      "category": string,
                      "professional_title": string,
                      "professional_subtitle": string,
                      "reason": string
                    }
                """.trimIndent()

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
                        put("temperature", 0.1)
                        put("responseMimeType", "application/json")
                    })
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    activeModel = modelName // Cache the verified working model
                    val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val textResponse = parts?.optJSONObject(0)?.optString("text") ?: ""

                    val cleanJson = textResponse.trim().removeSurrounding("```json", "```").trim()
                    val json = JSONObject(cleanJson)

                    val isTx = json.optBoolean("is_transaction", false)
                    val amt = if (!json.isNull("amount")) (json.optDouble("amount") * 100).toLong() else null
                    val type = json.optString("type", "EXPENSE")
                    val merch = json.optString("clean_merchant", "")
                    val cat = json.optString("category", "General")
                    val pTitle = json.optString("professional_title", "")
                    val pSub = json.optString("professional_subtitle", "")
                    val reason = json.optString("reason", "")

                    Log.d(TAG, "AI Agent ($modelName) result: isTx=$isTx, type=$type, title='$pTitle'")
                    return@withContext AiAnalysisResult(
                        isTransaction = isTx,
                        amountMinor = amt,
                        merchant = merch,
                        categoryName = cat,
                        recordType = type,
                        professionalTitle = pTitle,
                        professionalSubtitle = pSub,
                        reason = reason
                    )
                } else {
                    val errorDetails = connection.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                    lastError = "HTTP $responseCode from $modelName: $errorDetails"
                    Log.w(TAG, "Model $modelName returned $lastError")
                    // If it was 404, continue to next candidate model!
                    if (responseCode == 404) {
                        continue
                    } else {
                        // For other errors (like 400 Bad Key or 403), stop and report details
                        return@withContext localSemanticCheck(
                            text = text,
                            fallbackReason = "AI Agent error ($responseCode): ${extractGoogleErrorMessage(errorDetails)}"
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
                Log.w(TAG, "Exception calling $modelName: $lastError")
            }
        }

        localSemanticCheck(
            text = text,
            fallbackReason = "AI Agent fallback: $lastError"
        )
    }

    suspend fun verifyTransaction(
        apiKey: String,
        text: String
    ): AiAnalysisResult = verifyAndFormat(apiKey, text)

    private fun extractGoogleErrorMessage(errorJson: String): String {
        return try {
            val root = JSONObject(errorJson)
            val err = root.optJSONObject("error")
            err?.optString("message") ?: errorJson.take(120)
        } catch (_: Exception) {
            errorJson.take(120)
        }
    }

    private fun localSemanticCheck(text: String, fallbackReason: String): AiAnalysisResult {
        val lower = text.lowercase()
        // Strong rejection markers: suggested replacements, cancellations, promos, OTPs, cashback ads
        if (lower.contains("suggested replacement") ||
            lower.contains("choose as replacement") ||
            lower.contains("subscription cancelled") ||
            lower.contains("order cancelled") ||
            lower.contains("promotional") ||
            lower.contains("newsletter") ||
            lower.contains("cart reminder") ||
            lower.contains("claim your discount") ||
            lower.contains("cashback offer") ||
            lower.contains("super cashback") ||
            lower.contains("earn cashback") ||
            lower.contains("earning cashback") ||
            lower.contains("win cashback") ||
            lower.contains("t&c apply") ||
            lower.contains("t&c") ||
            lower.contains("don't miss") ||
            lower.contains("dont miss") ||
            lower.contains("add ₹") ||
            lower.contains("add rs") ||
            lower.contains("otp") ||
            lower.contains("verification code")
        ) {
            return AiAnalysisResult(
                isTransaction = false,
                reason = "Rejected promotional / suggested replacement content (Local safety filter)"
            )
        }

        val isFinancial = (lower.contains("debited") || lower.contains("credited") ||
                lower.contains("paid ") || lower.contains("transferred") ||
                lower.contains("received ") || lower.contains("sent ") ||
                lower.contains("refund")) && !lower.contains("t&c")

        return AiAnalysisResult(
            isTransaction = isFinancial,
            reason = fallbackReason
        )
    }
}
