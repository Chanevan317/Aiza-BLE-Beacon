package com.aiza.app.ui.pairing

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import com.aiza.app.data.DiscoveredTag
import com.aiza.platform.ble.AizaGatt

/**
 * Real pairing through the Android system dialog (FR-SET-01). The system scans for tags advertising the
 * AIZA service, the user picks one, and the result Intent carries the chosen device.
 * After association, PairingViewModel bonds (LE Secure Connections) and reads serial/firmware over GATT.
 */
object CompanionPairing {
    fun start(context: Context, onIntentSender: (IntentSender) -> Unit, onError: (String) -> Unit) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) { onError("Pairing is not supported on this phone"); return }
        val filter = BluetoothLeDeviceFilter.Builder()
            // Only tags in pairing mode advertise the pairing UUID (BLE_PROTOCOL.md), so the list stays short.
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(AizaGatt.AIZA_PAIRING)).build())
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        cdm.associate(request, context.mainExecutor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onIntentSender(intentSender)
            override fun onAssociationCreated(associationInfo: AssociationInfo) = Unit
            override fun onFailure(error: CharSequence?) = onError(error?.toString() ?: "Pairing failed")
        })
    }

    @Suppress("DEPRECATION")
    fun result(data: Intent?): DiscoveredTag? {
        data ?: return null
        val address = if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.deviceMacAddress?.toString()?.uppercase()
        } else {
            data.getParcelableExtra<ScanResult>(CompanionDeviceManager.EXTRA_DEVICE)?.device?.address
        } ?: return null
        return DiscoveredTag(address = address, serial = "AZ-" + address.replace(":", "").takeLast(8), rssiDbm = -60)
    }

    /** Drops the companion association for [address] when the tag is unpaired. */
    fun forget(context: Context, address: String) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching {
            cdm.myAssociations.filter { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
                .forEach { cdm.disassociate(it.id) }
        }
    }
}
