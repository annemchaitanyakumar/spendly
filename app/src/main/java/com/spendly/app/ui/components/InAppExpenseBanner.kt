package com.spendly.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.app.R
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.notifications.InAppNotificationEvent
import com.spendly.app.core.notifications.InAppNotificationManager
import kotlinx.coroutines.delay

@Composable
fun InAppExpenseBanner(
    onEditExpense: (Long) -> Unit,
    onUndoExpense: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeEvent by remember { mutableStateOf<InAppNotificationEvent?>(null) }

    LaunchedEffect(Unit) {
        InAppNotificationManager.events.collect { event ->
            activeEvent = event
            delay(6000)
            if (activeEvent == event) {
                activeEvent = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = activeEvent != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(320)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(280)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            activeEvent?.let { event ->
                Surface(
                    color = Color(0xFF000000),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF222222)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
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

                            val badgeText = when (event.label) {
                                "Transfer", "Self Transfer" -> "TRANSFER"
                                "Received", "Credited" -> "CREDIT"
                                "Refund" -> "REFUND"
                                else -> "PAID"
                            }

                            Surface(
                                color = Color(0xFF141414),
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, Color(0xFF333333))
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDFBF7),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Amount & Payee Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val amountStr = CurrencyFormatter.formatMinor(event.amountMinor, event.currencyCode)
                            Text(
                                text = amountStr,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDFBF7)
                            )
                            val payeePrefix = if (event.label == "Received" || event.label == "Credited" || event.label == "Refund") "from " else "to "
                            val cleanRecipient = event.merchantOrRecipient.trim()
                            if (cleanRecipient.isNotBlank()) {
                                Text(
                                    text = "$payeePrefix$cleanRecipient",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFC8C5BD),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }

                        // Meta Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                event.categoryName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDFBF7)
                            )
                            Text(" • ", color = Color(0xFF555555), style = MaterialTheme.typography.labelSmall)
                            Text(
                                event.sourceDisplayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA09E97)
                            )
                            if (!event.transactionRef.isNullOrBlank()) {
                                Text(" • ", color = Color(0xFF555555), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "Ref: ${event.transactionRef.take(8)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, Color(0xFF333333)),
                                modifier = Modifier.bouncingClickable {
                                    activeEvent = null
                                    onUndoExpense?.invoke(event.expenseId)
                                }
                            ) {
                                Text(
                                    "Undo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDFBF7),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFFDFBF7),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.bouncingClickable {
                                    val id = event.expenseId
                                    activeEvent = null
                                    onEditExpense(id)
                                }
                            ) {
                                Text(
                                    "Edit Details",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF000000),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
