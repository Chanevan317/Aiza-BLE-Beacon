package com.aiza.app

import com.aiza.app.ring.PhoneRing
import com.aiza.app.ring.PhoneRinger
import com.aiza.app.ring.RingOutput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneRingerTest {
    private class FakeOutput : RingOutput {
        val log = mutableListOf<String>()
        override fun start(ring: PhoneRing) { log += "start ${ring.tagName}" }
        override fun stop() { log += "stop" }
    }

    private fun TestScope.ringer(out: FakeOutput) = PhoneRinger(backgroundScope, out, timeoutMs = 30_000, clock = { testScheduler.currentTime })

    @Test fun pressRingsAndSecondPressStops() = runTest {
        val out = FakeOutput()
        val r = ringer(out)
        r.onButtonPress("A", "Keys")
        assertEquals("Keys", r.ringing.value?.tagName)
        r.onButtonPress("A", "Keys")
        assertNull(r.ringing.value)
        assertEquals(listOf("start Keys", "stop"), out.log)
    }

    @Test fun stopsAfterTimeout() = runTest {
        val out = FakeOutput()
        val r = ringer(out)
        r.onButtonPress("A", "Keys")
        advanceTimeBy(29_000); runCurrent()
        assertEquals("Keys", r.ringing.value?.tagName)
        advanceTimeBy(1_001); runCurrent()
        assertNull(r.ringing.value)
        assertEquals(listOf("start Keys", "stop"), out.log)
    }

    @Test fun otherTagTakesOverAndRestartsTimeout() = runTest {
        val out = FakeOutput()
        val r = ringer(out)
        r.onButtonPress("A", "Keys")
        advanceTimeBy(20_000); runCurrent()
        r.onButtonPress("B", "Wallet")
        advanceTimeBy(20_000); runCurrent()
        assertEquals("Wallet", r.ringing.value?.tagName) // old timeout must not stop the new ring
        advanceTimeBy(10_001); runCurrent()
        assertNull(r.ringing.value)
    }

    @Test fun stopWhenIdleDoesNothing() = runTest {
        val out = FakeOutput()
        ringer(out).stop()
        assertEquals(emptyList<String>(), out.log)
    }
}
