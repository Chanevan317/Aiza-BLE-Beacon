package com.aiza.core.direction

import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpinTest {
    /** Runs a simulated spin through the engine and returns its result. */
    private fun spin(tagX: Double, tagY: Double, bodyDb: Double, seed: Int = 1): Pair<WedgeEngine, SpinResult> {
        val sim = WalkSimulator(tagX, tagY, seed = seed, noiseDb = 4.0, bodyDb = bodyDb).stand(1.0, 0.0)
        val e = WedgeEngine()
        val before = sim.samples.size
        sim.spin()
        sim.samples.take(before).forEach { e.update(it) }
        e.startSpin()
        sim.samples.drop(before).forEach { e.update(it) }
        return e to assertNotNull(e.finishSpin())
    }

    @Test
    fun spinPointsAtTag() {
        // Tag to the east-north-east; body blocking makes the signal weakest facing away.
        val (_, r) = spin(5.0, 2.0, bodyDb = 6.0)
        val truth = Math.toDegrees(kotlin.math.atan2(5.0, 2.0)).toFloat()
        assertTrue(r.clear, "contrast ${r.contrastDb}, coverage ${r.coverage}")
        assertTrue(abs(HeadingFilter.angleDiff(r.bearingDeg, truth)) <= 45f, "spin ${r.bearingDeg} vs $truth")
    }

    @Test
    fun noBodyBlockingMeansNoClearDirection() {
        val (_, r) = spin(5.0, 2.0, bodyDb = 0.0)
        assertFalse(r.clear, "contrast ${r.contrastDb}")
    }

    @Test
    fun spinAloneNeverShowsWedge() {
        // FR-SPN-02: a starting estimate only. Stand still after the spin: no walking evidence, no wedge.
        val sim = WalkSimulator(5.0, 2.0, seed = 4, bodyDb = 8.0).stand(1.0, 0.0)
        val e = WedgeEngine()
        sim.samples.forEach { e.update(it) }
        val n = sim.samples.size
        e.startSpin(); sim.spin(); sim.samples.drop(n).forEach { e.update(it) }
        assertTrue(e.finishSpin()!!.clear)
        val m = sim.samples.size
        sim.stand(10.0)
        assertTrue(sim.samples.drop(m).mapNotNull { e.update(it) }.none { it.shown })
    }

    @Test
    fun walkingEvidenceOverridesWrongSpin() {
        // A spin that points the wrong way (fake readings: strongest facing south) must not stop walking from winning.
        val sim = WalkSimulator(3.0, 5.0, seed = 3).stand(1.0, 0.0)
        val e = WedgeEngine()
        sim.samples.forEach { e.update(it) }
        e.startSpin()
        for (i in 0 until 400) {
            val t = sim.samples.last().timestampNanos + (i + 1) * 20_000_000L
            val h = (i * 1.0f) % 360f
            e.update(HeadingSample(t, h))
            e.update(RssiSample(t, "sim", if (abs(HeadingFilter.angleDiff(h, 180f)) < 60f) -60 else -72))
        }
        val r = e.finishSpin()!!
        assertTrue(r.clear && abs(HeadingFilter.angleDiff(r.bearingDeg, 180f)) < 30f, "fake spin gave $r")
        // Same passing walk as WedgeEngineTest, timestamps continuing after the fake spin.
        val offset = sim.samples.last().timestampNanos + 400 * 20_000_000L
        val walk = WalkSimulator(3.0, 5.0, seed = 3).stand(1.0, 0.0).leg(0.0, 7.0).leg(270.0, 3.0).leg(180.0, 2.0).stand(3.0)
        val shown = walk.samples.mapNotNull { s -> e.update(shift(s, offset))?.takeIf { it.shown }?.let { it to s.timestampNanos } }
        assertTrue(shown.isNotEmpty(), "walking evidence never showed a wedge after a wrong spin")
        assertTrue(shown.all { (d, t) -> abs(HeadingFilter.angleDiff(d.centreDeg!!, walk.trueBearingAt(t).toFloat())) <= 60f })
    }

    private fun shift(s: com.aiza.core.model.Sample, by: Long) = when (s) {
        is RssiSample -> s.copy(timestampNanos = s.timestampNanos + by)
        is HeadingSample -> s.copy(timestampNanos = s.timestampNanos + by)
        is com.aiza.core.model.AccelSample -> s.copy(timestampNanos = s.timestampNanos + by)
        is com.aiza.core.model.GyroSample -> s.copy(timestampNanos = s.timestampNanos + by)
    }

    @Test
    fun colderStretchIntoAreaClearsIt() {
        // Fake spin says north; then walk north 5 m while the signal falls: the area must go.
        val e = WedgeEngine()
        var t = 0L
        e.startSpin()
        for (i in 0 until 400) {
            t += 20_000_000L
            val h = (i * 1.0f) % 360f
            e.update(HeadingSample(t, h))
            e.update(RssiSample(t, "x", if (abs(HeadingFilter.angleDiff(h, 0f)) < 60f) -60 else -72))
        }
        assertTrue(e.finishSpin()!!.clear)
        assertTrue(e.spinAreaDeg != null)
        val sim = WalkSimulator(0.0, -2.0, seed = 5, noiseDb = 1.0) // tag is actually just south
        sim.stand(1.0, 0.0).leg(0.0, 2.5).leg(90.0, 1.0)
        sim.samples.forEach { e.update(shift(it, t)) }
        kotlin.test.assertEquals(WedgeEngine.AreaEnd.COLDER, e.spinAreaEnd)
        kotlin.test.assertNull(e.spinAreaDeg)
    }

    @Test
    fun agreementLoggedWhenWedgeAppears() {
        // Real spin then the passing walk: the first wedge after it records the angle between them.
        val sim = WalkSimulator(3.0, 5.0, seed = 3, bodyDb = 6.0).stand(1.0, 0.0)
        val e = WedgeEngine()
        sim.samples.forEach { e.update(it) }
        val n = sim.samples.size
        e.startSpin(); sim.spin(); sim.samples.drop(n).forEach { e.update(it) }
        e.finishSpin()
        val m = sim.samples.size
        sim.leg(0.0, 7.0).leg(270.0, 3.0).leg(180.0, 2.0).stand(3.0)
        sim.samples.drop(m).forEach { e.update(it) }
        kotlin.test.assertEquals(1, e.spinVsWedgeDeg.size, "wedge after spin: ${e.spinAreaEnd}")
        println("spin vs wedge: ${e.spinVsWedgeDeg}")
    }
}
