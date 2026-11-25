package com.aiza.app.separation

import android.os.SystemClock
import com.aiza.data.tags.TagRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

sealed interface LinkEvent {
    data object Connected : LinkEvent
    data object Disconnected : LinkEvent
    data class Rssi(val dbm: Int) : LinkEvent
}

/** A kept-open connection to one tag while it is watched (FR-SEP-01). */
interface TagLink {
    /** Connects while collected; emits link changes and RSSI about once a second. */
    fun watch(address: String): Flow<LinkEvent>
}

/**
 * Demo mode link: the tag sits near the phone (about -60 dBm) until [leave] is called. Then the signal
 * fades by [fadeDbPerS] until it drops below [dropDbm] and the link is lost, as if the user walked away.
 * While away it writes the fading signal into the tag list so the screens match. TODO: real link with #6.
 */
class DemoTagLink(
    private val tags: TagRepository,
    private val fadeDbPerS: Double = 2.0,
    private val dropDbm: Int = -95,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : TagLink {
    private val leftAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val away: StateFlow<Set<String>> get() = awayState
    private val awayState = MutableStateFlow<Set<String>>(emptySet())

    fun leave(address: String) {
        leftAt.update { it + (address to clock()) }
        awayState.value = leftAt.value.keys
    }

    suspend fun comeBack(address: String) {
        leftAt.update { it - address }
        awayState.value = leftAt.value.keys
        tags.update(address) { it.copy(rssiDbm = -62, lastSeenEpochMs = System.currentTimeMillis()) }
    }

    override fun watch(address: String): Flow<LinkEvent> = flow {
        var connected = false
        while (true) {
            val left = leftAt.value[address]
            val rssi = if (left == null) -60 + Random.nextInt(-3, 4)
            else (-60 - fadeDbPerS * (clock() - left) / 1000.0).toInt() + Random.nextInt(-2, 3)
            if (rssi > dropDbm) {
                if (!connected) { connected = true; emit(LinkEvent.Connected) }
                emit(LinkEvent.Rssi(rssi))
                if (left != null) tags.update(address) { it.copy(rssiDbm = rssi, lastSeenEpochMs = System.currentTimeMillis()) }
            } else if (connected) {
                connected = false
                emit(LinkEvent.Disconnected)
                tags.update(address) { it.copy(rssiDbm = null) }
            }
            delay(1_000)
        }
    }
}
