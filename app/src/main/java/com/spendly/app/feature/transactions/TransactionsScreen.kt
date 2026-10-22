package com.spendly.app.feature.transactions

import androidx.compose.animation.*
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
            .padding(contentPadding)
    ) {
        // ── Top bar ─────────────────────────────────────────────────
        TopAppBar(
            title = { Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(Icons.Outlined.FilterList, "Filter")
                }
                IconButton(onClick = onAddExpense) {
                    Icon(Icons.Default.Add, "Add expense")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
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

        // ── Month filter chips ────────────────────────────────────────
        AnimatedVisibility(visible = showFilters) {
            FilterRow(
                filter = state.filter,
                categories = state.categories,
                onMonthChange = viewModel::setMonth,
                onCategoryChange = viewModel::setCategoryFilter
            )
        }

        // ── Transaction list ─────────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.groupedExpenses.isEmpty()) {
            EmptyTransactionState(onAdd = onAddExpense)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        ExpenseListItem(
                            expense = expense,
                            onClick = { editingExpense = it },
                            onDelete = { viewModel.deleteExpense(it) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: TransactionsFilter,
    categories: List<com.spendly.app.core.model.Category>,
    onMonthChange: (Int, Int) -> Unit,
    onCategoryChange: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Month chip
        AssistChip(
            onClick = {
                val prev = if (filter.month == 1) Pair(filter.year - 1, 12)
                          else Pair(filter.year, filter.month - 1)
                onMonthChange(prev.first, prev.second)
            },
            label = { Text(DateTimeUtils.formatMonthYear(filter.year, filter.month)) },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) }
        )

        // Category filter
        if (filter.categoryId != null) {
            FilterChip(
                selected = true,
                onClick = { onCategoryChange(null) },
                label = {
                    Text(categories.find { it.id == filter.categoryId }?.name ?: "Category")
                },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp))
                }
            )
        }
    }
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
        OutlinedButton(onClick = onAdd) {
            Text("Add Expense")
        }
    }
}
