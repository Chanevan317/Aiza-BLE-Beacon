package com.aiza.core.model

/** Hot/cold meter output (FR-HC-03). */
data class HotColdReading(
    val timestampNanos: TimestampNanos,
    /** Median RSSI of the latest window, in dBm. */
    val filteredRssiDbm: Float,
    val trend: Trend,
)

enum class Trend { HOTTER, COLDER, UNCLEAR }

/**
 * Direction wedge decision (FR-DIR-03..06). The wedge is always [WEDGE_WIDTH_DEG] wide (C-4).
 */
data class WedgeDecision(
    val timestampNanos: TimestampNanos,
    /** Centre of the wedge in degrees clockwise from north; null when no wedge is shown. */
    val centreDeg: Float?,
    /** Probability that the tag lies inside the wedge. */
    val probabilityInside: Float,
    val reason: NoWedgeReason? = null,
) {
    val shown: Boolean get() = centreDeg != null

    companion object {
        const val WEDGE_WIDTH_DEG = 120f
    }
}

enum class NoWedgeReason { LOW_CONFIDENCE, CONTRADICTS_HOT_COLD, MOVED_TOO_FAR, TAG_VERY_CLOSE, NOT_ENOUGH_DATA }

/** One detected step (FR-DIR-01). */
data class Step(
    val timestampNanos: TimestampNanos,
    /** Estimated stride length in metres. */
    val lengthM: Float,
    /** Smoothed heading at the moment of the step, degrees from north. */
    val headingDeg: Float,
)

/**
 * Pedestrian dead reckoning output: where the phone has walked relative to where tracking started.
 * x = metres east, y = metres north.
 */
data class PdrState(
    val timestampNanos: TimestampNanos,
    val x: Float,
    val y: Float,
    val headingDeg: Float?,
    val steps: Int,
    val distanceM: Float,
    /** True while the user is turning; readings taken now should be ignored (FR-DIR-02). */
    val turning: Boolean,
)
