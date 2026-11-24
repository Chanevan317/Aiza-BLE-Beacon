package com.aiza.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.core.direction.HotColdFilter
import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.direction.pdr.PdrTracker
import com.aiza.core.direction.pdr.StretchLabeler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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
)

class SearchViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val recorder = container.trialRecorder
    /** Set when this search is a research trial (FR-TRL-03); null for normal use. */
    val trial = recorder.live.value?.meta
    val trialLive = recorder.live
    private var lastWedge: Float? = null
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private val filter = HotColdFilter() // FR-HC-03: median of each 250 ms window
    private val history = ArrayDeque<Float>()
    private val pdr = PdrTracker()
    private val stretches = StretchLabeler()
    /** Demo only: world bearing of the fake tag, fixed when the demo wedge first appears. */
    private var demoBearing: Float? = null

    init {
        // Step + heading tracking runs for the whole search (FR-DIR-01).
        viewModelScope.launch {
            var lastPush = 0L
            container.motionSensors.samples().flowOn(Dispatchers.Default).collect { sample ->
                val step = pdr.add(sample)
                val p = pdr.state
                if (step != null) synchronized(stretches) { stretches.step(step, p.turning) }
                val s = _state.value
                if (step != null || p.turning != s.turning || p.timestampNanos - lastPush > 100_000_000L) {
                    lastPush = p.timestampNanos
                    _state.update {
                        it.copy(steps = p.steps, walkedM = p.distanceM, headingDeg = p.headingDeg, turning = p.turning,
                            wedgeDeg = relativeWedge(it.wedgeDeg != null, p.headingDeg))
                    }
                }
            }
        }
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _state.update { it.copy(settings = settings, tag = container.tags.tag(address).first()) }
            container.tagController.setFindMode(address, true) // FR-HC-01
            var startNs: Long? = null
            container.proximity.rssi(address).collect { sample ->
                recorder.sample(sample)
                val first = startNs ?: sample.timestampNanos.also { startNs = it }
                val reading = filter.add(sample) ?: return@collect
                synchronized(stretches) { stretches.reading(reading, _state.value.turning) }
                onReading(reading.filteredRssiDbm, settings.demoMode, (reading.timestampNanos - first) / 1e9)
            }
        }
    }

    private fun onReading(rssi: Float, demo: Boolean, elapsedS: Double) {
        // Smooth over ~1 s and compare with ~2 s earlier so the label doesn't flicker.
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
        // The real wedge comes from the direction core (FR-DIR-03..06), which is not built yet.
        // In demo mode a fake wedge shows the design: appears after a while, hides when very close.
        // The demo tag sits at a fixed compass bearing, so the wedge now turns as the user turns.
        val showDemo = demo && elapsedS > 10 && proximity != Proximity.VERY_CLOSE
        // Without-wedge trials never show it (SRS §6.2 "with vs without wedge").
        val show = showDemo && !(trial != null && !trial.wedgeShown)
        val h = _state.value.headingDeg
        if (show && demoBearing == null) demoBearing = ((h ?: 0f) + 18f) % 360f
        // Log the wedge in world degrees (comparable with the ground-truth bearing) when it appears/disappears.
        val world = if (show) demoBearing else null
        if (world != lastWedge) { recorder.wedge(world, if (world != null) 0.93f else 0f); lastWedge = world }
        _state.update { it.copy(rssiDbm = recent, heat = heat, proximity = proximity, heading = heading, wedgeDeg = relativeWedge(show, h)) }
    }

    /** Wedge centre relative to where the phone points; falls back to a fixed offset without a compass. */
    private fun relativeWedge(show: Boolean, headingDeg: Float?): Float? {
        if (!show) return null
        val b = demoBearing ?: return null
        return if (headingDeg == null) 18f else HeadingFilter.angleDiff(b, headingDeg)
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
