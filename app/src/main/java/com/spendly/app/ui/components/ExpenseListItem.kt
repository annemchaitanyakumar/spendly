package com.spendly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.common.DateTimeUtils
import com.spendly.app.core.model.*
import com.spendly.app.theme.ExpenseRed
import com.spendly.app.theme.IncomeGreen
import com.spendly.app.theme.PendingAmber
import com.spendly.app.theme.RefundBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListItem(
    expense: Expense,
    onClick: ((Expense) -> Unit)? = null,
    onDelete: ((Expense) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val amountColor = when {
        expense.recordType == RecordType.REFUND -> RefundBlue
        expense.direction == TransactionDirection.CREDIT -> IncomeGreen
        expense.recordType == RecordType.PENDING -> PendingAmber
        else -> ExpenseRed
    }

    val catColor = remember(expense.categoryColorHex) {
        expense.categoryColorHex?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = { onClick?.invoke(expense) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category icon circle
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = categoryIconEmoji(expense.categoryIcon),
                        fontSize = 20.sp
                    )
                }
            }

            // Merchant + date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant.ifBlank { "Expense" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expense.categoryName ?: "Uncategorized",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateTimeUtils.formatRelativeDate(expense.transactionAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (expense.direction == TransactionDirection.CREDIT) "+" else "-"}" +
                           CurrencyFormatter.formatMinor(expense.amountMinor),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                if (expense.recordType == RecordType.PENDING) {
                    Text(
                        "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = PendingAmber
                    )
                }
                if (expense.hasLocation) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Has location",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

fun categoryIconEmoji(icon: String?): String = when (icon) {
    "restaurant" -> "🍽"
    "shopping_cart" -> "🛒"
    "directions_car" -> "🚗"
    "shopping_bag" -> "👜"
    "receipt_long" -> "🧾"
    "movie" -> "🎬"
    "local_hospital" -> "🏥"
    "school" -> "🎓"
    "flight" -> "✈️"
    "local_gas_station" -> "⛽"
    "home" -> "🏠"
    "subscriptions" -> "📱"
    "swap_horiz" -> "↔️"
    "category" -> "•"
    else -> "💳"
}
