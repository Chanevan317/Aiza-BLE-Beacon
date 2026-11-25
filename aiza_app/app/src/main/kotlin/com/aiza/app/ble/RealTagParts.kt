package com.aiza.app.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiza.app.data.ProximitySource
import com.aiza.app.data.TagController
import com.aiza.app.separation.LinkEvent
import com.aiza.app.separation.TagLink
import com.aiza.core.model.RssiSample
import com.aiza.platform.ble.LinkState
import com.aiza.platform.ble.TagScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** Ring and find mode over GATT (FR-RNG-01, FR-HC-01). */
class RealTagController(private val hub: TagHub) : TagController {
    override suspend fun ring(address: String, on: Boolean): Result<Unit> = run(address) { it.ring(on) }
    override suspend fun setFindMode(address: String, on: Boolean): Result<Unit> = run(address) { it.setFindMode(on, 300) }

    private suspend fun run(address: String, block: suspend (com.aiza.platform.ble.TagConnection) -> Boolean): Result<Unit> {
        val c = hub.connection(address) ?: return Result.failure(IllegalStateException("Tag not connected"))
        if (!c.awaitReady(5_000)) return Result.failure(IllegalStateException("Tag out of range"))
        return if (block(c)) Result.success(Unit) else Result.failure(IllegalStateException("Tag did not respond"))
    }
}

/** Link state and connection RSSI once a second, for the left-behind alert (FR-SEP-01). */
class RealTagLink(private val hub: TagHub) : TagLink {
    override fun watch(address: String): Flow<LinkEvent> = flow {
        var last: LinkState? = null
        while (true) {
            val c = hub.connection(address)
            val s = c?.state?.value ?: LinkState.DISCONNECTED
            if (s != last) {
                if (s == LinkState.READY) emit(LinkEvent.Connected)
                else if (last == LinkState.READY) emit(LinkEvent.Disconnected)
                last = s
            }
            if (s == LinkState.READY) c?.readRssi()?.let { emit(LinkEvent.Rssi(it)) }
            delay(1_000)
        }
    }
}

/**
 * Search signal (FR-HC-02): one continuous low-latency scan for the tag's advertisements, timestamped by
 * the Bluetooth stack. If no advertisement arrived for 1 s (tag connected and not advertising, or scan not
 * allowed), it falls back to connection RSSI every 250 ms.
 */
class RealProximitySource(private val context: Context, private val hub: TagHub) : ProximitySource {
    private val scanner = TagScanner(context)

    override fun rssi(address: String): Flow<RssiSample> = channelFlow {
        var lastScanNanos = 0L
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            launch {
                @Suppress("MissingPermission")
                scanner.rssi()
                    .filter { it.tagId.equals(address, ignoreCase = true) }
                    .catch { Log.w("AizaScan", "Scan stopped", it) }
                    .collect { lastScanNanos = SystemClock.elapsedRealtimeNanos(); send(it.copy(tagId = address)) }
            }
        }
        launch {
            while (true) {
                delay(250)
                if (SystemClock.elapsedRealtimeNanos() - lastScanNanos > 1_000_000_000L) {
                    hub.connection(address)?.readRssi()?.let { send(RssiSample(SystemClock.elapsedRealtimeNanos(), address, it)) }
                }
            }
        }
    }
}
