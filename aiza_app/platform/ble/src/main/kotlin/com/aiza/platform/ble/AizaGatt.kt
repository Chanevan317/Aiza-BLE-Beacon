package com.aiza.platform.ble

import java.util.UUID

/** GATT layout of the tag (SRS §4.3). */
object AizaGatt {
    private fun sig(short: Int): UUID = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", short))

    val IMMEDIATE_ALERT_SERVICE: UUID = sig(0x1802)
    val LINK_LOSS_SERVICE: UUID = sig(0x1803)
    val BATTERY_SERVICE: UUID = sig(0x180F)
    val DEVICE_INFO_SERVICE: UUID = sig(0x180A)
    val ALERT_LEVEL: UUID = sig(0x2A06)
    val BATTERY_LEVEL: UUID = sig(0x2A19)

    // TODO: assign the real AIZA 128-bit UUIDs and keep them in sync with the firmware.
    val AIZA_SERVICE: UUID = UUID.fromString("a12a0000-0000-4000-8000-000000000000")
    val FIND_MODE_CONTROL: UUID = UUID.fromString("a12a0001-0000-4000-8000-000000000000")
    val BUTTON_EVENT: UUID = UUID.fromString("a12a0002-0000-4000-8000-000000000000")
    val SETTINGS: UUID = UUID.fromString("a12a0003-0000-4000-8000-000000000000")
}
