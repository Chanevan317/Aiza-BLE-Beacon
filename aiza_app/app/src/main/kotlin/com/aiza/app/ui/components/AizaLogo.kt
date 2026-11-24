package com.aiza.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aiza.app.ui.theme.AizaTheme

/**
 * The AIZA mark: gradient disc with a Bluetooth-style rune and two signal waves.
 * Same geometry as the launcher icon (drawn in the 108-unit adaptive-icon space).
 */
@Composable
fun AizaMark(modifier: Modifier = Modifier, size: Dp = 40.dp, arcsAlpha: Float = 1f) {
    val gradient = AizaTheme.extra.brandGradient
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(Brush.linearGradient(gradient, Offset.Zero, Offset(s, s)))
        val k = 0.75f * s / 68f // content scale, as in ic_splash_mark
        fun p(x: Float, y: Float) = Offset(s / 2 + (x - 54f) * k, s / 2 + (y - 54f) * k)
        val rune = Path().apply {
            listOf(34f to 46f, 56f to 64f, 46f to 74f, 46f to 34f, 56f to 44f, 34f to 62f).forEachIndexed { i, (x, y) ->
                val o = p(x, y)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(rune, Color.White, style = Stroke(width = 5.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fun wave(r: Float, alpha: Float) {
            val c = p(46f, 54f)
            drawArc(
                Color.White.copy(alpha = alpha * arcsAlpha), startAngle = -35f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(c.x - r * k, c.y - r * k), size = Size(2 * r * k, 2 * r * k),
                style = Stroke(width = 5f * k, cap = StrokeCap.Round),
            )
        }
        wave(20f, 1f)
        wave(29f, 0.6f)
    }
}

@Composable
fun AizaWordmark(modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleLarge) {
    Text("AIZA", modifier, style = style.copy(letterSpacing = 0.18.em), color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun AizaLogoLockup(modifier: Modifier = Modifier, markSize: Dp = 34.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AizaMark(size = markSize)
        AizaWordmark()
    }
}
