@file:OptIn(ExperimentalMaterial3Api::class)

package com.aiza.app.ui.tag

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiza.app.ui.aizaViewModel
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.BatteryGlyph
import com.aiza.app.ui.components.batteryColor
import com.aiza.app.ui.components.fraction
import com.aiza.data.tags.BatteryLevel
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.PulseRings
import com.aiza.app.ui.components.SectionLabel
import com.aiza.app.ui.components.SignalBars
import com.aiza.app.ui.components.StatusDot
import com.aiza.app.ui.components.TagAvatar
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.components.formatDate
import com.aiza.app.ui.components.relativeTime
import com.aiza.app.ui.home.statusLine
import com.aiza.app.ui.pairing.IconPicker
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.data.tags.Tag
import kotlinx.coroutines.launch

@Composable
fun TagScreen(address: String, onBack: () -> Unit, onSearch: () -> Unit) {
    val vm = aizaViewModel(key = "tag-$address") { TagViewModel(it, address) }
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp), colors = barButtonColors()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalIconButton({ editing = true }, colors = barButtonColors()) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit name and icon")
                    }
                    Box {
                        FilledTonalIconButton({ menu = true }, colors = barButtonColors()) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Unpair tag") },
                                leadingIcon = { Icon(Icons.Rounded.LinkOff, null) },
                                onClick = { menu = false; confirmUnpair = true },
                            )
                        }
                    }
                    HSpace(8.dp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val tag = state.tag
        if (tag == null) {
            if (!state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This tag is no longer paired.") }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Hero(tag, state.nowMs) }
            item { SearchButton(onSearch) }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingTile(state.ring, vm::toggleRing, Modifier.weight(1f).fillMaxHeight())
                    AlertTile(tag.leftBehindAlert, vm::setAlert, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item { SectionLabel("Details") }
            item { DetailsGrid(tag, state.nowMs, onCopied = { /* handled inside */ }, snackbar = snackbar) }
            item {
                VSpace(8.dp)
                OutlinedButton(
                    onClick = { confirmUnpair = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.LinkOff, null)
                    HSpace(8.dp)
                    Text("Unpair tag")
                }
            }
        }
        if (editing) EditDialog(tag, onDismiss = { editing = false }, onSave = { n, i -> vm.edit(n, i); editing = false })
        if (confirmUnpair) AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            icon = { Icon(Icons.Rounded.LinkOff, null) },
            title = { Text("Unpair ${tag.name}?") },
            text = { Text("The tag will be removed from this phone and its saved data deleted. To use it again, pair it from scratch.") },
            confirmButton = {
                TextButton({ confirmUnpair = false; vm.unpair(onBack) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Unpair")
                }
            },
            dismissButton = { TextButton({ confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun barButtonColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun Hero(tag: Tag, nowMs: Long) {
    val nearby = tag.isNearby(nowMs)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 20.dp)) {
            TagAvatar(tag.icon, size = 96.dp, dimmed = !nearby)
        }
        Text(tag.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        VSpace(8.dp)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (nearby) AizaTheme.extra.success else MaterialTheme.colorScheme.outline)
                HSpace(8.dp)
                Text(statusLine(tag, nowMs), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The main action: opens the active search screen (FR-HC-01). */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.background(Brush.linearGradient(AizaTheme.extra.brandGradient)).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Radar, null, tint = Color.White)
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text("Start active search", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Hot/cold guidance and direction", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun RingTile(state: RingState, onClick: () -> Unit, modifier: Modifier) {
    val active = state != RingState.IDLE
    val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow, label = "ring-bg")
    val fg by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "ring-fg")
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = bg, border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, AizaTheme.extra.cardBorder)) {
        Column(Modifier.padding(16.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                if (state == RingState.RINGING) PulseRings(fg, Modifier.size(40.dp), rings = 2, periodMs = 900, minRadiusFraction = 0.5f)
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = fg)
            }
            VSpace(10.dp)
            AnimatedContent(state, label = "ring-label") { s ->
                Column {
                    Text(
                        when (s) { RingState.IDLE -> "Ring"; RingState.STARTING -> "Connecting…"; RingState.RINGING -> "Ringing" },
                        style = MaterialTheme.typography.titleSmall, color = fg,
                    )
                    Text(
                        if (s == RingState.IDLE) "Play a sound on the tag" else "Tap to stop",
                        style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertTile(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    AizaCard(modifier, onClick = { onChange(!on) }) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.weight(1f))
            Switch(on, onCheckedChange = onChange)
        }
        VSpace(10.dp)
        Text("Left-behind alert", style = MaterialTheme.typography.titleSmall)
        Text(if (on) "On · warns if left behind" else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailsGrid(tag: Tag, nowMs: Long, onCopied: () -> Unit, snackbar: SnackbarHostState) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val nearby = tag.isNearby(nowMs)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile("Battery", Modifier.weight(1f).fillMaxHeight(), leading = {
                BatteryGlyph(
                    tag.batteryLevel?.fraction ?: 0f,
                    fill = batteryColor(tag.batteryLevel),
                    outline = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }) {
                val level = tag.batteryLevel
                Text(level?.label ?: "—", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when (level) {
                        null -> "Unknown"
                        BatteryLevel.FULL, BatteryLevel.GOOD -> "No need to charge"
                        BatteryLevel.LOW -> "Charge soon"
                        BatteryLevel.CRITICAL -> "Charge now"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (level == BatteryLevel.LOW || level == BatteryLevel.CRITICAL) batteryColor(level)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InfoTile("Signal", Modifier.weight(1f).fillMaxHeight(), leading = { SignalBars(if (nearby) tag.rssiDbm else null) }) {
                Text(if (nearby && tag.rssiDbm != null) "${tag.rssiDbm}" else "—", style = MaterialTheme.typography.headlineMedium)
                Text(if (nearby) "dBm · in range" else "Out of range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AizaCard(Modifier.fillMaxWidth(), padding = 4.dp) {
            DetailRow(Icons.Rounded.Badge, "Tag ID", tag.serial, trailing = {
                IconButton({
                    onCopied()
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Tag ID", tag.serial)))
                        snackbar.showSnackbar("Tag ID copied")
                    }
                }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy tag ID", modifier = Modifier.size(20.dp)) }
            })
            DetailRow(Icons.Rounded.Place, "Last seen", tag.lastSeenEpochMs?.let { relativeTime(it, nowMs) + (tag.lastSeenPlace?.let { p -> " · $p" } ?: "") } ?: "Never")
            DetailRow(Icons.Rounded.Bluetooth, "Bluetooth address", tag.address, mono = true)
            DetailRow(Icons.Rounded.Memory, "Firmware", tag.firmwareVersion ?: "Unknown")
            DetailRow(Icons.Rounded.Event, "Paired", formatDate(tag.pairedEpochMs))
        }
    }
}

@Composable
private fun InfoTile(label: String, modifier: Modifier, leading: @Composable () -> Unit, content: @Composable () -> Unit) {
    AizaCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            leading()
        }
        VSpace(10.dp)
        content()
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, mono: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HSpace(14.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun EditDialog(tag: Tag, onDismiss: () -> Unit, onSave: (String, com.aiza.data.tags.TagIcon) -> Unit) {
    var name by rememberSaveable { mutableStateOf(tag.name) }
    var icon by rememberSaveable { mutableStateOf(tag.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tag") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TagAvatar(icon, size = 64.dp)
                VSpace(16.dp)
                OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") }, shape = MaterialTheme.shapes.medium)
                VSpace(16.dp)
                IconPicker(icon, onPick = { icon = it })
            }
        },
        confirmButton = { Button({ onSave(name, icon) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
