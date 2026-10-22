package com.spendly.app.core.repository

import com.spendly.app.core.common.*
import com.spendly.app.core.database.DatabaseSeedData
import com.spendly.app.core.database.dao.*
import com.spendly.app.core.database.entity.TransactionCandidateEntity
import com.spendly.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val merchantDao: MerchantDao,
    private val appSourceDao: AppSourceDao,
    private val candidateDao: TransactionCandidateDao,
) {

    // ── Categories ───────────────────────────────────────────────────

    val categories: Flow<List<Category>> = categoryDao.observeAll()
        .map { list -> list.map { it.toDomain() } }

    suspend fun ensureCategoriesSeeded() = withContext(Dispatchers.IO) {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DatabaseSeedData.defaultCategories)
            appSourceDao.insertAll(DatabaseSeedData.knownPaymentApps)
        }
    }

    suspend fun getCategoryById(id: Long): Category? = withContext(Dispatchers.IO) {
        categoryDao.getById(id)?.toDomain()
    }

    suspend fun getAllCategories(): List<Category> = withContext(Dispatchers.IO) {
        categoryDao.getAll().map { it.toDomain() }
    }

    // ── Expenses ─────────────────────────────────────────────────────

    suspend fun saveExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        val id = expenseDao.insert(expense.toEntity())
        // Track merchant usage
        if (expense.merchant.isNotBlank()) {
            val existing = merchantDao.findByName(expense.merchant)
            if (existing != null) {
                merchantDao.incrementUsage(existing.id)
            } else {
                merchantDao.insert(
                    com.spendly.app.core.database.entity.MerchantEntity(
                        name = expense.merchant,
                        suggestedCategoryId = expense.categoryId,
                        usageCount = 1,
                        lastUsedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        id
    }

    suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.update(expense.toEntity())
    }

    suspend fun deleteExpense(expense: Expense) = withContext(Dispatchers.IO) {
        expenseDao.delete(expense.toEntity())
    }

    suspend fun deleteExpenseById(id: Long) = withContext(Dispatchers.IO) {
        expenseDao.deleteById(id)
    }

    suspend fun getExpenseById(id: Long): Expense? = withContext(Dispatchers.IO) {
        val entity = expenseDao.getById(id) ?: return@withContext null
        val category = entity.categoryId?.let { categoryDao.getById(it) }
        entity.toDomain(category)
    }

    fun observeExpenseById(id: Long): Flow<Expense?> = expenseDao.observeById(id)
        .map { entity ->
            entity?.let {
                val category = it.categoryId?.let { cid -> categoryDao.getById(cid) }
                it.toDomain(category)
            }
        }

    fun observeMonthlyExpenses(year: Int, month: Int): Flow<List<Expense>> {
        val start = DateTimeUtils.monthStartMillis(year, month)
        val end = DateTimeUtils.monthEndMillis(year, month)
        return expenseDao.observeMonthlyExpenses(start, end)
            .map { list -> list.map { it.toDomain() } }
    }

    fun observeMonthlyTotal(year: Int, month: Int): Flow<Long> {
        val start = DateTimeUtils.monthStartMillis(year, month)
        val end = DateTimeUtils.monthEndMillis(year, month)
        return expenseDao.observeMonthlyTotal(start, end)
    }

    fun observeMonthSummary(year: Int, month: Int): Flow<MonthSummary> {
        val start = DateTimeUtils.monthStartMillis(year, month)
        val end = DateTimeUtils.monthEndMillis(year, month)

        // Previous month
        val prevYear = if (month == 1) year - 1 else year
        val prevMonth = if (month == 1) 12 else month - 1
        val prevStart = DateTimeUtils.monthStartMillis(prevYear, prevMonth)
        val prevEnd = DateTimeUtils.monthEndMillis(prevYear, prevMonth)

        return combine(
            expenseDao.observeMonthlyTotal(start, end),
            expenseDao.observeMonthlyTotal(prevStart, prevEnd),
            expenseDao.observeCategoryTotals(start, end),
            expenseDao.observeDailyTotals(start, end),
            expenseDao.observeTopMerchants(start, end)
        ) { total, prevTotal, catTotals, dayTotals, merchants ->
            val allCategories = categoryDao.getAll().associateBy { it.id }

            MonthSummary(
                year = year,
                month = month,
                totalMinor = total,
                previousMonthTotalMinor = prevTotal.takeIf { it > 0L },
                categoryBreakdown = catTotals.map { ct ->
                    CategorySpending(
                        category = ct.categoryId?.let { allCategories[it]?.toDomain() },
                        totalMinor = ct.total
                    )
                }.sortedByDescending { it.totalMinor },
                dailyTotals = dayTotals.map { DailySpending(it.dayEpoch, it.total) },
                topMerchants = merchants.map { MerchantSpending(it.merchant, it.total, it.count) }
            )
        }
    }

    fun searchExpenses(query: String): Flow<List<Expense>> =
        expenseDao.searchExpenses(query).map { list -> list.map { it.toDomain() } }

    fun observeRecentExpenses(limit: Int = 10): Flow<List<Expense>> =
        expenseDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observePendingCount(): Flow<Int> = expenseDao.observePendingCount()

    // ── Transaction Candidates ───────────────────────────────────────

    fun observePendingCandidates(): Flow<List<TransactionCandidate>> =
        candidateDao.observePending().map { list -> list.map { it.toDomain() } }

    fun observePendingCandidateCount(): Flow<Int> = candidateDao.observePendingCount()

    suspend fun saveCandidate(entity: TransactionCandidateEntity): Long =
        withContext(Dispatchers.IO) { candidateDao.insert(entity) }

    suspend fun getCandidateById(id: Long): TransactionCandidateEntity? =
        withContext(Dispatchers.IO) { candidateDao.getById(id) }

    suspend fun dismissCandidate(id: Long) =
        withContext(Dispatchers.IO) { candidateDao.dismiss(id) }

    suspend fun confirmCandidate(candidateId: Long, expense: Expense): Long {
        return withContext(Dispatchers.IO) {
            val expenseId = saveExpense(expense)
            candidateDao.markConfirmed(candidateId, expenseId)
            candidateDao.clearNotificationText(candidateId)
            expenseId
        }
    }

    suspend fun findDuplicateCandidate(
        sourcePackage: String,
        amountMinor: Long?,
        detectedAt: Long
    ) = withContext(Dispatchers.IO) {
        if (amountMinor == null) null
        else candidateDao.findDuplicate(sourcePackage, amountMinor, detectedAt)
    }

    // ── Merchants ────────────────────────────────────────────────────

    suspend fun searchMerchants(query: String) = withContext(Dispatchers.IO) {
        merchantDao.searchMerchants(query)
    }

    // ── App Sources ──────────────────────────────────────────────────

    val appSources: Flow<List<com.spendly.app.core.database.entity.AppSourceEntity>> =
        appSourceDao.observeAll()

    suspend fun isSourceEnabled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        appSourceDao.isEnabled(packageName) ?: true // Default: enabled
    }

    suspend fun setSourceEnabled(packageName: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { appSourceDao.setEnabled(packageName, enabled) }

    // ── Duplicate detection ──────────────────────────────────────────

    suspend fun hasDuplicateExpense(amountMinor: Long, sourcePackage: String?, transactionAt: Long): Boolean =
        withContext(Dispatchers.IO) {
            expenseDao.countPotentialDuplicates(amountMinor, sourcePackage, transactionAt) > 0
        }

    suspend fun getAllExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        val allCategories = categoryDao.getAll().associateBy { it.id }
        expenseDao.getAll().map { entity ->
            val cat = entity.categoryId?.let { allCategories[it] }
            entity.toDomain(cat)
        }
    }

    suspend fun insertAllExpenses(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        expenseDao.insertAll(expenses.map { it.toEntity() })
    }

    suspend fun insertAllCategories(categories: List<Category>) = withContext(Dispatchers.IO) {
        categoryDao.insertAll(categories.map {
            com.spendly.app.core.database.entity.CategoryEntity(
                id = it.id,
                name = it.name,
                icon = it.icon,
                colorHex = it.colorHex,
                isDefault = it.isDefault,
                sortOrder = it.sortOrder,
                isSystem = it.isSystem
            )
        })
    }

    suspend fun clearCandidateNotificationText(candidateId: Long) = withContext(Dispatchers.IO) {
        candidateDao.clearNotificationText(candidateId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        expenseDao.deleteAll()
        candidateDao.deleteOlderThan(System.currentTimeMillis() + 86400000L)
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    suspend fun cleanupOldCandidates(olderThanDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        candidateDao.deleteOlderThan(cutoff)
    }
}
