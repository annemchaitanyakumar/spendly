package com.spendly.app.core.di

import android.content.Context
import androidx.room.Room
import com.spendly.app.core.backup.BackupManager
import com.spendly.app.core.database.SpendlyDatabase
import com.spendly.app.core.location.LocationHelper
import com.spendly.app.core.parser.NotificationParserRegistry
import com.spendly.app.core.repository.ExpenseRepository
import com.spendly.app.core.settings.AppSettingsRepository

class AppContainer(val context: Context) {

    val database: SpendlyDatabase by lazy {
        Room.databaseBuilder(
            context,
            SpendlyDatabase::class.java,
            "spendly_db"
        )
            .fallbackToDestructiveMigrationFrom()
            .build()
    }

    val parserRegistry: NotificationParserRegistry by lazy {
        NotificationParserRegistry()
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(
            expenseDao = database.expenseDao(),
            categoryDao = database.categoryDao(),
            merchantDao = database.merchantDao(),
            appSourceDao = database.appSourceDao(),
            candidateDao = database.transactionCandidateDao()
        )
    }

    val settingsRepository: AppSettingsRepository by lazy {
        AppSettingsRepository(context)
    }

    val locationHelper: LocationHelper by lazy {
        LocationHelper(context)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(context, expenseRepository)
    }
}
