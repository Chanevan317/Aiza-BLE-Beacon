package com.aiza.app.data

import android.content.Context
import com.aiza.data.tags.DataStoreTagRepository
import com.aiza.app.ble.RealProximitySource
import com.aiza.app.ble.RealTagController
import com.aiza.app.ble.RealTagLink
import com.aiza.app.ble.TagHub
import com.aiza.app.ring.AndroidRingOutput
import com.aiza.app.ui.pairing.CompanionPairing
import com.aiza.platform.ble.TagBonding
import com.aiza.app.separation.TagLink
import kotlinx.coroutines.flow.merge
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
class AppContainer(val context: Context) {
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

    /** True in demo mode (the default until settings load). */
    private val demoMode: StateFlow<Boolean> =
        settingsRepository.settings.map { it.demoMode }.stateIn(appScope, SharingStarted.Eagerly, true)
    private val demoModeFlow: Flow<Boolean> = settingsRepository.settings.map { it.demoMode }.distinctUntilChanged()

    /** Real Bluetooth: one connection per paired tag while demo mode is off (SRS §4.3). */
    val tagHub = TagHub(context, appScope, realTags, demoModeFlow.map { !it })

    private val demoController = DemoTagController()
    private val realController = RealTagController(tagHub)
    val tagController: TagController = object : TagController {
        override suspend fun ring(address: String, on: Boolean) = (if (demoMode.value) demoController else realController).ring(address, on)
        override suspend fun setFindMode(address: String, on: Boolean) =
            (if (demoMode.value) demoController else realController).setFindMode(address, on)
    }
    val discovery: TagDiscovery = DemoTagDiscovery()

    val motionSensors = MotionSensors(context)
    private val demoProximity = DemoProximitySource(motionSensors)
    private val realProximity = RealProximitySource(context, tagHub)
    val proximity: ProximitySource = object : ProximitySource {
        override fun rssi(address: String) = demoModeFlow.flatMapLatest { if (it) demoProximity.rssi(address) else realProximity.rssi(address) }
    }
    val trialStore = TrialStore(context)
    val trialRecorder = TrialRecorder(
        appScope, trialStore, motionSensors,
        appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?",
    )

    /** Demo stand-in for tag button presses; real presses come from the tag hub. */
    val demoButton = DemoTagButton(appScope)
    private val buttonPresses: Flow<String> = merge(demoButton.presses, tagHub.presses)
    val phoneRinger = PhoneRinger(appScope, AndroidRingOutput(context))

    /** Demo stand-in for the kept-open tag link; "Leave behind" fades the signal. */
    val demoLink = DemoTagLink(tags)
    private val realLink = RealTagLink(tagHub)
    private val link: TagLink = object : TagLink {
        override fun watch(address: String) = demoModeFlow.flatMapLatest { if (it) demoLink.watch(address) else realLink.watch(address) }
    }
    val separationAlerts = AndroidSeparationAlerts(context)
    val separation = SeparationWatcher(appScope, tags, settings, link, separationAlerts)

    /** Set when a notification asks to open a tag; the nav host consumes it. */
    val openTagRequest = MutableStateFlow<String?>(null)

    init {
        // A short press on a paired tag rings the phone, unless switched off for that tag (FR-RNG-02).
        appScope.launch {
            buttonPresses.collect { address ->
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

    /** Unpair (FR-SET-03): forget the tag, its data, the system bond and the companion association. */
    suspend fun unpair(address: String) {
        val real = !demoMode.value
        tags.remove(address)
        if (real) {
            TagBonding.removeBond(context, address)
            CompanionPairing.forget(context, address)
        }
    }
}
