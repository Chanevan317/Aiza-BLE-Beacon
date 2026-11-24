package com.aiza.app.data

import android.os.Build
import android.os.SystemClock
import com.aiza.core.logformat.EventRow
import com.aiza.core.logformat.LogRow
import com.aiza.core.logformat.SampleRow
import com.aiza.core.logformat.WedgeRow
import com.aiza.core.model.AccelSample
import com.aiza.core.model.GyroSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import com.aiza.core.model.Sample
import com.aiza.data.trial.LineOfSight
import com.aiza.data.trial.SearchStyle
import com.aiza.data.trial.TrialMeta
import com.aiza.data.trial.TrialOutcome
import com.aiza.data.trial.TrialStore
import com.aiza.platform.sensors.MotionSensors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.aiza.core.direction.pdr.PdrTracker
import kotlinx.coroutines.launch

/** What the researcher filled in before consent. */
data class TrialDraft(
    val tagAddress: String,
    val tagSerial: String,
    val tagName: String,
    val volunteerCode: String,
    val place: String,
    val room: String,
    val tagDistanceM: Float,
    val tagBearingDeg: Float?,
    val lineOfSight: LineOfSight,
    val searchStyle: SearchStyle,
    val wedgeShown: Boolean,
    val notes: String,
)

/** Live counters for the researcher bar. */
data class TrialLive(
    val meta: TrialMeta,
    val elapsedMs: Long = 0,
    val rssi: Long = 0,
    val motion: Long = 0,
    val heading: Long = 0,
    val events: Long = 0,
    val steps: Int = 0,
    val walkedM: Float = 0f,
)

/**
 * Records one trial at a time (FR-TRL-03): phone motion sensors are captured here for the whole trial;
 * the search screen feeds RSSI samples, wedge decisions and user actions. All rows go through one channel
 * to a single writer on the IO dispatcher, so the CSV is never written from two threads.
 */
class TrialRecorder(
    private val scope: CoroutineScope,
    private val store: TrialStore,
    private val sensors: MotionSensors,
    private val appVersion: String,
) {
    /** Filled by the setup form, consumed by the consent screen. */
    var draft: TrialDraft? = null

    private val _live = MutableStateFlow<TrialLive?>(null)
    val live: StateFlow<TrialLive?> = _live

    private var rows: Channel<LogRow>? = null
    private var jobs: List<Job> = emptyList()
    private var checkpoints = 0
    private var pdr = PdrTracker()

    val active: Boolean get() = _live.value != null

    fun start(draft: TrialDraft, consentEpochMs: Long, simulated: Boolean): TrialMeta {
        check(!active) { "A trial is already running" }
        val meta = TrialMeta(
            trialId = store.nextTrialId(),
            volunteerCode = draft.volunteerCode,
            consentEpochMs = consentEpochMs,
            tagAddress = draft.tagAddress, tagSerial = draft.tagSerial, tagName = draft.tagName,
            place = draft.place, room = draft.room,
            tagDistanceM = draft.tagDistanceM, tagBearingDeg = draft.tagBearingDeg,
            lineOfSight = draft.lineOfSight, searchStyle = draft.searchStyle, wedgeShown = draft.wedgeShown,
            notes = draft.notes,
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            appVersion = appVersion,
            simulated = simulated,
            startEpochMs = System.currentTimeMillis(),
            startElapsedNs = SystemClock.elapsedRealtimeNanos(),
        )
        store.saveMeta(meta)
        checkpoints = 0
        pdr = PdrTracker()
        val channel = Channel<LogRow>(Channel.UNLIMITED)
        rows = channel
        _live.value = TrialLive(meta)
        val writer = store.openLog(meta.trialId)
        jobs = listOf(
            scope.launch(Dispatchers.IO) {
                try { for (row in channel) { writer.write(row); count(row) } } finally { writer.close() }
            },
            scope.launch {
                sensors.samples().collect { s ->
                    channel.trySend(SampleRow(s))
                    if (pdr.add(s) != null) _live.update { it?.copy(steps = pdr.state.steps, walkedM = pdr.state.distanceM) }
                }
            },
            scope.launch {
                while (true) {
                    delay(500)
                    _live.update { it?.copy(elapsedMs = (SystemClock.elapsedRealtimeNanos() - meta.startElapsedNs) / 1_000_000) }
                }
            },
        )
        event("start")
        return meta
    }

    fun sample(s: Sample) { rows?.trySend(SampleRow(s)) }

    fun wedge(centreDeg: Float?, probability: Float) {
        rows?.trySend(WedgeRow(SystemClock.elapsedRealtimeNanos(), centreDeg, probability))
    }

    fun event(name: String, label: String = "") {
        rows?.trySend(EventRow(SystemClock.elapsedRealtimeNanos(), name, label))
    }

    fun checkpoint(): String {
        checkpoints++
        val label = "C$checkpoints"
        event("checkpoint", label)
        return label
    }

    /** Ends the trial; returns the saved meta. [discard] deletes it instead (e.g. false start). */
    suspend fun finish(outcome: TrialOutcome, discard: Boolean = false): TrialMeta? {
        val live = _live.value ?: return null
        event(if (outcome == TrialOutcome.FOUND) "found" else "end", outcome.name.lowercase())
        val endNs = SystemClock.elapsedRealtimeNanos()
        jobs.drop(1).forEach { it.cancel() } // stop sensors + ticker
        rows?.close()
        jobs.first().join() // drain and close the file
        rows = null
        _live.value = null
        if (discard) { store.delete(live.meta.trialId); return null }
        val meta = live.meta.copy(endElapsedNs = endNs, outcome = outcome, checkpoints = checkpoints, rowCount = writerRows,
            steps = pdr.state.steps, walkedM = pdr.state.distanceM)
        store.saveMeta(meta)
        return meta
    }

    private var writerRows = 0L

    private fun count(row: LogRow) {
        writerRows++
        _live.update { l -> if (l == null) null else when (row) {
            is SampleRow -> when (row.sample) {
                is RssiSample -> l.copy(rssi = l.rssi + 1)
                is AccelSample, is GyroSample -> l.copy(motion = l.motion + 1)
                is HeadingSample -> l.copy(heading = l.heading + 1)
            }
            else -> l.copy(events = l.events + 1)
        } }
    }
}
