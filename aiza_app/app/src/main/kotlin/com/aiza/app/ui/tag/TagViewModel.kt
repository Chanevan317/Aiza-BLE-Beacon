package com.aiza.app.ui.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RingState { IDLE, STARTING, RINGING }

data class TagUiState(
    val loading: Boolean = true,
    val tag: Tag? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val ring: RingState = RingState.IDLE,
)

class TagViewModel(private val container: AppContainer, private val address: String) : ViewModel() {
    private val ring = MutableStateFlow(RingState.IDLE)
    private var ringTimeout: Job? = null
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(10_000) } }

    val state: StateFlow<TagUiState> = combine(container.tags.tag(address), clock, ring) { tag, now, r ->
        TagUiState(loading = false, tag = tag, nowMs = now, ring = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagUiState())

    /** Demo mode only: there is no real tag whose button could be pressed. */
    val demoMode: StateFlow<Boolean> = container.settings.map { it?.demoMode == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Pretend the tag's button was pressed in [DEMO_PRESS_DELAY_MS], so the tester can lock the phone first (FR-RNG-02). */
    fun pressDemoButton() = container.demoButton.pressLater(address, DEMO_PRESS_DELAY_MS)

    /** Second tap stops; stops by itself after 30 s (FR-RNG-01). */
    fun toggleRing() {
        if (ring.value != RingState.IDLE) {
            ringTimeout?.cancel()
            ring.value = RingState.IDLE
            viewModelScope.launch { container.tagController.ring(address, false) }
            return
        }
        ring.value = RingState.STARTING
        viewModelScope.launch {
            container.tagController.ring(address, true)
                .onSuccess {
                    ring.value = RingState.RINGING
                    ringTimeout = launch { delay(30_000); ring.value = RingState.IDLE }
                }
                .onFailure { ring.value = RingState.IDLE }
        }
    }

    fun setButtonRingsPhone(on: Boolean) = viewModelScope.launch {
        container.tags.update(address) { it.copy(buttonRingsPhone = on) }
        if (!on && container.phoneRinger.ringing.value?.address == address) container.phoneRinger.stop()
    }

    fun setAlert(on: Boolean) = viewModelScope.launch { container.tags.update(address) { it.copy(leftBehindAlert = on) } }

    fun edit(name: String, icon: TagIcon) = viewModelScope.launch {
        container.tags.update(address) { it.copy(name = name.trim().ifEmpty { it.name }, icon = icon) }
    }

    fun unpair(done: () -> Unit) = viewModelScope.launch {
        container.tags.remove(address)
        done()
    }

    companion object {
        const val DEMO_PRESS_DELAY_MS = 5_000L
    }
}
