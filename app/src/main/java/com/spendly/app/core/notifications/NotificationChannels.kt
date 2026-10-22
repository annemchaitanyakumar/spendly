package com.spendly.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.spendly.app.MainActivity
import com.spendly.app.R
import com.spendly.app.core.common.CurrencyFormatter

object NotificationChannels {
    const val DETECTION_CHANNEL_ID = "spendly_detection"
    const val DETECTION_CHANNEL_NAME = "Expense Detections"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            DETECTION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for auto-detected expenses"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}

object ExpenseNotificationHelper {
    private const val REVIEW_INTENT_EXTRA = "candidate_id"

    /**
     * Show an "Expense detected" notification with Add / Review / Dismiss actions.
     */
    fun showExpenseDetectedNotification(
        context: Context,
        candidateId: Long,
        amountMinor: Long?,
        merchant: String?,
        sourceDisplayName: String,
        notificationId: Int = candidateId.toInt()
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open MainActivity with candidate
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CANDIDATE
            putExtra(REVIEW_INTENT_EXTRA, candidateId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss intent
        val dismissIntent = Intent(context, ExpenseNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS_CANDIDATE
            putExtra(REVIEW_INTENT_EXTRA, candidateId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context, notificationId + 10000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = amountMinor?.let { CurrencyFormatter.formatMinor(it) } ?: "Amount unknown"
        val contentTitle = "Possible expense detected"
        val contentText = buildString {
            append(amountStr)
            if (!merchant.isNullOrBlank()) append(" · $merchant")
            else append(" · $sourceDisplayName")
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_input_add,
                "Add Expense",
                openPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissPending
            )
            .build()

        manager.notify(notificationId, notification)
    }

    const val ACTION_OPEN_CANDIDATE = "com.spendly.app.OPEN_CANDIDATE"
    const val ACTION_DISMISS_CANDIDATE = "com.spendly.app.DISMISS_CANDIDATE"
    const val EXTRA_CANDIDATE_ID = "candidate_id"
}
