package com.aiza.platform.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.aiza.core.model.RssiSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * One continuous low-latency scan per search (FR-HC-02: never more than 5 scan starts in 30 s).
 * Timestamps come from ScanResult.timestampNanos (elapsedRealtimeNanos), matching sensor events (NFR-DAT-01).
 */
class TagScanner(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun rssi(tagIdOf: (ScanResult) -> String = { it.device.address }): Flow<RssiSample> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner ?: run { close(IllegalStateException("Bluetooth off")); return@callbackFlow }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(RssiSample(result.timestampNanos, tagIdOf(result), result.rssi))
            }
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed: $errorCode"))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(AizaGatt.AIZA_SERVICE)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        scanner.startScan(filters, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }
}
