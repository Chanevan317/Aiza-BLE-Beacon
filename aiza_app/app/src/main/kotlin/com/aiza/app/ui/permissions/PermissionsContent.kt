package com.aiza.app.ui.permissions

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiza.app.ui.components.AizaCard
import com.aiza.app.ui.components.HSpace
import com.aiza.app.ui.components.VSpace
import com.aiza.app.ui.theme.AizaTheme

/** Live permission + system switch state; refreshed whenever the screen resumes (e.g. back from Settings). */
class PermissionState(
    val granted: Map<AppPermission, Boolean>,
    val bluetoothOn: Boolean,
    val locationOn: Boolean,
) {
    val allGranted get() = granted.values.all { it }
    val allReady get() = allGranted && bluetoothOn && locationOn
}

/** Holds the state and the request actions for permissions UI. */
class PermissionController(
    val state: PermissionState,
    val request: (AppPermission) -> Unit,
    val requestAll: () -> Unit,
    val turnOnBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
)

@Composable
fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // After a denial Android may stop showing its dialog; then we send the user to app settings.
    var asked by rememberSaveable { mutableStateOf(setOf<String>()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose {} }

    val state = remember(tick) {
        PermissionState(
            granted = AppPermission.relevant.associateWith { it.isGranted(context) },
            bluetoothOn = SystemSwitches.bluetoothOn(context),
            locationOn = SystemSwitches.locationOn(context),
        )
    }

    fun openAppSettings() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun requestPerms(perms: List<AppPermission>) {
        val missing = perms.filter { state.granted[it] == false }
        if (missing.isEmpty()) return
        val activity = context.findActivity()
        val blocked = missing.filter { p ->
            p.name in asked && activity != null && p.manifest.none { activity.shouldShowRequestPermissionRationale(it) }
        }
        if (blocked.isNotEmpty() && blocked.size == missing.size) { openAppSettings(); return }
        asked = asked + missing.map { it.name }
        launcher.launch((missing - blocked.toSet()).flatMap { it.manifest }.toTypedArray())
    }

    return PermissionController(
        state = state,
        request = { requestPerms(listOf(it)) },
        requestAll = { requestPerms(AppPermission.relevant) },
        turnOnBluetooth = {
            if (state.granted[AppPermission.NEARBY_DEVICES] == true) {
                @Suppress("MissingPermission")
                enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else requestPerms(listOf(AppPermission.NEARBY_DEVICES))
        },
        openLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
}

/** The list of permission rows plus Bluetooth / Location switch rows. Used by onboarding and the Permissions screen. */
@Composable
fun PermissionList(controller: PermissionController, modifier: Modifier = Modifier) {
    val s = controller.state
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPermission.relevant.forEach { p ->
            PermissionRow(p.icon, p.title, p.reason, done = s.granted[p] == true, action = "Allow") { controller.request(p) }
        }
        if (!s.bluetoothOn) {
            PermissionRow(Icons.Rounded.BluetoothDisabled, "Bluetooth is off", "Turn it on to reach your tags.", done = false, action = "Turn on", onClick = controller.turnOnBluetooth)
        }
        if (!s.locationOn) {
            PermissionRow(Icons.Rounded.LocationOff, "Location is off", "Android needs location switched on to scan for Bluetooth tags.", done = false, action = "Open", onClick = controller.openLocationSettings)
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, reason: String, done: Boolean, action: String, onClick: () -> Unit) {
    AizaCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(
                    if (done) AizaTheme.extra.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer, CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (done) AizaTheme.extra.success else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                VSpace(2.dp)
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HSpace(10.dp)
            AnimatedContent(done, label = "perm-$title") { isDone ->
                if (isDone) {
                    Surface(shape = CircleShape, color = AizaTheme.extra.success, modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = "$title allowed", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    FilledTonalButton(onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) { Text(action) }
                }
            }
        }
    }
}
