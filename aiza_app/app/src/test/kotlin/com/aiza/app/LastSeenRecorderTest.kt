package com.aiza.app

import com.aiza.app.location.LastSeenRecorder
import com.aiza.app.location.Locator
import com.aiza.app.location.PhoneFix
import com.aiza.data.tags.Tag
import com.aiza.data.tags.TagIcon
import com.aiza.data.tags.TagRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastSeenRecorderTest {
    private class Repo(t: Tag) : TagRepository {
        val state = MutableStateFlow(listOf(t))
        override val tags: Flow<List<Tag>> = state
        override fun tag(address: String) = state.map { l -> l.find { it.address == address } }
        override suspend fun add(tag: Tag) = state.update { it + tag }
        override suspend fun update(address: String, change: (Tag) -> Tag) = state.update { l -> l.map { if (it.address == address) change(it) else it } }
        override suspend fun remove(address: String) = state.update { l -> l.filterNot { it.address == address } }
    }

    private class FakeLocator : Locator {
        var calls = 0
        var lat = 12.0
        val precise = mutableListOf<Boolean>()
        override fun allowed() = true
        override suspend fun current(precise: Boolean): PhoneFix { calls++; this.precise += precise; return PhoneFix(lat, 77.0, 5f, "Place $calls") }
    }

    private fun TestScope.setup(): Triple<Repo, FakeLocator, () -> Long> {
        val now = { testScheduler.currentTime + 1_000_000L }
        val repo = Repo(Tag("A", "Keys", TagIcon.KEYS, "AZ", null, null, -60, now(), null, null, null, false, 0))
        val loc = FakeLocator()
        LastSeenRecorder(backgroundScope, repo, loc, refreshMs = 120_000, clock = now, wallClock = now, tickMs = 10_000)
        return Triple(repo, loc, now)
    }

    @Test fun savesWhileConnectedButNotTooOften() = runTest {
        val (repo, loc, now) = setup()
        runCurrent()
        assertEquals(1, loc.calls)
        assertEquals("Place 1", repo.state.value.single().lastSeenPlace)
        // Stay connected (keep refreshing "last seen") for 60 s: no new fix yet.
        repeat(6) { repo.update("A") { it.copy(lastSeenEpochMs = now()) }; advanceTimeBy(10_000); runCurrent() }
        assertEquals(1, loc.calls)
        repeat(7) { repo.update("A") { it.copy(lastSeenEpochMs = now()) }; advanceTimeBy(10_000); runCurrent() }
        assertEquals(2, loc.calls)
    }

    @Test fun savesRightAwayWhenTheLinkDrops() = runTest {
        val (repo, loc, _) = setup()
        runCurrent()
        assertEquals(1, loc.calls)
        loc.lat = 13.0
        repo.update("A") { it.copy(rssiDbm = null) } // disconnected
        runCurrent()
        assertEquals(2, loc.calls)
        assertEquals(13.0, repo.state.value.single().lastSeenLat!!, 0.0)
        assertEquals(listOf(false, true), loc.precise) // the "lost here" fix asks for GPS quality
    }
}
