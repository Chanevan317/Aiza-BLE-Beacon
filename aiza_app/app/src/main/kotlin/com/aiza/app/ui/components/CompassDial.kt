package com.aiza.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiza.core.direction.pdr.HeadingFilter
import kotlin.math.cos
import kotlin.math.sin

/**
 * A compass card like a real compass: degree ticks and N/E/S/W letters. The card turns by −heading so N always
 * points to real north, while a fixed bar at the top (lubber line) marks the person's front (where the phone points).
 * Draws nothing until a heading is known.
 */
@Composable
fun CompassDial(
    headingDeg: Float?,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 16.sp,
    frontSize: Dp = 14.dp,
    /** False = ticks and front bar only (no N/E/S/W). */
    showLabels: Boolean = true,
) {
    // Unwrap so 359° → 1° turns 2°, not 358° the long way round.
    var unwrapped by remember { mutableFloatStateOf(headingDeg ?: 0f) }
    headingDeg?.let { unwrapped += HeadingFilter.angleDiff(it, unwrapped) }
    val rotation by animateFloatAsState(-unwrapped, tween(200), label = "compass")

    val measurer = rememberTextMeasurer()
    val tick = MaterialTheme.colorScheme.outline
    val text = MaterialTheme.colorScheme.onSurface
    val north = MaterialTheme.colorScheme.error
    val front = MaterialTheme.colorScheme.primary
    val base = TextStyle(fontSize = labelSize, fontWeight = FontWeight.Bold)
    val labels = remember(labelSize, text, north) {
        listOf("N" to north, "E" to text, "S" to text, "W" to text).map { (l, c) -> measurer.measure(l, base.copy(color = c)) }
    }

    Canvas(modifier) {
        if (headingDeg == null) return@Canvas
        val r = size.minDimension / 2
        val major = r * 0.09f
        val minor = r * 0.05f
        val labelHeight = labels[0].size.height
        val labelCentre = r - major - 4.dp.toPx() - labelHeight / 2f
        rotate(rotation) {
            // Ticks every 10°, longer every 30°.
            for (deg in 0 until 360 step 10) {
                val long = deg % 30 == 0
                rotate(deg.toFloat()) {
                    drawLine(
                        if (long) tick else tick.copy(alpha = 0.5f),
                        Offset(center.x, center.y - r), Offset(center.x, center.y - r + if (long) major else minor),
                        (if (long) 2.dp else 1.dp).toPx(), StrokeCap.Round,
                    )
                }
            }
        }
        // Front marker (lubber line): fixed, does not turn with the card. A thick bar, not an arrow, so it reads as
        // "you are facing here" and is not mistaken for the direction to the tag.
        drawLine(
            front, Offset(center.x, center.y - r - frontSize.toPx() * 0.35f), Offset(center.x, center.y - r + major),
            frontSize.toPx() * 0.4f, StrokeCap.Round,
        )
        if (!showLabels) return@Canvas
        // Letters at 0/90/180/270: positioned with the card but kept upright so they are always readable.
        labels.forEachIndexed { i, layout ->
            val a = Math.toRadians((rotation + i * 90f - 90f).toDouble())
            val c = Offset(center.x + labelCentre * cos(a).toFloat(), center.y + labelCentre * sin(a).toFloat())
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
        }
    }
}
