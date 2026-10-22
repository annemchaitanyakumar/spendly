package com.spendly.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.spendly.app.MainActivity
import com.spendly.app.R
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
            description = "High priority notifications for auto-recorded payments, transfers, and credits"
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
     * Post an enhanced, high-priority system status bar notification when a spend/transfer is recorded.
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
        sourceDisplayName: String = "App",
        transactionRef: String? = null,
        notificationId: Int = expenseId.toInt().coerceIn(1, Int.MAX_VALUE)
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open MainActivity and directly show edit sheet for this expense
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
        val title = when (label) {
            "Self Transfer" -> "🔄 Self Transfer $amountStr to ${merchantOrRecipient.ifBlank { "Account" }}"
            "Transfer" -> "🔄 Transfer $amountStr to ${merchantOrRecipient.ifBlank { "Account" }}"
            "Received", "Credited" -> "💰 Credited +$amountStr from ${merchantOrRecipient.ifBlank { "Sender" }}"
            "Refund" -> "💰 Refund +$amountStr from ${merchantOrRecipient.ifBlank { "Merchant" }}"
            else -> "🛍️ Paid $amountStr to ${merchantOrRecipient.ifBlank { "Shop" }}"
        }

        val contentText = "📂 $categoryName • via $sourceDisplayName"

        val bigTextBuilder = StringBuilder()
        bigTextBuilder.append(title).append("\n")
        bigTextBuilder.append("📂 Category: ").append(categoryName).append("\n")
        bigTextBuilder.append("📱 Source: via ").append(sourceDisplayName)
        if (!transactionRef.isNullOrBlank()) {
            bigTextBuilder.append("\n🔢 Ref/UTR: ").append(transactionRef)
        }
        bigTextBuilder.append("\n✓ Recorded in Spendly. Tap to edit or categorize.")

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spendly)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBuilder.toString()))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "✏️ Edit Spend",
                openPending
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "❌ Undo",
                undoPending
            )

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val notification = builder.build()

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

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spendly)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Add Expense", openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val notification = builder.build()

        manager.notify(notificationId, notification)
    }
}
