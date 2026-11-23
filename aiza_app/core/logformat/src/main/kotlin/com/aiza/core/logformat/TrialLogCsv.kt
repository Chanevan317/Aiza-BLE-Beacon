package com.aiza.core.logformat

import com.aiza.core.model.AccelSample
import com.aiza.core.model.GyroSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Sample

/**
 * CSV schema for trial logs (FR-TRL-03/04). One row per sample:
 * `timestamp_ns,type,tag_id,v1,v2,v3`. Written on the phone, read back by tools:replay.
 * Logs never contain names (NFR-PRV-01).
 */
object TrialLogCsv {
    const val HEADER = "timestamp_ns,type,tag_id,v1,v2,v3"

    fun encode(sample: Sample): String = when (sample) {
        is RssiSample -> "${sample.timestampNanos},rssi,${sample.tagId},${sample.rssiDbm},,"
        is AccelSample -> "${sample.timestampNanos},accel,,${sample.x},${sample.y},${sample.z}"
        is GyroSample -> "${sample.timestampNanos},gyro,,${sample.x},${sample.y},${sample.z}"
        is HeadingSample -> "${sample.timestampNanos},heading,,${sample.headingDeg},,"
    }

    fun decode(line: String): Sample? {
        if (line.isBlank() || line == HEADER) return null
        val c = line.split(',')
        require(c.size == 6) { "Expected 6 columns, got ${c.size}: $line" }
        val t = c[0].toLong()
        return when (c[1]) {
            "rssi" -> RssiSample(t, c[2], c[3].toInt())
            "accel" -> AccelSample(t, c[3].toFloat(), c[4].toFloat(), c[5].toFloat())
            "gyro" -> GyroSample(t, c[3].toFloat(), c[4].toFloat(), c[5].toFloat())
            "heading" -> HeadingSample(t, c[3].toFloat())
            else -> error("Unknown sample type '${c[1]}'")
        }
    }
}
