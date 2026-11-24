package com.aiza.core.direction.pdr

import com.aiza.core.model.AccelSample
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects steps from raw accelerometer data (FR-DIR-01). Works with the phone in any orientation because it
 * uses the magnitude of acceleration:
 *  1. remove gravity with a slow moving average (~1 s),
 *  2. smooth the rest (~80 ms) to remove hand jitter,
 *  3. a step is a peak above [peakThreshold] followed by a fall below zero, at least [minStepNanos] after the last.
 * Stride length uses the Weinberg model: K * (max − min)^¼ of the bounce, clamped to a sensible walking range.
 */
class StepDetector(
    private val peakThreshold: Float = 1.0f,
    private val minStepNanos: Long = 280_000_000L,
    private val weinbergK: Float = 0.45f,
    private val gravityTauS: Float = 1.0f,
    private val smoothTauS: Float = 0.08f,
) {
    private var lastT: Long? = null
    private var gravity = 9.81f
    private var smooth = 0f
    private var peak = 0f
    private var peakT = 0L
    private var valley = 0f
    private var inPeak = false
    private var lastStepT = Long.MIN_VALUE / 2

    /** Feeds one accelerometer sample; returns (timestamp, stride metres) when a step completes, else null. */
    fun add(s: AccelSample): Pair<Long, Float>? {
        val m = sqrt(s.x * s.x + s.y * s.y + s.z * s.z)
        val prev = lastT
        lastT = s.timestampNanos
        if (prev == null) { gravity = m; return null }
        val dt = ((s.timestampNanos - prev) / 1e9f).coerceIn(0.001f, 0.2f)
        gravity += (m - gravity) * (dt / (gravityTauS + dt))
        smooth += ((m - gravity) - smooth) * (dt / (smoothTauS + dt))
        val a = smooth

        if (!inPeak) {
            if (a < valley) valley = a
            if (a > peakThreshold) { inPeak = true; peak = a; peakT = s.timestampNanos }
            return null
        }
        if (a > peak) { peak = a; peakT = s.timestampNanos }
        if (a >= 0f) return null
        // Fell back through zero: the bounce is over.
        inPeak = false
        val bounce = peak - valley
        valley = a
        if (peakT - lastStepT < minStepNanos) return null
        lastStepT = peakT
        val length = (weinbergK * bounce.coerceAtLeast(0f).pow(0.25f)).coerceIn(0.35f, 1.0f)
        return peakT to length
    }
}
