package com.spendly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

    val labelText = when {
        expense.recordType == RecordType.TRANSFER -> "Transfer"
        expense.recordType == RecordType.REFUND -> "Refund"
        expense.direction == TransactionDirection.CREDIT -> "Received"
        else -> "Paid"
    }

    val (badgeBg, badgeText) = when (labelText) {
        "Transfer" -> Pair(Color(0xFFEDE7F6), Color(0xFF5E35B1))
        "Received" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "Refund" -> Pair(Color(0xFFE1F5FE), Color(0xFF0277BD))
        else -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp,
        onClick = { onClick?.invoke(expense) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category icon circle
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, (catColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = categoryIconEmoji(expense.categoryIcon),
                        fontSize = 22.sp
                    )
                }
            }

            // Merchant / Recipient + Category & Date
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (expense.recordType == RecordType.TRANSFER && !expense.merchant.startsWith("Transfer", ignoreCase = true)) {
                            "Transfer to ${expense.merchant.ifBlank { "Account" }}"
                        } else {
                            expense.merchant.ifBlank { "Expense" }
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Pill Badge for "Paid" vs "Transfer" vs "Received"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expense.categoryName ?: "General",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = DateTimeUtils.formatRelativeDate(expense.transactionAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (expense.direction == TransactionDirection.CREDIT) "+" else "-"}" +
                           CurrencyFormatter.formatMinor(expense.amountMinor, expense.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                if (expense.recordType == RecordType.PENDING) {
                    Text(
                        "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = PendingAmber,
                        fontWeight = FontWeight.Medium
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
    "restaurant" -> "🍽️"
    "shopping_cart" -> "🛒"
    "directions_car" -> "🚗"
    "shopping_bag" -> "🛍️"
    "receipt_long" -> "🧾"
    "movie" -> "🎬"
    "local_hospital" -> "🏥"
    "school" -> "🎓"
    "flight" -> "✈️"
    "local_gas_station" -> "⛽"
    "home" -> "🏠"
    "subscriptions" -> "📱"
    "swap_horiz" -> "↔️"
    "trending_up" -> "📈"
    "face" -> "💇"
    "card_giftcard" -> "🎁"
    "family_restroom" -> "👨‍👩‍👦"
    "pets" -> "🐾"
    "build" -> "🔧"
    "category" -> "📦"
    else -> "💳"
}
