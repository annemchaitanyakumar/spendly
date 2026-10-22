package com.spendly.app.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.DayCashFlow
import com.spendly.app.core.model.Expense
import com.spendly.app.theme.ExpenseRed
import com.spendly.app.theme.IncomeGreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SpendCalendarView(
    year: Int,
    month: Int,
    cashFlowDays: List<DayCashFlow>,
    totalDebitsMinor: Long,
    totalCreditsMinor: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    onEditExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember(year, month) {
        val currentMonthYear = YearMonth.of(year, month)
        if (today.year == year && today.monthValue == month) {
            mutableStateOf(today)
        } else {
            mutableStateOf(currentMonthYear.atDay(1))
        }
    }

    val ym = remember(year, month) { YearMonth.of(year, month) }
    val daysInMonth = ym.lengthOfMonth()
    val firstDayOfWeek = ym.atDay(1).dayOfWeek.value // 1 = Monday, 7 = Sunday
    val leadingEmptyDays = firstDayOfWeek - 1

    val dayFlowMap = remember(cashFlowDays) {
        cashFlowDays.associateBy { it.date.dayOfMonth }
    }

    val selectedDayFlow = dayFlowMap[selectedDate.dayOfMonth]

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Selector & Cashflow Highlights
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header with navigation arrows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevMonth,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Previous month")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = DateTimeUtils.formatMonthYear(year, month),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onNextMonth,
                        enabled = canGoNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            "Next month",
                            tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }

                // Financial Flow Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowMetricItem(
                        label = "Total Debits",
                        amountText = CurrencyFormatter.formatMinor(totalDebitsMinor),
                        color = ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    FlowMetricItem(
                        label = "Total Credits",
                        amountText = "+${CurrencyFormatter.formatMinor(totalCreditsMinor)}",
                        color = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    val netBalance = totalCreditsMinor - totalDebitsMinor
                    FlowMetricItem(
                        label = "Net Flow",
                        amountText = if (netBalance >= 0) "+${CurrencyFormatter.formatMinor(netBalance)}" else "-${CurrencyFormatter.formatMinor(-netBalance)}",
                        color = if (netBalance >= 0) IncomeGreen else ExpenseRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Calendar Grid
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Day of Week Header (Mon, Tue, Wed, Thu, Fri, Sat, Sun)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { dayName ->
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Month Grid
                val totalCells = leadingEmptyDays + daysInMonth
                val totalRows = (totalCells + 6) / 7

                for (rowIndex in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        for (colIndex in 0 until 7) {
                            val cellIndex = rowIndex * 7 + colIndex
                            val dayNumber = cellIndex - leadingEmptyDays + 1

                            if (dayNumber in 1..daysInMonth) {
                                val cellDate = ym.atDay(dayNumber)
                                val isSelected = cellDate == selectedDate
                                val isToday = cellDate == today
                                val flow = dayFlowMap[dayNumber]

                                CalendarDayCell(
                                    dayNumber = dayNumber,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    debitMinor = flow?.debitMinor ?: 0L,
                                    creditMinor = flow?.creditMinor ?: 0L,
                                    onClick = { selectedDate = cellDate },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f).height(58.dp))
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Transactions List
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val deb = selectedDayFlow?.debitMinor ?: 0L
                        val cred = selectedDayFlow?.creditMinor ?: 0L
                        Text(
                            text = "Debit: ${CurrencyFormatter.formatMinor(deb)}  •  Credit: ${CurrencyFormatter.formatMinor(cred)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val count = selectedDayFlow?.transactions?.size ?: 0
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "$count ${if (count == 1) "txn" else "txns"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                val txns = selectedDayFlow?.transactions ?: emptyList()
                if (txns.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions on this date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        txns.forEach { expense ->
                            ExpenseListItem(
                                expense = expense,
                                onClick = onEditExpense
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowMetricItem(
    label: String,
    amountText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = amountText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    debitMinor: Long,
    creditMinor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(58.dp)
            .padding(1.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day Number
            Text(
                text = "$dayNumber",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            // Flow Indicators
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (debitMinor > 0L) {
                    Text(
                        text = "-${formatCompactAmount(debitMinor)}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed,
                        maxLines = 1
                    )
                }
                if (creditMinor > 0L) {
                    Text(
                        text = "+${formatCompactAmount(creditMinor)}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatCompactAmount(minor: Long): String {
    val rupees = minor / 100
    return when {
        rupees >= 100_000 -> "${rupees / 100_000}L"
        rupees >= 1_000 -> "${rupees / 1_000}k"
        else -> "₹$rupees"
    }
}
