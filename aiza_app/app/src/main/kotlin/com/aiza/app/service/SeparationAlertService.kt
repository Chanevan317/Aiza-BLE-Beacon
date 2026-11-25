package com.aiza.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aiza.app.AizaApplication
import com.aiza.app.separation.AndroidSeparationAlerts
import kotlinx.coroutines.launch

/**
 * Foreground service of type connectedDevice that keeps the app alive, and so the tag links open, while
 * at least one tag has its left-behind alert on (FR-SEP-01, NFR-REL-01). The watching itself lives in
 * SeparationWatcher; this service only shows "Watching N tags" and stops when nothing is watched.
 */
class SeparationAlertService : LifecycleService() {
    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val container = (application as AizaApplication).container
        val count = container.separation.watching.value.size
        val started = runCatching {
            ServiceCompat.startForeground(
                this, AndroidSeparationAlerts.SERVICE_NOTIFICATION_ID,
                container.separationAlerts.serviceNotification(count.coerceAtLeast(1)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }
        if (started.isFailure) {
            // Missing Bluetooth permission or started from the background: watching still runs while the app is open.
            Log.w(TAG, "Could not start in the foreground", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            lifecycleScope.launch {
                container.separation.watching.collect { watched ->
                    if (watched.isEmpty()) stopSelf()
                    else getSystemService(android.app.NotificationManager::class.java).notify(
                        AndroidSeparationAlerts.SERVICE_NOTIFICATION_ID,
                        container.separationAlerts.serviceNotification(watched.size),
                    )
                }
            }
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "SeparationService"

        /**
         * Android 14+ only allows a connectedDevice service once Nearby devices (BLUETOOTH_CONNECT) is granted.
         * Without it, watching still runs while the app is open, and the Permissions screen asks for it.
         */
        fun canStart(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            if (!canStart(context)) return
            runCatching { context.startForegroundService(Intent(context, SeparationAlertService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start", it) }
        }

        fun stop(context: Context) = context.stopService(Intent(context, SeparationAlertService::class.java))
    }
}
