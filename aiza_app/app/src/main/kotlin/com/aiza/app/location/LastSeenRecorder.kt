package com.aiza.app.location

import android.os.SystemClock
import com.aiza.data.tags.Presence
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Saves where the phone was when it last heard each tag (FR-STA-03):
 * - when a tag goes from connected to not connected ("you lost it here"), right away, with a GPS-quality fix;
 * - while connected, at most every [refreshMs], so the place stays current as you move.
 * Location is only read while the app is open or the left-behind service runs (no background location permission).
 */
class LastSeenRecorder(
    private val scope: CoroutineScope,
    private val tags: TagRepository,
    private val locator: Locator,
    private val refreshMs: Long = 120_000,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    tickMs: Long = 30_000,
) {
    private val lastFix = HashMap<String, Long>()
    private var previous = emptyMap<String, Presence>()
    private val pending = LinkedHashSet<String>()
    private var pendingPrecise = false
    private var busy = false

    init {
        val ticks = flow { while (true) { emit(Unit); delay(tickMs) } }
        scope.launch {
            combine(tags.tags, ticks) { list, _ -> list }.collect { list -> check(list) }
        }
    }

    @Synchronized
    private fun check(list: List<Tag>) {
        val now = wallClock()
        val presence = list.associate { it.address to it.presence(now) }
        val due = list.filter { t ->
            val p = presence[t.address]
            val lost = previous[t.address] == Presence.CONNECTED && p == Presence.DISCONNECTED
            val stale = p == Presence.CONNECTED && clock() - (lastFix[t.address] ?: Long.MIN_VALUE / 2) >= refreshMs
            lost || stale
        }.map { it.address }
        val previous0 = previous
        previous = presence
        pending += due
        if (due.any { previous0[it] == Presence.CONNECTED && presence[it] == Presence.DISCONNECTED }) pendingPrecise = true
        if (pending.isEmpty() || busy || !locator.allowed()) return
        val batch = pending.toList()
        val precise = pendingPrecise
        pending.clear()
        pendingPrecise = false
        busy = true
        scope.launch {
            try {
                val fix = locator.current(precise) ?: return@launch
                val t = clock()
                batch.forEach { a ->
                    lastFix[a] = t
                    // A new spot replaces the old place name even if the geocoder had nothing for it.
                    tags.update(a) { it.copy(lastSeenLat = fix.lat, lastSeenLon = fix.lon, lastSeenAccuracyM = fix.accuracyM, lastSeenPlace = fix.place) }
                }
            } finally {
                busy = false
            }
        }
    }
}
