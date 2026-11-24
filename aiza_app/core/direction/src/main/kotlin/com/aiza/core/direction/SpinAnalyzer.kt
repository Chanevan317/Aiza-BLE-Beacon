package com.aiza.core.direction

import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.model.WedgeDecision
import kotlin.math.abs
import kotlin.math.cos

/** Outcome of a guided spin (FR-SPN-01). */
data class SpinResult(
    /** Compass bearing where the signal was strongest, i.e. probably towards the tag. */
    val bearingDeg: Float,
    /** Strongest minus weakest smoothed signal, dB. Small = no clear direction. */
    val contrastDb: Float,
    /** Share of the 72 bins that got readings. */
    val coverage: Float,
    /** Smoothed mean signal per 5° bin (index 0 = 0–5° from north); NaN where empty. */
    val profile: List<Float>,
) {
    /** Clear enough to use as a starting estimate. */
    val clear: Boolean get() = coverage >= SpinAnalyzer.MIN_COVERAGE && contrastDb >= SpinAnalyzer.MIN_CONTRAST_DB

    /** Width of the "start here" area shown after the spin: fixed 120°, the same as the search wedge (C-4). */
    val areaWidthDeg: Float get() = WedgeDecision.WEDGE_WIDTH_DEG
}

/**
 * Records signal strength against compass heading in 5° bins while the user turns on the spot (FR-SPN-01).
 * The user's body blocks 2.4 GHz, so the signal is strongest when facing the tag (SRS App. B, Kandula et al.).
 * Raw samples are used (not 250 ms medians) to fill the bins quickly.
 */
class SpinAnalyzer {
    private val sum = DoubleArray(BINS)
    private val count = IntArray(BINS)
    private var lastHeading: Float? = null
    private var lastT = 0L
    private var rotated = 0f

    /** Degrees per second over the latest heading update; used to say "slower". */
    var speedDegPerS = 0f; private set

    val coverage: Float get() = count.count { it > 0 }.toFloat() / BINS
    /** Total turned so far, degrees (either direction). */
    val rotatedDeg: Float get() = rotated
    /** Done once almost every bin has readings or the user has turned a full circle and a bit. */
    val complete: Boolean get() = coverage >= 0.95f || (rotated >= 380f && coverage >= MIN_COVERAGE)

    /** Mean raw signal per bin, NaN where empty (for drawing the ring as it fills). */
    fun means(): FloatArray = FloatArray(BINS) { if (count[it] == 0) Float.NaN else (sum[it] / count[it]).toFloat() }

    fun heading(t: Long, headingDeg: Float) {
        lastHeading?.let { prev ->
            val d = abs(HeadingFilter.angleDiff(headingDeg, prev))
            rotated += d
            val dt = (t - lastT) / 1e9f
            if (dt > 0f) speedDegPerS = speedDegPerS * 0.8f + (d / dt) * 0.2f
        }
        lastHeading = headingDeg; lastT = t
    }

    fun rssi(rssiDbm: Int) {
        val h = lastHeading ?: return
        val b = ((h % 360f + 360f) % 360f / BIN_DEG).toInt() % BINS
        sum[b] += rssiDbm.toDouble(); count[b]++
    }

    fun result(): SpinResult {
        val m = means()
        // Body blocking is broad, so smooth over ±30° with cosine weights; empty bins are skipped.
        val smooth = FloatArray(BINS) { i ->
            var s = 0.0; var w = 0.0
            for (k in -6..6) {
                val v = m[(i + k + BINS) % BINS]
                if (v.isNaN()) continue
                val wk = cos(Math.toRadians(k * BIN_DEG.toDouble()))
                s += v * wk; w += wk
            }
            if (w == 0.0) Float.NaN else (s / w).toFloat()
        }
        val valid = smooth.indices.filter { !smooth[it].isNaN() }
        if (valid.isEmpty()) return SpinResult(0f, 0f, coverage, smooth.toList())
        val max = valid.maxOf { smooth[it] }
        val contrast = max - valid.minOf { smooth[it] }
        // Centre of everything within 1.5 dB of the peak (circular mean), so a flat top gives its middle.
        var sx = 0.0; var sy = 0.0
        valid.filter { smooth[it] >= max - 1.5f }.forEach {
            val a = Math.toRadians((it * BIN_DEG + BIN_DEG / 2f).toDouble())
            sx += kotlin.math.sin(a); sy += cos(a)
        }
        val bearing = ((Math.toDegrees(kotlin.math.atan2(sx, sy)) + 360.0) % 360.0).toFloat()
        return SpinResult(bearing, contrast, coverage, smooth.toList())
    }

    companion object {
        const val BINS = 72
        const val BIN_DEG = 5f
        const val MIN_COVERAGE = 0.85f
        const val MIN_CONTRAST_DB = 2f
    }
}
