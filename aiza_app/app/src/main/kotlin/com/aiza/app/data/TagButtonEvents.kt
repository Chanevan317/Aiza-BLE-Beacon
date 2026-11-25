package com.aiza.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Demo mode stand-in for the tag's Button Event notification. [pressLater] waits a few seconds so the
 * tester can lock the phone or leave the app first. The real source is TagHub.presses.
 */
class DemoTagButton(private val scope: CoroutineScope) {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short presses of a tag's button, by tag address (FR-RNG-02). */
    val presses: SharedFlow<String> = flow

    fun pressLater(address: String, delayMs: Long = 5_000) {
        scope.launch { delay(delayMs); flow.emit(address) }
    }
}
