package com.aiza.core.direction.pdr

import com.aiza.core.model.HeadingSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooths the fused compass heading (rotation vector: gyroscope + magnetometer + accelerometer) and
 * reports when the user is turning (FR-DIR-01/02). Averaging is done on the unit circle so 359° and 1°
 * average to 0°, not 180°.
 */
class HeadingFilter(
    private val tauS: Float = 0.3f,
    private val turnRateDegPerS: Float = 35f,
    private val turnWindowNanos: Long = 500_000_000L,
) {
    private var c = 0.0
    private var s = 0.0
    private var lastT: Long? = null
    private val history = ArrayDeque<Pair<Long, Float>>()

    var headingDeg: Float? = null
        private set
    var turning: Boolean = false
        private set

    fun add(sample: HeadingSample): Float {
        val rad = sample.headingDeg * PI / 180
        val prev = lastT
        lastT = sample.timestampNanos
        if (prev == null) {
            c = cos(rad); s = sin(rad)
        } else {
            val dt = ((sample.timestampNanos - prev) / 1e9).coerceIn(0.001, 0.5)
            val k = dt / (tauS + dt)
            c += (cos(rad) - c) * k
            s += (sin(rad) - s) * k
        }
        val h = ((atan2(s, c) * 180 / PI + 360) % 360).toFloat()
        headingDeg = h

        history.addLast(sample.timestampNanos to h)
        while (history.size > 1 && sample.timestampNanos - history.first().first > turnWindowNanos) history.removeFirst()
        val (t0, h0) = history.first()
        val span = (sample.timestampNanos - t0) / 1e9f
        turning = span > 0.1f && abs(angleDiff(h, h0)) / span > turnRateDegPerS
        return h
    }

    companion object {
        /** Signed smallest difference a − b in degrees, in (−180, 180]. */
        fun angleDiff(a: Float, b: Float): Float {
            var d = (a - b) % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}
