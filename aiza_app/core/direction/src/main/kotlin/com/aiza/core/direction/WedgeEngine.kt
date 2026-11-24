package com.aiza.core.direction

import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.direction.pdr.PdrTracker
import com.aiza.core.direction.pdr.Stretch
import com.aiza.core.direction.pdr.StretchLabeler
import com.aiza.core.model.HotColdReading
import com.aiza.core.model.NoWedgeReason
import com.aiza.core.model.PdrState
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Sample
import com.aiza.core.model.Trend
import com.aiza.core.model.WedgeDecision
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

/** Tunable settings. Defaults are starting points; the threshold is meant to be set from trials (FR-DIR-05). */
data class WedgeConfig(
    /** Show the wedge when P(tag inside) stays at or above this. */
    val threshold: Float = 0.9f,
    /** ...for this long (FR-DIR-05). */
    val holdNanos: Long = 2_000_000_000L,
    /** Hide again when P falls below threshold − hysteresis. */
    val hysteresis: Float = 0.1f,
    /** Indoor path-loss exponent: signal falls 10·n dB per tenfold distance. */
    val pathLossExponent: Float = 2.0f,
    /**
     * Spread of one evidence point around the model, dB. Larger = more cautious (shown less, wrong less).
     * 4 dB: in simulated rooms with 5 dB reflections + body blocking the wedge was right ~95% of the time shown.
     */
    val sigmaDb: Float = 4f,
    /** Expected signal at 1 m and its spread (soft prior; the real value is learned per candidate). */
    val rssiAt1mDbm: Float = -60f,
    val rssiAt1mSpreadDb: Float = 8f,
    /** Candidate tag positions: a grid of this radius and spacing around the start point. */
    val gridRadiusM: Float = 12f,
    val gridStepM: Float = 0.4f,
    /** One evidence point per this much walking (limits correlated, repeated readings). */
    val evidenceEveryM: Float = 0.5f,
    val minEvidence: Int = 6,
    /** FR-DIR-06: hide after walking this far from where the wedge was estimated... */
    val moveLimitM: Float = 3f,
    /** ...or when the tag is probably within this distance. */
    val closeM: Float = 1f,
)

/**
 * The direction wedge (FR-DIR-03..06, C-4). A fixed set of candidate tag positions (a grid "particle" filter)
 * is scored against signal readings taken at known points of the walked path (from steps + heading):
 * for the true position, signal + 10·n·log10(distance) is nearly constant along the walk, so candidates where it
 * varies a lot are unlikely. The unknown signal-at-1 m is estimated per candidate, so it needs no calibration.
 *
 * The posterior is turned into a bearing histogram from the user's current position; the 120° window holding the
 * most probability is the wedge and that probability is P. Deterministic: the same log always gives the same
 * decisions (NFR-MNT-02), so tools:replay scores exactly what the phone showed.
 *
 * Positions are metres in the walk frame (x east, y north, start at 0,0); [WedgeDecision.centreDeg] is a compass
 * bearing, so the UI turns it relative to the phone's heading (FR-DIR-08).
 */
class WedgeEngine(private val cfg: WedgeConfig = WedgeConfig()) : DirectionEngine {
    private var pdr = PdrTracker()
    private var hotCold = HotColdFilter()
    private var stretchLabeler = StretchLabeler()

    // Candidate positions and per-candidate running sums of q = rssi + 10·n·log10(d).
    private val cx: FloatArray
    private val cy: FloatArray
    private val sum: DoubleArray
    private val sumSq: DoubleArray
    private val weight: DoubleArray

    private var evidence = 0
    private val pending = mutableListOf<Float>()
    private var lastEvidenceX = 0f
    private var lastEvidenceY = 0f

    private var aboveSince: Long? = null
    private var shown = false
    private var shownX = 0f
    private var shownY = 0f
    private var contradicted = false

    /** Latest values, for the UI and logs. */
    var lastReading: HotColdReading? = null; private set
    var lastDecision: WedgeDecision? = null; private set
    /** Probability-weighted median distance to the tag, metres; null until there is evidence. */
    var estimatedDistanceM: Float? = null; private set
    val pdrState: PdrState get() = pdr.state
    val stretches: List<Stretch> get() = stretchLabeler.stretches

    init {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        val n = (cfg.gridRadiusM / cfg.gridStepM).toInt()
        for (i in -n..n) for (j in -n..n) {
            val x = i * cfg.gridStepM
            val y = j * cfg.gridStepM
            if (hypot(x, y) <= cfg.gridRadiusM) { xs += x; ys += y }
        }
        cx = xs.toFloatArray(); cy = ys.toFloatArray()
        sum = DoubleArray(cx.size); sumSq = DoubleArray(cx.size); weight = DoubleArray(cx.size)
    }

    override fun reset() {
        pdr = PdrTracker(); hotCold = HotColdFilter(); stretchLabeler = StretchLabeler()
        sum.fill(0.0); sumSq.fill(0.0); weight.fill(0.0)
        evidence = 0; pending.clear(); lastEvidenceX = 0f; lastEvidenceY = 0f
        aboveSince = null; shown = false; contradicted = false
        lastReading = null; lastDecision = null; estimatedDistanceM = null
    }

