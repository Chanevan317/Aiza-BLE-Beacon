package com.aiza.app.ui.search

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.ui.aizaViewModel
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.TagAvatar
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.theme.AizaTheme
import kotlinx.coroutines.delay

/** Colour on the cold → hot scale. */
@Composable
private fun heatColor(heat: Float): Color {
    val scale = AizaTheme.extra.heatScale
    val x = heat.coerceIn(0f, 1f) * (scale.size - 1)
    val i = x.toInt().coerceAtMost(scale.size - 2)
    return lerp(scale[i], scale[i + 1], x - i)
}

@Composable
fun SearchScreen(address: String, onClose: () -> Unit) {
    val vm = aizaViewModel(key = "search-$address") { SearchViewModel(it, address) }
    val s by vm.state.collectAsStateWithLifecycle()
    val heat by animateFloatAsState(s.heat, tween(400), label = "heat")
    val color by animateColorAsState(heatColor(heat), tween(400), label = "heat-color")

    Feedback(s, heat)

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent, color.copy(alpha = 0.12f)))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            TopRow(s, onClose, vm::toggleMute)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Radar(s, heat, color)
            }
            DirectionHint(s.wedgeDeg != null, s.proximity == Proximity.VERY_CLOSE)
            VSpace(16.dp)
            HeatBar(heat)
            VSpace(20.dp)
            Actions(s.ringing, vm::toggleRing)
            VSpace(12.dp)
        }
    }
}

@Composable
private fun TopRow(s: SearchState, onClose: () -> Unit, onMute: () -> Unit) {
    // A tint of the text colour stays visible on any heat background, in light and dark.
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClose, colors = colors) { Icon(Icons.Rounded.Close, "Stop search") }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            s.tag?.let { TagAvatar(it.icon, size = 28.dp); HSpace(8.dp) }
            Text("Finding ${s.tag?.name ?: "tag"}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        FilledTonalIconButton(onMute, colors = colors) {
            Icon(
                if (s.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (s.muted) "Unmute sound and vibration" else "Mute sound and vibration",
            )
        }
    }
}

/** Pulse speed, orb colour and wedge all react to the signal (FR-HC-03/04, FR-DIR-04/05). */
@Composable
private fun Radar(s: SearchState, heat: Float, color: Color) {
    val wedgeAlpha by animateFloatAsState(if (s.wedgeDeg != null) 1f else 0f, tween(900), label = "wedge") // fades, never pops
    val wedgeAngle by animateFloatAsState(s.wedgeDeg ?: 0f, tween(600), label = "wedge-angle")
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onBg = MaterialTheme.colorScheme.onBackground
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight, 340.dp)
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(d)) {
                val r = size.minDimension / 2
                // Compass rings.
                drawCircle(outline, r * 0.98f, style = Stroke(1.dp.toPx()))
                drawCircle(outline.copy(alpha = 0.6f), r * 0.72f, style = Stroke(1.dp.toPx()))
                // Forward tick (top of the phone).
                drawLine(onBg.copy(alpha = 0.5f), Offset(center.x, center.y - r), Offset(center.x, center.y - r + 14.dp.toPx()), 3.dp.toPx())
                // 120° wedge, centred on the likely direction (C-4).
                if (wedgeAlpha > 0f) {
                    val start = -90f + wedgeAngle - 60f
                    drawArc(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.05f), color.copy(alpha = 0.45f)), center, r),
                        startAngle = start, sweepAngle = 120f, useCenter = true,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), alpha = wedgeAlpha,
                    )
                    drawArc(
                        color, startAngle = start, sweepAngle = 120f, useCenter = false,
                        topLeft = Offset(center.x - r * 0.98f, center.y - r * 0.98f), size = Size(2 * r * 0.98f, 2 * r * 0.98f),
                        style = Stroke(4.dp.toPx()), alpha = wedgeAlpha,
                    )
                }
            }
            PulseRings(color, Modifier.size(d * 0.7f), rings = 3, periodMs = (2200 - 1700 * heat).toInt(), minRadiusFraction = 0.55f, filled = true)
            Orb(s, color, Modifier.size(d * 0.42f))
        }
    }
}

