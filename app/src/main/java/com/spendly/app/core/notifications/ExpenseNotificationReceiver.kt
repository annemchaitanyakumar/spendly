package com.spendly.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spendly.app.SpendlyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives broadcast intents from notification action buttons.
 */
class ExpenseNotificationReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SpendlyApplication ?: return
        val repository = app.container.expenseRepository

        when (intent.action) {
            ExpenseNotificationHelper.ACTION_DISMISS_CANDIDATE -> {
                val candidateId = intent.getLongExtra(ExpenseNotificationHelper.EXTRA_CANDIDATE_ID, -1L)
                if (candidateId > 0) {
                    scope.launch {
                        repository.dismissCandidate(candidateId)
                    }
                }
            }
        }
    }
}
