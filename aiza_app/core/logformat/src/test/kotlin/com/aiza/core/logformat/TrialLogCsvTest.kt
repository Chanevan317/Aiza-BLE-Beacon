package com.aiza.core.logformat

import com.aiza.core.model.AccelSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import kotlin.test.Test
import kotlin.test.assertEquals

class TrialLogCsvTest {
    @Test
    fun roundTrip() {
        val samples = listOf(
            RssiSample(1, "tagA", -67),
            AccelSample(2, 0.1f, 9.8f, -0.2f),
            HeadingSample(3, 271.5f),
        )
        samples.forEach { assertEquals(it, TrialLogCsv.decode(TrialLogCsv.encode(it))) }
    }
}
