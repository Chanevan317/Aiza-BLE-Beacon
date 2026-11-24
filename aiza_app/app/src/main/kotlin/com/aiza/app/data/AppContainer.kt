package com.aiza.app.data

import android.content.Context
import com.aiza.data.tags.DataStoreTagRepository
import com.aiza.data.trial.TrialStore
import com.aiza.platform.sensors.MotionSensors
import com.aiza.data.tags.DemoTagRepository
import com.aiza.data.tags.Settings
import com.aiza.data.tags.SettingsRepository
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers

/** Manual dependency container. Demo mode swaps in fake tags so the UI works without hardware. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context)
    val settings: StateFlow<Settings?> = settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    private val realTags = DataStoreTagRepository(context)
    private val demoTags = DemoTagRepository(appScope)

    private suspend fun active(): TagRepository = if (settingsRepository.settings.first().demoMode) demoTags else realTags

    /** The tag repository for the current mode, as a single facade the UI can use. */
    val tags: TagRepository = object : TagRepository {
        override val tags: Flow<List<Tag>> =
            settingsRepository.settings.flatMapLatest { if (it.demoMode) demoTags.tags else realTags.tags }
        override fun tag(address: String): Flow<Tag?> =
            settingsRepository.settings.flatMapLatest { if (it.demoMode) demoTags.tag(address) else realTags.tag(address) }
        override suspend fun add(tag: Tag) = active().add(tag)
        override suspend fun update(address: String, change: (Tag) -> Tag) = active().update(address, change)
        override suspend fun remove(address: String) = active().remove(address)
    }

    val tagController: TagController = DemoTagController()
    val discovery: TagDiscovery = DemoTagDiscovery()

    val motionSensors = MotionSensors(context)
    val proximity: ProximitySource = DemoProximitySource(motionSensors)
    val trialStore = TrialStore(context)
    val trialRecorder = TrialRecorder(
        appScope, trialStore, motionSensors,
        appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?",
    )
}
