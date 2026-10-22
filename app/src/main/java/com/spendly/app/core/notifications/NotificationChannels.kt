package com.spendly.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.spendly.app.MainActivity
import com.spendly.app.core.common.CurrencyFormatter

object NotificationChannels {
    const val DETECTION_CHANNEL_ID = "spendly_detection"
    const val DETECTION_CHANNEL_NAME = "Spend Detections"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            DETECTION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority notifications for auto-recorded payments and transfers"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}

object ExpenseNotificationHelper {
    const val ACTION_OPEN_EXPENSE = "com.spendly.app.OPEN_EXPENSE"
    const val ACTION_OPEN_CANDIDATE = "com.spendly.app.OPEN_CANDIDATE"
    const val ACTION_DISMISS_CANDIDATE = "com.spendly.app.DISMISS_CANDIDATE"
    const val ACTION_UNDO_EXPENSE = "com.spendly.app.UNDO_EXPENSE"

    const val EXTRA_EXPENSE_ID = "expense_id"
    const val EXTRA_CANDIDATE_ID = "candidate_id"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    /**
     * Post a high-priority system status bar notification when a spend is automatically recorded.
     */
    fun showAutoAddedExpenseNotification(
        context: Context,
        expenseId: Long,
        candidateId: Long,
        amountMinor: Long,
        currencyCode: String = "INR",
        label: String = "Paid",
        merchantOrRecipient: String,
        categoryName: String,
        notificationId: Int = expenseId.toInt().coerceIn(1, Int.MAX_VALUE)
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open MainActivity to view/edit the expense
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_EXPENSE
            putExtra(EXTRA_EXPENSE_ID, expenseId)
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Broadcast intent to Undo/Delete this expense directly from the notification shade
        val undoIntent = Intent(context, ExpenseNotificationReceiver::class.java).apply {
            action = ACTION_UNDO_EXPENSE
            putExtra(EXTRA_EXPENSE_ID, expenseId)
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val undoPending = PendingIntent.getBroadcast(
            context,
            notificationId + 20000,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = CurrencyFormatter.formatMinor(amountMinor, currencyCode)
        val title = if (label == "Transfer") {
            "$amountStr • Transfer to ${merchantOrRecipient.ifBlank { "Account" }}"
        } else {
            "$amountStr • Paid to ${merchantOrRecipient.ifBlank { "Merchant" }}"
        }

        val contentText = "Auto-added to $categoryName • Tap to edit or categorize"

        val bigText = "$title\nAuto-recorded in Spendly under $categoryName. You can tap below to adjust category or undo."

        val notification = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Edit / Note",
                openPending
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Undo / Remove",
                undoPending
            )
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Fallback notification for ambiguous candidates requiring confirmation.
     */
    fun showExpenseDetectedNotification(
        context: Context,
        candidateId: Long,
        amountMinor: Long?,
        merchant: String?,
        sourceDisplayName: String,
        notificationId: Int = candidateId.toInt().coerceIn(1, Int.MAX_VALUE)
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CANDIDATE
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ExpenseNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS_CANDIDATE
            putExtra(EXTRA_CANDIDATE_ID, candidateId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            notificationId + 10000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = amountMinor?.let { CurrencyFormatter.formatMinor(it) } ?: "Amount detected"
        val contentTitle = "$amountStr • Possible payment detected"
        val contentText = if (!merchant.isNullOrBlank()) "From $merchant ($sourceDisplayName)" else "Detected from $sourceDisplayName"

        val notification = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Add Expense", openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
            .build()

        manager.notify(notificationId, notification)
    }
}
