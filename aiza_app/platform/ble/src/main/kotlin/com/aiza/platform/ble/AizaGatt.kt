package com.aiza.platform.ble

import java.util.UUID

/** GATT layout and byte formats of the tag. Must match aiza_firmware/BLE_PROTOCOL.md (SRS §4.3). */
object AizaGatt {
    private fun sig(short: Int): UUID = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", short))
    private fun aiza(short: Int): UUID = UUID.fromString(String.format("9a99%04x-982c-45e1-a09e-5e8952971cdb", short))

    val IMMEDIATE_ALERT_SERVICE: UUID = sig(0x1802)
    val LINK_LOSS_SERVICE: UUID = sig(0x1803)
    val BATTERY_SERVICE: UUID = sig(0x180F)
    val DEVICE_INFO_SERVICE: UUID = sig(0x180A)
    val ALERT_LEVEL: UUID = sig(0x2A06)
    val BATTERY_LEVEL: UUID = sig(0x2A19)
    val MODEL_NUMBER: UUID = sig(0x2A24)
    val SERIAL_NUMBER: UUID = sig(0x2A25)
    val FIRMWARE_REVISION: UUID = sig(0x2A26)
    val CCCD: UUID = sig(0x2902)

    val AIZA_SERVICE: UUID = aiza(0x0000)
    val FIND_MODE_CONTROL: UUID = aiza(0x0001)
    val BUTTON_EVENT: UUID = aiza(0x0002)
    val SETTINGS: UUID = aiza(0x0003)

    /** Advertised instead of [AIZA_SERVICE] while the tag is in pairing mode (fits the 31-byte legacy payload). */
    val AIZA_PAIRING: UUID = aiza(0x0100)

    const val ALERT_NONE: Byte = 0
    const val ALERT_HIGH: Byte = 2

    fun findMode(on: Boolean, timeoutSeconds: Int): ByteArray {
        val t = timeoutSeconds.coerceIn(0, 0xFFFF)
        return byteArrayOf(if (on) 1 else 0, (t and 0xFF).toByte(), (t shr 8).toByte())
    }

    /** Button Event notification: 1 = short press. Returns null for unknown or short payloads. */
    fun buttonEvent(value: ByteArray): ButtonEvent? {
        if (value.size < 2) return null
        val kind = when (value[0].toInt()) { 1 -> ButtonEvent.Kind.SHORT_PRESS; else -> return null }
        return ButtonEvent(kind, value[1].toInt() and 0xFF)
    }

    fun encodeSettings(s: TagSettings): ByteArray = byteArrayOf(
        (s.findIntervalMs and 0xFF).toByte(), (s.findIntervalMs shr 8).toByte(),
        (s.findMaxSeconds and 0xFF).toByte(), (s.findMaxSeconds shr 8).toByte(),
        s.txPowerDbm.toByte(),
    )

    fun decodeSettings(v: ByteArray): TagSettings? {
        if (v.size < 5) return null
        fun u16(i: Int) = (v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8)
        return TagSettings(u16(0), u16(2), v[4].toInt())
    }
}

data class ButtonEvent(val kind: Kind, val counter: Int) {
    enum class Kind { SHORT_PRESS }
}

/** Find-mode tuning for experiments (AIZA Settings characteristic). */
data class TagSettings(val findIntervalMs: Int, val findMaxSeconds: Int, val txPowerDbm: Int)
