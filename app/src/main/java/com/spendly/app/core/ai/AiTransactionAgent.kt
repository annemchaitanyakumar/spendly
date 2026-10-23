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
    val recordType: String = "EXPENSE",
    val reason: String = ""
)

/**
 * Free AI Transaction Agent supporting Google Gemini 1.5 Flash / 2.0 Flash.
 *
 * Uses the free Google AI Studio API tier (15 requests/min, free tier with no credit card).
 * Performs deep semantic inspection to reject promotional emails, cancellation notices,
 * suggested replacements, cart reminders, and non-financial snippets.
 */
object AiTransactionAgent {
    private const val TAG = "AiTransactionAgent"

    suspend fun verifyTransaction(
        apiKey: String,
        text: String
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext AiAnalysisResult(
                isTransaction = true,
                reason = "No API key configured; falling back to local engine"
            )
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val prompt = """
                Analyze this smartphone notification or email snippet and decide if it represents an ACTUAL EXECUTED financial transaction (money leaving or entering an account/wallet).
                
                Rules:
                - If this is a promotional email, suggested replacement, product catalogue, cart reminder, bill due reminder, subscription cancellation, or OTP, set "is_transaction" to FALSE.
                - Only set "is_transaction" to TRUE if money was actually transferred, debited, or credited.
                
                Text to analyze:
                $text
                
                Respond ONLY with a valid JSON object matching this schema:
                {
                  "is_transaction": boolean,
                  "amount": number or null,
                  "type": "EXPENSE" | "TRANSFER" | "INCOME" | "REFUND" | "NONE",
                  "merchant": string,
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
                val merch = json.optString("merchant", "")
                val reason = json.optString("reason", "")

                Log.d(TAG, "AI Agent result: isTx=$isTx, type=$type, amt=$amt, merchant=$merch, reason=$reason")
                AiAnalysisResult(
                    isTransaction = isTx,
                    amountMinor = amt,
                    merchant = merch,
                    recordType = type,
                    reason = reason
                )
            } else {
                val errorStream = connection.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
                Log.w(TAG, "Gemini API returned HTTP $responseCode: $errorStream")
                AiAnalysisResult(
                    isTransaction = true,
                    reason = "AI Agent error ($responseCode); local fallback used"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling AI Agent: ${e.message}")
            AiAnalysisResult(
                isTransaction = true,
                reason = "AI Agent exception; local fallback used"
            )
        }
    }
}
