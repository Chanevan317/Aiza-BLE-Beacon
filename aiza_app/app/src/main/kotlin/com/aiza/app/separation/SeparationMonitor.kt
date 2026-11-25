package com.aiza.app.separation

/** Why a tag counts as left behind (FR-SEP-02). */
enum class LeftBehindReason { LINK_LOST, WEAK_SIGNAL }

sealed interface SeparationEvent {
    data class LeftBehind(val reason: LeftBehindReason) : SeparationEvent
    data object BackInRange : SeparationEvent
}

/**
 * Decides when one tag is left behind (FR-SEP-02). Pure logic: feed it link events and RSSI with
 * timestamps (ms, monotonic), call [tick] about once a second, and act on the events it returns.
 *
 * - Link lost: the connection stays down for [dropGraceMs] (short blips are ignored).
 * - Weak signal: the mean RSSI over the last [windowMs] is below [thresholdDbm].
 * - Back in range: connected again with a mean at least [recoverMarginDb] above the threshold, so the
 *   alert does not flap while the signal hovers around it.
 * No alert before the tag has been connected once, so starting the app far from a tag stays quiet.
 * Both rules fire well inside the 30 s the SRS allows.
 */
class SeparationMonitor(
    private val thresholdDbm: Int,
    private val windowMs: Long = 5_000,
    private val dropGraceMs: Long = 5_000,
    private val recoverMarginDb: Int = 5,
) {
    private var connected = false
    private var lostAtMs: Long? = null
    private val samples = ArrayDeque<Pair<Long, Int>>()

    var alerted: LeftBehindReason? = null
        private set

    fun onConnected(@Suppress("UNUSED_PARAMETER") tMs: Long) {
        connected = true
        lostAtMs = null
    }

    fun onDisconnected(tMs: Long) {
        if (connected) lostAtMs = tMs
        connected = false
        samples.clear()
    }

    fun onRssi(tMs: Long, dbm: Int) {
        if (!connected) return
        samples.addLast(tMs to dbm)
        prune(tMs)
    }

    fun tick(tMs: Long): SeparationEvent? {
        prune(tMs)
        val mean = windowMean(tMs)
        if (alerted == null) {
            val lost = lostAtMs
            val reason = when {
                !connected && lost != null && tMs - lost >= dropGraceMs -> LeftBehindReason.LINK_LOST
                connected && mean != null && mean < thresholdDbm -> LeftBehindReason.WEAK_SIGNAL
                else -> null
            } ?: return null
            alerted = reason
            return SeparationEvent.LeftBehind(reason)
        }
        if (connected && mean != null && mean >= thresholdDbm + recoverMarginDb) {
            alerted = null
            return SeparationEvent.BackInRange
        }
        return null
    }

    private fun prune(tMs: Long) {
        while (samples.isNotEmpty() && samples.first().first < tMs - windowMs) samples.removeFirst()
    }

    /** Mean of the window, or null until the readings span most of it (one stray reading is not enough). */
    private fun windowMean(tMs: Long): Double? {
        if (samples.size < 3 || tMs - samples.first().first < windowMs - 1_500) return null
        return samples.sumOf { it.second }.toDouble() / samples.size
    }
}
