package com.spendly.app.core.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.spendly.app.MainActivity
import com.spendly.app.R
import com.spendly.app.core.common.CurrencyFormatter

object FloatingExpenseOverlayManager {

    private const val TAG = "FloatingOverlayMgr"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentOverlayView: View? = null
    private var dismissRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        context: Context,
        event: InAppNotificationEvent
    ) {
        // Can only display system overlay if permission is granted
        if (!Settings.canDrawOverlays(context)) {
            Log.d(TAG, "canDrawOverlays is false, skipping system overlay")
            return
        }

        mainHandler.post {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

                // Dismiss any active overlay before showing a new one
                dismiss(windowManager)

                val inflater = LayoutInflater.from(context)
                val overlayView = inflater.inflate(R.layout.overlay_spendly_card, null)
                val card = overlayView.findViewById<View>(R.id.overlay_card)
                val badgeView = overlayView.findViewById<TextView>(R.id.overlay_badge)
                val amountView = overlayView.findViewById<TextView>(R.id.overlay_amount)
                val merchantView = overlayView.findViewById<TextView>(R.id.overlay_merchant)
                val categoryView = overlayView.findViewById<TextView>(R.id.overlay_category)
                val sourceView = overlayView.findViewById<TextView>(R.id.overlay_source)
                val refView = overlayView.findViewById<TextView>(R.id.overlay_ref)
                val btnUndo = overlayView.findViewById<View>(R.id.overlay_btn_undo)
                val btnEdit = overlayView.findViewById<View>(R.id.overlay_btn_edit)

                // Fill Data
                val badgeText = when (event.label) {
                    "Transfer", "Self Transfer" -> "TRANSFER"
                    "Received", "Credited" -> "CREDIT"
                    "Refund" -> "REFUND"
                    else -> "PAID"
                }
                badgeView.text = badgeText

                val amountStr = CurrencyFormatter.formatMinor(event.amountMinor, event.currencyCode)
                amountView.text = amountStr

                val payeePrefix = if (event.label == "Received" || event.label == "Credited" || event.label == "Refund") "from " else "to "
                val cleanRecipient = event.merchantOrRecipient.trim()
                if (cleanRecipient.isNotBlank()) {
                    merchantView.text = "$payeePrefix$cleanRecipient"
                    merchantView.visibility = View.VISIBLE
                } else {
                    merchantView.visibility = View.GONE
                }

                categoryView.text = event.categoryName
                sourceView.text = event.sourceDisplayName

                if (!event.transactionRef.isNullOrBlank()) {
                    refView.text = "Ref: ${event.transactionRef}"
                    refView.visibility = View.VISIBLE
                } else {
                    refView.visibility = View.GONE
                }

                // Window Layout Params
                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                // Calculate status bar inset for top margin
                val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusBarHeight = if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 72

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = statusBarHeight + 12
                }

                // Click Listeners
                btnEdit.setOnClickListener {
                    dismiss(windowManager)
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        action = ExpenseNotificationHelper.ACTION_OPEN_EXPENSE
                        putExtra(ExpenseNotificationHelper.EXTRA_EXPENSE_ID, event.expenseId)
                        putExtra(ExpenseNotificationHelper.EXTRA_CANDIDATE_ID, event.candidateId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(openIntent)
                }

                btnUndo.setOnClickListener {
                    dismiss(windowManager)
                    val undoIntent = Intent(context, ExpenseNotificationReceiver::class.java).apply {
                        action = ExpenseNotificationHelper.ACTION_UNDO_EXPENSE
                        putExtra(ExpenseNotificationHelper.EXTRA_EXPENSE_ID, event.expenseId)
                        putExtra(ExpenseNotificationHelper.EXTRA_CANDIDATE_ID, event.candidateId)
                        putExtra(ExpenseNotificationHelper.EXTRA_NOTIFICATION_ID, ExpenseNotificationHelper.LATEST_SPEND_NOTIFICATION_ID)
                    }
                    context.sendBroadcast(undoIntent)
                }

                // Swipe-up to dismiss gesture
                var startY = 0f
                card.setOnTouchListener { v, motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startY = motionEvent.rawY
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = motionEvent.rawY - startY
                            if (dy < 0) {
                                card.translationY = dy
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val dy = motionEvent.rawY - startY
                            if (dy < -60) {
                                // Swiped up significantly: animate off-screen and dismiss
                                card.animate()
                                    .translationY(-400f)
                                    .alpha(0f)
                                    .setDuration(200)
                                    .withEndAction { dismiss(windowManager) }
                                    .start()
                            } else {
                                // Snap back
                                card.animate().translationY(0f).setDuration(150).start()
                            }
                            true
                        }
                        else -> false
                    }
                }

                // Entrance Animation
                card.translationY = -350f
                card.alpha = 0f

                windowManager.addView(overlayView, params)
                currentOverlayView = overlayView

                card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(320)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // Auto-dismiss after 6.5 seconds
                val dismissTask = Runnable {
                    try {
                        card.animate()
                            .translationY(-350f)
                            .alpha(0f)
                            .setDuration(250)
                            .withEndAction { dismiss(windowManager) }
                            .start()
                    } catch (_: Exception) {
                        dismiss(windowManager)
                    }
                }
                dismissRunnable = dismissTask
                mainHandler.postDelayed(dismissTask, 6500L)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to display overlay card: ${e.message}", e)
            }
        }
    }

    fun dismiss(windowManager: WindowManager? = null) {
        dismissRunnable?.let {
            mainHandler.removeCallbacks(it)
            dismissRunnable = null
        }
        currentOverlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    val wm = windowManager ?: (view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                    wm?.removeViewImmediate(view)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            } finally {
                currentOverlayView = null
            }
        }
    }
}
