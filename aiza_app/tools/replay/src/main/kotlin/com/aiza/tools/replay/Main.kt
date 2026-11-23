package com.aiza.tools.replay

import com.aiza.core.direction.HotColdFilter
import com.aiza.core.direction.NoWedgeEngine
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
    var samples = 0
    var readings = 0
    var wedgesShown = 0
    var decisions = 0
    file.useLines { lines ->
        lines.mapNotNull(TrialLogCsv::decode).forEach { s ->
            samples++
            if (s is RssiSample && hotCold.add(s) != null) readings++
            engine.update(s)?.let { decisions++; if (it.shown) wedgesShown++ }
        }
    }
    println("samples=$samples hotColdReadings=$readings wedgeDecisions=$decisions wedgesShown=$wedgesShown")
    // TODO: compare decisions with ground truth -> wedge hit rate, no-wedge rate, time to find (SRS §6.2)
}
