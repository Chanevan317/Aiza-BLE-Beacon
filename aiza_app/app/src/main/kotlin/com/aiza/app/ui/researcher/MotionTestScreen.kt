package com.aiza.app.ui.researcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiza.app.AizaApplication
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.SectionLabel
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.search.compassLabel
import com.aiza.core.direction.pdr.PdrTracker
import com.aiza.core.model.PdrState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Live check of step + heading tracking (FR-DIR-01): walk a known number of steps or a known shape
 * and compare. Uses the same core code as search and offline replay.
 */
@Composable
fun MotionTestScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    var run by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<PdrState?>(null) }
    var path by remember { mutableStateOf(listOf(Offset.Zero)) }
    var lastStride by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(run) {
        val pdr = PdrTracker()
        state = null; path = listOf(Offset.Zero); lastStride = null
        var lastPush = 0L
        container.motionSensors.samples().flowOn(Dispatchers.Default).collect { s ->
            val step = pdr.add(s)
            val p = pdr.state
            if (step != null) { path = path + Offset(p.x, p.y); lastStride = step.lengthM }
            if (step != null || p.timestampNanos - lastPush > 80_000_000L || p.turning != state?.turning) {
                lastPush = p.timestampNanos; state = p
            }
        }
    }

    ResearchScaffold(
        "Step & heading test", onBack,
        actions = {
            FilledTonalIconButton({ run++ }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(Icons.Rounded.RestartAlt, "Reset")
            }
        },
    ) {
        item {
            Text(
                "Hold the phone as you would while searching. Walk 10 steps and check the count, or walk a square and see if the path closes.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            val s = state
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Big("Steps", "${s?.steps ?: 0}", Modifier.weight(1f))
                Big("Walked", "%.1f m".format(s?.distanceM ?: 0f), Modifier.weight(1f))
                Big("Stride", lastStride?.let { "%.2f m".format(it) } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            SectionLabel("Heading")
            AizaCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Compass(state?.headingDeg, Modifier.size(96.dp))
                    Box(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text(state?.headingDeg?.let { compassLabel(it) } ?: "Waiting for compass…", style = MaterialTheme.typography.headlineSmall)
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (state?.turning == true) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            if (state?.turning == true) "Turning" else "Steady",
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        item {
            SectionLabel("Walked path (north up)")
            AizaCard(Modifier.fillMaxWidth()) {
                PathView(path, state?.headingDeg, Modifier.fillMaxWidth().aspectRatio(1f))
                VSpace(6.dp)
                val end = path.last()
                Text(
                    "Start → now: %.1f m (%.1f m E, %.1f m N)".format(kotlin.math.hypot(end.x, end.y), end.x, end.y),
                    Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    AizaCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Compass(headingDeg: Float?, modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val north = MaterialTheme.colorScheme.error
    val needle = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(ring, r, style = Stroke(2.dp.toPx()))
        // The dial turns so N points to real north; the needle is the phone's forward direction.
        rotate(-(headingDeg ?: 0f)) {
            drawCircle(north, 5.dp.toPx(), Offset(center.x, center.y - r + 9.dp.toPx()))
        }
        drawLine(needle, center, Offset(center.x, center.y - r * 0.7f), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(needle, 4.dp.toPx())
    }
}

@Composable
private fun PathView(points: List<Offset>, headingDeg: Float?, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.primary
    val start = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        // Scale so the whole path fits, at least ±3 m; 1 m grid.
        val extent = max(3f, points.maxOf { max(abs(it.x), abs(it.y)) } * 1.15f)
        val k = size.minDimension / 2 / extent
        fun map(p: Offset) = Offset(center.x + p.x * k, center.y - p.y * k)
        var m = -extent.toInt().toFloat()
        while (m <= extent) {
            drawLine(grid.copy(alpha = 0.4f), Offset(center.x + m * k, 0f), Offset(center.x + m * k, size.height), 1f)
            drawLine(grid.copy(alpha = 0.4f), Offset(0f, center.y - m * k), Offset(size.width, center.y - m * k), 1f)
            m += 1f
        }
        if (points.size > 1) {
            val p = Path().apply { moveTo(map(points[0]).x, map(points[0]).y); points.drop(1).forEach { lineTo(map(it).x, map(it).y) } }
            drawPath(p, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.drop(1).forEach { drawCircle(line, 2.5.dp.toPx(), map(it)) }
        }
        drawCircle(start, 6.dp.toPx(), map(points.first()))
        val now = map(points.last())
        drawCircle(line, 7.dp.toPx(), now)
        headingDeg?.let {
            val a = Math.toRadians(it.toDouble())
            val tip = Offset(now.x + (18.dp.toPx() * sin(a)).toFloat(), now.y - (18.dp.toPx() * cos(a)).toFloat())
            drawLine(line, now, tip, 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
