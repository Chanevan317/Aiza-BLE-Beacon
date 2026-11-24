package com.aiza.core.direction

import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.model.NoWedgeReason
import com.aiza.core.model.WedgeDecision
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WedgeEngineTest {
    private fun run(sim: WalkSimulator, engine: WedgeEngine = WedgeEngine()): List<WedgeDecision> =
        sim.samples.mapNotNull { engine.update(it) }

    private fun hit(sim: WalkSimulator, d: WedgeDecision) =
        abs(HeadingFilter.angleDiff(d.centreDeg!!, sim.trueBearingAt(d.timestampNanos).toFloat())) <= 60f

    /** Walking past the tag and turning gives the filter two viewpoints; walking straight at it gives almost none. */
    private fun passingWalk() = WalkSimulator(3.0, 5.0, seed = 3).stand(1.0, 0.0)
        .leg(0.0, 7.0).leg(270.0, 3.0).leg(180.0, 2.0).stand(3.0)

    @Test
    fun noWedgeWhenStandingStill() {
        val sim = WalkSimulator(2.0, 5.0, seed = 1).stand(20.0, 0.0)
        assertTrue(run(sim).none { it.shown })
    }

    @Test
    fun noWedgeBeforeEnoughWalking() {
        val sim = WalkSimulator(2.0, 5.0, seed = 2).stand(1.0, 0.0).leg(0.0, 1.5)
        val d = run(sim)
        assertTrue(d.none { it.shown })
        assertEquals(NoWedgeReason.NOT_ENOUGH_DATA, d.first().reason)
    }

    @Test
    fun walkingPastTagFindsIt() {
        // Walk north past the tag (to its left), turn away west, then south, then stop.
        val sim = passingWalk()
        val shown = run(sim).filter { it.shown }
        assertTrue(shown.isNotEmpty(), "wedge never shown")
        assertTrue(shown.all { hit(sim, it) }, "wedge missed the tag")
    }

    @Test
    fun wedgeWaitsTwoSecondsAboveThreshold() {
        val sim = passingWalk()
        val d = run(sim)
        val firstShown = d.indexOfFirst { it.shown }
        assertTrue(firstShown >= 0, "never shown")
        val t = d[firstShown].timestampNanos
        val before = d.filter { it.timestampNanos in (t - 2_000_000_000L) until t }
        assertTrue(before.all { it.probabilityInside >= 0.9f }, "shown without 2 s above threshold")
    }

    @Test
    fun deterministicReplay() {
        fun once() = run(WalkSimulator(-4.0, 3.0, seed = 9).stand(1.0, 0.0).leg(0.0, 3.0).leg(270.0, 3.0))
        assertEquals(once(), once())
    }

    /**
     * Many random searches in rooms with reflections and body blocking: the wedge, when shown, must contain the tag
     * at least 90% of the time (NFR-ACC-01). Real rooms decide the final answer; this checks the method.
     */
    @Test
    fun hitRateOnRandomSearches() {
        val rnd = Random(42)
        var shown = 0
        var hits = 0
        var decisions = 0
        var searchesWithWedge = 0
        repeat(40) { i ->
            val dist = rnd.nextDouble(2.0, 9.0)
            val bearing = Math.toRadians(rnd.nextDouble(0.0, 360.0))
            var h = rnd.nextDouble(0.0, 360.0)
            val sim = WalkSimulator(dist * sin(bearing), dist * cos(bearing), seed = 100 + i, noiseDb = 5.0, roomDb = 4.0, bodyDb = 5.0)
                .stand(1.0, h)
            repeat(4) { sim.leg(h, rnd.nextDouble(2.5, 4.5)); h = (h + if (rnd.nextBoolean()) 90.0 else -90.0 + 360) % 360 }
            var any = false
            run(sim).forEach { d ->
                decisions++
                if (d.shown) { any = true; shown++; if (hit(sim, d)) hits++ }
            }
            if (any) searchesWithWedge++
        }
        val rate = hits.toDouble() / shown
        println("random searches: wedge in $searchesWithWedge/40, shown ${100 * shown / decisions}% of the time, hit rate ${"%.1f".format(100 * rate)}%")
        assertTrue(searchesWithWedge >= 20, "wedge too rare: $searchesWithWedge/40")
        assertTrue(rate >= 0.9, "hit rate $rate")
    }
}
