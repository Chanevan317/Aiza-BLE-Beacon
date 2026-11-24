package com.aiza.data.trial

import android.content.Context
import com.aiza.core.logformat.LogRow
import com.aiza.core.logformat.TrialLogCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Trial files in app-private storage (C-5, NFR-PRV-01): `trials/<id>/log.csv` + `trials/<id>/meta.json`.
 * Exported only when the researcher chooses to (FR-TRL-04).
 */
class TrialStore(context: Context) {
    private val root = File(context.filesDir, "trials").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val _trials = MutableStateFlow(load())
    val trials: StateFlow<List<TrialMeta>> = _trials

    private fun dir(id: String) = File(root, id)
    private fun load(): List<TrialMeta> = root.listFiles().orEmpty()
        .mapNotNull { d -> File(d, META).takeIf { it.isFile }?.let { runCatching { json.decodeFromString<TrialMeta>(it.readText()) }.getOrNull() } }
        .sortedByDescending { it.startEpochMs }

    private fun refresh() { _trials.value = load() }

    /** Next free ID for today, e.g. T-20261125-03. */
    fun nextTrialId(nowMs: Long = System.currentTimeMillis()): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
        val n = (1..999).first { !dir("T-$day-%02d".format(it)).exists() }
        return "T-$day-%02d".format(n)
    }

    fun nextVolunteerCode(): String {
        val used = _trials.value.mapNotNull { it.volunteerCode.removePrefix("V").toIntOrNull() }
        return "V%02d".format((used.maxOrNull() ?: 0) + 1)
    }

    fun saveMeta(meta: TrialMeta) {
        dir(meta.trialId).mkdirs()
        File(dir(meta.trialId), META).writeText(json.encodeToString(meta))
        refresh()
    }

    /** Opens the log for writing; the caller writes rows from a single coroutine. */
    fun openLog(trialId: String): TrialLogWriter {
        dir(trialId).mkdirs()
        return TrialLogWriter(File(dir(trialId), LOG).bufferedWriter())
    }

    fun get(trialId: String): TrialMeta? = _trials.value.find { it.trialId == trialId }

    fun delete(trialId: String) { dir(trialId).deleteRecursively(); refresh() }

    /** Deletes every trial of one volunteer (FR-TRL-05). Returns how many were removed. */
    fun deleteVolunteer(code: String): Int {
        val ids = _trials.value.filter { it.volunteerCode.equals(code.trim(), ignoreCase = true) }.map { it.trialId }
        ids.forEach { dir(it).deleteRecursively() }
        refresh()
        return ids.size
    }

    /** Writes the given trials (log.csv + meta.json each) into a ZIP stream. */
    suspend fun exportZip(trialIds: List<String>, out: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(out.buffered()).use { zip ->
            trialIds.forEach { id ->
                listOf(LOG, META).map { File(dir(id), it) }.filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry("$id/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    companion object {
        const val LOG = "log.csv"
        const val META = "meta.json"
    }
}

class TrialLogWriter internal constructor(private val writer: BufferedWriter) {
    var rows = 0L
        private set

    init { writer.write(TrialLogCsv.HEADER); writer.newLine() }

    fun write(row: LogRow) {
        writer.write(TrialLogCsv.encode(row)); writer.newLine(); rows++
    }

    fun close() = writer.close()
}
