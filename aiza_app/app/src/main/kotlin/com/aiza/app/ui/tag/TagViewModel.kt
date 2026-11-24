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

    fun setAlert(on: Boolean) = viewModelScope.launch { container.tags.update(address) { it.copy(leftBehindAlert = on) } }

    fun edit(name: String, icon: TagIcon) = viewModelScope.launch {
        container.tags.update(address) { it.copy(name = name.trim().ifEmpty { it.name }, icon = icon) }
    }

    fun unpair(done: () -> Unit) = viewModelScope.launch {
        container.tags.remove(address)
        done()
    }
}
