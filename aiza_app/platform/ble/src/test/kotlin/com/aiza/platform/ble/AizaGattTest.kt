package com.aiza.platform.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AizaGattTest {
    @Test fun uuidsUseTheAizaBase() {
        assertEquals("9a990000-982c-45e1-a09e-5e8952971cdb", AizaGatt.AIZA_SERVICE.toString())
        assertEquals("9a990002-982c-45e1-a09e-5e8952971cdb", AizaGatt.BUTTON_EVENT.toString())
        assertEquals("00002a06-0000-1000-8000-00805f9b34fb", AizaGatt.ALERT_LEVEL.toString())
    }

    @Test fun findModeIsModeThenLittleEndianTimeout() {
        assertArrayEquals(byteArrayOf(1, 0x2C, 0x01), AizaGatt.findMode(true, 300))
        assertArrayEquals(byteArrayOf(0, 0, 0), AizaGatt.findMode(false, 0))
    }

    @Test fun buttonEvent() {
        assertEquals(ButtonEvent(ButtonEvent.Kind.SHORT_PRESS, 200), AizaGatt.buttonEvent(byteArrayOf(1, 200.toByte())))
        assertNull(AizaGatt.buttonEvent(byteArrayOf(9, 1)))
        assertNull(AizaGatt.buttonEvent(byteArrayOf(1)))
    }

    @Test fun settingsRoundTrip() {
        val s = TagSettings(findIntervalMs = 50, findMaxSeconds = 600, txPowerDbm = -8)
        assertEquals(s, AizaGatt.decodeSettings(AizaGatt.encodeSettings(s)))
    }
}
