package com.spendly.app

import android.app.Application
import com.spendly.app.core.di.AppContainer
import com.spendly.app.core.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendlyApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createChannels(this)
        applicationScope.launch {
            container.expenseRepository.ensureCategoriesSeeded()
        }
    }
}