    /** Feeds samples in time order. Returns a decision for every filtered signal reading (about 4 per second). */
    override fun update(sample: Sample): WedgeDecision? {
        if (sample !is RssiSample) {
            pdr.add(sample)?.let { step -> stretchLabeler.step(step, pdr.state.turning)?.let(::checkStretch) }
            return null
        }
        val reading = hotCold.add(sample) ?: return null
        lastReading = reading
        val p = pdr.state
        stretchLabeler.reading(reading, p.turning)?.let(::checkStretch)
        if (p.turning) pending.clear() // FR-DIR-02: readings taken while turning are ignored
        else {
            pending += reading.filteredRssiDbm
            if (hypot(p.x - lastEvidenceX, p.y - lastEvidenceY) >= cfg.evidenceEveryM) {
                addEvidence(median(pending), (p.x + lastEvidenceX) / 2f, (p.y + lastEvidenceY) / 2f)
                pending.clear(); lastEvidenceX = p.x; lastEvidenceY = p.y
            }
        }
        return decide(reading.timestampNanos, p).also { lastDecision = it }
    }

    private fun addEvidence(rssi: Float, x: Float, y: Float) {
        val k = 10.0 * cfg.pathLossExponent
        for (i in cx.indices) {
            val d = max(0.3, hypot((cx[i] - x).toDouble(), (cy[i] - y).toDouble()))
            val q = rssi + k * log10(d)
            sum[i] += q; sumSq[i] += q * q
        }
        evidence++
    }

    private fun posterior() {
        val n = evidence.toDouble()
        var best = Double.NEGATIVE_INFINITY
        val s2 = 2.0 * cfg.sigmaDb * cfg.sigmaDb
        val p2 = 2.0 * cfg.rssiAt1mSpreadDb * cfg.rssiAt1mSpreadDb
        for (i in cx.indices) {
            val mean = sum[i] / n
            val spread = max(0.0, sumSq[i] - sum[i] * sum[i] / n) // Σ(q − mean)²
            val prior = (mean - cfg.rssiAt1mDbm).let { it * it } / p2
            weight[i] = -spread / s2 - prior
            if (weight[i] > best) best = weight[i]
        }
        var total = 0.0
        for (i in cx.indices) { weight[i] = exp(weight[i] - best); total += weight[i] }
        for (i in cx.indices) weight[i] /= total
    }

    private fun decide(t: Long, p: PdrState): WedgeDecision {
        if (evidence < cfg.minEvidence) {
            hide()
            return WedgeDecision(t, null, 0f, NoWedgeReason.NOT_ENOUGH_DATA)
        }
        posterior()
        // Bearing histogram from where the user is now, 5° bins.
        val bins = DoubleArray(BINS)
        var close = 0.0
        val byDistance = ArrayList<Pair<Float, Double>>(cx.size)
        for (i in cx.indices) {
            val dx = cx[i] - p.x
            val dy = cy[i] - p.y
            val d = hypot(dx, dy)
            if (d < cfg.closeM) close += weight[i]
            if (weight[i] > 1e-6) byDistance += d to weight[i]
            val bearing = (Math.toDegrees(atan2(dx, dy).toDouble()) + 360.0) % 360.0
            bins[(bearing / BIN_DEG).toInt() % BINS] += weight[i]
        }
        estimatedDistanceM = byDistance.sortedBy { it.first }.let { list ->
            var acc = 0.0
            list.firstOrNull { acc += it.second; acc >= 0.5 }?.first
        }
        // Best 120° window (C-4).
        var bestStart = 0
        var bestMass = -1.0
        for (s in 0 until BINS) {
            var m = 0.0
            for (k in 0 until WINDOW) m += bins[(s + k) % BINS]
            if (m > bestMass) { bestMass = m; bestStart = s }
        }
        val centre = ((bestStart * BIN_DEG + WedgeDecision.WEDGE_WIDTH_DEG / 2f) % 360f)
        val prob = bestMass.toFloat().coerceIn(0f, 1f)

        if (shown) {
            val reason = when {
                close > 0.5 -> NoWedgeReason.TAG_VERY_CLOSE
                contradicted -> NoWedgeReason.CONTRADICTS_HOT_COLD
                hypot(p.x - shownX, p.y - shownY) > cfg.moveLimitM -> NoWedgeReason.MOVED_TOO_FAR
                prob < cfg.threshold - cfg.hysteresis -> NoWedgeReason.LOW_CONFIDENCE
                else -> null
            }
            if (reason == null) return WedgeDecision(t, centre, prob)
            hide()
            // After walking on, the wedge may come back from here once it has held for 2 s again.
            if (reason == NoWedgeReason.MOVED_TOO_FAR && prob >= cfg.threshold) aboveSince = t
            return WedgeDecision(t, null, prob, reason)
        }

        aboveSince = if (prob >= cfg.threshold) aboveSince ?: t else null
        val reason = when {
            close > 0.5 -> NoWedgeReason.TAG_VERY_CLOSE
            contradicted -> NoWedgeReason.CONTRADICTS_HOT_COLD
            prob < cfg.threshold -> NoWedgeReason.LOW_CONFIDENCE
            t - aboveSince!! < cfg.holdNanos -> NoWedgeReason.LOW_CONFIDENCE
            else -> null
        }
        if (reason != null) return WedgeDecision(t, null, prob, reason)
        shown = true; shownX = p.x; shownY = p.y
        return WedgeDecision(t, centre, prob)
    }

    private fun hide() { shown = false; contradicted = false }

    /** FR-DIR-06: a finished straight stretch that disagrees with the shown wedge hides it. */
    private fun checkStretch(s: Stretch) {
        val centre = lastDecision?.centreDeg ?: return
        if (!shown) return
        val off = abs(HeadingFilter.angleDiff(s.headingDeg, centre))
        if ((s.label == Trend.COLDER && off <= 60f) || (s.label == Trend.HOTTER && off >= 120f)) contradicted = true
    }

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    private companion object {
        const val BIN_DEG = 5f
        const val BINS = 72
        const val WINDOW = 24 // 24 × 5° = 120°
    }
}
