package com.aiza.app

import com.aiza.app.separation.LeftBehindReason
import com.aiza.app.separation.SeparationEvent
import com.aiza.app.separation.SeparationMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeparationMonitorTest {
    /** Runs 1 Hz readings from [rssi] (null = link down) and returns the events with their second. */
    private fun run(m: SeparationMonitor, rssi: List<Int?>): List<Pair<Int, SeparationEvent>> {
        val out = mutableListOf<Pair<Int, SeparationEvent>>()
        var up = false
        rssi.forEachIndexed { i, r ->
            val t = i * 1_000L
            if (r == null) { if (up) m.onDisconnected(t); up = false }
            else { if (!up) m.onConnected(t); up = true; m.onRssi(t, r) }
            m.tick(t)?.let { out += i to it }
        }
        return out
    }

    @Test fun nearbyStaysQuiet() {
        assertEquals(emptyList<Any>(), run(SeparationMonitor(-85), List(60) { -60 }))
    }

    @Test fun neverConnectedStaysQuiet() {
        assertEquals(emptyList<Any>(), run(SeparationMonitor(-85), List(60) { null }))
    }

    @Test fun linkLossAlertsAfterGraceAndWellWithin30s() {
        val events = run(SeparationMonitor(-85), List(10) { -60 } + List(30) { null })
        assertEquals(listOf(15 to SeparationEvent.LeftBehind(LeftBehindReason.LINK_LOST)), events)
    }

    @Test fun shortBlipIsIgnored() {
        assertEquals(emptyList<Any>(), run(SeparationMonitor(-85), List(10) { -60 } + List(3) { null } + List(20) { -60 }))
    }

    @Test fun weakSignalAlertsOnce() {
        // Fading 2 dB/s from -60: crosses -85 after about 12 s; the 5 s mean follows a few seconds later.
        val fade = List(40) { -60 - 2 * it }.map { if (it > -95) it else null }
        val events = run(SeparationMonitor(-85), List(10) { -60 } + fade)
        assertEquals(1, events.size)
        val (second, e) = events.single()
        assertEquals(SeparationEvent.LeftBehind(LeftBehindReason.WEAK_SIGNAL), e)
        assertTrue("alert at $second s", second - 10 in 12..20)
    }

    @Test fun backInRangeNeedsMarginSoItDoesNotFlap() {
        val m = SeparationMonitor(-85)
        val events = run(m, List(10) { -60 } + List(10) { -88 } + List(10) { -82 } + List(10) { -65 })
        assertEquals(SeparationEvent.LeftBehind(LeftBehindReason.WEAK_SIGNAL), events[0].second)
        // -82 is above the threshold but within the margin: still left behind.
        assertEquals(2, events.size)
        assertEquals(SeparationEvent.BackInRange, events[1].second)
        assertTrue(events[1].first >= 30)
        assertNull(m.alerted)
    }
}
