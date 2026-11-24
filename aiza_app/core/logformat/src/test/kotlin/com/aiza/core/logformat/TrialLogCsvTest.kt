package com.aiza.core.logformat

import com.aiza.core.model.AccelSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrialLogCsvTest {
    @Test
    fun samplesRoundTrip() {
        val samples = listOf(
            RssiSample(1, "tagA", -67),
            AccelSample(2, 0.1f, 9.8f, -0.2f),
            HeadingSample(3, 271.5f),
        )
        samples.forEach { assertEquals(it, TrialLogCsv.decode(TrialLogCsv.encode(it))) }
    }

    @Test
    fun eventsAndWedgesRoundTrip() {
        val rows = listOf(
            EventRow(10, "checkpoint", "C2"),
            EventRow(11, "found"),
            WedgeRow(12, 45.5f, 0.93f),
            WedgeRow(13, null, 0.4f),
        )
        rows.forEach { assertEquals(it, TrialLogCsv.decodeRow(TrialLogCsv.encode(it))) }
    }

    @Test
    fun commasInLabelsAreRemoved() {
        val row = TrialLogCsv.decodeRow(TrialLogCsv.encode(EventRow(1, "note", "a,b"))) as EventRow
        assertEquals("a b", row.label)
    }

    @Test
    fun decodeSkipsNonSamples() {
        assertNull(TrialLogCsv.decode(TrialLogCsv.encode(EventRow(1, "found"))))
    }
}
