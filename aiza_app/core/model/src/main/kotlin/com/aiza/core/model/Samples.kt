package com.aiza.core.model

/**
 * All samples share one monotonic time base in nanoseconds (SRS NFR-DAT-01, A-4).
 * On Android this is SystemClock.elapsedRealtimeNanos(), which ScanResult and SensorEvent both use.
 */
typealias TimestampNanos = Long

sealed interface Sample {
    val timestampNanos: TimestampNanos
}

/** One received advertisement from a tag. */
data class RssiSample(
    override val timestampNanos: TimestampNanos,
    val tagId: String,
    val rssiDbm: Int,
) : Sample

/** Raw accelerometer reading in m/s² (used for step detection, FR-DIR-01). */
data class AccelSample(
    override val timestampNanos: TimestampNanos,
    val x: Float,
    val y: Float,
    val z: Float,
) : Sample

/** Raw gyroscope reading in rad/s. */
data class GyroSample(
    override val timestampNanos: TimestampNanos,
    val x: Float,
    val y: Float,
    val z: Float,
) : Sample

/** Fused heading in degrees clockwise from magnetic north, 0 until 360. */
data class HeadingSample(
    override val timestampNanos: TimestampNanos,
    val headingDeg: Float,
) : Sample
