package com.aiza.app.separation

import android.os.SystemClock
import com.aiza.data.tags.Settings
import com.aiza.data.tags.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update

enum class SeparationStatus { WATCHING, LEFT_BEHIND }

/** What the alert side needs to show (implemented with Android notifications). */
interface SeparationAlerts {
    fun leftBehind(address: String, tagName: String, reason: LeftBehindReason)
    fun clear(address: String)
}

/**
 * Watches every tag whose left-behind alert is on (FR-SEP-01..03): keeps its link open, runs a
 * [SeparationMonitor] on it with the threshold from Settings, and raises or clears the alert.
 * The foreground service only keeps the process alive while [watching] is not empty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeparationWatcher(
    scope: CoroutineScope,
    private val tags: TagRepository,
    settings: Flow<Settings?>,
    private val link: TagLink,
    private val alerts: SeparationAlerts,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _status = MutableStateFlow<Map<String, SeparationStatus>>(emptyMap())
    /** Per watched tag address. Tags missing from the map are not watched. */
    val status: StateFlow<Map<String, SeparationStatus>> = _status

    /** Addresses being watched. */
    val watching: StateFlow<Set<String>> get() = _watching
    private val _watching = MutableStateFlow<Set<String>>(emptySet())

    init {
        val watched = tags.tags.map { list -> list.filter { it.leftBehindAlert }.map { it.address }.toSet() }.distinctUntilChanged()
        val threshold = settings.filterNotNull().map { it.alertThresholdDbm }.distinctUntilChanged()
        combine(watched, threshold, ::Pair)
            .flatMapLatest { (addresses, thr) ->
                _watching.value = addresses
                if (addresses.isEmpty()) emptyFlow() else merge(*addresses.map { watchOne(it, thr) }.toTypedArray())
            }
            .launchIn(scope)
    }

    private fun watchOne(address: String, thresholdDbm: Int): Flow<Unit> {
        val monitor = SeparationMonitor(thresholdDbm)
        val ticks = flow { while (true) { emit(null); delay(1_000) } }
        return merge(link.watch(address), ticks)
            .map { event ->
                val t = clock()
                when (event) {
                    LinkEvent.Connected -> monitor.onConnected(t)
                    LinkEvent.Disconnected -> monitor.onDisconnected(t)
                    is LinkEvent.Rssi -> monitor.onRssi(t, event.dbm)
                    null -> when (val e = monitor.tick(t)) {
                        is SeparationEvent.LeftBehind -> {
                            val name = tags.tag(address).first()?.name ?: "Your tag"
                            alerts.leftBehind(address, name, e.reason)
                        }
                        SeparationEvent.BackInRange -> alerts.clear(address)
                        null -> {}
                    }
                }
                setStatus(address, if (monitor.alerted != null) SeparationStatus.LEFT_BEHIND else SeparationStatus.WATCHING)
            }
            .onCompletion {
                // Alert switched off, tag unpaired or threshold changed: drop any alert for it.
                alerts.clear(address)
                _status.update { it - address }
            }
    }

    private fun setStatus(address: String, s: SeparationStatus) {
        if (_status.value[address] != s) _status.update { it + (address to s) }
    }
}
