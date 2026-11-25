package com.aiza.app.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aiza.data.tags.TagRepository
import com.aiza.platform.ble.LinkState
import com.aiza.platform.ble.TagConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one [TagConnection] open per real paired tag while demo mode is off (SRS §4.3). Writes what it
 * learns (signal, last seen, battery, firmware) into the tag list, and passes on button presses.
 * Tags being paired are connected too ([hold]) before they are saved.
 */
class TagHub(
    private val context: Context,
    private val scope: CoroutineScope,
    private val tags: TagRepository,
    realMode: Flow<Boolean>,
) {
    private val connections = ConcurrentHashMap<String, Pair<TagConnection, Job>>()
    private val held = MutableStateFlow<Set<String>>(emptySet())
    private val _presses = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short presses of a tag's button, by address (FR-RNG-02). */
    val presses: SharedFlow<String> = _presses

    private val permission = flow { while (true) { emit(hasConnectPermission()); delay(5_000) } }.distinctUntilChanged()

    init {
        val paired = tags.tags.map { list -> list.map { it.address }.toSet() }.distinctUntilChanged()
        combine(realMode, permission, paired, held) { real, granted, p, h -> if (real && granted) p + h else emptySet() }
            .distinctUntilChanged()
            .onEach(::sync)
            .launchIn(scope)
    }

    fun hasConnectPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun connection(address: String): TagConnection? = connections[address]?.first

    /** Connect to a tag that is not saved yet (during pairing). Returns its connection right away. */
    @Synchronized
    fun hold(address: String): TagConnection {
        held.update { it + address }
        return connections[address]?.first ?: open(address)
    }

    fun release(address: String) = held.update { it - address }

    @Synchronized
    private fun sync(wanted: Set<String>) {
        (connections.keys - wanted - held.value).forEach { a -> connections.remove(a)?.let { (c, job) -> job.cancel(); c.close() } }
        (wanted - connections.keys).forEach(::open)
    }

    private fun open(address: String): TagConnection {
        val c = TagConnection(context, address, scope)
        connections[address] = c to watch(c)
        c.connect()
        return c
    }

    private fun watch(c: TagConnection): Job = scope.launch {
        launch { c.buttonEvents.collect { _presses.emit(c.address) } }
        launch { c.battery.filterNotNull().collect { b -> tags.update(c.address) { it.copy(batteryPercent = b) } } }
        launch { c.info.filterNotNull().collect { i -> tags.update(c.address) { it.copy(firmwareVersion = i.firmware ?: it.firmwareVersion) } } }
        launch {
            c.state.collect { s ->
                if (s == LinkState.DISCONNECTED) tags.update(c.address) { it.copy(rssiDbm = null) }
            }
        }
        // Signal and "last seen" for the home and tag screens (FR-STA-01).
        // Median of the last 3 readings, so one noisy reading doesn't flip "Close by" / "Far away".
        launch {
            val recent = ArrayDeque<Int>()
            while (true) {
                val r = c.readRssi()
                if (r == null) recent.clear() else {
                    recent.addLast(r); if (recent.size > 3) recent.removeFirst()
                    val median = recent.sorted()[recent.size / 2]
                    tags.update(c.address) { it.copy(rssiDbm = median, lastSeenEpochMs = System.currentTimeMillis()) }
                }
                delay(3_000)
            }
        }
    }
}
