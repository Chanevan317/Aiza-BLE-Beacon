package com.aiza.core.direction

import com.aiza.core.direction.pdr.StepDetector
import com.aiza.core.model.AccelSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Sample
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * Synthetic search for tests: a person walks straight legs between turns while the phone records accelerometer
 * bounce (50 Hz), compass heading (50 Hz) and tag RSSI (20 Hz) from a log-distance model with noise.
 * The true position is kept so tests can check the wedge against the real direction to the tag.
 */
class WalkSimulator(
    private val tagX: Double,
    private val tagY: Double,
    seed: Int,
    private val rssiAt1m: Double = -59.0,
    private val exponent: Double = 2.0,
    private val noiseDb: Double = 4.0,
    /** Room reflections: smooth stronger/weaker zones about a metre across, this many dB (std). */
    private val roomDb: Double = 0.0,
    /** Extra loss when the user's body is between phone and tag (tag behind them). */
    private val bodyDb: Double = 0.0,
) {
    private val r = Random(seed.toLong())
    // Sum of random plane waves ≈ a smooth random field with ~1 m correlation length.
    private val waves = List(12) { DoubleArray(4).also { w ->
        val a = r.nextDouble() * 2 * PI; val k = 2 * PI / (1.5 + r.nextDouble() * 2.0)
        w[0] = k * cos(a); w[1] = k * sin(a); w[2] = r.nextDouble() * 2 * PI
    } }
    private fun room(px: Double, py: Double) = roomDb * kotlin.math.sqrt(2.0 / waves.size) * waves.sumOf { cos(it[0] * px + it[1] * py + it[2]) }
    val samples = mutableListOf<Sample>()
    /** True position at each RSSI sample time, for scoring. */
    val truth = mutableListOf<Triple<Long, Double, Double>>()
    private var t = 0L
    private var x = 0.0
    private var y = 0.0
    private var heading = 0.0
    private var phase = 0.0

    /** Stand still for [seconds] facing [headingDeg]. */
    fun stand(seconds: Double, headingDeg: Double = heading): WalkSimulator { heading = headingDeg; run(seconds, 0.0, 0.0); return this }

    /** Turn to [headingDeg] over about a second (no steps), then walk [metres] in that direction. */
    fun leg(headingDeg: Double, metres: Double): WalkSimulator {
        val from = heading
        val diff = ((headingDeg - from + 540) % 360) - 180
        val turnS = max(0.6, abs(diff) / 90.0)
        val n = (turnS * HZ).toInt()
        repeat(n) { i -> heading = from + diff * (i + 1) / n; tick(0.0, 0.0) }
        heading = (headingDeg + 360) % 360
        run(metres / SPEED, SPEED, 2.5)
        return this
    }

    /** Turn a full circle (plus a bit) on the spot at [degPerS], e.g. a guided spin. */
    fun spin(degPerS: Double = 30.0, totalDeg: Double = 380.0): WalkSimulator {
        val n = (totalDeg / degPerS * HZ).toInt()
        val from = heading
        repeat(n) { i -> heading = (from + totalDeg * (i + 1) / n) % 360; tick(0.0, 0.0) }
        return this
    }

    private fun run(seconds: Double, speed: Double, amp: Double) = repeat((seconds * HZ).toInt()) { tick(speed, amp) }

    private fun tick(speed: Double, amp: Double) {
        val dt = 1.0 / HZ
        phase += 2 * PI * STEP_HZ * dt
        val bounce = if (amp > 0) amp * sin(phase) else 0.0
        val rad = Math.toRadians(heading)
        x += speed * dt * sin(rad); y += speed * dt * cos(rad)
        samples += AccelSample(t, gauss(0.15).toFloat(), gauss(0.15).toFloat(), (9.81 + bounce + gauss(0.25)).toFloat())
        samples += HeadingSample(t, (((heading + gauss(2.0)) % 360 + 360) % 360).toFloat())
        if ((t / DT_NS) % 2 == 0L) { // 25 Hz RSSI, close to find-mode advertising
            val d = max(0.3, hypot(tagX - x, tagY - y))
            val toTag = Math.toDegrees(kotlin.math.atan2(tagX - x, tagY - y))
            val behind = abs(((toTag - heading + 540) % 360) - 180) > 120
            val rssi = rssiAt1m - 10 * exponent * log10(d) + room(x, y) - (if (behind) bodyDb else 0.0) + gauss(noiseDb)
            samples += RssiSample(t, "sim", rssi.toInt())
            truth += Triple(t, x, y)
        }
        t += DT_NS
    }

    private fun gauss(sd: Double) = r.nextGaussian() * sd

    /** Compass bearing from the true position at time [t] to the tag. */
    fun trueBearingAt(time: Long): Double {
        val (_, px, py) = truth.last { it.first <= time }
        return (Math.toDegrees(kotlin.math.atan2(tagX - px, tagY - py)) + 360) % 360
    }

    companion object {
        const val HZ = 50
        const val DT_NS = 1_000_000_000L / HZ
        const val STEP_HZ = 1.8
        /** Walking speed matching what the step detector measures for this bounce, so true and tracked paths agree. */
        val SPEED: Double by lazy {
            val d = StepDetector()
            val strides = (0 until 10 * HZ).mapNotNull { i ->
                d.add(AccelSample(i * DT_NS, 0f, 0f, (9.81 + 2.5 * sin(2 * PI * STEP_HZ * i / HZ)).toFloat()))?.second
            }
            strides.drop(2).average() * STEP_HZ
        }
    }
}
