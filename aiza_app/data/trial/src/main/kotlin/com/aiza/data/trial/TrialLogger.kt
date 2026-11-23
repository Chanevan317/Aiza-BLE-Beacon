package com.aiza.data.trial

import android.content.Context
import com.aiza.core.logformat.TrialLogCsv
import com.aiza.core.model.Sample
import java.io.BufferedWriter
import java.io.File

/** Trial metadata (FR-TRL-01). No names — trial IDs only (NFR-PRV-01). */
data class TrialInfo(
    val trialId: String,
    val phoneModel: String,
    val room: String,
    val startDistanceM: Float,
    val wedgeEnabled: Boolean,
)

/**
 * Writes every sample of a trial to app-private storage as CSV (FR-TRL-03/04).
 * TODO: ground truth + checkpoints (FR-TRL-02), wedge decisions and user actions, consent record (FR-TRL-05).
 */
class TrialLogger(context: Context) {
    private val dir = File(context.filesDir, "trials").apply { mkdirs() }
    private var writer: BufferedWriter? = null

    fun start(info: TrialInfo): File {
        val file = File(dir, "${info.trialId}.csv")
        writer = file.bufferedWriter().apply { write(TrialLogCsv.HEADER); newLine() }
        return file
    }

    fun log(sample: Sample) {
        writer?.apply { write(TrialLogCsv.encode(sample)); newLine() }
    }

    fun stop() {
        writer?.close()
        writer = null
    }

    fun trials(): List<File> = dir.listFiles()?.sortedBy { it.name } ?: emptyList()

    fun delete(trialId: String) = File(dir, "$trialId.csv").delete()
}
