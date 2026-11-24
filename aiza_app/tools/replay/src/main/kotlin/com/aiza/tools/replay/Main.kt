package com.aiza.tools.replay

import com.aiza.core.direction.HotColdFilter
import com.aiza.core.direction.NoWedgeEngine
import com.aiza.core.direction.pdr.PdrTracker
import com.aiza.core.direction.pdr.StretchLabeler
import com.aiza.core.logformat.TrialLogCsv
import com.aiza.core.model.RssiSample
import java.io.File
import kotlin.system.exitProcess

/**
 * Replays a trial-log CSV through the same direction core the app uses (FR-TRL-06).
 * Usage: ./gradlew :tools:replay:run --args="path/to/trial.csv"
 */
fun main(args: Array<String>) {
    val file = args.firstOrNull()?.let(::File)
    if (file == null || !file.isFile) {
        System.err.println("usage: replay <trial.csv>")
        exitProcess(2)
    }
    val hotCold = HotColdFilter()
    val engine = NoWedgeEngine()
    val pdr = PdrTracker()
    val stretches = StretchLabeler()
    var samples = 0
    var readings = 0
    var wedgesShown = 0
    var decisions = 0
    file.useLines { lines ->
        lines.mapNotNull(TrialLogCsv::decode).forEach { s ->
            samples++
            pdr.add(s)?.let { stretches.step(it, pdr.state.turning) }
            if (s is RssiSample) hotCold.add(s)?.let { readings++; stretches.reading(it, pdr.state.turning) }
            engine.update(s)?.let { decisions++; if (it.shown) wedgesShown++ }
        }
    }
    stretches.finish()
    val p = pdr.state
    println("steps=${p.steps} walked=%.1fm end=(%.1fE, %.1fN)".format(p.distanceM, p.x, p.y))
    stretches.stretches.forEachIndexed { i, st ->
        println("stretch ${i + 1}: %3.0f° %.1fm %2d steps %s %s".format(
            st.headingDeg, st.lengthM, st.steps, st.label, st.rssiDeltaDb?.let { "%+.1f dB".format(it) } ?: ""))
    }
    println("samples=$samples hotColdReadings=$readings wedgeDecisions=$decisions wedgesShown=$wedgesShown")
    // TODO: compare decisions with ground truth -> wedge hit rate, no-wedge rate, time to find (SRS §6.2)
}
