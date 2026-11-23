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
