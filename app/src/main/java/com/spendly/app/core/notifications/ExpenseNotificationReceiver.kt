package com.spendly.app.core.notifications

import android.app.NotificationManager
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
            ExpenseNotificationHelper.ACTION_UNDO_EXPENSE -> {
                val expenseId = intent.getLongExtra(ExpenseNotificationHelper.EXTRA_EXPENSE_ID, -1L)
                val candidateId = intent.getLongExtra(ExpenseNotificationHelper.EXTRA_CANDIDATE_ID, -1L)
                val notificationId = intent.getIntExtra(ExpenseNotificationHelper.EXTRA_NOTIFICATION_ID, -1)

                if (notificationId > 0) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notificationId)
                }

                scope.launch {
                    if (expenseId > 0) {
                        repository.deleteExpenseById(expenseId)
                    }
                    if (candidateId > 0) {
                        repository.dismissCandidate(candidateId)
                    }
                }
            }

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
