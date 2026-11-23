package com.aiza.platform.ble

/**
 * GATT client for one bonded tag: ring (FR-RNG-01), battery (FR-STA-02), find-mode control (FR-HC-01),
 * button events (FR-RNG-02), link-loss level (FR-SEP).
 * TODO: implement on BluetoothGatt with a serialized operation queue.
 */
interface TagConnection {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun ring(on: Boolean)
    suspend fun setFindMode(on: Boolean, timeoutSeconds: Int = 300)
    suspend fun readBatteryPercent(): Int
}
