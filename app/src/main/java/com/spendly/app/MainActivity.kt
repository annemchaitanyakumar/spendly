package com.spendly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.settings.ThemeMode
import com.spendly.app.feature.expenseform.AddExpenseSheet
import com.spendly.app.feature.home.HomeScreen
import com.spendly.app.feature.insights.InsightsScreen
import com.spendly.app.feature.settings.SettingsScreen
import com.spendly.app.feature.transactions.TransactionsScreen
import com.spendly.app.theme.SpendlyTheme
import com.spendly.app.ui.MainViewModel
import com.spendly.app.ui.spendlyViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}

sealed class NavDest(val route: String, val label: String, val icon: ImageVector) {
    object Home : NavDest("home", "Home", Icons.Default.Home)
    object Transactions : NavDest("transactions", "Expenses", Icons.Default.List)
    object Insights : NavDest("insights", "Insights", Icons.Default.BarChart)
    object Settings : NavDest("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    NavDest.Home,
    NavDest.Transactions,
    NavDest.Insights,
    NavDest.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendlyApp() {
    var currentDest by remember { mutableStateOf<NavDest>(NavDest.Home) }
    var showAddExpense by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SpendlyBottomBar(
                current = currentDest,
                onSelect = { currentDest = it },
                onAddClick = { showAddExpense = true }
            )
        },
        floatingActionButton = {},  // FAB is inside bottom bar area
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentDest,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) + slideInHorizontally { 30 })
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "nav_anim"
            ) { dest ->
                when (dest) {
                    NavDest.Home -> HomeScreen(
                        contentPadding = padding,
                        onAddExpense = { showAddExpense = true },
                        snackbarHostState = snackbarHostState
                    )
                    NavDest.Transactions -> TransactionsScreen(
                        contentPadding = padding,
                        onAddExpense = { showAddExpense = true },
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

    if (showAddExpense) {
        AddExpenseSheet(
            onDismiss = { showAddExpense = false },
            onSaved = {
                showAddExpense = false
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
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEachIndexed { index, dest ->
            // Insert FAB-like add button between Expenses and Insights
            if (index == 2) {
                NavigationBarItem(
                    selected = false,
                    onClick = onAddClick,
                    icon = {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
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
            NavigationBarItem(
                selected = current == dest,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label
                    )
                },
                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
