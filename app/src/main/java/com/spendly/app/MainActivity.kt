package com.spendly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spendly.app.ui.components.bouncingClickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.settings.ThemeMode
import android.app.ActivityManager
import com.spendly.app.feature.expenseform.AddExpenseSheet
import com.spendly.app.feature.home.HomeScreen
import com.spendly.app.feature.insights.InsightsScreen
import com.spendly.app.feature.notes.NotesScreen
import com.spendly.app.feature.settings.SettingsScreen
import com.spendly.app.feature.transactions.TransactionsScreen
import com.spendly.app.theme.SpendlyTheme
import com.spendly.app.ui.MainViewModel
import com.spendly.app.ui.components.InAppExpenseBanner
import com.spendly.app.ui.spendlyViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set task description for Recent Apps switcher to display Spendly icon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                setTaskDescription(
                    ActivityManager.TaskDescription.Builder()
                        .setLabel(getString(R.string.app_name))
                        .setIcon(R.mipmap.ic_launcher)
                        .setPrimaryColor(getColor(R.color.ic_launcher_bg))
                        .build()
                )
            } catch (_: Exception) {}
        }

        // Clear any old stacked notifications when opening the app
        try {
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancelAll()
        } catch (_: Exception) {}

        setContent {
            val mainVm: MainViewModel = spendlyViewModel()
            val settings by mainVm.settings.collectAsStateWithLifecycle()

            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SpendlyTheme(darkTheme = isDark) {
                SpendlyApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val comp = android.content.ComponentName(this, com.spendly.app.core.notifications.SpendlyNotificationListener::class.java)
            android.service.notification.NotificationListenerService.requestRebind(comp)
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}

sealed class NavDest(val route: String, val label: String, val icon: ImageVector) {
    object Home : NavDest("home", "Home", Icons.Default.Home)
    object Notes : NavDest("notes", "Notes", Icons.Default.Notes)
    object Transactions : NavDest("transactions", "Expenses", Icons.Default.List)
    object Insights : NavDest("insights", "Insights", Icons.Default.BarChart)
    object Settings : NavDest("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    NavDest.Home,
    NavDest.Notes,
    NavDest.Transactions,
    NavDest.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendlyApp(mainVm: MainViewModel = spendlyViewModel()) {
    var currentDest by remember { mutableStateOf<NavDest>(NavDest.Home) }
    var showAddExpense by remember { mutableStateOf(false) }
    var expenseIdToEdit by remember { mutableStateOf<Long?>(null) }
    var notesSharedTextToImport by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity

    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent ?: return@LaunchedEffect
        when (intent.action) {
            com.spendly.app.core.notifications.ExpenseNotificationHelper.ACTION_OPEN_EXPENSE -> {
                val expId = intent.getLongExtra(com.spendly.app.core.notifications.ExpenseNotificationHelper.EXTRA_EXPENSE_ID, -1L)
                if (expId > 0L) {
                    expenseIdToEdit = expId
                    showAddExpense = true
                }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    currentDest = NavDest.Notes
                    notesSharedTextToImport = sharedText
                } else {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (streamUri != null) {
                        try {
                            context.contentResolver.openInputStream(streamUri)?.bufferedReader()?.use {
                                val text = it.readText()
                                if (text.isNotBlank()) {
                                    currentDest = NavDest.Notes
                                    notesSharedTextToImport = text
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                val sb = StringBuilder()
                streamUris?.forEach { uri ->
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            sb.append(it.readText()).append("\n\n")
                        }
                    } catch (_: Exception) {}
                }
                if (sb.isNotBlank()) {
                    currentDest = NavDest.Notes
                    notesSharedTextToImport = sb.toString()
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Backup permission request launcher for Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                SpendlyBottomBar(
                    current = currentDest,
                    onSelect = { currentDest = it },
                    onAddClick = {
                        expenseIdToEdit = null
                        showAddExpense = true
                    }
                )
            },
            floatingActionButton = {},
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = currentDest,
                    transitionSpec = {
                        val targetIdx = bottomNavItems.indexOf(targetState).let { if (it >= 0) it else 99 }
                        val initialIdx = bottomNavItems.indexOf(initialState).let { if (it >= 0) it else 99 }
                        val direction = if (targetIdx >= initialIdx) 1 else -1

                        (fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { direction * (it / 5) }
                            )).togetherWith(
                            fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -direction * (it / 8) }
                                )
                        )
                    },
                    label = "nav_anim"
                ) { dest ->
                    when (dest) {
                        NavDest.Home -> HomeScreen(
                            contentPadding = padding,
                            onAddExpense = {
                                expenseIdToEdit = null
                                showAddExpense = true
                            },
                            snackbarHostState = snackbarHostState
                        )
                        NavDest.Notes -> NotesScreen(
                            contentPadding = padding,
                            onAddExpense = {
                                expenseIdToEdit = null
                                showAddExpense = true
                            },
                            onEditExpense = { id ->
                                expenseIdToEdit = id
                                showAddExpense = true
                            },
                            snackbarHostState = snackbarHostState,
                            initialImportText = notesSharedTextToImport,
                            onClearInitialImportText = { notesSharedTextToImport = null }
                        )
                        NavDest.Transactions -> TransactionsScreen(
                            contentPadding = padding,
                            onAddExpense = {
                                expenseIdToEdit = null
                                showAddExpense = true
                            },
                            snackbarHostState = snackbarHostState
                        )
                        NavDest.Insights -> InsightsScreen(
                            contentPadding = padding
                        )
                        NavDest.Settings -> SettingsScreen(
                            contentPadding = padding,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }

        // Exact Floating Heads-Up Banner matching the requested card without anything surrounding it
        InAppExpenseBanner(
            onEditExpense = { id ->
                expenseIdToEdit = id
                showAddExpense = true
            }
        )
    }

    if (showAddExpense) {
        AddExpenseSheet(
            expenseIdToEdit = expenseIdToEdit,
            onDismiss = {
                showAddExpense = false
                expenseIdToEdit = null
            },
            onSaved = {
                showAddExpense = false
                expenseIdToEdit = null
            }
        )
    }
}

@Composable
fun SpendlyBottomBar(
    current: NavDest,
    onSelect: (NavDest) -> Unit,
    onAddClick: () -> Unit
) {
    NavigationBar(
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        bottomNavItems.forEachIndexed { index, dest ->
            // Insert elevated tactile add button between Expenses and Insights
            if (index == 2) {
                NavigationBarItem(
                    selected = false,
                    onClick = onAddClick,
                    icon = {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(48.dp)
                                .bouncingClickable(scaleDown = 0.90f, onClick = onAddClick)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add Expense",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    label = null,
                    alwaysShowLabel = false
                )
            }
            val isSelected = current == dest
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.18f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tab_icon_scale"
            )
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        modifier = Modifier.scale(iconScale)
                    )
                },
                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
