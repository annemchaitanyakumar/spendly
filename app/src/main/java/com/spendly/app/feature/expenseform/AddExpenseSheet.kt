package com.spendly.app.feature.expenseform

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.spendly.app.core.model.Category
import com.spendly.app.ui.spendlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    prefillAmountMinor: Long? = null,
    prefillMerchant: String? = null,
    prefillCategoryId: Long? = null,
    viewModel: AddExpenseViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amountFocus = remember { FocusRequester() }

    // Prefill from notification data
    LaunchedEffect(Unit) {
        viewModel.prefill(prefillAmountMinor, prefillMerchant, prefillCategoryId)
        amountFocus.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
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
            // Title
            Text(
                "Add Expense",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

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
                supportingText = state.amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            // Merchant
            OutlinedTextField(
                value = state.merchant,
                onValueChange = viewModel::setMerchant,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Merchant / Payee") },
                leadingIcon = { Icon(Icons.Default.Store, null) },
                singleLine = true
            )

            // Merchant suggestions
            AnimatedVisibility(state.merchantSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.merchantSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.setMerchant(suggestion) },
                            label = { Text(suggestion) }
                        )
                    }
                }
            }

            // Category selection
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

            // Note
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, null) },
                maxLines = 3
            )

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.save(onSuccess = onSaved) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isSaving,
                shape = MaterialTheme.shapes.medium
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
                    Text("Save Expense", style = MaterialTheme.typography.labelLarge)
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(16.dp))
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
        label = { Text(category.name, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = catColor.copy(alpha = 0.15f),
            selectedLabelColor = catColor
        )
    )
}
