package com.aiza.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Expanding rings that fade out, like a radar ping. [periodMs] sets the speed. */
@Composable
fun PulseRings(
    color: Color,
    modifier: Modifier = Modifier,
    rings: Int = 3,
    periodMs: Int = 2400,
    minRadiusFraction: Float = 0.3f,
    filled: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val maxR = size.minDimension / 2
        val minR = maxR * minRadiusFraction
        repeat(rings) { i ->
            val p = (phase + i.toFloat() / rings) % 1f
            val r = minR + (maxR - minR) * p
            val alpha = (1f - p) * 0.55f
            if (filled) drawCircle(color.copy(alpha = alpha * 0.35f), r)
            drawCircle(color.copy(alpha = alpha), r, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
