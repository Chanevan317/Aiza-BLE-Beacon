package com.aiza.platform.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Bonding with LE Secure Connections after the system pairing dialog (FR-SET-01), and unbonding (FR-SET-03). */
@SuppressLint("MissingPermission")
object TagBonding {
    /** Bonds with [address] if not bonded yet. Returns the identity address to store, or null on failure. */
    suspend fun bond(context: Context, address: String, timeoutMs: Long = 30_000): String? {
        val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            val done = CompletableDeferred<Boolean>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val d = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d?.address != device.address) return
                    when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                        BluetoothDevice.BOND_BONDED -> done.complete(true)
                        BluetoothDevice.BOND_NONE -> done.complete(false)
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_EXPORTED)
            try {
                if (!device.createBond()) return null
                if (withTimeoutOrNull(timeoutMs) { done.await() } != true) return null
            } finally {
                context.unregisterReceiver(receiver)
            }
        }
        // The real tag uses a rotating address; store the identity address the phone resolves it to.
        val identity = if (Build.VERSION.SDK_INT >= 35) runCatching { device.identityAddressWithType.address }.getOrNull() else null
        return (identity ?: device.address).uppercase()
    }

    /** Removes the system bond. There is no public API for this, so it uses the long-standing hidden method. */
    fun removeBond(context: Context, address: String) {
        val device = context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
        runCatching { device.javaClass.getMethod("removeBond").invoke(device) }
    }
}
