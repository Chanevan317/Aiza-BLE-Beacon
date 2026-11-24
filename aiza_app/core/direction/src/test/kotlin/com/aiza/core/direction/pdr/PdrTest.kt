package com.aiza.core.direction.pdr

import com.aiza.core.model.AccelSample
import com.aiza.core.model.HeadingSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdrTest {
    private val hz = 50
    private val dtNs = 1_000_000_000L / hz

    /** Synthetic walking: vertical bounce at [stepHz] steps/s with noise, phone held still otherwise. */
    private fun walk(seconds: Double, stepHz: Double = 1.8, amp: Double = 2.5, startNs: Long = 0, seed: Int = 1): List<AccelSample> {
        val r = Random(seed)
        return (0 until (seconds * hz).toInt()).map { i ->
            val t = i.toDouble() / hz
            val bounce = amp * sin(2 * PI * stepHz * t) + r.nextDouble(-0.3, 0.3)
            AccelSample(startNs + i * dtNs, r.nextDouble(-0.2, 0.2).toFloat(), r.nextDouble(-0.2, 0.2).toFloat(), (9.81 + bounce).toFloat())
        }
    }

    @Test
    fun countsStepsWithinOne() {
        val d = StepDetector()
        val steps = walk(10.0).mapNotNull { d.add(it) }
        // 1.8 steps/s for 10 s ≈ 18 steps; the first second is used to settle the gravity estimate.
        assertTrue(steps.size in 16..19, "got ${steps.size}")
    }

    @Test
    fun noStepsWhenStandingStill() {
        val d = StepDetector()
        val steps = walk(10.0, amp = 0.0).mapNotNull { d.add(it) }
        assertEquals(0, steps.size)
    }

    @Test
    fun strideLengthIsPlausible() {
        val d = StepDetector()
        walk(10.0).mapNotNull { d.add(it) }.forEach { (_, len) -> assertTrue(len in 0.5f..0.9f, "stride $len") }
    }

    @Test
    fun headingAveragesAcrossNorth() {
        val f = HeadingFilter()
        repeat(100) { i -> f.add(HeadingSample(i * dtNs, if (i % 2 == 0) 359f else 1f)) }
        val h = f.headingDeg!!
        assertTrue(h > 358f || h < 2f, "heading $h")
    }

    @Test
    fun detectsTurning() {
        val f = HeadingFilter()
        repeat(50) { i -> f.add(HeadingSample(i * dtNs, 90f)) }
        assertFalse(f.turning)
        // Turn 90° in one second.
        repeat(50) { i -> f.add(HeadingSample((50 + i) * dtNs, 90f + 90f * i / 50f)) }
        assertTrue(f.turning)
    }

    @Test
    fun walkingNorthMovesNorth() {
        val pdr = PdrTracker()
        val accel = walk(10.0)
        accel.forEachIndexed { i, a ->
            pdr.add(HeadingSample(a.timestampNanos, 0f))
            pdr.add(a)
        }
        val s = pdr.state
        assertTrue(s.steps >= 15)
        assertTrue(s.y > 8f, "north ${s.y}")
        assertTrue(abs(s.x) < 0.5f, "east ${s.x}")
        assertEquals(s.distanceM, pdr.path.sumOf { it.lengthM.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun replayIsDeterministic() {
        fun run(): Pair<Float, Float> {
            val pdr = PdrTracker()
            walk(8.0, seed = 7).forEach { pdr.add(HeadingSample(it.timestampNanos, 45f)); pdr.add(it) }
            return pdr.state.x to pdr.state.y
        }
        assertEquals(run(), run())
    }
}
