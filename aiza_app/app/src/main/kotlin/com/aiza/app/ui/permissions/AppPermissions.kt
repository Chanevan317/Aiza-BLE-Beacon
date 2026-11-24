package com.aiza.app.ui.permissions

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

/** Runtime permissions AIZA needs (SRS §4.4), grouped the way the user sees them. */
enum class AppPermission(
    val title: String,
    val reason: String,
    val icon: ImageVector,
    val manifest: List<String>,
    val minSdk: Int = 31,
) {
    NEARBY_DEVICES(
        "Nearby devices",
        "Find, pair and connect to your tags over Bluetooth.",
        Icons.Rounded.Bluetooth,
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
    ),
    LOCATION(
        "Location",
        "Used to work out the direction to a tag and to remember where you last had it. It never leaves your phone.",
        Icons.Rounded.LocationOn,
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    ),
    NOTIFICATIONS(
        "Notifications",
        "Warn you when you leave a tag behind.",
        Icons.Rounded.NotificationsActive,
        listOf(Manifest.permission.POST_NOTIFICATIONS),
        minSdk = 33,
    );

    val applies: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    fun isGranted(context: Context): Boolean =
        !applies || manifest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    companion object {
        val relevant: List<AppPermission> get() = entries.filter { it.applies }
        fun allGranted(context: Context) = relevant.all { it.isGranted(context) }
    }
}

/** Phone-level switches that must be on as well as the permissions. */
object SystemSwitches {
    fun bluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    fun locationOn(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true
}

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