@Composable
private fun Orb(s: SearchState, color: Color, modifier: Modifier) {
    Surface(modifier, shape = CircleShape, color = Color.Transparent) {
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color))),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${s.proximity.label}, ${s.heading.label}" },
            ) {
                AnimatedContent(
                    s.heading to (s.proximity == Proximity.VERY_CLOSE),
                    transitionSpec = { (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut()) },
                    label = "heading",
                ) { (h, veryClose) ->
                    Text(
                        when {
                            veryClose -> "Almost there"
                            h == Heading.WARMER -> "Warmer ↑"
                            h == Heading.COLDER -> "Colder ↓"
                            else -> "Searching"
                        },
                        style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        softWrap = false,
                        fontSize = if (veryClose) MaterialTheme.typography.titleMedium.fontSize else MaterialTheme.typography.titleLarge.fontSize,
                    )
                }
                Text(s.proximity.label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                s.rssiDbm?.let {
                    VSpace(2.dp)
                    Text("${it.toInt()} dBm", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DirectionHint(wedge: Boolean, veryClose: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(if (veryClose) 2 else if (wedge) 1 else 0, label = "hint") { mode ->
            val w = mode == 1
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (mode) { 2 -> Icons.Rounded.NotificationsActive; 1 -> Icons.Rounded.Explore; else -> Icons.AutoMirrored.Rounded.DirectionsWalk },
                    null, tint = MaterialTheme.colorScheme.primary,
                )
                HSpace(12.dp)
                Column {
                    Text(
                        when (mode) { 2 -> "It's within reach"; 1 -> "Likely this way"; else -> "Keep walking" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        when (mode) {
                            2 -> "Look around you, or ring the tag and listen for it."
                            1 -> "The highlighted area shows where your tag probably is."
                            else -> "Walk around slowly. A direction appears once AIZA is confident."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatBar(heat: Float) {
    val scale = AizaTheme.extra.heatScale
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.horizontalGradient(scale), CircleShape))
            Box(
                Modifier.offset(x = (maxWidth - 20.dp) * heat).size(20.dp)
                    .background(Color.White, CircleShape).padding(4.dp).background(heatColor(heat), CircleShape),
            )
        }
        VSpace(6.dp)
        Row(Modifier.fillMaxWidth()) {
            Text("Cold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Hot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ring stays visible for the whole search (FR-HC-05). */
@Composable
private fun Actions(ringing: Boolean, onRing: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { /* TODO FR-SPN-01 guided spin */ },
            modifier = Modifier.weight(1f).height(58.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.Rounded.Autorenew, null)
            HSpace(6.dp)
            Text("Turn slowly", maxLines = 1)
        }
        Button(
            onClick = onRing,
            modifier = Modifier.weight(1f).height(58.dp),
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = if (ringing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
        ) {
            Icon(Icons.Rounded.NotificationsActive, null)
            HSpace(8.dp)
            Text(if (ringing) "Stop ringing" else "Ring tag", maxLines = 1)
        }
    }
}

/** Beeps and vibration speed up as the tag gets closer (FR-HC-04); mute stops both. */
@Composable
private fun Feedback(s: SearchState, heat: Float) {
    val context = LocalContext.current
    val current by rememberUpdatedState(s)
    val currentHeat by rememberUpdatedState(heat)
    LaunchedEffect(Unit) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
        try {
            while (true) {
                val st = current
                if (st.rssiDbm != null && !st.muted) {
                    if (st.settings.searchVibration) vibrator?.vibrate(VibrationEffect.createOneShot(30, (60 + 160 * currentHeat).toInt()))
                    if (st.settings.searchSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                }
                delay((1400 - 1150 * currentHeat).toLong())
            }
        } finally {
            tone?.release()
        }
    }
}
