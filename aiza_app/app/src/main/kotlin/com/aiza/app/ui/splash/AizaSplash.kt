package com.aiza.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aiza.app.ui.components.AizaMark
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.VSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded intro that picks up where the system splash leaves off: the mark starts where the system
 * icon was, pings once, then the wordmark and tagline rise in.
 */
@Composable
fun AizaSplash(onFinished: () -> Unit) {
    val markScale = remember { Animatable(1.45f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(24f) }
    val ringsAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { ringsAlpha.animateTo(1f, tween(500)) }
        markScale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        launch { textShift.animateTo(0f, tween(550, easing = FastOutSlowInEasing)) }
        textAlpha.animateTo(1f, tween(550))
        delay(650)
        onFinished()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                PulseRings(
                    MaterialTheme.colorScheme.primary,
                    Modifier.size(260.dp).graphicsLayer { alpha = ringsAlpha.value },
                    periodMs = 1800,
                    minRadiusFraction = 0.4f,
                )
                AizaMark(Modifier.graphicsLayer { scaleX = markScale.value; scaleY = markScale.value }, size = 104.dp)
            }
            Column(
                Modifier.graphicsLayer { alpha = textAlpha.value; translationY = textShift.value * density },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "AIZA",
                    style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 0.22.em),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                VSpace(6.dp)
                Text(
                    "Find what matters.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "R&D prototype",
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                .graphicsLayer { alpha = textAlpha.value * 0.7f },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
