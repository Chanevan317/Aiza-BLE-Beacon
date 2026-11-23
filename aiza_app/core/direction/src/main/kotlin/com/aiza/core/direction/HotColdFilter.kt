package com.aiza.core.direction

import com.aiza.core.model.HotColdReading
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Trend

/**
 * Median of each fixed RSSI window (default 250 ms), compared with the previous window (FR-HC-03).
 * Pure function of its input so replays are deterministic (NFR-MNT-02).
 */
class HotColdFilter(
    private val windowNanos: Long = 250_000_000L,
    private val trendThresholdDb: Float = 1.0f,
) {
    private val window = mutableListOf<Int>()
    private var windowStart: Long? = null
    private var previousMedian: Float? = null

    /** Feeds one sample; returns a reading when a window closes, otherwise null. */
    fun add(sample: RssiSample): HotColdReading? {
        val start = windowStart ?: sample.timestampNanos.also { windowStart = it }
        if (sample.timestampNanos - start < windowNanos) {
            window += sample.rssiDbm
            return null
        }
        val reading = closeWindow(start + windowNanos)
        windowStart = sample.timestampNanos
        window += sample.rssiDbm
        return reading
    }

    private fun closeWindow(endNanos: Long): HotColdReading? {
        if (window.isEmpty()) return null
        val median = median(window)
        window.clear()
        val prev = previousMedian
        previousMedian = median
        val trend = when {
            prev == null -> Trend.UNCLEAR
            median - prev >= trendThresholdDb -> Trend.HOTTER
            prev - median >= trendThresholdDb -> Trend.COLDER
            else -> Trend.UNCLEAR
        }
        return HotColdReading(endNanos, median, trend)
    }

    internal companion object {
        fun median(values: List<Int>): Float {
            val s = values.sorted()
            val mid = s.size / 2
            return if (s.size % 2 == 1) s[mid].toFloat() else (s[mid - 1] + s[mid]) / 2f
        }
    }
}
