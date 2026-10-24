package com.spendly.app.feature.transactions

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.Expense
import com.spendly.app.ui.components.ExpenseListItem
import com.spendly.app.ui.components.bounceOnPress
import com.spendly.app.ui.components.bouncingClickable
import com.spendly.app.ui.spendlyViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    contentPadding: PaddingValues,
    onAddExpense: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: TransactionsViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    // Undo snackbar
    LaunchedEffect(state.deletedExpense) {
        val deleted = state.deletedExpense ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Expense deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        // ── Top bar ─────────────────────────────────────────────────
        TopAppBar(
            title = { Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(
                    onClick = { showFilters = !showFilters },
                    modifier = Modifier.bounceOnPress()
                ) {
                    Icon(Icons.Outlined.FilterList, "Filter")
                }
                IconButton(
                    onClick = onAddExpense,
                    modifier = Modifier.bounceOnPress()
                ) {
                    Icon(Icons.Default.Add, "Add expense")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            windowInsets = WindowInsets(0.dp)
        )

        // ── Search bar ───────────────────────────────────────────────
        OutlinedTextField(
            value = state.filter.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by merchant or note…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.filter.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge
        )

        var showMonthPicker by remember { mutableStateOf(false) }

        // ── Month Navigator Bar (Navigate Back / Forward / Pick Any Month) ──
        MonthNavigatorBar(
            filter = state.filter,
            onPrevMonth = viewModel::prevMonth,
            onNextMonth = viewModel::nextMonth,
            onOpenPicker = { showMonthPicker = true },
            onCurrentMonth = viewModel::setCurrentMonth
        )

        // ── Category filter chips ────────────────────────────────────────
        AnimatedVisibility(visible = showFilters || state.filter.categoryId != null) {
            CategoryFilterRow(
                categories = state.categories,
                selectedCategoryId = state.filter.categoryId,
                onSelectCategory = viewModel::setCategoryFilter
            )
        }

        if (showMonthPicker) {
            MonthYearPickerDialog(
                initialYear = state.filter.year,
                initialMonth = state.filter.month,
                onDismiss = { showMonthPicker = false },
                onSelectMonthYear = { y, m ->
                    viewModel.setMonth(y, m)
                    showMonthPicker = false
                },
                onCurrentMonth = {
                    viewModel.setCurrentMonth()
                    showMonthPicker = false
                }
            )
        }

        // ── Transaction list ─────────────────────────────────────────
        AnimatedContent(
            targetState = state.isLoading to state.groupedExpenses.isEmpty(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "transactions_content_anim",
            modifier = Modifier.weight(1f)
        ) { (isLoading, isEmpty) ->
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (isEmpty) {
                EmptyTransactionState(onAdd = onAddExpense)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    state.groupedExpenses.forEach { (dateLabel, expenses) ->
                        val dailyTotal = state.dailyTotals[dateLabel] ?: 0L

                        stickyHeader(key = "header_$dateLabel") {
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (dailyTotal > 0L) {
                                        Text(
                                            CurrencyFormatter.formatMinor(dailyTotal),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = expenses,
                            key = { "exp_${it.id}" }
                        ) { expense ->
                            Box(modifier = Modifier.animateItem()) {
                                ExpenseListItem(
                                    expense = expense,
                                    onClick = { editingExpense = it },
                                    onDelete = { viewModel.deleteExpense(it) }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    editingExpense?.let { exp ->
        com.spendly.app.feature.expenseform.AddExpenseSheet(
            onDismiss = { editingExpense = null },
            onSaved = { editingExpense = null },
            expenseIdToEdit = exp.id
        )
    }
}

@Composable
private fun MonthNavigatorBar(
    filter: TransactionsFilter,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenPicker: () -> Unit,
    onCurrentMonth: () -> Unit
) {
    val now = java.time.LocalDate.now()
    val isCurrent = filter.year == now.year && filter.month == now.monthValue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Previous month (<) button
            IconButton(
                onClick = onPrevMonth,
                modifier = Modifier
                    .size(36.dp)
                    .bounceOnPress()
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous Month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Month Year Pill (Clickable to open dialog)
            Surface(
                onClick = onOpenPicker,
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.bounceOnPress()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        DateTimeUtils.formatMonthYear(filter.year, filter.month),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Select Month",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Next month (>) button
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .size(36.dp)
                    .bounceOnPress()
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Next Month",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Shortcut to jump back to current month if navigated away
        if (!isCurrent) {
            Surface(
                onClick = onCurrentMonth,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.bounceOnPress()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        "This Month",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<com.spendly.app.core.model.Category>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All Categories") },
                modifier = Modifier.bounceOnPress()
            )
        }
        items(categories, key = { it.id }) { cat ->
            FilterChip(
                selected = selectedCategoryId == cat.id,
                onClick = {
                    onSelectCategory(if (selectedCategoryId == cat.id) null else cat.id)
                },
                label = { Text(cat.name) },
                modifier = Modifier.bounceOnPress()
            )
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onSelectMonthYear: (Int, Int) -> Unit,
    onCurrentMonth: () -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val now = java.time.LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedYear-- },
                    modifier = Modifier.bounceOnPress()
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous Year")
                }
                Text(
                    text = "$selectedYear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { selectedYear++ },
                    modifier = Modifier.bounceOnPress()
                ) {
                    Icon(Icons.Default.ChevronRight, "Next Year")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 12 Months in 4 rows of 3 columns
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..2) {
                            val monthNum = row * 3 + col + 1
                            val isSelected = selectedYear == initialYear && monthNum == initialMonth
                            val isRealCurrent = selectedYear == now.year && monthNum == now.monthValue

                            Surface(
                                onClick = { onSelectMonthYear(selectedYear, monthNum) },
                                shape = MaterialTheme.shapes.medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else if (isRealCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isRealCurrent && !isSelected)
                                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceOnPress()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = monthNames[monthNum - 1],
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected || isRealCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCurrentMonth,
                modifier = Modifier.bounceOnPress()
            ) {
                Text("Jump to Current Month", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyTransactionState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No transactions found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier.bounceOnPress()
        ) {
            Text("Add Expense")
        }
    }
}
