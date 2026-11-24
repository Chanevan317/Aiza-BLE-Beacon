package com.aiza.tools.replay

import com.aiza.core.direction.WedgeEngine
import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.logformat.EventRow
import com.aiza.core.logformat.SampleRow
import com.aiza.core.logformat.TrialLogCsv
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Replays a trial through the same direction core the app uses (FR-TRL-06) and scores the wedge against
 * ground truth (SRS §6.2). Accepts a trial folder (log.csv + meta.json, as exported) or a bare log.csv.
 * Usage: ./gradlew :tools:replay:run --args="path/to/T-20261125-01"
 *
 * Ground truth: the tag is at the measured distance and compass bearing from the start point, and the walk
 * starts at that point, so the true direction at any moment is from the tracked position to the tag.
 */
fun main(args: Array<String>) {
    val arg = args.firstOrNull()?.let(::File)
    val log = when {
        arg == null -> null
        arg.isDirectory -> File(arg, "log.csv")
        else -> arg
    }
    if (log == null || !log.isFile) {
        System.err.println("usage: replay <trial folder | log.csv>")
        exitProcess(2)
    }
    val meta = File(log.parentFile, "meta.json").takeIf { it.isFile }?.readText()
    fun num(key: String) = meta?.let { Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(it)?.groupValues?.get(1)?.toDouble() }
    val tagDistance = num("tagDistanceM")
    val tagBearing = num("tagBearingDeg")
    val tag = if (tagDistance != null && tagBearing != null)
        Math.toRadians(tagBearing).let { tagDistance * sin(it) to tagDistance * cos(it) } else null

    val engine = WedgeEngine()
    var samples = 0
    var decisions = 0
    var shown = 0
    var hits = 0
    var spins = 0
    log.useLines { lines ->
        lines.mapNotNull(TrialLogCsv::decodeRow).forEach { row ->
            // Guided spins: start/cancel from the logged taps; finish when complete, exactly as the app does.
            if (row is EventRow) {
                when (row.name) { "spin_start" -> engine.startSpin(); "spin_cancel" -> engine.cancelSpin() }
                return@forEach
            }
            val s = (row as? SampleRow)?.sample ?: return@forEach
            samples++
            val d0 = engine.update(s)
            if (engine.activeSpin?.complete == true) engine.finishSpin()?.let {
                spins++
                println("spin: strongest %.0f°, contrast %.1f dB, %s".format(it.bearingDeg, it.contrastDb, if (it.clear) "used" else "not clear"))
            }
            val d = d0 ?: return@forEach
            decisions++
            if (!d.shown) return@forEach
            shown++
            if (tag != null) {
                val p = engine.pdrState
                val truth = Math.toDegrees(atan2(tag.first - p.x, tag.second - p.y)).toFloat()
                if (abs(HeadingFilter.angleDiff(d.centreDeg!!, truth)) <= 60f) hits++
            }
        }
    }
    val p = engine.pdrState
    println("samples=$samples decisions=$decisions")
    println("walked: ${p.steps} steps, %.1f m, ended at (%.1f E, %.1f N)".format(p.distanceM, p.x, p.y))
    engine.stretches.forEachIndexed { i, st ->
        println("  stretch ${i + 1}: %3.0f° %.1f m %2d steps %-8s %s".format(
            st.headingDeg, st.lengthM, st.steps, st.label, st.rssiDeltaDb?.let { "%+.1f dB".format(it) } ?: ""))
    }
    if (spins > 0) {
        println("spin start area ended: ${engine.spinAreaEnd?.name?.lowercase() ?: "still shown"}")
        println("spin vs first wedge after it (from the spin spot): " +
            (engine.spinVsWedgeDeg.joinToString { "%.0f°".format(it) }.ifEmpty { "no wedge after the spin" }))
    }
    println("no-wedge rate: %.0f%% of decisions".format(if (decisions == 0) 0.0 else 100.0 * (decisions - shown) / decisions))
    when {
        tag == null -> println("wedge hit rate: no ground truth (meta.json with tagDistanceM and tagBearingDeg needed)")
        shown == 0 -> println("wedge hit rate: wedge never shown")
        else -> println("wedge hit rate: %.1f%% (%d/%d decisions; chance is 33%%)".format(100.0 * hits / shown, hits, shown))
    }
}
