package com.aiza.core.direction.pdr

import com.aiza.core.model.AccelSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.PdrState
import com.aiza.core.model.Sample
import com.aiza.core.model.Step
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pedestrian dead reckoning (FR-DIR-01): each detected step moves the position by its stride length in the
 * current heading. Pure function of the input stream, so replaying a log gives identical output (NFR-MNT-02).
 */
class PdrTracker(
    private val steps: StepDetector = StepDetector(),
    private val heading: HeadingFilter = HeadingFilter(),
) {
    private var x = 0f
    private var y = 0f
    private var count = 0
    private var distance = 0f
    private var lastT = 0L

    /** Every step taken so far, in order. */
    val path = mutableListOf<Step>()

    /** Feeds any sample (non-motion samples are ignored). Returns the new step if one was detected. */
    fun add(sample: Sample): Step? {
        lastT = maxOf(lastT, sample.timestampNanos)
        when (sample) {
            is HeadingSample -> heading.add(sample)
            is AccelSample -> {
                val (t, length) = steps.add(sample) ?: return null
                val h = heading.headingDeg ?: return null // no heading yet: count nothing rather than guess
                val rad = h * PI / 180
                x += (length * sin(rad)).toFloat()
                y += (length * cos(rad)).toFloat()
                count++
                distance += length
                return Step(t, length, h).also { path += it }
            }
            else -> Unit
        }
        return null
    }

    val state: PdrState
        get() = PdrState(lastT, x, y, heading.headingDeg, count, distance, heading.turning)
}
