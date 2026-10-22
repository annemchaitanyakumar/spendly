package com.spendly.app.core.database.dao

import androidx.room.*
import com.spendly.app.core.database.entity.TransactionCandidateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionCandidateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candidate: TransactionCandidateEntity): Long

    @Update
    suspend fun update(candidate: TransactionCandidateEntity)

    @Query("SELECT * FROM transaction_candidates WHERE id = :id")
    suspend fun getById(id: Long): TransactionCandidateEntity?

    @Query("SELECT * FROM transaction_candidates WHERE is_dismissed = 0 AND expense_id IS NULL ORDER BY detected_at DESC")
    fun observePending(): Flow<List<TransactionCandidateEntity>>

    @Query("SELECT * FROM transaction_candidates WHERE is_dismissed = 0 ORDER BY detected_at DESC LIMIT :limit")
    fun observeRecentCandidates(limit: Int = 5): Flow<List<TransactionCandidateEntity>>

    @Query("SELECT COUNT(*) FROM transaction_candidates WHERE is_dismissed = 0 AND expense_id IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM transaction_candidates ORDER BY detected_at DESC LIMIT :limit")
    fun observeHistory(limit: Int = 50): Flow<List<TransactionCandidateEntity>>

    @Query("UPDATE transaction_candidates SET is_dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE transaction_candidates SET expense_id = :expenseId WHERE id = :id")
    suspend fun markConfirmed(id: Long, expenseId: Long)

    @Query("SELECT * FROM transaction_candidates WHERE notification_key = :notifKey LIMIT 1")
    suspend fun findByNotificationKey(notifKey: String): TransactionCandidateEntity?

    // Deduplication check: same source, similar amount, within time window
    @Query("""
        SELECT * FROM transaction_candidates
        WHERE source_package = :sourcePackage
          AND amount_minor = :amountMinor
          AND ABS(detected_at - :detectedAt) < :windowMs
        LIMIT 1
    """)
    suspend fun findDuplicate(
        sourcePackage: String,
        amountMinor: Long?,
        detectedAt: Long,
        windowMs: Long = 300_000L
    ): TransactionCandidateEntity?

    @Query("DELETE FROM transaction_candidates WHERE detected_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    // Clear notification text after processing to avoid storing sensitive data
    @Query("UPDATE transaction_candidates SET notification_text = '' WHERE id = :id")
    suspend fun clearNotificationText(id: Long)
}
