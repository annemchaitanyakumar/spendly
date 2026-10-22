package com.spendly.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.spendly.app.core.database.dao.*
import com.spendly.app.core.database.entity.*
import com.spendly.app.core.model.*

@Database(
    entities = [
        ExpenseEntity::class,
        TransactionCandidateEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        AppSourceEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(SpendlyTypeConverters::class)
abstract class SpendlyDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun transactionCandidateDao(): TransactionCandidateDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun appSourceDao(): AppSourceDao
}

class SpendlyTypeConverters {
    @TypeConverter fun fromDirection(v: TransactionDirection): String = v.name
    @TypeConverter fun toDirection(v: String): TransactionDirection =
        TransactionDirection.entries.firstOrNull { it.name == v } ?: TransactionDirection.UNKNOWN

    @TypeConverter fun fromRecordType(v: RecordType): String = v.name
    @TypeConverter fun toRecordType(v: String): RecordType =
        RecordType.entries.firstOrNull { it.name == v } ?: RecordType.UNKNOWN

    @TypeConverter fun fromConfirmation(v: ConfirmationStatus): String = v.name
    @TypeConverter fun toConfirmation(v: String): ConfirmationStatus =
        ConfirmationStatus.entries.firstOrNull { it.name == v } ?: ConfirmationStatus.PENDING_REVIEW

    @TypeConverter fun fromLocationSource(v: LocationSource): String = v.name
    @TypeConverter fun toLocationSource(v: String): LocationSource =
        LocationSource.entries.firstOrNull { it.name == v } ?: LocationSource.NONE
}
