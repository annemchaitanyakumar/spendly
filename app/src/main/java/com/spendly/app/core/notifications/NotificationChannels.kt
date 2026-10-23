package com.spendly.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
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

    const val LATEST_SPEND_NOTIFICATION_ID = 1001

    /**
     * Post a minimal, professional system notification for the latest transaction only.
     * Clears all older notifications so only the latest credit or debit is displayed.
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
        notificationId: Int = LATEST_SPEND_NOTIFICATION_ID,
        customTitle: String? = null,
        customSubtitle: String? = null
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Clear all older notifications: user strictly sees ONLY the latest transaction
        manager.cancelAll()

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
        val cleanRecipient = merchantOrRecipient.trim()
        val hasRecipient = cleanRecipient.isNotBlank() &&
                !cleanRecipient.equals("Shop", ignoreCase = true) &&
                !cleanRecipient.equals("Account", ignoreCase = true) &&
                !cleanRecipient.equals("Sender", ignoreCase = true) &&
                !cleanRecipient.equals("Messages", ignoreCase = true) &&
                !cleanRecipient.equals("Message", ignoreCase = true) &&
                !cleanRecipient.equals("Bank SMS", ignoreCase = true) &&
                !cleanRecipient.equals("SMS", ignoreCase = true) &&
                !cleanRecipient.equals("Gmail", ignoreCase = true) &&
                !cleanRecipient.equals("Outlook", ignoreCase = true) &&
                !cleanRecipient.equals("Yahoo Mail", ignoreCase = true) &&
                !cleanRecipient.equals("Money Transfer", ignoreCase = true) &&
                !cleanRecipient.equals("Account Transfer", ignoreCase = true) &&
                !cleanRecipient.equals("Transfer", ignoreCase = true) &&
                !cleanRecipient.equals("Expense", ignoreCase = true) &&
                !cleanRecipient.equals("Income", ignoreCase = true) &&
                !cleanRecipient.equals("Received Money", ignoreCase = true)

        // Professional, minimal title (supports AI-generated title when enabled)
        val title = if (!customTitle.isNullOrBlank()) {
            customTitle
        } else {
            when (label) {
                "Self Transfer" -> if (hasRecipient) "Self Transfer $amountStr to $cleanRecipient" else "Self Transfer $amountStr"
                "Transfer" -> if (hasRecipient) "Transferred $amountStr to $cleanRecipient" else "Transferred $amountStr"
                "Received", "Credited" -> if (hasRecipient) "Received $amountStr from $cleanRecipient" else "Received $amountStr"
                "Refund" -> if (hasRecipient) "Refund $amountStr from $cleanRecipient" else "Refund $amountStr"
                else -> if (hasRecipient) "Paid $amountStr to $cleanRecipient" else "Paid $amountStr"
            }
        }

        val contentText = if (!customSubtitle.isNullOrBlank()) {
            customSubtitle
        } else {
            "$categoryName • Spendly"
        }

        val badgeText = when (label) {
            "Transfer", "Self Transfer" -> "TRANSFER"
            "Received", "Credited" -> "CREDIT"
            "Refund" -> "REFUND"
            else -> "PAID"
        }

        // Custom Collapsed RemoteViews
        val collapsedViews = RemoteViews(context.packageName, R.layout.notification_spendly_collapsed).apply {
            setTextViewText(R.id.notif_title, title)
            setTextViewText(R.id.notif_subtitle, contentText)
            setTextViewText(R.id.notif_badge, badgeText)
        }

        // Custom Expanded RemoteViews
        val expandedViews = RemoteViews(context.packageName, R.layout.notification_spendly_expanded).apply {
            setTextViewText(R.id.notif_exp_badge, badgeText)
            setTextViewText(R.id.notif_exp_amount, amountStr)
            val merchantDisplay = if (hasRecipient) {
                when (label) {
                    "Transfer", "Self Transfer" -> "to $cleanRecipient"
                    "Received", "Credited", "Refund" -> "from $cleanRecipient"
                    else -> "to $cleanRecipient"
                }
            } else ""
            setTextViewText(R.id.notif_exp_merchant, merchantDisplay)
            setTextViewText(R.id.notif_exp_category, categoryName)
            setTextViewText(R.id.notif_exp_source, sourceDisplayName)
            if (!transactionRef.isNullOrBlank()) {
                setTextViewText(R.id.notif_exp_ref, "Ref: $transactionRef")
                setViewVisibility(R.id.notif_exp_ref, View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_exp_ref, View.GONE)
            }
            setOnClickPendingIntent(R.id.notif_btn_edit, openPending)
            setOnClickPendingIntent(R.id.notif_btn_undo, undoPending)
        }

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spendly)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setColor(0xFF000000.toInt())
            .setColorized(false)
            .setContentTitle(title)
            .setContentText(contentText)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Edit",
                openPending
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Undo",
                undoPending
            )

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
        notificationId: Int = LATEST_SPEND_NOTIFICATION_ID
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()

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
        val cleanMerchant = merchant?.trim().orEmpty()
        val isCarrierOrApp = cleanMerchant.equals("Messages", ignoreCase = true) ||
                cleanMerchant.equals("SMS", ignoreCase = true) ||
                cleanMerchant.equals("Bank SMS", ignoreCase = true) ||
                cleanMerchant.equals("Gmail", ignoreCase = true)
        val hasMerchant = cleanMerchant.isNotBlank() && !isCarrierOrApp

        val contentTitle = if (hasMerchant) "$amountStr paid to $cleanMerchant" else "$amountStr payment detected"
        val contentText = "Tap to confirm • Spendly"

        val collapsedViews = RemoteViews(context.packageName, R.layout.notification_spendly_collapsed).apply {
            setTextViewText(R.id.notif_title, contentTitle)
            setTextViewText(R.id.notif_subtitle, "Tap to review or confirm • Spendly")
            setTextViewText(R.id.notif_badge, "REVIEW")
        }

        val expandedViews = RemoteViews(context.packageName, R.layout.notification_spendly_expanded).apply {
            setTextViewText(R.id.notif_exp_badge, "REVIEW")
            setTextViewText(R.id.notif_exp_amount, amountStr)
            setTextViewText(R.id.notif_exp_merchant, if (hasMerchant) "at $cleanMerchant" else "")
            setTextViewText(R.id.notif_exp_category, "Unconfirmed")
            setTextViewText(R.id.notif_exp_source, sourceDisplayName)
            setViewVisibility(R.id.notif_exp_ref, View.GONE)
            setTextViewText(R.id.notif_btn_edit, "Confirm")
            setTextViewText(R.id.notif_btn_undo, "Dismiss")
            setOnClickPendingIntent(R.id.notif_btn_edit, openPending)
            setOnClickPendingIntent(R.id.notif_btn_undo, dismissPending)
        }

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spendly)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setColor(0xFF000000.toInt())
            .setColorized(false)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_input_add, "Add", openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)

        val notification = builder.build()

        manager.notify(notificationId, notification)
    }
}
