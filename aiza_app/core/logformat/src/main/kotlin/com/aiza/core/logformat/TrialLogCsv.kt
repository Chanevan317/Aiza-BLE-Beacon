package com.aiza.core.logformat

import com.aiza.core.model.AccelSample
import com.aiza.core.model.GyroSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Sample

/** One row of a trial log: a sensor/radio sample, a user/researcher event, or a wedge decision. */
sealed interface LogRow {
    val timestampNanos: Long
}

data class SampleRow(val sample: Sample) : LogRow {
    override val timestampNanos: Long get() = sample.timestampNanos
}

/** E.g. name="checkpoint" label="C2", name="found", name="ring" label="on". */
data class EventRow(override val timestampNanos: Long, val name: String, val label: String = "") : LogRow

/** What the wedge showed: centre in degrees (null = hidden) and probability inside (FR-DIR-03/05). */
data class WedgeRow(override val timestampNanos: Long, val centreDeg: Float?, val probability: Float) : LogRow

/**
 * CSV schema for trial logs (FR-TRL-03/04). One row per sample or event:
 * `timestamp_ns,type,tag_id,v1,v2,v3`, all timestamps on one clock (elapsedRealtimeNanos, NFR-DAT-01).
 * Written on the phone, read back by tools:replay. Logs never contain names (NFR-PRV-01).
 */
object TrialLogCsv {
    const val HEADER = "timestamp_ns,type,tag_id,v1,v2,v3"

    fun encode(sample: Sample): String = when (sample) {
        is RssiSample -> "${sample.timestampNanos},rssi,${sample.tagId},${sample.rssiDbm},,"
        is AccelSample -> "${sample.timestampNanos},accel,,${sample.x},${sample.y},${sample.z}"
        is GyroSample -> "${sample.timestampNanos},gyro,,${sample.x},${sample.y},${sample.z}"
        is HeadingSample -> "${sample.timestampNanos},heading,,${sample.headingDeg},,"
    }

    fun encode(row: LogRow): String = when (row) {
        is SampleRow -> encode(row.sample)
        is EventRow -> "${row.timestampNanos},event,,${clean(row.name)},${clean(row.label)},"
        is WedgeRow -> "${row.timestampNanos},wedge,,${row.centreDeg ?: ""},${row.probability},"
    }

    /** Parses any row; null for header/blank lines. */
    fun decodeRow(line: String): LogRow? {
        if (line.isBlank() || line == HEADER) return null
        val c = line.split(',')
        require(c.size == 6) { "Expected 6 columns, got ${c.size}: $line" }
        val t = c[0].toLong()
        return when (c[1]) {
            "rssi" -> SampleRow(RssiSample(t, c[2], c[3].toInt()))
            "accel" -> SampleRow(AccelSample(t, c[3].toFloat(), c[4].toFloat(), c[5].toFloat()))
            "gyro" -> SampleRow(GyroSample(t, c[3].toFloat(), c[4].toFloat(), c[5].toFloat()))
            "heading" -> SampleRow(HeadingSample(t, c[3].toFloat()))
            "event" -> EventRow(t, c[3], c[4])
            "wedge" -> WedgeRow(t, c[3].takeIf { it.isNotEmpty() }?.toFloat(), c[4].toFloat())
            else -> error("Unknown row type '${c[1]}'")
        }
    }

    /** Samples only (events and wedge rows are skipped). */
    fun decode(line: String): Sample? = (decodeRow(line) as? SampleRow)?.sample

    private fun clean(s: String) = s.replace(',', ' ').replace('\n', ' ')
}
