package com.spendly.app.feature.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.feature.insights.InsightsScreen
import com.spendly.app.theme.*
import com.spendly.app.ui.components.*
import com.spendly.app.ui.spendlyViewModel

enum class HomeViewTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    INSIGHTS("Insights", Icons.Default.BarChart)
}

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onAddExpense: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isNotifListenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }

    var selectedTab by remember { mutableStateOf(HomeViewTab.OVERVIEW) }
    var editingExpenseId by remember { mutableStateOf<Long?>(null) }
    var reviewingCandidate by remember { mutableStateOf<TransactionCandidate?>(null) }

    // Auto-refresh permission status whenever user returns from Android Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotifListenerEnabled = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
        // ── Greeting Header ──────────────────────────────────────────
        item(key = "greeting") {
            GreetingHeader(
                year = state.currentYear,
                month = state.currentMonth,
                onPrevMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                canGoNext = viewModel.canGoToNextMonth(),
                onRefreshAiCopilot = viewModel::refreshAiCopilotTip
            )
        }

        // ── Notification Listener Banner ──────────────────────────────
        if (!isNotifListenerEnabled) {
            item(key = "notif_banner") {
                NotificationPermissionBanner(
                    onEnable = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onRefresh = {
                        isNotifListenerEnabled = isNotificationListenerEnabled(context)
                    }
                )
            }
        }

        // ── View Switcher: [ Overview | Calendar ] ─────────────────────
        item(key = "view_tab_switcher") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HomeViewTab.values().forEach { tab ->
                    val selected = tab == selectedTab
                    Surface(
                        onClick = { selectedTab = tab },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ── AI Financial Copilot Card ─────────────────────────────────
        if (state.aiCopilotTip != null || state.isGeneratingCopilotTip) {
            item(key = "ai_copilot_card") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("AI Financial Copilot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = viewModel::dismissCopilotTip, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                        if (state.isGeneratingCopilotTip) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text("Analyzing monthly spends with Gemini...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                state.aiCopilotTip.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }

        if (selectedTab == HomeViewTab.CALENDAR) {
            // ── Calendar View ─────────────────────────────────────────
            item(key = "calendar_view") {
                SpendCalendarView(
                    year = state.currentYear,
                    month = state.currentMonth,
                    cashFlowDays = state.cashFlowDays,
                    totalDebitsMinor = state.monthSummary?.totalMinor ?: 0L,
                    totalCreditsMinor = state.monthSummary?.totalCreditsMinor ?: 0L,
                    onPrevMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    canGoNext = viewModel.canGoToNextMonth(),
                    onEditExpense = { editingExpenseId = it.id }
                )
            }
        } else if (selectedTab == HomeViewTab.INSIGHTS) {
            // ── Financial Insights View ──────────────────────────────
            item(key = "insights_view") {
                InsightsScreen(contentPadding = PaddingValues(0.dp))
            }
        } else {
            // ── Auto-Detected / Auto-Logged Transaction Cards ─────────
            if (state.pendingCandidates.isNotEmpty()) {
                item(key = "pending_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Auto-Detected Spends",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${state.pendingCandidates.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                items(state.pendingCandidates, key = { "candidate_${it.id}" }) { candidate ->
                    DetectedExpenseCard(
                        candidate = candidate,
                        onEdit = { reviewingCandidate = candidate },
                        onConfirm = { viewModel.confirmCandidate(candidate) },
                        onUndo = { viewModel.undoAutoLoggedExpense(candidate) },
                        onDismiss = { viewModel.dismissCandidate(candidate.id) }
                    )
                }
            }

            // ── Monthly Total Card (Purchases, Transfers, Credits, Net) ─
            item(key = "monthly_total") {
                MonthlyTotalCard(summary = state.monthSummary)
            }

            // ── App-Specified Spends Card ─────────────────────────────
            val appSpends = state.monthSummary?.appSpends ?: emptyList()
            if (appSpends.isNotEmpty()) {
                item(key = "app_spends") {
                    AppSpendsCard(
                        appSpends = appSpends,
                        totalSpendsMinor = state.monthSummary?.totalMinor ?: 0L
                    )
                }
            }

            // ── Category Breakdown ────────────────────────────────────
            val catBreakdown = state.monthSummary?.categoryBreakdown?.filter { it.totalMinor > 0 }
            if (!catBreakdown.isNullOrEmpty()) {
                item(key = "category_chart") {
                    CategoryBreakdownCard(categories = catBreakdown, total = state.monthSummary!!.totalMinor)
                }
            }

            // ── Recent Expenses Header ────────────────────────────────
            item(key = "recent_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Expenses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap any to edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
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
                    ExpenseListItem(
                        expense = expense,
                        onClick = { editingExpenseId = it.id }
                    )
                }
            }
        }
    }

    // Modal to review a detected candidate before logging
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

    // Modal to edit any automated or manual expense
    editingExpenseId?.let { expId ->
        com.spendly.app.feature.expenseform.AddExpenseSheet(
            onDismiss = { editingExpenseId = null },
            onSaved = { editingExpenseId = null },
            expenseIdToEdit = expId
        )
    }
}


@Composable
private fun GreetingHeader(
    year: Int,
    month: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onRefreshAiCopilot: () -> Unit
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
        Spacer(modifier = Modifier.height(6.dp))
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onRefreshAiCopilot,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI Copilot", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                // Month navigation capsule
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(onClick = onPrevMonth, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous month",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(34.dp),
                            enabled = canGoNext
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next month",
                                tint = if (canGoNext) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun NotificationPermissionBanner(
    onEnable: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-detect UPI & Card Payments",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Enable notification access so Spendly can auto-record payments from PhonePe, GPay, Paytm, and bank SMS. 100% offline & private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check Status", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Enable Access", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DetectedExpenseCard(
    candidate: TransactionCandidate,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAutoLogged = candidate.expenseId != null

    val isCredit = candidate.direction == TransactionDirection.CREDIT ||
            candidate.recordType == RecordType.INCOME
    val isTransfer = candidate.recordType == RecordType.TRANSFER ||
            candidate.merchant.contains("transfer", ignoreCase = true)

    val labelText = when {
        isCredit -> "Received"
        isTransfer -> "Transfer"
        candidate.recordType == RecordType.REFUND -> "Refund"
        else -> "Paid"
    }

    val (badgeBg, badgeText) = when {
        isCredit -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        isTransfer -> Pair(Color(0xFFEDE7F6), Color(0xFF5E35B1))
        candidate.recordType == RecordType.REFUND -> Pair(Color(0xFFE1F5FE), Color(0xFF0277BD))
        else -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
    }

    val sourceName = when {
        candidate.sourcePackage.contains("phonepe") -> "PhonePe"
        candidate.sourcePackage.contains("paisa") -> "Google Pay"
        candidate.sourcePackage.contains("paytm") -> "Paytm"
        candidate.sourcePackage.contains("npci") -> "BHIM"
        candidate.sourcePackage.contains("dreamplug") -> "CRED"
        candidate.sourcePackage.contains("amazon") -> "Amazon Pay"
        else -> "Bank SMS"
    }

    val displayTitle = when {
        candidate.merchant.isNotBlank() -> {
            if (isTransfer && !candidate.merchant.startsWith("Transfer", ignoreCase = true)) {
                "Transfer to ${candidate.merchant}"
            } else {
                candidate.merchant
            }
        }
        isTransfer -> "Account Transfer"
        isCredit -> "Received Money"
        else -> "Debit • $sourceName"
    }

    val statusPillText = when {
        isAutoLogged -> "Auto-Added to Expenses"
        isCredit -> "Detected Income"
        isTransfer -> "Detected Transfer"
        else -> "Detected Payment"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status and source row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusPillText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Amount + Merchant row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val amountPrefix = if (isCredit) "+" else ""
                        Text(
                            text = candidate.amountMinor?.let { "$amountPrefix${CurrencyFormatter.formatMinor(it, candidate.currencyCode)}" } ?: "₹0.00",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCredit) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAutoLogged) {
                    OutlinedButton(
                        onClick = onUndo,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Undo", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit / Note", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dismiss", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add as Expense", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyTotalCard(summary: MonthSummary?) {
    val totalMinor = summary?.totalMinor ?: 0L
    val creditsMinor = summary?.totalCreditsMinor ?: 0L
    val transfersMinor = summary?.totalTransfersMinor ?: 0L
    val purchasesMinor = (totalMinor - transfersMinor).coerceAtLeast(0L)
    val netBalanceMinor = creditsMinor - totalMinor
    val changePercent = summary?.changePercent

    val animatedTotal by animateFloatAsState(
        targetValue = totalMinor.toFloat(),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "total_anim"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D281C),
                            Color(0xFF06150E)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOTAL SPENT THIS MONTH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    if (changePercent != null) {
                        val isIncrease = changePercent > 0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isIncrease) Color(0x33FF5252) else Color(0x3369F0AE)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isIncrease) Color(0xFFFF8A80) else Color(0xFFB9F6CA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${if (isIncrease) "+" else ""}${String.format("%.1f", changePercent)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncrease) Color(0xFFFF8A80) else Color(0xFFB9F6CA)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = CurrencyFormatter.formatMinor(animatedTotal.toLong()),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                // Financial Breakdown Sub-Card: Purchases vs Transfers vs Credits
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "💳 Purchases",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                CurrencyFormatter.formatMinor(purchasesMinor),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        )

                        Column {
                            Text(
                                "🔄 Transfers Out",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                CurrencyFormatter.formatMinor(transfersMinor),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCE93D8)
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        )

                        Column {
                            Text(
                                "💰 Credits In",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "+${CurrencyFormatter.formatMinor(creditsMinor)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(categories: List<CategorySpending>, total: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = categoryIconEmoji(spending.category?.icon),
                    fontSize = 16.sp
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    spending.category?.name ?: "General",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    CurrencyFormatter.formatMinor(spending.totalMinor),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
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
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            "No expenses recorded yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Payments made on PhonePe, GPay, Paytm, or bank SMS\nwill appear here automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Button(
            onClick = onAddExpense,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Expense Manually", fontWeight = FontWeight.Bold)
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkg = context.packageName
    // 1. Try NotificationManagerCompat
    try {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (packages.contains(pkg)) return true
    } catch (_: Exception) {}

    // 2. Try Settings.Secure
    try {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrBlank()) {
            val parts = flat.split(":")
            for (part in parts) {
                if (part.contains(pkg)) return true
            }
        }
    } catch (_: Exception) {}

    return false
}
