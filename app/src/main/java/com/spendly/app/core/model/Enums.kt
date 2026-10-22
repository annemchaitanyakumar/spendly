package com.spendly.app.core.model

/**
 * Transaction direction - whether money is going out or coming in.
 */
enum class TransactionDirection {
    DEBIT,    // Money going out (expense)
    CREDIT,   // Money coming in
    UNKNOWN
}

/**
 * Record type for classification.
 */
enum class RecordType {
    EXPENSE,          // Standard outgoing payment
    INCOME,           // Incoming amount
    TRANSFER,         // Transfer between accounts
    REFUND,           // Refund/reversal of a previous expense
    FAILED,           // Failed transaction
    PENDING,          // Not yet settled
    UNKNOWN
}

/**
 * Confirmation status of a transaction.
 */
enum class ConfirmationStatus {
    CONFIRMED,        // User confirmed this expense
    PENDING_REVIEW,   // Detected but awaiting user confirmation
    DISMISSED,        // User dismissed the candidate
    AUTO_CONFIRMED    // Auto-confirmed for low-risk, clear transactions (future feature)
}

/**
 * Location capture method.
 */
enum class LocationSource {
    MANUAL,           // User chose location manually
    NOTIFICATION_TIME,// Captured when notification arrived
    CONFIRMATION_TIME,// Captured when user confirmed expense
    NONE              // No location
}
