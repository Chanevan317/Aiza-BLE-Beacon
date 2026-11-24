package com.aiza.data.tags

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the home screen shows tags. */
enum class HomeLayout { LIST, CARDS }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val homeLayout: HomeLayout = HomeLayout.LIST,
    val demoMode: Boolean = true,
    val onboardingDone: Boolean = false,
    val searchSound: Boolean = true,
    val searchVibration: Boolean = true,
    /** Left-behind alert fires when the signal stays below this (FR-SEP-02). */
    val alertThresholdDbm: Int = -85,
)

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val layout = stringPreferencesKey("home_layout")
        val demo = booleanPreferencesKey("demo")
        val onboarded = booleanPreferencesKey("onboarding_done")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val threshold = intPreferencesKey("alert_threshold_dbm")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            homeLayout = p[Keys.layout]?.let { runCatching { HomeLayout.valueOf(it) }.getOrNull() } ?: d.homeLayout,
            demoMode = p[Keys.demo] ?: d.demoMode,
            onboardingDone = p[Keys.onboarded] ?: d.onboardingDone,
            searchSound = p[Keys.sound] ?: d.searchSound,
            searchVibration = p[Keys.vibration] ?: d.searchVibration,
            alertThresholdDbm = p[Keys.threshold] ?: d.alertThresholdDbm,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) { context.settingsStore.edit { it[Keys.theme] = mode.name } }
    suspend fun setHomeLayout(layout: HomeLayout) { context.settingsStore.edit { it[Keys.layout] = layout.name } }
    suspend fun setOnboardingDone(done: Boolean) { context.settingsStore.edit { it[Keys.onboarded] = done } }
    suspend fun setDemoMode(on: Boolean) { context.settingsStore.edit { it[Keys.demo] = on } }
    suspend fun setSearchSound(on: Boolean) { context.settingsStore.edit { it[Keys.sound] = on } }
    suspend fun setSearchVibration(on: Boolean) { context.settingsStore.edit { it[Keys.vibration] = on } }
    suspend fun setAlertThreshold(dbm: Int) { context.settingsStore.edit { it[Keys.threshold] = dbm } }
}
