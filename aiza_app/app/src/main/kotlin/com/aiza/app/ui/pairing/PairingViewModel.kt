package com.aiza.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.app.data.DiscoveredTag
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagIcon
import com.aiza.platform.ble.TagBonding
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
    /** Real tag picked: bonding, connecting and reading its ID (FR-SET-01). */
    data class Connecting(val tag: DiscoveredTag) : PairingStep
    data class Failed(val message: String) : PairingStep
    data class Naming(val tag: DiscoveredTag) : PairingStep
    data class Done(val address: String, val name: String, val icon: TagIcon) : PairingStep
}

class PairingViewModel(private val container: AppContainer) : ViewModel() {
    private val _step = MutableStateFlow<PairingStep>(PairingStep.Intro)
    val step: StateFlow<PairingStep> = _step
    private var scan: Job? = null
    /** Real tag connected during pairing but not saved yet. */
    private var held: String? = null

    private fun releaseHeld() { held?.let(container.tagHub::release); held = null }

    override fun onCleared() = releaseHeld()

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
        if (container.settings.value?.demoMode != false) { _step.value = PairingStep.Naming(tag); return }
        scan = viewModelScope.launch {
            _step.value = PairingStep.Connecting(tag)
            val address = TagBonding.bond(container.context, tag.address)
            if (address == null) {
                _step.value = PairingStep.Failed("Pairing didn't finish. Hold the tag's button for 2 s until it beeps, then try again.")
                return@launch
            }
            held = address
            val c = container.tagHub.hold(address)
            if (!c.awaitReady(15_000)) {
                _step.value = PairingStep.Failed("Paired, but the tag didn't answer. Keep it close to the phone and try again.")
                return@launch
            }
            val info = c.info.value
            _step.value = PairingStep.Naming(tag.copy(address = address, serial = info?.serial ?: tag.serial))
        }
    }

    fun back() {
        when (_step.value) {
            is PairingStep.Searching -> { scan?.cancel(); _step.value = PairingStep.Intro }
            is PairingStep.Naming, is PairingStep.Connecting, is PairingStep.Failed -> { releaseHeld(); startSearch() }
            else -> Unit
        }
    }

    fun save(tag: DiscoveredTag, name: String, icon: TagIcon) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val demo = container.settingsRepository.settings.first().demoMode
        val conn = if (demo) null else container.tagHub.connection(tag.address)
        container.tags.add(
            Tag(
                address = tag.address,
                name = name.trim().ifEmpty { icon.name.lowercase().replaceFirstChar { it.uppercase() } },
                icon = icon,
                serial = tag.serial,
                firmwareVersion = if (demo) "0.1.0" else conn?.info?.value?.firmware,
                batteryPercent = if (demo) Random.nextInt(70, 100) else conn?.battery?.value,
                rssiDbm = tag.rssiDbm,
                lastSeenEpochMs = now,
                lastSeenPlace = null,
                lastSeenLat = null,
                lastSeenLon = null,
                leftBehindAlert = false,
                pairedEpochMs = now,
            ),
        )
        releaseHeld() // saved now, so the hub keeps it connected as a paired tag
        _step.value = PairingStep.Done(tag.address, name.trim().ifEmpty { "Tag" }, icon)
    }
}
