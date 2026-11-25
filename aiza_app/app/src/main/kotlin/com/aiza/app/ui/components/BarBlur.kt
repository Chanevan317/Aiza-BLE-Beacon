package com.aiza.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Frosted top bar: content scrolling under the status bar and top bar is blurred and tinted with the
 * background, strongest at the top and fading out towards the bar's bottom edge. Pair with
 * `Modifier.hazeSource(state)` on the scrolling content, and give the bar a transparent container.
 */
@Composable
fun Modifier.topBarBlur(state: HazeState): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val style = remember(bg) {
        HazeBlurStyle {
            blurRadius(20.dp)
            backgroundColor(bg)
            colorEffects(listOf(HazeColorEffect.tint(Brush.verticalGradient(listOf(bg.copy(alpha = 0.85f), bg.copy(alpha = 0.6f), bg.copy(alpha = 0.15f))))))
            progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
        }
    }
    return hazeBlur(HazeInput.Sources(state), style)
}

/** Fades content out over [height] at its top edge, for lists that scroll under a fixed header. */
fun Modifier.fadeTopEdge(height: Dp = 20.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = height.toPx()),
            blendMode = BlendMode.DstIn,
        )
    }
