package com.aiza.core.direction

import com.aiza.core.model.RssiSample
import com.aiza.core.model.Trend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HotColdFilterTest {
    private fun s(ms: Long, rssi: Int) = RssiSample(ms * 1_000_000, "tag", rssi)

    @Test
    fun medianOfOddAndEven() {
        assertEquals(-60f, HotColdFilter.median(listOf(-70, -60, -50)))
        assertEquals(-55f, HotColdFilter.median(listOf(-60, -50)))
    }

    @Test
    fun reportsHotterWhenSignalRises() {
        val f = HotColdFilter()
        assertNull(f.add(s(0, -80)))
        assertNull(f.add(s(100, -80)))
        val first = f.add(s(260, -70))!!
        assertEquals(Trend.UNCLEAR, first.trend)
        val second = f.add(s(520, -60))!!
        assertEquals(Trend.HOTTER, second.trend)
        assertEquals(-70f, second.filteredRssiDbm)
    }
}
