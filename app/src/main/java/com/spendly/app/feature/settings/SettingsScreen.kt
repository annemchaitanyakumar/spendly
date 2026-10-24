package com.spendly.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource
import com.spendly.app.R
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.settings.LocationCapturePref
import com.spendly.app.core.settings.ThemeMode
import com.spendly.app.ui.components.bounceOnPress
import com.spendly.app.ui.spendlyViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = spendlyViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showDebugSection by remember { mutableStateOf(false) }

    // File pickers
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri) { success ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (success) "Expenses exported to CSV successfully" else "Failed to export CSV"
                    )
                }
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri) { success ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (success) "Backup file created successfully" else "Failed to create backup"
                    )
                }
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackup(uri) { res ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (res.success) "Restored ${res.importedExpenses} expenses"
                        else res.errorMessage ?: "Failed to restore backup"
                    )
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkLocationPermission()
    }

    val isNotifListenerActive = remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }

    LaunchedEffect(Unit) {
        isNotifListenerActive.value = isNotificationListenerEnabled(context)
        viewModel.checkLocationPermission()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header with App Icon ──────────────────────────────────────
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.spendly.app.R.drawable.ic_app_icon),
                        contentDescription = "Spendly Icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column {
                    Text(
                        "Spendly",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Settings & Preferences",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Notification Detection Card ───────────────────────────────
        item(key = "notification_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isNotifListenerActive.value)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isNotifListenerActive.value) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isNotifListenerActive.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Automatic Detection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        AssistChip(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            label = { Text(if (isNotifListenerActive.value) "Active" else "Grant Access") },
                            leadingIcon = {
                                Icon(
                                    if (isNotifListenerActive.value) Icons.Default.Done else Icons.Default.ArrowForward,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    Text(
                        "Spendly listens for incoming payment notifications from authorized financial apps on your phone to prompt you with one-tap expense entries. No SMS or banking passwords are ever read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── App Sources ───────────────────────────────────────────────
        item(key = "app_sources_title") {
            Text(
                "Monitored Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(state.appSources, key = { it.packageName }) { appSource ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appSource.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(appSource.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = appSource.isEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleAppSource(appSource.packageName, enabled)
                        }
                    )
                }
            }
        }

        // ── Free AI Smart Agent (Gemini Free Tier) ────────────────────
        item(key = "ai_agent_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                var apiKeyInput by remember(state.settings.geminiApiKey) { mutableStateOf(state.settings.geminiApiKey) }
                var aiTestResult by remember { mutableStateOf<String?>(null) }
                var isTestingAi by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Smart Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Powered by Google Gemini (Free Tier)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Switch(
                            checked = state.settings.aiAgentEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setAiAgentEnabled(enabled)
                            }
                        )
                    }

                    Text(
                        "Enables semantic AI analysis to reject false positives like Amazon suggested replacement prices, promotional newsletters, and cancellation notices. 100% free with no credit card required from Google AI Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.settings.aiAgentEnabled) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                viewModel.setGeminiApiKey(it)
                            },
                            label = { Text("Google AI Studio API Key (Free)") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"))
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Get Free Key")
                            }

                            FilledTonalButton(
                                onClick = {
                                    isTestingAi = true
                                    viewModel.testAiAgent(
                                        "Amazon.in: Subscription Cancelled. Suggested Replacements: Tugain Essentials Anti Dandruff Shampoo... ₹376.20 Choose as replacement."
                                    ) { res ->
                                        isTestingAi = false
                                        aiTestResult = res
                                    }
                                },
                                enabled = !isTestingAi && apiKeyInput.isNotBlank()
                            ) {
                                Text(if (isTestingAi) "Testing..." else "Test AI Filter")
                            }
                        }

                        if (!aiTestResult.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    aiTestResult!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Location Preferences ──────────────────────────────────────
        item(key = "location_pref") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Location-Aware Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (!state.hasLocationPermission) {
                            TextButton(onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }) {
                                Text("Allow GPS")
                            }
                        }
                    }
                    Text(
                        "Tag expenses with place names or coordinates so you remember where purchases occurred. 100% stored offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LocationCapturePref.entries.forEach { pref ->
                            val selected = state.settings.locationCapture == pref
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setLocationCapture(pref) },
                                label = {
                                    Text(
                                        when (pref) {
                                            LocationCapturePref.DISABLED -> "Off"
                                            LocationCapturePref.ON_CONFIRM -> "On Save"
                                            LocationCapturePref.ON_NOTIFICATION -> "Auto"
                                            LocationCapturePref.MANUAL -> "Manual"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Appearance & Theme ────────────────────────────────────────
        item(key = "appearance") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = state.settings.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                leadingIcon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.LIGHT -> Icons.Default.LightMode
                                            ThemeMode.DARK -> Icons.Default.DarkMode
                                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        },
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Currency ──────────────────────────────────────────────────
        item(key = "currency") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("INR" to "₹ INR", "USD" to "$ USD", "EUR" to "€ EUR", "GBP" to "£ GBP").forEach { (code, label) ->
                            FilterChip(
                                selected = state.settings.currencyCode == code,
                                onClick = { viewModel.setCurrency(code) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // ── Data & Backups ────────────────────────────────────────────
        item(key = "data_management") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Data & Backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your data lives exclusively on this phone. Export CSV for spreadsheet apps or save a full JSON backup to retain your history if switching devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { csvExportLauncher.launch("spendly_expenses_${System.currentTimeMillis()}.csv") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = { backupExportLauncher.launch("spendly_backup_${System.currentTimeMillis()}.json") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Backup, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup JSON", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { showClearDataDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Data", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── Notification Preview & System Alerts ───────────────────────
        item(key = "notification_preview_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = MaterialTheme.shapes.large
            ) {
                var selectedPresetIndex by remember { mutableIntStateOf(0) }
                val presets = remember {
                    listOf(
                        NotificationPreviewPreset(
                            name = "PhonePe (₹450)",
                            amountMinor = 45000L,
                            merchant = "Starbucks",
                            category = "Food & Dining",
                            source = "PhonePe",
                            recordType = "PAID",
                            pkg = "com.phonepe.app",
                            sampleText = "Paid Rs. 450 to Starbucks on PhonePe"
                        ),
                        NotificationPreviewPreset(
                            name = "Google Pay (₹1,250)",
                            amountMinor = 125000L,
                            merchant = "Swiggy",
                            category = "Food & Dining",
                            source = "Google Pay",
                            recordType = "PAID",
                            pkg = "com.google.android.apps.nbu.paisa.user",
                            sampleText = "You paid ₹1,250.00 to Swiggy using UPI"
                        ),
                        NotificationPreviewPreset(
                            name = "Paytm (₹280)",
                            amountMinor = 28000L,
                            merchant = "Uber",
                            category = "Transport",
                            source = "Paytm",
                            recordType = "PAID",
                            pkg = "net.one97.paytm",
                            sampleText = "Paid Rs 280 to Uber"
                        ),
                        NotificationPreviewPreset(
                            name = "Salary (₹45,000)",
                            amountMinor = 4500000L,
                            merchant = "Infosys Ltd",
                            category = "Salary & Income",
                            source = "HDFC Bank",
                            recordType = "CREDIT",
                            pkg = "com.snapwork.hdfc",
                            sampleText = "A/c *1234 credited with INR 45,000.00 from Infosys Ltd"
                        ),
                        NotificationPreviewPreset(
                            name = "Refund (₹350)",
                            amountMinor = 35000L,
                            merchant = "Amazon",
                            category = "Shopping",
                            source = "Amazon",
                            recordType = "REFUND",
                            pkg = "com.phonepe.app",
                            sampleText = "Refund of Rs. 350 initiated from Amazon"
                        )
                    )
                }

                val currentPreset = presets[selectedPresetIndex]

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Notification & Status Bar Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        "Live preview of Spendly's high-visibility system status bar favicon and executive OLED notification panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Preset Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEachIndexed { index, preset ->
                            FilterChip(
                                selected = selectedPresetIndex == index,
                                onClick = { selectedPresetIndex = index },
                                label = { Text(preset.name) },
                                modifier = Modifier.bounceOnPress()
                            )
                        }
                    }

                    // Status Bar Simulation Strip
                    Surface(
                        color = Color(0xFF000000),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "9:41",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDFBF7)
                                    )
                                    // Status bar favicon preview
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_stat_spendly),
                                        contentDescription = "Status Bar Favicon",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SignalCellularAlt, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                                    Icon(Icons.Default.Wifi, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                                    Icon(Icons.Default.BatteryFull, null, tint = Color(0xFF888888), modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                "✦ System Status Bar: High-visibility solid 'S' monogram favicon",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA09E97),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Notification Drawer Card Mockup
                    Surface(
                        color = Color(0xFF000000),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF222222)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_app_icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        "Spendly Auto-Capture",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA09E97)
                                    )
                                }

                                Surface(
                                    color = Color(0xFF141414),
                                    shape = RoundedCornerShape(100.dp),
                                    border = BorderStroke(1.dp, Color(0xFF333333))
                                ) {
                                    Text(
                                        text = currentPreset.recordType,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDFBF7),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Amount & Payee Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val amountStr = CurrencyFormatter.formatMinor(currentPreset.amountMinor, "INR")
                                Text(
                                    text = amountStr,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDFBF7)
                                )
                                val payeePrefix = if (currentPreset.recordType == "CREDIT" || currentPreset.recordType == "REFUND") "from " else "to "
                                Text(
                                    text = "$payeePrefix${currentPreset.merchant}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFC8C5BD),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            // Meta Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    currentPreset.category,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDFBF7)
                                )
                                Text(" • ", color = Color(0xFF555555), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    currentPreset.source,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFA09E97)
                                )
                                Text(" • ", color = Color(0xFF555555), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "Ref: 4296...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF666666)
                                )
                            }

                            // Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF1A1A1A),
                                    shape = RoundedCornerShape(100.dp),
                                    border = BorderStroke(1.dp, Color(0xFF333333))
                                ) {
                                    Text(
                                        "Undo",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDFBF7),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFFDFBF7),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        "Edit Details",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF000000),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Test Live Notification Alert Button
                    Button(
                        onClick = {
                            viewModel.triggerSimulatedNotificationDirect(
                                context = context,
                                amountMinor = currentPreset.amountMinor,
                                currencyCode = "INR",
                                label = if (currentPreset.recordType == "CREDIT") "Credited" else if (currentPreset.recordType == "REFUND") "Refund" else "Paid",
                                merchant = currentPreset.merchant,
                                category = currentPreset.category,
                                source = currentPreset.source,
                                ref = "429612345678"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceOnPress()
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Trigger Live Alert on Phone", fontWeight = FontWeight.Bold)
                    }

                    // Collapsible Parser Pipeline Debugger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Pipeline Debugger", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { showDebugSection = !showDebugSection }) {
                            Icon(if (showDebugSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp))
                        }
                    }

                    AnimatedVisibility(visible = showDebugSection) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.testParser(currentPreset.pkg, "Transaction Alert", currentPreset.sampleText)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify Regex Pipeline for ${currentPreset.source}")
                            }

                            state.debugResult?.let { res ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (res.isCandidate) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Parser: ${res.parserName} · Direction: ${res.direction}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(res.summary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Privacy Manifesto ─────────────────────────────────────────
        item(key = "manifesto") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Spendly Privacy Guarantee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "• 100% Offline: Zero servers, zero analytics, zero advertising SDKs.\n• Local Storage: All financial records stay exclusively inside this app's private sandbox.\n• Transparent Notifications: Notification text is only processed in-memory for expense extraction and never transmitted anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Reset all data?") },
            text = { Text("This will permanently delete all expenses and candidates from local storage. This action cannot be undone unless you have a JSON backup.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDataDialog = false
                        viewModel.clearAllData {
                            scope.launch {
                                snackbarHostState.showSnackbar("All data cleared successfully")
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val myComponent = "${context.packageName}/${com.spendly.app.core.notifications.SpendlyNotificationListener::class.java.name}"
    return enabledListeners.contains(myComponent)
}

private data class NotificationPreviewPreset(
    val name: String,
    val amountMinor: Long,
    val merchant: String,
    val category: String,
    val source: String,
    val recordType: String,
    val pkg: String,
    val sampleText: String
)
