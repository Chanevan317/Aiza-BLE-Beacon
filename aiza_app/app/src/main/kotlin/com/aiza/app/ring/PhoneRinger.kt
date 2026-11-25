package com.aiza.app.ring

import com.aiza.data.tags.TagIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The tag that is ringing the phone. */
data class PhoneRing(val address: String, val tagName: String, val icon: TagIcon, val startedAtMs: Long)

/** Sound, vibration and notification for a ring. Split out so the ring logic can be tested without Android. */
interface RingOutput {
    fun start(ring: PhoneRing)
    fun stop()
}

/**
 * "Tag is ringing your phone" (FR-RNG-02). A short press on the tag starts the ring; a second press, the Stop
 * button or [timeoutMs] stops it. A press from another tag while ringing switches the ring to that tag.
 */
class PhoneRinger(
    private val scope: CoroutineScope,
    private val output: RingOutput,
    private val timeoutMs: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _ringing = MutableStateFlow<PhoneRing?>(null)
    val ringing: StateFlow<PhoneRing?> = _ringing
    private var timeout: Job? = null

    @Synchronized
    fun onButtonPress(address: String, tagName: String, icon: TagIcon = TagIcon.TAG) {
        val current = _ringing.value
        if (current?.address == address) { stop(); return }
        val ring = PhoneRing(address, tagName, icon, clock())
        output.start(ring)
        _ringing.value = ring
        timeout?.cancel()
        timeout = scope.launch { delay(timeoutMs); stopIf(ring) }
    }

    @Synchronized
    fun stop() {
        timeout?.cancel()
        timeout = null
        if (_ringing.value == null) return
        _ringing.value = null
        output.stop()
    }

    @Synchronized
    private fun stopIf(ring: PhoneRing) { if (_ringing.value == ring) stop() }
}
