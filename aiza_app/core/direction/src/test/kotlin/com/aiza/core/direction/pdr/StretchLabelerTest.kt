package com.aiza.core.direction.pdr

import com.aiza.core.model.HotColdReading
import com.aiza.core.model.Step
import com.aiza.core.model.Trend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StretchLabelerTest {
    private val s = 1_000_000_000L
    private fun r(t: Long, dbm: Float) = HotColdReading(t, dbm, Trend.UNCLEAR)

    /** Walks [n] steps (0.5 s apart, 0.7 m) on [heading] while the signal changes by [slope] dB per step. */
    private fun walk(l: StretchLabeler, t0: Long, n: Int, heading: Float, rssi0: Float, slope: Float): Long {
        var t = t0
        repeat(n) { i ->
            l.step(Step(t, 0.7f, heading), turning = false)
            l.reading(r(t + s / 4, rssi0 + slope * i), turning = false)
            t += s / 2
        }
        return t
    }

    @Test
    fun walkingTowardsTagIsHotter() {
        val l = StretchLabeler()
        walk(l, 0, 10, 90f, -80f, 1f)
        val st = assertNotNull(l.finish())
        assertEquals(Trend.HOTTER, st.label)
        assertEquals(10, st.steps)
        assertEquals(90f, st.headingDeg, 0.5f)
    }

    @Test
    fun walkingAwayIsColder() {
        val l = StretchLabeler()
        walk(l, 0, 10, 0f, -60f, -1f)
        assertEquals(Trend.COLDER, l.finish()!!.label)
    }

    @Test
    fun flatSignalOrShortWalkIsUnclear() {
        val l = StretchLabeler()
        walk(l, 0, 10, 0f, -70f, 0f)
        assertEquals(Trend.UNCLEAR, l.finish()!!.label)
        walk(l, 100 * s, 1, 0f, -70f, 10f)
        assertEquals(Trend.UNCLEAR, l.finish()!!.label)
    }

    @Test
    fun turnSplitsStretchesAndDropsReadings() {
        val l = StretchLabeler()
        val t = walk(l, 0, 8, 0f, -80f, 1f)
        val closed = l.reading(r(t, -40f), turning = true) // a wild reading mid-turn must not count
        assertEquals(Trend.HOTTER, assertNotNull(closed).label)
        walk(l, t + s, 8, 180f, -60f, -1f)
        assertEquals(Trend.COLDER, l.finish()!!.label)
        assertEquals(2, l.stretches.size)
    }

    @Test
    fun veeringStartsNewStretch() {
        val l = StretchLabeler()
        val t = walk(l, 0, 5, 0f, -70f, 0f)
        assertNull(l.step(Step(t, 0.7f, 20f), false)) // small drift stays in the stretch
        assertNotNull(l.step(Step(t + s / 2, 0.7f, 90f), false)) // big change closes it
    }
}
