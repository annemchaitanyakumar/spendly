package com.spendly.app.feature.home

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.theme.*
import com.spendly.app.ui.components.*
import com.spendly.app.ui.spendlyViewModel
import java.time.LocalDate

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onAddExpense: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isNotifListenerEnabled = remember {
        mutableStateOf<Boolean>(isNotificationListenerEnabled(context))
    }

    LaunchedEffect(Unit) {
        isNotifListenerEnabled.value = isNotificationListenerEnabled(context)
    }

    var reviewingCandidate by remember { mutableStateOf<TransactionCandidate?>(null) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Greeting ──────────────────────────────────────────────────
        item(key = "greeting") {
            GreetingHeader(
                year = state.currentYear,
                month = state.currentMonth,
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                canGoNext = viewModel.canGoToNextMonth()
            )
        }

        // ── Notification Listener Banner ──────────────────────────────
        if (!isNotifListenerEnabled.value) {
            item(key = "notif_banner") {
                NotificationPermissionBanner(
                    onEnable = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }

        // ── Pending confirmations ─────────────────────────────────────
        if (state.pendingCandidates.isNotEmpty()) {
            item(key = "pending_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Detected from Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("${state.pendingCandidates.size}", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            items(state.pendingCandidates, key = { "candidate_${it.id}" }) { candidate ->
                DetectedExpenseCard(
                    candidate = candidate,
                    onConfirm = { reviewingCandidate = candidate },
                    onDismiss = { viewModel.dismissCandidate(candidate.id) }
                )
            }
        }

        // ── Monthly Total ─────────────────────────────────────────────
        item(key = "monthly_total") {
            MonthlyTotalCard(summary = state.monthSummary)
        }

        // ── Category Breakdown ────────────────────────────────────────
        val catBreakdown = state.monthSummary?.categoryBreakdown?.filter { it.totalMinor > 0 }
        if (!catBreakdown.isNullOrEmpty()) {
            item(key = "category_chart") {
                CategoryBreakdownCard(categories = catBreakdown, total = state.monthSummary!!.totalMinor)
            }
        }

        // ── Recent Transactions ───────────────────────────────────────
        item(key = "recent_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (state.recentExpenses.isEmpty()) {
            item(key = "empty_state") {
                EmptyExpensesState(onAddExpense = onAddExpense)
            }
        } else {
            items(
                items = state.recentExpenses,
                key = { "expense_${it.id}" }
            ) { expense ->
                ExpenseListItem(expense = expense)
            }
        }
    }

    reviewingCandidate?.let { candidate ->
        com.spendly.app.feature.expenseform.AddExpenseSheet(
            onDismiss = { reviewingCandidate = null },
            onSaved = {
                viewModel.dismissCandidate(candidate.id)
                reviewingCandidate = null
            },
            prefillAmountMinor = candidate.amountMinor,
            prefillMerchant = candidate.merchant,
            prefillCategoryId = candidate.suggestedCategoryId
        )
    }
}

@Composable
private fun GreetingHeader(
    year: Int,
    month: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean
) {
    val greeting = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateTimeUtils.formatMonthYear(year, month),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row {
                IconButton(onClick = onPrevMonth, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ChevronLeft, "Previous month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(36.dp),
                    enabled = canGoNext
                ) {
                    Icon(Icons.Default.ChevronRight, "Next month",
                        tint = if (canGoNext) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onEnable: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Auto-detect payments",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Enable notification access to detect expenses automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onEnable) {
                Text("Enable", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DetectedExpenseCard(
    candidate: TransactionCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        candidate.amountMinor?.let { CurrencyFormatter.formatMinor(it) } ?: "Amount unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (candidate.merchant.isNotBlank()) candidate.merchant else "Payment detected",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                candidate.sourcePackage.contains("phonepe") -> "PhonePe"
                                candidate.sourcePackage.contains("paisa") -> "Google Pay"
                                candidate.sourcePackage.contains("paytm") -> "Paytm"
                                else -> "Notification"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Expense")
                }
            }
        }
    }
}

@Composable
private fun MonthlyTotalCard(summary: MonthSummary?) {
    val totalMinor = summary?.totalMinor ?: 0L
    val changePercent = summary?.changePercent

    // Animated amount display
    val animatedTotal by animateFloatAsState(
        targetValue = totalMinor.toFloat(),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "total_anim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Total spent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Text(
                CurrencyFormatter.formatMinor(animatedTotal.toLong()),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (changePercent != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isIncrease = changePercent > 0
                    Icon(
                        if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isIncrease)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${if (isIncrease) "+" else ""}${String.format("%.1f", changePercent)}% vs last month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(categories: List<CategorySpending>, total: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "By Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            categories.take(5).forEach { cat ->
                CategoryProgressRow(spending = cat, total = total)
            }
        }
    }
}

@Composable
private fun CategoryProgressRow(spending: CategorySpending, total: Long) {
    val fraction = if (total > 0) spending.totalMinor.toFloat() / total else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "cat_progress"
    )

    val catColor = remember(spending.category?.colorHex) {
        spending.category?.colorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Category icon
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = categoryIconEmoji(spending.category?.icon),
                    fontSize = 14.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    spending.category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    CurrencyFormatter.formatMinor(spending.totalMinor),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = catColor ?: MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun EmptyExpensesState(onAddExpense: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            "No expenses yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Add your first expense or enable notification\naccess to detect payments automatically",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onAddExpense) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Expense")
        }
    }
}

// Map category icon names to simple display strings (Material icon names → emoji)
private fun categoryIconEmoji(icon: String?): String = when (icon) {
    "restaurant" -> "🍽"
    "shopping_cart" -> "🛒"
    "directions_car" -> "🚗"
    "shopping_bag" -> "👜"
    "receipt_long" -> "🧾"
    "movie" -> "🎬"
    "local_hospital" -> "🏥"
    "school" -> "🎓"
    "flight" -> "✈️"
    "local_gas_station" -> "⛽"
    "home" -> "🏠"
    "subscriptions" -> "📱"
    "swap_horiz" -> "↔️"
    else -> "•"
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.contains(context.packageName)
}
