package com.spendly.app.core.model

/**
 * Domain model for an expense - decoupled from Room entity.
 */
data class Expense(
    val id: Long = 0,
    val amountMinor: Long,
    val currencyCode: String = "INR",
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val recordType: RecordType = RecordType.EXPENSE,
    val merchant: String = "",
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categoryIcon: String? = null,
    val categoryColorHex: String? = null,
    val transactionAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourcePackage: String? = null,
    val transactionRef: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val locationCapturedAt: Long? = null,
    val locationSource: LocationSource = LocationSource.NONE,
    val locationLabel: String? = null,
    val confirmationStatus: ConfirmationStatus = ConfirmationStatus.CONFIRMED,
    val duplicateGroupId: String? = null,
    val candidateId: Long? = null
) {
    /** Human readable amount in major units (e.g. 149.50 for INR) */
    val amountMajor: Double get() = amountMinor / 100.0

    val hasLocation: Boolean get() = latitude != null && longitude != null

    val isExpense: Boolean
        get() = confirmationStatus == ConfirmationStatus.CONFIRMED &&
                direction == TransactionDirection.DEBIT &&
                recordType == RecordType.EXPENSE
}

/**
 * Domain model for a transaction candidate (auto-detected, pending confirmation).
 */
data class TransactionCandidate(
    val id: Long = 0,
    val sourcePackage: String,
    val notificationKey: String? = null,
    val amountMinor: Long?,
    val currencyCode: String = "INR",
    val direction: TransactionDirection = TransactionDirection.UNKNOWN,
    val recordType: RecordType = RecordType.UNKNOWN,
    val merchant: String = "",
    val transactionRef: String? = null,
    val suggestedCategoryId: Long? = null,
    val confidence: Float = 0f,
    val detectedAt: Long = System.currentTimeMillis(),
    val isDismissed: Boolean = false,
    val expenseId: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationSource: LocationSource = LocationSource.NONE
) {
    val amountMajor: Double? get() = amountMinor?.let { it / 100.0 }
    val isPending: Boolean get() = !isDismissed && expenseId == null
}

/**
 * Domain model for a category.
 */
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false
)

/**
 * Monthly spending summary for dashboard.
 */
data class MonthSummary(
    val year: Int,
    val month: Int,
    val totalMinor: Long,
    val previousMonthTotalMinor: Long?,
    val categoryBreakdown: List<CategorySpending>,
    val dailyTotals: List<DailySpending>,
    val topMerchants: List<MerchantSpending>
) {
    val totalMajor: Double get() = totalMinor / 100.0
    val previousMajor: Double? get() = previousMonthTotalMinor?.let { it / 100.0 }
    val changePercent: Double?
        get() {
            val prev = previousMonthTotalMinor ?: return null
            if (prev == 0L) return null
            return ((totalMinor - prev).toDouble() / prev) * 100.0
        }
}

data class CategorySpending(
    val category: Category?,
    val totalMinor: Long
) {
    val totalMajor: Double get() = totalMinor / 100.0
}

data class DailySpending(
    val dayEpoch: Long,
    val totalMinor: Long
) {
    val totalMajor: Double get() = totalMinor / 100.0
}

data class MerchantSpending(
    val merchant: String,
    val totalMinor: Long,
    val transactionCount: Int
) {
    val totalMajor: Double get() = totalMinor / 100.0
}
