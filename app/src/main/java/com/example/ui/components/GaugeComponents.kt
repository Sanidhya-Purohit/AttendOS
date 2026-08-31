package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AttendanceTier
import com.example.ui.theme.*

@Composable
fun CircularAttendanceGauge(
    percentage: Double,
    targetPercentage: Double = 75.0,
    tier: AttendanceTier,
    size: Dp = 100.dp,
    strokeWidth: Dp = 10.dp,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "GaugeProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("circular_attendance_gauge")
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val canvasSize = this.size.minDimension
            val radius = canvasSize / 2

            // Track Background
            drawCircle(
                color = DarkBorder,
                radius = radius,
                style = Stroke(width = strokeWidth.toPx())
            )

            // 75% Target Marker (subtle dot or arc)
            val targetAngle = (targetPercentage / 100.0).toFloat() * 360f - 90f

            // Progress Arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        tier.color.copy(alpha = 0.7f),
                        tier.color,
                        tier.color
                    )
                ),
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        if (showLabel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${String.format("%.1f", percentage)}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = if (size > 90.dp) 18.sp else 14.sp,
                        color = tier.color
                    )
                )
                if (size > 90.dp) {
                    Text(
                        text = if (percentage >= targetPercentage) "ELIGIBLE" else "DANGER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (percentage >= targetPercentage) NeonEmerald else NeonCrimson
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TierBadge(
    tier: AttendanceTier,
    modifier: Modifier = Modifier
) {
    Surface(
        color = tier.color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tier.color.copy(alpha = 0.5f)),
        modifier = modifier.testTag("tier_badge_${tier.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tier.color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tier.badgeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = tier.color,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun QuickAttendanceActions(
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Present (+1)
        Button(
            onClick = onPresent,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonEmerald.copy(alpha = 0.2f),
                contentColor = NeonEmerald
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp)
                .testTag("btn_present_quick")
        ) {
            Icon(Icons.Default.Check, contentDescription = "Present", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Present", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Absent (-1)
        Button(
            onClick = onAbsent,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCrimson.copy(alpha = 0.2f),
                contentColor = NeonCrimson
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCrimson),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(44.dp)
                .testTag("btn_absent_quick")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Absent", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Absent", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Cancelled / Free
        OutlinedButton(
            onClick = onCancelled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextSecondary
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier
                .weight(0.9f)
                .height(44.dp)
                .testTag("btn_cancelled_quick")
        ) {
            Icon(Icons.Default.Block, contentDescription = "Cancelled", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("Off", fontSize = 11.sp)
        }
    }
}

@Composable
fun EmergencyBufferPill(
    bufferCount: Int,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onAdjustCount: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isEnabled) DarkSurfaceElevated else DarkSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) NeonCyan.copy(alpha = 0.4f) else DarkBorder
        ),
        modifier = modifier.testTag("emergency_buffer_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = "Emergency Buffer",
                tint = if (isEnabled) NeonCyan else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Emergency Leave Buffer: ${if (isEnabled) "$bufferCount classes" else "OFF"}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) TextPrimary else TextSecondary,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = if (isEnabled) "Protected for sickness/fests" else "Raw calculation mode",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            if (isEnabled) {
                IconButton(
                    onClick = { if (bufferCount > 1) onAdjustCount(bufferCount - 1) },
                    modifier = Modifier.size(28.dp).testTag("btn_buffer_minus")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease Buffer", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "$bufferCount",
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = { if (bufferCount < 10) onAdjustCount(bufferCount + 1) },
                    modifier = Modifier.size(28.dp).testTag("btn_buffer_plus")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase Buffer", tint = NeonCyan, modifier = Modifier.size(16.dp))
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = NeonCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurface
                ),
                modifier = Modifier.padding(start = 6.dp).testTag("switch_emergency_buffer")
            )
        }
    }
}
