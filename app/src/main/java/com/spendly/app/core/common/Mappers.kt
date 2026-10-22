package com.spendly.app.core.common

import com.spendly.app.core.database.entity.*
import com.spendly.app.core.model.*

// ─────────────────────────────────────────────────────────────────────
//  Entity → Domain
// ─────────────────────────────────────────────────────────────────────

fun ExpenseEntity.toDomain(category: CategoryEntity? = null) = Expense(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    direction = direction,
    recordType = recordType,
    merchant = merchant,
    categoryId = categoryId,
    categoryName = category?.name,
    categoryIcon = category?.icon,
    categoryColorHex = category?.colorHex,
    transactionAt = transactionAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourcePackage = sourcePackage,
    transactionRef = transactionRef,
    note = note,
    tags = tags.split(",").filter { it.isNotBlank() },
    latitude = location.latitude,
    longitude = location.longitude,
    locationAccuracy = location.accuracyMeters,
    locationCapturedAt = location.capturedAt,
    locationSource = location.source,
    locationLabel = location.label,
    confirmationStatus = confirmationStatus,
    duplicateGroupId = duplicateGroupId,
    candidateId = candidateId
)

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    direction = direction,
    recordType = recordType,
    merchant = merchant,
    categoryId = categoryId,
    transactionAt = transactionAt,
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
    sourcePackage = sourcePackage,
    transactionRef = transactionRef,
    note = note,
    tags = tags.joinToString(","),
    location = LocationData(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = locationAccuracy,
        capturedAt = locationCapturedAt,
        source = locationSource,
        label = locationLabel
    ),
    confirmationStatus = confirmationStatus,
    duplicateGroupId = duplicateGroupId,
    candidateId = candidateId
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    icon = icon,
    colorHex = colorHex,
    isDefault = isDefault,
    sortOrder = sortOrder,
    isSystem = isSystem
)

fun TransactionCandidateEntity.toDomain() = TransactionCandidate(
    id = id,
    sourcePackage = sourcePackage,
    notificationKey = notificationKey,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    direction = direction,
    recordType = recordType,
    merchant = merchant,
    transactionRef = transactionRef,
    suggestedCategoryId = suggestedCategoryId,
    confidence = confidence,
    detectedAt = detectedAt,
    isDismissed = isDismissed,
    expenseId = expenseId,
    latitude = location.latitude,
    longitude = location.longitude,
    locationSource = location.source
)
