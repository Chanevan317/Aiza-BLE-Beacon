package com.aiza.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiza.app.data.AppContainer
import com.aiza.data.tags.HomeLayout
import com.aiza.data.tags.Settings
import com.aiza.data.tags.Tag
import com.aiza.data.tags.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val tags: List<Tag> = emptyList(),
    val nowMs: Long = System.currentTimeMillis(),
    val settings: Settings = Settings(),
) {
    val nearbyCount: Int get() = tags.count { it.presence(nowMs).heard }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(15_000) } }

    val state: StateFlow<HomeState> = combine(container.tags.tags, container.settingsRepository.settings, clock) { tags, s, now ->
        HomeState(loading = false, tags = tags.sortedByDescending { it.lastSeenEpochMs ?: 0 }, nowMs = now, settings = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun toggleLayout() = viewModelScope.launch {
        val next = if (state.value.settings.homeLayout == HomeLayout.LIST) HomeLayout.CARDS else HomeLayout.LIST
        container.settingsRepository.setHomeLayout(next)
    }

    /** The theme button cycles System → Light → Dark. */
    fun cycleTheme() = viewModelScope.launch {
        val next = when (state.value.settings.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        container.settingsRepository.setThemeMode(next)
    }
}
