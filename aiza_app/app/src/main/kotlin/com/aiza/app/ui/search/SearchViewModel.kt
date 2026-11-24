package com.aiza.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.core.direction.WedgeEngine
import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.model.RssiSample
import com.aiza.core.model.WedgeDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import com.aiza.data.tags.Settings
import com.aiza.data.tags.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class Proximity(val label: String) { FAR("Far away"), NEAR("Getting close"), CLOSE("Close"), VERY_CLOSE("Very close") }
enum class Heading(val label: String) { WARMER("Warmer"), COLDER("Colder"), STEADY("Keep going") }

data class SearchState(
    val tag: Tag? = null,
    val settings: Settings = Settings(),
    val rssiDbm: Float? = null,
    /** 0 = cold, 1 = hot. */
    val heat: Float = 0f,
    val proximity: Proximity = Proximity.FAR,
    val heading: Heading = Heading.STEADY,
    /** Wedge centre in degrees relative to the phone's forward direction; null = no wedge (FR-DIR-07). */
    val wedgeDeg: Float? = null,
    val muted: Boolean = false,
    val ringing: Boolean = false,
    /** Step + heading tracking (FR-DIR-01/02). */
    val steps: Int = 0,
    val walkedM: Float = 0f,
    val headingDeg: Float? = null,
    val turning: Boolean = false,
    /** Probability the tag is inside the best 120° window (shown to researchers only). */
    val probability: Float = 0f,
)

class SearchViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val recorder = container.trialRecorder
    /** Set when this search is a research trial (FR-TRL-03); null for normal use. */
    val trial = recorder.live.value?.meta
    val trialLive = recorder.live
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    /** Steps + heading, hot/cold and the wedge filter, all in the pure-Kotlin core (FR-DIR-01..06). */
    private val engine = WedgeEngine()
    private val history = ArrayDeque<Float>()
    /** Last wedge decision as a compass bearing; turned relative to the phone on every heading update (FR-DIR-08). */
    private var wedgeWorld: Float? = null
    private var lastPushNs = 0L

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _state.update { it.copy(settings = settings, tag = container.tags.tag(address).first()) }
            container.tagController.setFindMode(address, true) // FR-HC-01
            // One ordered stream into one engine, on one coroutine: deterministic, no locking needed.
            merge(container.motionSensors.samples(), container.proximity.rssi(address))
                .flowOn(Dispatchers.Default)
                .collect { sample ->
                    if (sample is RssiSample) recorder.sample(sample) // motion samples are logged by the recorder itself
                    val decision = engine.update(sample)
                    if (decision != null) onDecision(decision) else maybePushMotion()
                }
        }
    }

    /** Motion-only update (steps, heading, turning), at most ~10 per second. */
    private fun maybePushMotion() {
        val p = engine.pdrState
        val s = _state.value
        if (p.steps == s.steps && p.turning == s.turning && p.timestampNanos - lastPushNs < 100_000_000L) return
        lastPushNs = p.timestampNanos
        _state.update {
            it.copy(steps = p.steps, walkedM = p.distanceM, headingDeg = p.headingDeg, turning = p.turning,
                wedgeDeg = relative(wedgeWorld, p.headingDeg))
        }
    }

    private fun onDecision(d: WedgeDecision) {
        val rssi = engine.lastReading?.filteredRssiDbm ?: return
        // Hot/cold label: smooth over ~1 s and compare with ~2 s earlier so it doesn't flicker.
        history.addLast(rssi)
        while (history.size > 12) history.removeFirst()
        val recent = history.takeLast(4).average().toFloat()
        val earlier = if (history.size >= 12) history.take(4).average().toFloat() else null
        val heading = when {
            earlier == null -> Heading.STEADY
            recent - earlier >= 2.5f -> Heading.WARMER
            earlier - recent >= 2.5f -> Heading.COLDER
            else -> Heading.STEADY
        }
        val heat = ((recent + 90f) / 42f).coerceIn(0f, 1f)
        val proximity = when {
            heat > 0.85f -> Proximity.VERY_CLOSE
            heat > 0.6f -> Proximity.CLOSE
            heat > 0.3f -> Proximity.NEAR
            else -> Proximity.FAR
        }
        // Every decision is logged (shown or not) so trials can score the wedge even when it is switched off.
        recorder.wedge(d.centreDeg, d.probabilityInside)
        // Without-wedge trials never show it (SRS §6.2 "with vs without wedge").
        wedgeWorld = if (trial != null && !trial.wedgeShown) null else d.centreDeg
        val p = engine.pdrState
        _state.update {
            it.copy(
                rssiDbm = recent, heat = heat, proximity = proximity, heading = heading,
                wedgeDeg = relative(wedgeWorld, p.headingDeg), probability = d.probabilityInside,
                steps = p.steps, walkedM = p.distanceM, headingDeg = p.headingDeg, turning = p.turning,
            )
        }
    }

    /** Wedge centre relative to where the phone points (0 = straight ahead). */
    private fun relative(world: Float?, headingDeg: Float?): Float? {
        world ?: return null
        return if (headingDeg == null) world else HeadingFilter.angleDiff(world, headingDeg)
    }

    fun toggleMute() {
        _state.update { it.copy(muted = !it.muted) }
        recorder.event("mute", if (_state.value.muted) "on" else "off")
    }

    fun toggleRing() = viewModelScope.launch {
        val on = !_state.value.ringing
        _state.update { it.copy(ringing = on) }
        recorder.event("ring", if (on) "on" else "off")
        container.tagController.ring(address, on)
    }

    fun checkpoint(): String = recorder.checkpoint()

    /** Ends the trial and reports its ID (null if discarded). */
    fun finishTrial(outcome: com.aiza.data.trial.TrialOutcome, discard: Boolean, done: (String?) -> Unit) =
        container.appScope.launch {
            val meta = recorder.finish(outcome, discard)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { done(meta?.trialId) }
        }

    override fun onCleared() {
        // Back to normal advertising when leaving the find screen (FR-HC-01).
        container.appScope.launch { withContext(NonCancellable) { container.tagController.setFindMode(address, false) } }
    }
}
