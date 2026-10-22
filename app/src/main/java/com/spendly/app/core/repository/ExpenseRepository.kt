package com.spendly.app.core.repository

import com.spendly.app.core.common.*
import com.spendly.app.core.database.DatabaseSeedData
import com.spendly.app.core.database.dao.*
import com.spendly.app.core.database.entity.*
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
        categoryDao.insertAll(DatabaseSeedData.defaultCategories)
        appSourceDao.insertAll(DatabaseSeedData.knownPaymentApps)
        candidateDao.dismissGarbageCandidates()
        deduplicateRecentExpenses()
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
            expenseDao.observeMonthlyCredits(start, end),
            expenseDao.observeMonthlyTransfersTotal(start, end),
            expenseDao.observeCategoryTotals(start, end),
            expenseDao.observeDailyTotals(start, end),
            expenseDao.observeTopMerchants(start, end),
            expenseDao.observeAppSourceTotals(start, end)
        ) { args ->
            val total = args[0] as Long
            val prevTotal = args[1] as Long
            val credits = args[2] as Long
            val transfers = args[3] as Long
            @Suppress("UNCHECKED_CAST")
            val catTotals = args[4] as List<CategoryTotal>
            @Suppress("UNCHECKED_CAST")
            val dayTotals = args[5] as List<DailyTotal>
            @Suppress("UNCHECKED_CAST")
            val merchants = args[6] as List<MerchantTotal>
            @Suppress("UNCHECKED_CAST")
            val appTotals = args[7] as List<AppSourceTotal>

            val allCategories = categoryDao.getAll().associateBy { it.id }

            MonthSummary(
                year = year,
                month = month,
                totalMinor = total,
                previousMonthTotalMinor = prevTotal.takeIf { it > 0L },
                totalCreditsMinor = credits,
                totalTransfersMinor = transfers,
                categoryBreakdown = catTotals.map { ct ->
                    CategorySpending(
                        category = ct.categoryId?.let { allCategories[it]?.toDomain() },
                        totalMinor = ct.total
                    )
                }.sortedByDescending { it.totalMinor },
                dailyTotals = dayTotals.map { DailySpending(it.dayEpoch, it.total) },
                topMerchants = merchants.map { MerchantSpending(it.merchant, it.total, it.count) },
                appSpends = appTotals.map { app ->
                    AppSpending(
                        packageName = app.sourcePackage,
                        appDisplayName = mapPackageToDisplayName(app.sourcePackage),
                        iconKey = mapPackageToIconKey(app.sourcePackage),
                        totalMinor = app.total,
                        transactionCount = app.count
                    )
                }.sortedByDescending { it.totalMinor }
            )
        }
    }

    fun observeMonthlyCashFlow(year: Int, month: Int): Flow<List<DayCashFlow>> {
        val start = DateTimeUtils.monthStartMillis(year, month)
        val end = DateTimeUtils.monthEndMillis(year, month)
        val allCategoriesFlow = categoryDao.observeAll().map { list -> list.associateBy { it.id } }

        return combine(
            expenseDao.observeAllMonthlyTransactions(start, end),
            allCategoriesFlow
        ) { entities, categories ->
            val domainExpenses = entities.map { entity ->
                val cat = entity.categoryId?.let { categories[it] }
                entity.toDomain(cat)
            }
            val groupedByDate = domainExpenses.groupBy { exp ->
                DateTimeUtils.epochToLocalDate(exp.transactionAt)
            }

            val ym = java.time.YearMonth.of(year, month)
            val daysInMonth = ym.lengthOfMonth()

            (1..daysInMonth).map { day ->
                val date = ym.atDay(day)
                val dayExpenses = groupedByDate[date] ?: emptyList()
                val debits = dayExpenses.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amountMinor }
                val credits = dayExpenses.filter { it.direction == TransactionDirection.CREDIT }.sumOf { it.amountMinor }
                DayCashFlow(
                    date = date,
                    debitMinor = debits,
                    creditMinor = credits,
                    transactions = dayExpenses
                )
            }
        }
    }

    fun mapPackageToDisplayName(pkg: String?): String {
        return when (pkg) {
            "com.phonepe.app" -> "PhonePe"
            "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM UPI"
            "com.dreamplug.androidapp" -> "CRED"
            "in.amazon.mShop.android.shopping" -> "Amazon Pay"
            "bank_sms" -> "Bank SMS"
            null, "" -> "Manual Entry"
            else -> {
                if (pkg.contains("sms", ignoreCase = true) || pkg.contains("messaging", ignoreCase = true)) {
                    "Bank SMS"
                } else {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
        }
    }

    fun mapPackageToIconKey(pkg: String?): String {
        return when (pkg) {
            "com.phonepe.app" -> "phonepe"
            "com.google.android.apps.nbu.paisa.user" -> "gpay"
            "net.one97.paytm" -> "paytm"
            "in.org.npci.upiapp" -> "bhim"
            "com.dreamplug.androidapp" -> "cred"
            "in.amazon.mShop.android.shopping" -> "amazon"
            "bank_sms" -> "sms"
            null, "" -> "manual"
            else -> {
                if (pkg.contains("sms", ignoreCase = true) || pkg.contains("messaging", ignoreCase = true)) {
                    "sms"
                } else {
                    "generic"
                }
            }
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

    fun observeRecentCandidates(limit: Int = 5): Flow<List<TransactionCandidate>> =
        candidateDao.observeRecentCandidates(limit).map { list -> list.map { it.toDomain() } }

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
        sourcePackage: String? = null,
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

    suspend fun findRecentDuplicateExpenseAnySource(
        amountMinor: Long,
        transactionAt: Long,
        windowMs: Long = 300_000L
    ): Expense? = withContext(Dispatchers.IO) {
        val allCategories = categoryDao.getAll().associateBy { it.id }
        expenseDao.findRecentDuplicateAnySource(amountMinor, transactionAt, windowMs)?.let { entity ->
            val cat = entity.categoryId?.let { allCategories[it] }
            entity.toDomain(cat)
        }
    }

    suspend fun findByTransactionRef(ref: String): Expense? = withContext(Dispatchers.IO) {
        if (ref.isBlank()) return@withContext null
        val allCategories = categoryDao.getAll().associateBy { it.id }
        expenseDao.findByTransactionRef(ref)?.let { entity ->
            val cat = entity.categoryId?.let { allCategories[it] }
            entity.toDomain(cat)
        }
    }

    suspend fun updateExpenseSourcePackage(id: Long, sourcePackage: String) = withContext(Dispatchers.IO) {
        expenseDao.updateSourcePackage(id, sourcePackage)
    }

    suspend fun updateExpenseMerchantAndRef(id: Long, merchant: String, ref: String?) =
        withContext(Dispatchers.IO) {
            expenseDao.updateMerchantAndRef(id, merchant, ref)
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

    suspend fun hasTransactionRef(ref: String): Boolean = withContext(Dispatchers.IO) {
        expenseDao.countByTransactionRef(ref) > 0
    }

    suspend fun findCandidateByNotifKey(notifKey: String): com.spendly.app.core.database.entity.TransactionCandidateEntity? =
        withContext(Dispatchers.IO) {
            candidateDao.findByNotificationKey(notifKey)
        }

    suspend fun deduplicateRecentExpenses() = withContext(Dispatchers.IO) {
        val all = expenseDao.getAll()
        val toDelete = mutableListOf<Long>()
        val seen = mutableSetOf<String>()

        for (e in all) {
            val timeBucket = e.transactionAt / 180_000L
            val key = "${e.amountMinor}_$timeBucket"
            if (!seen.add(key)) {
                toDelete.add(e.id)
            }
        }

        for (id in toDelete) {
            expenseDao.deleteById(id)
        }
    }

    suspend fun suggestCategoryId(merchant: String, recordType: RecordType): Long = withContext(Dispatchers.IO) {
        if (recordType == RecordType.TRANSFER) return@withContext 13L // Transfers & UPI category
        val lower = merchant.lowercase().trim()
        if (lower.isNotBlank()) {
            if (lower.contains("self") ||
                lower.contains("account") ||
                lower.contains("a/c") ||
                lower.contains("chaitanya") ||
                lower.contains("annem") ||
                com.spendly.app.core.parser.CommonPatterns.isLikelyPersonPayee(merchant)
            ) {
                return@withContext 13L // Transfers & UPI
            }
            for ((keyword, catId) in DatabaseSeedData.merchantCategoryHints) {
                if (lower.contains(keyword)) return@withContext catId
            }
            val existing = merchantDao.findByName(merchant)
            if (existing?.suggestedCategoryId != null) {
                return@withContext existing.suggestedCategoryId
            }
        }
        20L // Default to Other (NEVER Food & Dining)
    }

    suspend fun getCategoryName(categoryId: Long): String = withContext(Dispatchers.IO) {
        categoryDao.getById(categoryId)?.name ?: "Expenses"
    }

    suspend fun getCategoryIcon(categoryId: Long): String? = withContext(Dispatchers.IO) {
        categoryDao.getById(categoryId)?.icon
    }
}
