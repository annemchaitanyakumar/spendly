package com.spendly.app.feature.expenseform

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.Category
import com.spendly.app.core.model.RecordType
import com.spendly.app.ui.spendlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    expenseIdToEdit: Long? = null,
    prefillAmountMinor: Long? = null,
    prefillMerchant: String? = null,
    prefillCategoryId: Long? = null,
    viewModel: AddExpenseViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amountFocus = remember { FocusRequester() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(expenseIdToEdit) {
        if (expenseIdToEdit != null) {
            viewModel.loadExpense(expenseIdToEdit)
        } else {
            viewModel.prefill(prefillAmountMinor, prefillMerchant, prefillCategoryId)
            amountFocus.requestFocus()
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete this transaction?", fontWeight = FontWeight.Bold) },
            text = { Text("This expense will be permanently deleted and monthly spending will be updated.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteExpense(onSuccess = onSaved)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.editingExpenseId != null) "Edit Transaction" else "Add Expense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (state.editingExpenseId != null) {
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete expense")
                    }
                }
            }

            var naturalInput by remember { mutableStateOf("") }
            var isAiParsing by remember { mutableStateOf(false) }

            if (state.editingExpenseId == null) {
                OutlinedTextField(
                    value = naturalInput,
                    onValueChange = { naturalInput = it },
                    placeholder = { Text("✨ AI Quick-Fill (e.g. coffee 150 at CCD)", style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = {
                        if (naturalInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    isAiParsing = true
                                    viewModel.parseNaturalLanguage(naturalInput) {
                                        isAiParsing = false
                                        naturalInput = ""
                                    }
                                },
                                enabled = !isAiParsing
                            ) {
                                if (isAiParsing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, "Apply AI", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Transaction Type Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeFilterChip(
                    label = "💳 Paid",
                    selected = state.recordType == RecordType.EXPENSE,
                    onClick = { viewModel.setRecordType(RecordType.EXPENSE) },
                    modifier = Modifier.weight(1f)
                )
                TypeFilterChip(
                    label = "🔄 Transfer",
                    selected = state.recordType == RecordType.TRANSFER,
                    onClick = { viewModel.setRecordType(RecordType.TRANSFER) },
                    modifier = Modifier.weight(1f)
                )
                TypeFilterChip(
                    label = "💰 Received",
                    selected = state.recordType == RecordType.INCOME,
                    onClick = { viewModel.setRecordType(RecordType.INCOME) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Amount field — prominent
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus),
                label = { Text("Amount") },
                prefix = { Text("₹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                shape = RoundedCornerShape(16.dp)
            )

            // Merchant / Payee
            OutlinedTextField(
                value = state.merchant,
                onValueChange = viewModel::setMerchant,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (state.recordType == RecordType.TRANSFER) "Transfer to / Recipient"
                        else if (state.recordType == RecordType.INCOME) "Received from / Sender"
                        else "Merchant / Payee"
                    )
                },
                leadingIcon = {
                    Icon(
                        if (state.recordType == RecordType.TRANSFER) Icons.Default.SwapHoriz
                        else if (state.recordType == RecordType.INCOME) Icons.Default.AccountBalanceWallet
                        else Icons.Default.Store,
                        null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Merchant suggestions
            AnimatedVisibility(state.merchantSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.merchantSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.setMerchant(suggestion) },
                            label = { Text(suggestion) },
                            shape = CircleShape
                        )
                    }
                }
            }

            // Category selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { cat ->
                        CategoryChip(
                            category = cat,
                            selected = state.categoryId == cat.id,
                            onClick = {
                                viewModel.setCategory(if (state.categoryId == cat.id) null else cat.id)
                            }
                        )
                    }
                }
            }

            // Date & Time info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Date: ${DateTimeUtils.formatDateTime(state.transactionAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Note
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, null) },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Save / Update button
            Button(
                onClick = { viewModel.save(onSuccess = onSaved) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isSaving && !state.isDeleting,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.editingExpenseId != null) "Update Transaction" else "Save Expense",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TypeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    val catColor = remember(category.colorHex) {
        try { Color(android.graphics.Color.parseColor(category.colorHex)) } catch (_: Exception) { Color.Gray }
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { 
            Text(
                "${com.spendly.app.ui.components.categoryIconEmoji(category.icon)} ${category.name}", 
                style = MaterialTheme.typography.labelMedium
            ) 
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = catColor.copy(alpha = 0.18f),
            selectedLabelColor = catColor
        ),
        shape = RoundedCornerShape(10.dp)
    )
}
