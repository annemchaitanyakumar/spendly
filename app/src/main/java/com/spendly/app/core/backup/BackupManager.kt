package com.spendly.app.core.backup

import android.content.Context
import android.net.Uri
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.core.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val repository: ExpenseRepository
) {

    // ── CSV Export ───────────────────────────────────────────────────

    suspend fun generateCsvString(): String = withContext(Dispatchers.IO) {
        val expenses = repository.getAllExpenses()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val sb = StringBuilder()
        sb.append("ID,Date,Time,Merchant,Amount,Currency,Category,Direction,Type,Note,Latitude,Longitude,LocationLabel,SourcePackage\n")

        for (e in expenses) {
            val date = Date(e.transactionAt)
            val row = listOf(
                e.id.toString(),
                dateFormat.format(date),
                timeFormat.format(date),
                escapeCsv(e.merchant),
                String.format(Locale.US, "%.2f", e.amountMajor),
                e.currencyCode,
                escapeCsv(e.categoryName ?: "Uncategorized"),
                e.direction.name,
                e.recordType.name,
                escapeCsv(e.note),
                e.latitude?.toString() ?: "",
                e.longitude?.toString() ?: "",
                escapeCsv(e.locationLabel ?: ""),
                escapeCsv(e.sourcePackage ?: "")
            )
            sb.append(row.joinToString(",")).append("\n")
        }
        sb.toString()
    }

    suspend fun writeCsvToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val csv = generateCsvString()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(csv)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    // ── JSON Backup & Restore ────────────────────────────────────────

    suspend fun generateJsonBackup(): String = withContext(Dispatchers.IO) {
        val expenses = repository.getAllExpenses()
        val categories = repository.getAllCategories()

        val root = JSONObject()
        root.put("app", "Spendly")
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("expenseCount", expenses.size)

        // Categories
        val catsArray = JSONArray()
        for (c in categories) {
            val catObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("icon", c.icon)
                put("colorHex", c.colorHex)
                put("sortOrder", c.sortOrder)
                put("isDefault", c.isDefault)
                put("isSystem", c.isSystem)
            }
            catsArray.put(catObj)
        }
        root.put("categories", catsArray)

        // Expenses
        val expArray = JSONArray()
        for (e in expenses) {
            val expObj = JSONObject().apply {
                put("id", e.id)
                put("amountMinor", e.amountMinor)
                put("currencyCode", e.currencyCode)
                put("direction", e.direction.name)
                put("recordType", e.recordType.name)
                put("merchant", e.merchant)
                if (e.categoryId != null) put("categoryId", e.categoryId)
                put("transactionAt", e.transactionAt)
                put("createdAt", e.createdAt)
                put("updatedAt", e.updatedAt)
                if (e.sourcePackage != null) put("sourcePackage", e.sourcePackage)
                if (e.transactionRef != null) put("transactionRef", e.transactionRef)
                put("note", e.note)
                if (e.latitude != null) put("latitude", e.latitude)
                if (e.longitude != null) put("longitude", e.longitude)
                if (e.locationAccuracy != null) put("locationAccuracy", e.locationAccuracy.toDouble())
                if (e.locationLabel != null) put("locationLabel", e.locationLabel)
                put("locationSource", e.locationSource.name)
                put("confirmationStatus", e.confirmationStatus.name)
            }
            expArray.put(expObj)
        }
        root.put("expenses", expArray)

        root.toString(2)
    }

    suspend fun writeBackupToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = generateJsonBackup()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(json)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    data class ImportResult(
        val success: Boolean,
        val importedExpenses: Int = 0,
        val importedCategories: Int = 0,
        val errorMessage: String? = null
    )

    suspend fun restoreFromJsonUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: return@withContext ImportResult(false, errorMessage = "Failed to open backup file")

            restoreFromJsonString(jsonString)
        } catch (e: Exception) {
            ImportResult(false, errorMessage = "Error parsing backup file: ${e.message}")
        }
    }

    suspend fun restoreFromJsonString(jsonString: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val app = root.optString("app")
            if (app != "Spendly") {
                return@withContext ImportResult(false, errorMessage = "Invalid backup file: not a Spendly backup")
            }

            // Restore categories
            val catsArray = root.optJSONArray("categories")
            val categories = mutableListOf<Category>()
            if (catsArray != null) {
                for (i in 0 until catsArray.length()) {
                    val obj = catsArray.getJSONObject(i)
                    categories.add(
                        Category(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            icon = obj.optString("icon", "category"),
                            colorHex = obj.optString("colorHex", "#2E7D32"),
                            sortOrder = obj.optInt("sortOrder", 0),
                            isDefault = obj.optBoolean("isDefault", false),
                            isSystem = obj.optBoolean("isSystem", false)
                        )
                    )
                }
                repository.insertAllCategories(categories)
            }

            // Restore expenses
            val expArray = root.optJSONArray("expenses")
            val expenses = mutableListOf<Expense>()
            if (expArray != null) {
                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    expenses.add(
                        Expense(
                            amountMinor = obj.getLong("amountMinor"),
                            currencyCode = obj.optString("currencyCode", "INR"),
                            direction = try {
                                TransactionDirection.valueOf(obj.optString("direction", "DEBIT"))
                            } catch (_: Exception) { TransactionDirection.DEBIT },
                            recordType = try {
                                RecordType.valueOf(obj.optString("recordType", "EXPENSE"))
                            } catch (_: Exception) { RecordType.EXPENSE },
                            merchant = obj.optString("merchant", ""),
                            categoryId = if (obj.has("categoryId")) obj.getLong("categoryId") else null,
                            transactionAt = obj.getLong("transactionAt"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                            sourcePackage = if (obj.has("sourcePackage")) obj.getString("sourcePackage") else null,
                            transactionRef = if (obj.has("transactionRef")) obj.getString("transactionRef") else null,
                            note = obj.optString("note", ""),
                            latitude = if (obj.has("latitude")) obj.getDouble("latitude") else null,
                            longitude = if (obj.has("longitude")) obj.getDouble("longitude") else null,
                            locationAccuracy = if (obj.has("locationAccuracy")) obj.getDouble("locationAccuracy").toFloat() else null,
                            locationLabel = if (obj.has("locationLabel")) obj.getString("locationLabel") else null,
                            locationSource = try {
                                LocationSource.valueOf(obj.optString("locationSource", "NONE"))
                            } catch (_: Exception) { LocationSource.NONE },
                            confirmationStatus = try {
                                ConfirmationStatus.valueOf(obj.optString("confirmationStatus", "CONFIRMED"))
                            } catch (_: Exception) { ConfirmationStatus.CONFIRMED }
                        )
                    )
                }
                repository.insertAllExpenses(expenses)
            }

            ImportResult(
                success = true,
                importedExpenses = expenses.size,
                importedCategories = categories.size
            )
        } catch (e: Exception) {
            ImportResult(false, errorMessage = "Corrupt backup: ${e.message}")
        }
    }
}
