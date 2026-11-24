package com.aiza.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.core.direction.HotColdFilter
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
)

class SearchViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private val filter = HotColdFilter() // FR-HC-03: median of each 250 ms window
    private val history = ArrayDeque<Float>()

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            _state.update { it.copy(settings = settings, tag = container.tags.tag(address).first()) }
            container.tagController.setFindMode(address, true) // FR-HC-01
            var startNs: Long? = null
            container.proximity.rssi(address).collect { sample ->
                val first = startNs ?: sample.timestampNanos.also { startNs = it }
                val reading = filter.add(sample) ?: return@collect
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
        val wedge = if (demo && elapsedS > 10 && proximity != Proximity.VERY_CLOSE) 18f else null
        _state.update { it.copy(rssiDbm = recent, heat = heat, proximity = proximity, heading = heading, wedgeDeg = wedge) }
    }

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }

    fun toggleRing() = viewModelScope.launch {
        val on = !_state.value.ringing
        _state.update { it.copy(ringing = on) }
        container.tagController.ring(address, on)
    }

    override fun onCleared() {
        // Back to normal advertising when leaving the find screen (FR-HC-01).
        container.appScope.launch { withContext(NonCancellable) { container.tagController.setFindMode(address, false) } }
    }
}
