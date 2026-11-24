package com.aiza.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.app.data.DiscoveredTag
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface PairingStep {
    data object Intro : PairingStep
    data class Searching(val found: List<DiscoveredTag>, val demo: Boolean) : PairingStep
    data class Naming(val tag: DiscoveredTag) : PairingStep
    data class Done(val address: String, val name: String, val icon: TagIcon) : PairingStep
}

class PairingViewModel(private val container: AppContainer) : ViewModel() {
    private val _step = MutableStateFlow<PairingStep>(PairingStep.Intro)
    val step: StateFlow<PairingStep> = _step
    private var scan: Job? = null

    fun startSearch() {
        scan?.cancel()
        scan = viewModelScope.launch {
            val demo = container.settingsRepository.settings.first().demoMode
            _step.value = PairingStep.Searching(emptyList(), demo)
            if (demo) container.discovery.discover().collect { found ->
                _step.update { if (it is PairingStep.Searching) it.copy(found = found) else it }
            }
        }
    }

    /** Called with the tag picked in the list (demo) or returned by the system pairing dialog (real). */
    fun select(tag: DiscoveredTag) {
        scan?.cancel()
        _step.value = PairingStep.Naming(tag)
    }

    fun back() {
        when (_step.value) {
            is PairingStep.Searching -> { scan?.cancel(); _step.value = PairingStep.Intro }
            is PairingStep.Naming -> startSearch()
            else -> Unit
        }
    }

    fun save(tag: DiscoveredTag, name: String, icon: TagIcon) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val demo = container.settingsRepository.settings.first().demoMode
        container.tags.add(
            Tag(
                address = tag.address,
                name = name.trim().ifEmpty { icon.name.lowercase().replaceFirstChar { it.uppercase() } },
                icon = icon,
                serial = tag.serial,
                firmwareVersion = if (demo) "0.1.0" else null,
                batteryPercent = if (demo) Random.nextInt(70, 100) else null,
                rssiDbm = tag.rssiDbm,
                lastSeenEpochMs = now,
                lastSeenPlace = null,
                lastSeenLat = null,
                lastSeenLon = null,
                leftBehindAlert = false,
                pairedEpochMs = now,
            ),
        )
        container.tagController.ring(tag.address, false) // TODO real: confirm beep on pairing (FR-SET-05)
        _step.value = PairingStep.Done(tag.address, name.trim().ifEmpty { "Tag" }, icon)
    }
}
