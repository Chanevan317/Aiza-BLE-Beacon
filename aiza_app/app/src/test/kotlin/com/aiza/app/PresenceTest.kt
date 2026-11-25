package com.aiza.app

import com.aiza.data.tags.Presence
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceTest {
    private fun tag(rssi: Int?, seenAgoMs: Long?) = Tag(
        "A", "Keys", TagIcon.KEYS, "AZ", null, null, rssi,
        seenAgoMs?.let { NOW - it }, null, null, null, leftBehindAlert = false, pairedEpochMs = 0,
    )

    @Test fun connectedWhileHeard() {
        assertEquals(Presence.CONNECTED, tag(-58, 1_000).presence(NOW))
        assertEquals(Presence.CONNECTED, tag(-92, 1_000).presence(NOW)) // weak but still linked
    }

    @Test fun disconnectedWhenNotHeard() {
        assertEquals(Presence.DISCONNECTED, tag(null, 1_000).presence(NOW))   // link dropped
        assertEquals(Presence.DISCONNECTED, tag(-58, 120_000).presence(NOW))  // stale reading
        assertEquals(Presence.DISCONNECTED, tag(null, null).presence(NOW))    // never seen
    }

    private companion object { const val NOW = 10_000_000L }
}
