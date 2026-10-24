package com.spendly.app.feature.insights

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.spendly.app.ui.components.bounceOnPress
import com.spendly.app.ui.components.bouncingClickable
import com.spendly.app.ui.components.rememberPulsingAlpha
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.spendly.app.theme.*
import com.spendly.app.ui.spendlyViewModel
import kotlin.math.min

@Composable
fun InsightsScreen(
    contentPadding: PaddingValues,
    viewModel: InsightsViewModel = spendlyViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InsightsContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun InsightsContent(
    state: InsightsUiState,
    viewModel: InsightsViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Insights",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = viewModel::previousMonth,
                        modifier = Modifier
                            .size(36.dp)
                            .bounceOnPress()
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            "Previous month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    AnimatedContent(
                        targetState = DateTimeUtils.formatMonthYear(state.currentYear, state.currentMonth),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                        },
                        label = "insights_month_anim"
                    ) { monthLabel ->
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    IconButton(
                        onClick = viewModel::nextMonth,
                        modifier = Modifier
                            .size(36.dp)
                            .bounceOnPress()
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            "Next month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }

        val summary = state.summary

        if (summary == null || summary.totalMinor == 0L) {
            InsightsEmptyState()
            return
        }

        // ── AI Financial Advisor Card ──────────────────────────────────
        val aiPulseAlpha by rememberPulsingAlpha(minAlpha = 0.5f, maxAlpha = 1.0f)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = aiPulseAlpha),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "AI Financial Advisor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.generateAiAnalysis() },
                        enabled = !state.isAnalyzing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.bounceOnPress(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Analyzing...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (state.aiAnalysis == null) "Ask AI" else "Refresh",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (state.aiAnalysis != null) {
                    Text(
                        state.aiAnalysis!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                } else {
                    Text(
                        "Get an executive breakdown of your monthly spending velocity, category burn, and personalized savings tips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Daily bar chart ────────────────────────────────────────────
        if (summary.dailyTotals.isNotEmpty()) {
            InsightCard(title = "Daily Spending") {
                DailyBarChart(dailyTotals = summary.dailyTotals)
            }
        }

        // ── Category donut ─────────────────────────────────────────────
        val catBreakdown = summary.categoryBreakdown.filter { it.totalMinor > 0 }
        if (catBreakdown.isNotEmpty()) {
            InsightCard(title = "Spending by Category") {
                CategoryDonutSection(categories = catBreakdown, total = summary.totalMinor)
            }
        }

        // ── Top merchants ──────────────────────────────────────────────
        if (summary.topMerchants.isNotEmpty()) {
            InsightCard(title = "Top Merchants") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    summary.topMerchants.take(5).forEachIndexed { index, merchant ->
                        MerchantRow(rank = index + 1, merchant = merchant)
                    }
                }
            }
        }

        // ── Month comparison ────────────────────────────────────────────
        if (summary.previousMonthTotalMinor != null) {
            InsightCard(title = "Month Comparison") {
                MonthComparisonView(summary = summary)
            }
        }
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    val maxTotal = dailyTotals.maxOfOrNull { it.totalMinor }?.coerceAtLeast(1L) ?: 1L
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val animProgress = remember(dailyTotals) { Animatable(0f) }
    LaunchedEffect(dailyTotals) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(750, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / (dailyTotals.size.coerceAtLeast(1) * 1.5f)
        val spacing = barWidth * 0.5f
        val maxBarHeight = size.height * 0.85f

        dailyTotals.forEachIndexed { index, daily ->
            val x = index * (barWidth + spacing) + spacing / 2
            val barFraction = if (maxTotal > 0L) (daily.totalMinor.toFloat() / maxTotal) * animProgress.value else 0f
            val barHeight = if (barFraction.isFinite()) (barFraction * maxBarHeight).coerceAtLeast(0f) else 0f
            val y = size.height - barHeight

            // Background bar
            drawRoundRect(
                color = containerColor,
                topLeft = Offset(x, size.height - maxBarHeight),
                size = Size(barWidth, maxBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )
            // Filled bar
            if (barHeight > 0f && barHeight.isFinite() && y.isFinite()) {
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
    // Pure monochrome warm white and grayscale shades for OLED black
    val monochromeShades = listOf(
        WarmWhite,
        Color(0xFFE5E2DC),
        Color(0xFFD4D0C8),
        Color(0xFFA8A29E),
        Color(0xFF78716C),
        Color(0xFF52525B),
        Color(0xFF3F3F46)
    )

    val colors = remember(categories) {
        categories.mapIndexed { index, _ ->
            monochromeShades[index % monochromeShades.size]
        }
    }

    val animProgress = remember(categories, total) { Animatable(0f) }
    LaunchedEffect(categories, total) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    }

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
            val safeTotal = total.coerceAtLeast(1L)

            categories.forEachIndexed { index, spending ->
                val fraction = if (safeTotal > 0L) spending.totalMinor.toFloat() / safeTotal else 0f
                val sweepAngle = if (fraction.isFinite()) fraction * 360f * animProgress.value else 0f
                if (sweepAngle > 0f && sweepAngle.isFinite()) {
                    drawArc(
                        color = colors.getOrElse(index) { WarmWhiteMuted },
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
                            .background(colors.getOrElse(index) { WarmWhiteMuted })
                    )
                    Text(
                        spending.category?.name ?: "Other",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        CurrencyFormatter.formatCompact(spending.totalMinor),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
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
            color = MaterialTheme.colorScheme.onSurface,
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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
            Text(
                "Last month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                CurrencyFormatter.formatMinor(prev),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            changePercent?.let {
                Text(
                    "${if (isUp) "+" else ""}${String.format("%.1f", it)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "This month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                CurrencyFormatter.formatMinor(curr),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
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
