package com.aiza.data.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * In-memory tags for building and demoing the UI without a phone or tag.
 * Nearby demo tags get a fresh signal reading every few seconds so the screens feel live.
 */
class DemoTagRepository(scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) : TagRepository {
    private val state = MutableStateFlow(seed(clock()))
    override val tags: StateFlow<List<Tag>> = state

    init {
        scope.launch {
            while (true) {
                delay(3_000)
                val now = clock()
                state.update { list ->
                    list.map { t ->
                        if (t.address in LIVE) t.copy(
                            lastSeenEpochMs = now,
                            rssiDbm = ((t.rssiDbm ?: -70) + Random.nextInt(-3, 4)).coerceIn(-90, -45),
                        ) else t
                    }
                }
            }
        }
    }

    override fun tag(address: String): Flow<Tag?> = state.map { list -> list.find { it.address == address } }
    override suspend fun add(tag: Tag) = state.update { list -> list.filterNot { it.address == tag.address } + tag }
    override suspend fun update(address: String, change: (Tag) -> Tag) =
        state.update { list -> list.map { if (it.address == address) change(it) else it } }
    override suspend fun remove(address: String) = state.update { list -> list.filterNot { it.address == address } }

    companion object {
        private val LIVE = setOf("D4:3A:91:0C:7E:21", "F1:08:5B:A2:33:9D")

        fun seed(now: Long): List<Tag> = listOf(
            Tag("D4:3A:91:0C:7E:21", "Keys", TagIcon.KEYS, "AZ-7E21-0C91", "0.1.0", 86, -58,
                now, "Home", 12.9716, 77.5946, leftBehindAlert = true, pairedEpochMs = now - 12 * DAY),
            Tag("F1:08:5B:A2:33:9D", "Backpack", TagIcon.BACKPACK, "AZ-339D-A25B", "0.1.0", 54, -73,
                now, "Home", 12.9716, 77.5946, leftBehindAlert = false, pairedEpochMs = now - 5 * DAY),
            Tag("C7:6E:12:F0:41:B8", "Wallet", TagIcon.WALLET, "AZ-41B8-F012", "0.1.0", 14, null,
                now - 47 * MIN, "Office", 12.9352, 77.6245, leftBehindAlert = true, pairedEpochMs = now - 30 * DAY),
        )

        private const val MIN = 60_000L
        private const val DAY = 24 * 60 * MIN
    }
}
