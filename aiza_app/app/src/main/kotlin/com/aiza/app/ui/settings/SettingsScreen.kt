@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiza.app.ui.settings

import androidx.compose.ui.graphics.Color
import com.aiza.app.ui.components.topBarBlur
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.AizaApplication
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.AizaLogoLockup
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.SectionLabel
import com.aiza.app.ui.components.VSpace
import com.aiza.data.tags.HomeLayout
import com.aiza.data.tags.Settings
import com.aiza.data.tags.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onResearcher: () -> Unit, onPermissions: () -> Unit) {
    val container = (LocalContext.current.applicationContext as AizaApplication).container
    val repo = container.settingsRepository
    val settings by container.settings.collectAsStateWithLifecycle()
    val s = settings ?: Settings()
    val scope = rememberCoroutineScope()

    val haze = rememberHazeState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = Modifier.topBarBlur(haze),
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onBack, Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.hazeSource(haze), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
            item {
                SectionLabel("Appearance")
                AizaCard(Modifier.fillMaxWidth()) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    VSpace(12.dp)
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (mode, label, icon) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { scope.launch { repo.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                icon = { Icon(icon, null, Modifier.padding(end = 2.dp)) },
                            ) { Text(label) }
                        }
                    }
                    VSpace(18.dp)
                    Text("Home view", style = MaterialTheme.typography.titleSmall)
                    VSpace(12.dp)
                    val layouts = listOf(
                        Triple(HomeLayout.LIST, "List", Icons.AutoMirrored.Rounded.ViewList),
                        Triple(HomeLayout.CARDS, "Cards", Icons.Rounded.GridView),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        layouts.forEachIndexed { i, (layout, label, icon) ->
                            SegmentedButton(
                                selected = s.homeLayout == layout,
                                onClick = { scope.launch { repo.setHomeLayout(layout) } },
                                shape = SegmentedButtonDefaults.itemShape(i, layouts.size),
                                icon = { Icon(icon, null, Modifier.padding(end = 2.dp)) },
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                SectionLabel("Search")
                AizaCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    SwitchRow(Icons.AutoMirrored.Rounded.VolumeUp, "Sound", "Beeps speed up as you get closer", s.searchSound) {
                        scope.launch { repo.setSearchSound(it) }
                    }
                    Divider()
                    SwitchRow(Icons.Rounded.Vibration, "Vibration", "Pulses speed up as you get closer", s.searchVibration) {
                        scope.launch { repo.setSearchVibration(it) }
                    }
                    Divider()
                    SwitchRow(Icons.Rounded.Explore, "Show compass", "N, E, S, W and degrees on the search screen", s.showCompass) {
                        scope.launch { repo.setShowCompass(it) }
                    }
                }
            }
            item {
                SectionLabel("Left-behind alert")
                AizaCard(Modifier.fillMaxWidth()) {
                    var value by remember(s.alertThresholdDbm) { mutableFloatStateOf(s.alertThresholdDbm.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        HSpace(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Sensitivity", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Alert when the signal stays below ${value.toInt()} dBm",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        onValueChangeFinished = { scope.launch { repo.setAlertThreshold(value.toInt()) } },
                        valueRange = -95f..-65f,
                        steps = 5,
                    )
                    Row {
                        Text("Sooner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Later", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SectionLabel("Privacy")
                AizaCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    NavRow(Icons.Rounded.Shield, "Permissions", "Bluetooth, location and notifications", onPermissions)
                }
            }
            item {
                SectionLabel("Development")
                AizaCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    SwitchRow(Icons.Rounded.Widgets, "Demo mode", "Sample tags and simulated signal — no hardware needed", s.demoMode) {
                        scope.launch { repo.setDemoMode(it) }
                    }
                    Divider()
                    NavRow(Icons.Rounded.Science, "Researcher mode", "Trials, ground truth and log export", onResearcher)
                }
            }
            item {
                SectionLabel("About")
                AizaCard(Modifier.fillMaxWidth()) {
                    AizaLogoLockup(markSize = 28.dp)
                    VSpace(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Version ${appVersion()} · R&D prototype. All data stays on this phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, color = androidx.compose.ui.graphics.Color.Transparent, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            HSpace(16.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HSpace(8.dp)
            Switch(checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            HSpace(16.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
