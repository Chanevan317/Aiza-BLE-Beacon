package com.aiza.core.direction.pdr

import com.aiza.core.model.HotColdReading
import com.aiza.core.model.Step
import com.aiza.core.model.Trend
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One straight stretch of walking and whether the signal rose or fell along it (FR-DIR-02). */
data class Stretch(
    val startNanos: Long,
    val endNanos: Long,
    /** Mean walking direction, degrees from north. */
    val headingDeg: Float,
    val lengthM: Float,
    val steps: Int,
    /** Signal at the end minus signal at the start; null when there were too few readings. */
    val rssiDeltaDb: Float?,
    val label: Trend,
)

/**
 * Splits the walk into straight stretches and labels each one hotter, colder or unclear (FR-DIR-02).
 * A stretch ends when the user turns, veers more than [maxVeerDeg] from its direction, or stops for [idleNanos].
 * Signal readings taken while turning are ignored. Start/end signal = median of readings in the first/last [edgeNanos].
 */
class StretchLabeler(
    private val minLengthM: Float = 1.0f,
    private val thresholdDb: Float = 3f,
    private val maxVeerDeg: Float = 30f,
    private val idleNanos: Long = 2_000_000_000L,
    private val edgeNanos: Long = 1_000_000_000L,
) {
    private class Open(val firstStep: Step) {
        val steps = mutableListOf(firstStep)
        val readings = mutableListOf<HotColdReading>()
        val lastStepNanos get() = steps.last().timestampNanos
    }

    private var open: Open? = null
    val stretches = mutableListOf<Stretch>()

    /** Feeds a detected step; returns a stretch if this step closed the previous one. */
    fun step(step: Step, turning: Boolean): Stretch? {
        val cur = open
        if (turning) return close()
        if (cur == null) { open = Open(step); return null }
        val veer = abs(HeadingFilter.angleDiff(step.headingDeg, cur.firstStep.headingDeg))
        if (step.timestampNanos - cur.lastStepNanos > idleNanos || veer > maxVeerDeg) {
            val done = close()
            open = Open(step)
            return done
        }
        cur.steps += step
        return null
    }

    /** Feeds a filtered signal reading; readings while turning are dropped and end the stretch. */
    fun reading(r: HotColdReading, turning: Boolean): Stretch? {
        val cur = open ?: return null
        if (turning || r.timestampNanos - cur.lastStepNanos > idleNanos) return close()
        cur.readings += r
        return null
    }

    /** Closes whatever is open (end of the walk). */
    fun finish(): Stretch? = close()

    private fun close(): Stretch? {
        val cur = open ?: return null
        open = null
        val start = cur.steps.first().timestampNanos
        val end = maxOf(cur.lastStepNanos, cur.readings.lastOrNull()?.timestampNanos ?: 0)
        val length = cur.steps.sumOf { it.lengthM.toDouble() }.toFloat()
        val head = cur.readings.filter { it.timestampNanos - start <= edgeNanos }
        val tail = cur.readings.filter { end - it.timestampNanos <= edgeNanos }
        val delta = if (head.isNotEmpty() && tail.isNotEmpty() && cur.readings.size >= 4)
            median(tail) - median(head) else null
        val label = when {
            delta == null || length < minLengthM -> Trend.UNCLEAR
            delta >= thresholdDb -> Trend.HOTTER
            delta <= -thresholdDb -> Trend.COLDER
            else -> Trend.UNCLEAR
        }
        return Stretch(start, end, meanHeading(cur.steps), length, cur.steps.size, delta, label).also { stretches += it }
    }

    private fun median(rs: List<HotColdReading>): Float {
        val v = rs.map { it.filteredRssiDbm }.sorted()
        return if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2f
    }

    private fun meanHeading(steps: List<Step>): Float {
        val s = steps.sumOf { sin(it.headingDeg * PI / 180) }
        val c = steps.sumOf { cos(it.headingDeg * PI / 180) }
        return ((atan2(s, c) * 180 / PI + 360) % 360).toFloat()
    }
}
