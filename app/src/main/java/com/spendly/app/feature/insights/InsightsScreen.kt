package com.spendly.app.feature.insights

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.ui.spendlyViewModel
import kotlin.math.min

@Composable
fun InsightsScreen(
    contentPadding: PaddingValues,
    viewModel: InsightsViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Insights",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.Default.ChevronLeft, "Previous month")
                    }
                    TextButton(onClick = {}) {
                        Text(DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth))
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.Default.ChevronRight, "Next month")
                    }
                }
            }
        }

        if (state.isLoading) {
            item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }}
            return@LazyColumn
        }

        val summary = state.summary

        if (summary == null || summary.totalMinor == 0L) {
            item { InsightsEmptyState() }
            return@LazyColumn
        }

        // ── Daily bar chart ────────────────────────────────────────────
        if (summary.dailyTotals.isNotEmpty()) {
            item {
                InsightCard(title = "Daily Spending") {
                    DailyBarChart(dailyTotals = summary.dailyTotals)
                }
            }
        }

        // ── Category donut ─────────────────────────────────────────────
        val catBreakdown = summary.categoryBreakdown.filter { it.totalMinor > 0 }
        if (catBreakdown.isNotEmpty()) {
            item {
                InsightCard(title = "Spending by Category") {
                    CategoryDonutSection(categories = catBreakdown, total = summary.totalMinor)
                }
            }
        }

        // ── Top merchants ──────────────────────────────────────────────
        if (summary.topMerchants.isNotEmpty()) {
            item {
                InsightCard(title = "Top Merchants") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.topMerchants.take(5).forEachIndexed { index, merchant ->
                            MerchantRow(rank = index + 1, merchant = merchant)
                        }
                    }
                }
            }
        }

        // ── Month comparison ────────────────────────────────────────────
        if (summary.previousMonthTotalMinor != null) {
            item {
                InsightCard(title = "Month Comparison") {
                    MonthComparisonView(summary = summary)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun DailyBarChart(dailyTotals: List<DailySpending>) {
    val maxTotal = dailyTotals.maxOfOrNull { it.totalMinor } ?: 1L
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "bar_anim"
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / (dailyTotals.size.coerceAtLeast(1) * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height * 0.85f

        dailyTotals.forEachIndexed { index, daily ->
            val x = index * (barWidth + spacing) + spacing / 2
            val barFraction = (daily.totalMinor.toFloat() / maxTotal) * animProgress
            val barHeight = barFraction * maxBarHeight
            val y = size.height - barHeight

            // Background bar
            drawRoundRect(
                color = containerColor,
                topLeft = Offset(x, size.height - maxBarHeight),
                size = Size(barWidth, maxBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
            // Filled bar
            if (barHeight > 0) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                )
            }
        }
    }
}

@Composable
private fun CategoryDonutSection(categories: List<CategorySpending>, total: Long) {
    val colors = remember(categories) {
        categories.map { cat ->
            cat.category?.colorHex?.let {
                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
            } ?: Color.Gray
        }
    }

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "donut_anim"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut chart
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 24f
            val radius = (min(size.width, size.height) / 2) - strokeWidth
            var startAngle = -90f

            categories.forEachIndexed { index, spending ->
                val fraction = spending.totalMinor.toFloat() / total
                val sweepAngle = fraction * 360f * animProgress
                drawArc(
                    color = colors.getOrElse(index) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(
                        (size.width / 2) - radius,
                        (size.height / 2) - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
                startAngle += sweepAngle + 2f
            }
        }

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.take(5).forEachIndexed { index, spending ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.getOrElse(index) { Color.Gray })
                    )
                    Text(
                        spending.category?.name ?: "Other",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        CurrencyFormatter.formatCompact(spending.totalMinor),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MerchantRow(rank: Int, merchant: MerchantSpending) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Text(
            merchant.merchant.ifBlank { "Unknown" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${merchant.transactionCount}x",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            CurrencyFormatter.formatMinor(merchant.totalMinor),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MonthComparisonView(summary: MonthSummary) {
    val prev = summary.previousMonthTotalMinor ?: 0L
    val curr = summary.totalMinor
    val changePercent = summary.changePercent
    val isUp = (changePercent ?: 0.0) > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Last month", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(CurrencyFormatter.formatMinor(prev), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isUp) com.spendly.app.theme.ExpenseRed else com.spendly.app.theme.IncomeGreen
            )
            changePercent?.let {
                Text(
                    "${if (isUp) "+" else ""}${String.format("%.1f", it)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUp) com.spendly.app.theme.ExpenseRed else com.spendly.app.theme.IncomeGreen
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This month", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(CurrencyFormatter.formatMinor(curr), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InsightsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            "No data for this month",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Add expenses to see spending insights",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
