package com.aiza.app.ui.ring

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiza.app.ring.PhoneRing
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.TagAvatar
import com.aiza.app.ui.theme.AizaTheme
import kotlinx.coroutines.delay

/** Full-screen "Keys is ringing your phone" with a big Stop button (FR-RNG-02). Covers every other screen. */
@Composable
fun PhoneRingScreen(ring: PhoneRing, onStop: () -> Unit, timeoutMs: Long = 30_000, nowMs: () -> Long = System::currentTimeMillis) {
    BackHandler(onBack = onStop)
    var now by remember { mutableLongStateOf(nowMs()) }
    LaunchedEffect(ring) { while (true) { now = nowMs(); delay(250) } }
    val left = ((timeoutMs - (now - ring.startedAtMs)) / 1000).coerceIn(0, timeoutMs / 1000)

    val wobble by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "scale",
    )
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(AizaTheme.extra.brandGradient))
            // Swallow touches so nothing underneath reacts.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Found your phone?",
                style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 24.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    PulseRings(Color.White, Modifier.fillMaxSize(), rings = 3, periodMs = 1800, minRadiusFraction = 0.32f, filled = true)
                    Box(Modifier.scale(wobble).background(Color.White.copy(alpha = 0.18f), CircleShape).padding(14.dp)) {
                        TagAvatar(ring.icon, size = 84.dp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "${ring.tagName} is ringing your phone",
                    style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "The button on your tag was pressed. Press it again or tap Stop.",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.Close, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Stop", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Text("Stops by itself in $left s", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
