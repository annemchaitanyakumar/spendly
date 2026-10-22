package com.spendly.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.app.core.common.CurrencyFormatter
import com.spendly.app.core.livealert.LiveAlertData
import com.spendly.app.core.livealert.LiveAlertManager
import kotlinx.coroutines.delay

/**
 * OxygenOS Live Alerts & Apple Dynamic Island style floating capsule alert.
 * Drops down from the camera cutout with spring physics when a payment or transfer occurs.
 */
@Composable
fun DynamicIslandAlert(
    modifier: Modifier = Modifier,
    onUndoExpense: ((Long) -> Unit)? = null
) {
    val alertState by LiveAlertManager.currentAlert.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    // Reset expanded state when alert changes
    LaunchedEffect(alertState?.id) {
        isExpanded = false
    }

    // Auto-dismiss after 6 seconds if not manually expanded
    LaunchedEffect(alertState?.id, isExpanded) {
        if (alertState != null && !isExpanded) {
            delay(5500L)
            LiveAlertManager.dismiss()
        }
    }

    val currentAlert = alertState

    AnimatedVisibility(
        visible = currentAlert != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        if (currentAlert != null) {
            DynamicIslandContent(
                alert = currentAlert,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                onDismiss = {
                    isExpanded = false
                    LiveAlertManager.dismiss()
                },
                onUndo = {
                    currentAlert.expenseId?.let { id ->
                        onUndoExpense?.invoke(id)
                    }
                    isExpanded = false
                    LiveAlertManager.dismiss()
                }
            )
        }
    }
}

@Composable
private fun DynamicIslandContent(
    alert: LiveAlertData,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit
) {
    // Pulsing radar animation for the glowing live activity dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val shape = if (isExpanded) RoundedCornerShape(26.dp) else RoundedCornerShape(percent = 50)

    Surface(
        shape = shape,
        color = Color(0xFF0F141C),
        shadowElevation = 14.dp,
        tonalElevation = 6.dp,
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF00E676).copy(alpha = 0.7f),
                    Color(0xFF00B0FF).copy(alpha = 0.4f),
                    Color(0xFF00E676).copy(alpha = 0.7f)
                )
            )
        ),
        modifier = Modifier
            .padding(horizontal = if (isExpanded) 16.dp else 20.dp)
            .fillMaxWidth(if (isExpanded) 0.94f else 0.88f)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    // Swipe up to dismiss
                    if (dragAmount < -15) {
                        onDismiss()
                    }
                }
            }
            .clickable { onToggleExpand() }
    ) {
        if (!isExpanded) {
            // ── COMPACT CAPSULE VIEW (OxygenOS Live Alert Pill) ──────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Pulsing emerald beacon + Category emoji
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = pulseAlpha))
                    )

                    Text(
                        text = categoryIconEmoji(alert.categoryIcon),
                        fontSize = 17.sp
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${alert.label} • ${alert.merchant}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "${CurrencyFormatter.formatMinor(alert.amountMinor, alert.currencyCode)} • ${alert.categoryName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    }
                }

                // Right: Checkmark icon with green accent
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E676).copy(alpha = 0.18f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Recorded",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        } else {
            // ── EXPANDED LIVE CARD VIEW (Dynamic Island Expanded) ────────────
            Column(
                modifier = Modifier
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Text(
                            text = (alert.sourceApp ?: "Spendly") + " Live Alert",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Main Amount and Merchant
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${alert.label} to",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = alert.merchant.ifBlank { "Merchant / Payee" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = CurrencyFormatter.formatMinor(alert.amountMinor, alert.currencyCode),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00E676)
                    )
                }

                // Category & Details Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2633)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(categoryIconEmoji(alert.categoryIcon), fontSize = 14.sp)
                            Text(
                                text = alert.categoryName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    if (alert.transactionRef != null) {
                        Text(
                            text = "Ref: ${alert.transactionRef.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Action buttons: Undo and Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (alert.expenseId != null) {
                        OutlinedButton(
                            onClick = onUndo,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF5252)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Undo, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Undo")
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
