package com.spendly.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendly.app.core.model.ConfirmationStatus
import com.spendly.app.core.model.LocationSource
import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection

// ─────────────────────────────────────────────────────────────────────
//  Category
// ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String,          // Material icon name
    @ColumnInfo(name = "color_hex")
    val colorHex: String,      // Hex color e.g. "#4CAF50"
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false // built-in categories cannot be deleted
)

// ─────────────────────────────────────────────────────────────────────
//  Merchant
// ─────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "merchants",
    indices = [Index(value = ["name"])]
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "alias_patterns")
    val aliasPatterns: String = "",  // JSON array of known name patterns
    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: Long? = null,
    val icon: String = "storefront",
    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = 0
)

// ─────────────────────────────────────────────────────────────────────
//  App Source (payment apps)
// ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "app_sources")
data class AppSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,
    val icon: String = "payment"
)

// ─────────────────────────────────────────────────────────────────────
//  Location (embedded into expenses)
// ─────────────────────────────────────────────────────────────────────

data class LocationData(
    @ColumnInfo(name = "lat") val latitude: Double? = null,
    @ColumnInfo(name = "lng") val longitude: Double? = null,
    @ColumnInfo(name = "location_accuracy") val accuracyMeters: Float? = null,
    @ColumnInfo(name = "location_captured_at") val capturedAt: Long? = null,
    @ColumnInfo(name = "location_source") val source: LocationSource = LocationSource.NONE,
    @ColumnInfo(name = "location_label") val label: String? = null // user-readable place name
)

// ─────────────────────────────────────────────────────────────────────
//  Expense
// ─────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("category_id"),
        Index("transaction_at"),
        Index("confirmation_status"),
        Index("source_package")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Amount stored in minor currency units (paise for INR) to avoid float math. */
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "INR",

    val direction: TransactionDirection = TransactionDirection.DEBIT,

    @ColumnInfo(name = "record_type")
    val recordType: RecordType = RecordType.EXPENSE,

    val merchant: String = "",

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    /** Epoch millis of the actual transaction time */
    @ColumnInfo(name = "transaction_at")
    val transactionAt: Long,

    /** Epoch millis when the record was created in Spendly */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /** Android package name of the payment app that generated the notification */
    @ColumnInfo(name = "source_package")
    val sourcePackage: String? = null,

    /** Transaction reference / UTR / order ID from notification */
    @ColumnInfo(name = "transaction_ref")
    val transactionRef: String? = null,

    val note: String = "",
    val tags: String = "",  // Comma-separated tags

    @Embedded
    val location: LocationData = LocationData(),

    @ColumnInfo(name = "confirmation_status")
    val confirmationStatus: ConfirmationStatus = ConfirmationStatus.CONFIRMED,

    /** Links to duplicate group if applicable */
    @ColumnInfo(name = "duplicate_group_id")
    val duplicateGroupId: String? = null,

    /** ID of the TransactionCandidate this was created from, if any */
    @ColumnInfo(name = "candidate_id")
    val candidateId: Long? = null
)

// ─────────────────────────────────────────────────────────────────────
//  Transaction Candidate (auto-detected, not yet confirmed)
// ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "transaction_candidates")
data class TransactionCandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String,

    @ColumnInfo(name = "notification_key")
    val notificationKey: String? = null,

    @ColumnInfo(name = "notification_text")
    val notificationText: String,  // Only store for short duration; clear after use

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long?,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "INR",

    val direction: TransactionDirection = TransactionDirection.UNKNOWN,

    @ColumnInfo(name = "record_type")
    val recordType: RecordType = RecordType.UNKNOWN,

    val merchant: String = "",

    @ColumnInfo(name = "transaction_ref")
    val transactionRef: String? = null,

    @ColumnInfo(name = "suggested_category_id")
    val suggestedCategoryId: Long? = null,

    /** Confidence score 0.0 - 1.0 */
    val confidence: Float = 0f,

    @ColumnInfo(name = "detected_at")
    val detectedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "notification_posted_at")
    val notificationPostedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_dismissed")
    val isDismissed: Boolean = false,

    @ColumnInfo(name = "expense_id")
    val expenseId: Long? = null,  // Set when converted to confirmed expense

    @Embedded
    val location: LocationData = LocationData()
)
