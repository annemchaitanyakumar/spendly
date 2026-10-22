package com.spendly.app.core.database.dao

import androidx.room.*
import com.spendly.app.core.database.entity.ExpenseEntity
import com.spendly.app.core.model.ConfirmationStatus
import com.spendly.app.core.model.RecordType
import com.spendly.app.core.model.TransactionDirection
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expenses ORDER BY transaction_at DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseEntity?>

    // ── All confirmed expenses ───────────────────────────────────────
    @Query("""
        SELECT * FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type IN ('EXPENSE', 'TRANSFER')
        ORDER BY transaction_at DESC
    """)
    fun observeAllConfirmedExpenses(): Flow<List<ExpenseEntity>>

    // ── Monthly queries ──────────────────────────────────────────────
    @Query("""
        SELECT * FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type IN ('EXPENSE', 'TRANSFER')
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        ORDER BY transaction_at DESC
    """)
    fun observeMonthlyExpenses(startMillis: Long, endMillis: Long): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type = 'EXPENSE'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
    """)
    fun observeMonthlyTotal(startMillis: Long, endMillis: Long): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type = 'EXPENSE'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
    """)
    fun observeMonthlyPurchasesTotal(startMillis: Long, endMillis: Long): Flow<Long>

    // ── Category totals for a period ────────────────────────────────
    @Query("""
        SELECT category_id, SUM(amount_minor) as total FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type = 'EXPENSE'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        GROUP BY category_id
    """)
    fun observeCategoryTotals(startMillis: Long, endMillis: Long): Flow<List<CategoryTotal>>

    // ── Daily totals for a period ────────────────────────────────────
    @Query("""
        SELECT 
            (transaction_at / 86400000) * 86400000 as day_epoch,
            SUM(amount_minor) as total
        FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type IN ('EXPENSE', 'TRANSFER')
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        GROUP BY day_epoch
        ORDER BY day_epoch ASC
    """)
    fun observeDailyTotals(startMillis: Long, endMillis: Long): Flow<List<DailyTotal>>

    // ── Search ───────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM expenses
        WHERE (merchant LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%')
          AND confirmation_status = 'CONFIRMED'
        ORDER BY transaction_at DESC
        LIMIT 100
    """)
    fun searchExpenses(query: String): Flow<List<ExpenseEntity>>

    // ── Filter ───────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:sourcePackage IS NULL OR source_package = :sourcePackage)
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        ORDER BY transaction_at DESC
    """)
    fun observeFiltered(
        startMillis: Long,
        endMillis: Long,
        categoryId: Long?,
        sourcePackage: String?
    ): Flow<List<ExpenseEntity>>

    // ── Top merchants ────────────────────────────────────────────────
    @Query("""
        SELECT merchant, SUM(amount_minor) as total, COUNT(*) as count
        FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type IN ('EXPENSE', 'TRANSFER')
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
          AND merchant != ''
        GROUP BY merchant
        ORDER BY total DESC
        LIMIT :limit
    """)
    fun observeTopMerchants(startMillis: Long, endMillis: Long, limit: Int = 5): Flow<List<MerchantTotal>>

    // ── Pending review count ─────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM expenses WHERE confirmation_status = 'PENDING_REVIEW'")
    fun observePendingCount(): Flow<Int>

    // ── Recent ───────────────────────────────────────────────────────
    @Query("""
        SELECT * FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
        ORDER BY transaction_at DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<ExpenseEntity>>

    // ── Check for duplicate ──────────────────────────────────────────
    @Query("""
        SELECT COUNT(*) FROM expenses
        WHERE amount_minor = :amountMinor
          AND (:sourcePackage IS NULL OR source_package = :sourcePackage)
          AND ABS(transaction_at - :transactionAt) < 180000
    """)
    suspend fun countPotentialDuplicates(
        amountMinor: Long,
        sourcePackage: String?,
        transactionAt: Long
    ): Int

    @Query("""
        SELECT * FROM expenses
        WHERE amount_minor = :amountMinor
          AND ABS(transaction_at - :transactionAt) < :windowMs
        LIMIT 1
    """)
    suspend fun findRecentDuplicateAnySource(
        amountMinor: Long,
        transactionAt: Long,
        windowMs: Long = 180000L
    ): ExpenseEntity?

    @Query("UPDATE expenses SET merchant = :merchant, transaction_ref = COALESCE(:ref, transaction_ref), updated_at = :now WHERE id = :id")
    suspend fun updateMerchantAndRef(id: Long, merchant: String, ref: String?, now: Long = System.currentTimeMillis())

    @Query("""
        SELECT COUNT(*) FROM expenses
        WHERE transaction_ref = :ref
          AND transaction_ref IS NOT NULL
          AND transaction_ref != ''
    """)
    suspend fun countByTransactionRef(ref: String): Int

    @Query("""
        SELECT * FROM expenses
        WHERE transaction_ref = :ref
          AND transaction_ref IS NOT NULL
          AND transaction_ref != ''
        LIMIT 1
    """)
    suspend fun findByTransactionRef(ref: String): ExpenseEntity?

    @Query("UPDATE expenses SET source_package = :sourcePackage, updated_at = :now WHERE id = :id")
    suspend fun updateSourcePackage(id: Long, sourcePackage: String, now: Long = System.currentTimeMillis())

    // ── Update confirmation status ───────────────────────────────────
    @Query("UPDATE expenses SET confirmation_status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateConfirmationStatus(id: Long, status: ConfirmationStatus, now: Long = System.currentTimeMillis())
    @Query("""
        SELECT source_package, SUM(amount_minor) as total, COUNT(*) as count
        FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type = 'EXPENSE'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        GROUP BY source_package
        ORDER BY total DESC
    """)
    fun observeAppSourceTotals(startMillis: Long, endMillis: Long): Flow<List<AppSourceTotal>>

    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'CREDIT'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
    """)
    fun observeMonthlyCredits(startMillis: Long, endMillis: Long): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND direction = 'DEBIT'
          AND record_type = 'TRANSFER'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
    """)
    fun observeMonthlyTransfersTotal(startMillis: Long, endMillis: Long): Flow<Long>

    @Query("""
        SELECT * FROM expenses
        WHERE confirmation_status = 'CONFIRMED'
          AND transaction_at >= :startMillis
          AND transaction_at < :endMillis
        ORDER BY transaction_at DESC
    """)
    fun observeAllMonthlyTransactions(startMillis: Long, endMillis: Long): Flow<List<ExpenseEntity>>
}

data class CategoryTotal(
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val total: Long
)

data class DailyTotal(
    @ColumnInfo(name = "day_epoch") val dayEpoch: Long,
    val total: Long
)

data class MerchantTotal(
    val merchant: String,
    val total: Long,
    val count: Int
)

data class AppSourceTotal(
    @ColumnInfo(name = "source_package") val sourcePackage: String?,
    val total: Long,
    val count: Int
)
