package com.aiza.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.data.tags.BatteryLevel

/** 0..4 bars from RSSI. Changes in signal matter more than absolute values (SRS App. G), so keep it coarse. */
fun signalBars(rssiDbm: Int?): Int = when {
    rssiDbm == null -> 0
    rssiDbm >= -60 -> 4
    rssiDbm >= -70 -> 3
    rssiDbm >= -80 -> 2
    else -> 1
}

@Composable
fun SignalBars(rssiDbm: Int?, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val bars = signalBars(rssiDbm)
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(width = height * 1.1f, height = height).semantics { contentDescription = "Signal $bars of 4" }) {
        val w = size.width / 7f
        repeat(4) { i ->
            val h = size.height * (i + 1) / 4f
            drawRoundRect(
                if (i < bars) on else off,
                topLeft = Offset(i * w * 2, size.height - h), size = Size(w * 1.3f, h),
                cornerRadius = CornerRadius(w),
            )
        }
    }
}

/** How full the battery icon is drawn for each level. */
val BatteryLevel.fraction: Float
    get() = when (this) {
        BatteryLevel.FULL -> 1f
        BatteryLevel.GOOD -> 0.6f
        BatteryLevel.LOW -> 0.3f
        BatteryLevel.CRITICAL -> 0.12f
    }

@Composable
fun batteryColor(level: BatteryLevel?): Color = when (level) {
    null -> MaterialTheme.colorScheme.outline
    BatteryLevel.FULL, BatteryLevel.GOOD -> AizaTheme.extra.success
    BatteryLevel.LOW -> AizaTheme.extra.warning
    BatteryLevel.CRITICAL -> MaterialTheme.colorScheme.error
}

/** Battery glyph + level word (Full / Good / Low / Critical). Highlighted when low (FR-STA-02). */
@Composable
fun BatteryPill(level: BatteryLevel?, modifier: Modifier = Modifier) {
    val low = level == BatteryLevel.LOW || level == BatteryLevel.CRITICAL
    val fill by animateColorAsState(batteryColor(level), label = "battery")
    Surface(
        modifier.semantics { contentDescription = "Battery " + (level?.label?.lowercase() ?: "unknown") },
        shape = CircleShape,
        color = if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(level?.fraction ?: 0f, fill, outline = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                level?.label ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun BatteryGlyph(fraction: Float, fill: Color, outline: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 20.dp, height = 11.dp)) {
        val stroke = 1.4.dp.toPx()
        val nub = 2.dp.toPx()
        val body = Size(size.width - nub - stroke, size.height - stroke)
        drawRoundRect(outline, Offset(stroke / 2, stroke / 2), body, CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawRoundRect(outline, Offset(size.width - nub, size.height * 0.3f), Size(nub, size.height * 0.4f), CornerRadius(1.dp.toPx()))
        val inset = stroke * 1.6f
        val w = (body.width - inset * 2 + stroke) * fraction.coerceIn(0f, 1f)
        drawRoundRect(fill, Offset(inset, inset), Size(w, size.height - inset * 2), CornerRadius(1.5.dp.toPx()))
    }
}

/** Small coloured dot + text, e.g. "Nearby". */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Spacer(modifier.size(size).background(color, CircleShape))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

val CardSpacing = Arrangement.spacedBy(12.dp)
