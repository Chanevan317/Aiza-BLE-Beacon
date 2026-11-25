package com.aiza.app.data

import android.content.Context
import com.aiza.data.tags.DataStoreTagRepository
import com.aiza.app.ring.AndroidRingOutput
import com.aiza.app.ring.PhoneRinger
import com.aiza.app.separation.AndroidSeparationAlerts
import com.aiza.app.separation.DemoTagLink
import com.aiza.app.separation.SeparationWatcher
import com.aiza.app.service.SeparationAlertService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import kotlinx.coroutines.launch

/** Manual dependency container. Demo mode swaps in fake tags so the UI works without hardware. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(context)
    val settings: StateFlow<Settings?> = settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    private val realTags = DataStoreTagRepository(context)
    private val demoTags = DemoTagRepository(appScope, isDrivenElsewhere = { it in demoLink.away.value })

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

    /** Demo stand-in for tag button presses. TODO: swap for the real Button Event source with the BLE layer. */
    val demoButton = DemoTagButton(appScope)
    val buttonEvents: TagButtonEvents = demoButton
    val phoneRinger = PhoneRinger(appScope, AndroidRingOutput(context))

    /** Demo stand-in for the kept-open tag link; "Leave behind" fades the signal. TODO: real link with #6. */
    val demoLink = DemoTagLink(tags)
    val separationAlerts = AndroidSeparationAlerts(context)
    val separation = SeparationWatcher(appScope, tags, settings, demoLink, separationAlerts)

    /** Set when a notification asks to open a tag; the nav host consumes it. */
    val openTagRequest = MutableStateFlow<String?>(null)

    init {
        // A short press on a paired tag rings the phone, unless switched off for that tag (FR-RNG-02).
        appScope.launch {
            buttonEvents.presses.collect { address ->
                val tag = tags.tag(address).first()?.takeIf { it.buttonRingsPhone } ?: return@collect
                phoneRinger.onButtonPress(address, tag.name, tag.icon)
            }
        }
        // Keep the process alive with a foreground service while any tag is watched (FR-SEP-01).
        appScope.launch {
            separation.watching.map { it.isNotEmpty() }.distinctUntilChanged().collect { any ->
                if (any) SeparationAlertService.start(context) else SeparationAlertService.stop(context)
            }
        }
    }
}
