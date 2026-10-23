package com.spendly.app.feature.notes

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.parser.ParsedNoteGroup
import com.spendly.app.core.parser.ParsedNoteItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportNotesSheet(
    state: NotesUiState,
    onDismiss: () -> Unit,
    onParseText: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onToggleItem: (String, String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConfirmImport: ((count: Int, totalMinor: Long) -> Unit) -> Unit,
    onImportSuccess: (count: Int, totalMinor: Long) -> Unit
) {
    val context = LocalContext.current
    var pastedInputText by remember { mutableStateOf("") }

    // File picker launcher for notes files (.txt, .json, .csv, .md, keep archives)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val combinedText = StringBuilder()
            for (uri in uris) {
                try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        combinedText.append(reader.readText()).append("\n\n")
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            if (combinedText.isNotBlank()) {
                onParseText(combinedText.toString())
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Top Sheet Title Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = if (state.importNotesList.isNotEmpty()) "Select Notes to Import" else "Import from Notes App",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.importNotesList.isNotEmpty())
                                "Choose which notes you want to add"
                            else
                                "Import notes from Keep, Samsung Notes, or text",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Content Switcher based on Parsing / Selection State ────────
            if (state.isParsingImport) {
                // Loading State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            "Analyzing notes & extracting spends...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (state.importNotesList.isEmpty()) {
                // Initial Input / File Chooser State
                SourceInputContent(
                    pastedText = pastedInputText,
                    onTextChanged = { pastedInputText = it },
                    onOpenFilePicker = {
                        filePickerLauncher.launch(
                            arrayOf("text/plain", "application/json", "text/comma-separated-values", "text/*", "*/*")
                        )
                    },
                    onPasteFromClipboard = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val item = clip.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!item.isNullOrBlank()) {
                            pastedInputText = item
                            onParseText(item)
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLoadSample = {
                        val sample = """
                            October spends
                            Oct 1
                            Vegetables 120
                            Milk 35
                            Oct 2
                            Petrol 500
                            Swiggy dinner 450
                            Oct 3
                            Groceries 850
                            Auto fare 60
                        """.trimIndent()
                        pastedInputText = sample
                        onParseText(sample)
                    },
                    onParse = {
                        if (pastedInputText.isNotBlank()) {
                            onParseText(pastedInputText)
                        } else {
                            Toast.makeText(context, "Please enter or paste notes first", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                // Notes Selection Checklist (The Core User Request)
                NotesSelectionContent(
                    notes = state.importNotesList,
                    onToggleGroup = onToggleGroup,
                    onToggleItem = onToggleItem,
                    onSelectAll = onSelectAll,
                    onCancel = onDismiss,
                    onImport = {
                        onConfirmImport { count, totalMinor ->
                            onImportSuccess(count, totalMinor)
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Source Input View (File Picker + Paste Text)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceInputContent(
    pastedText: String,
    onTextChanged: (String) -> Unit,
    onOpenFilePicker: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onLoadSample: () -> Unit,
    onParse: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Option 1: File Picker Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFilePicker() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Select Notes File from Device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Support .txt, .json, .csv, Keep export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("OR PASTE NOTES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // Option 2: Paste notes text
        OutlinedTextField(
            value = pastedText,
            onValueChange = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
            placeholder = {
                Text(
                    "Paste notes from your phone here...\n\nExample:\nOct 1\nVegetables 100\nMilk 35\nOct 2\nPetrol 500",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            shape = RoundedCornerShape(14.dp)
        )

        // Paste shortcuts row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onPasteFromClipboard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste Clipboard", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = onLoadSample,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try Sample", style = MaterialTheme.typography.labelMedium)
            }
        }

        Button(
            onClick = onParse,
            enabled = pastedText.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Review & Choose Notes to Import", fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Notes Selection View (Interactive Checklist of Notes to Import)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun NotesSelectionContent(
    notes: List<ParsedNoteGroup>,
    onToggleGroup: (String) -> Unit,
    onToggleItem: (String, String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    val selectedNotesCount = notes.count { it.isSelected }
    val selectedItemsCount = notes.filter { it.isSelected }.sumOf { it.items.count { i -> i.isSelected } }
    val totalSelectedMinor = notes.filter { it.isSelected }
        .sumOf { it.items.filter { i -> i.isSelected }.sumOf { i -> i.amountMinor } }
    val allSelected = notes.isNotEmpty() && notes.all { it.isSelected }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Control Bar: Select All / Summary
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onSelectAll(!allSelected) }
                    )
                    Text(
                        if (allSelected) "Deselect All" else "Select All Notes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        "$selectedNotesCount notes • ${CurrencyFormatter.formatMinor(totalSelectedMinor)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Scrollable Notes List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notes, key = { it.id }) { group ->
                NoteGroupCard(
                    group = group,
                    onToggleGroup = { onToggleGroup(group.id) },
                    onToggleItem = { itemId -> onToggleItem(group.id, itemId) }
                )
            }
        }

        // Bottom Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onImport,
                enabled = selectedItemsCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(2f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Import Selected ($selectedItemsCount items)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NoteGroupCard(
    group: ParsedNoteGroup,
    onToggleGroup: () -> Unit,
    onToggleItem: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (group.isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(
            1.dp,
            if (group.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Checkbox, Title, Total, Expand Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = group.isSelected,
                    onCheckedChange = { onToggleGroup() }
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.items.size} spend items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = group.totalFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (group.isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand"
                    )
                }
            }

            // Expandable Items breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleItem(item.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { onToggleItem(item.id) },
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = item.merchant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = item.suggestedCategoryName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = item.amountFormatted,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isSelected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
