package com.aiza.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/** A tag in pairing mode that has not been paired yet. */
data class DiscoveredTag(val address: String, val serial: String, val rssiDbm: Int)

/**
 * Finds tags in pairing mode. The real implementation goes through CompanionDeviceManager
 * (FR-SET-01) — see ui/pairing/CompanionPairing.kt.
 */
interface TagDiscovery {
    fun discover(): Flow<List<DiscoveredTag>>
}

class DemoTagDiscovery : TagDiscovery {
    override fun discover(): Flow<List<DiscoveredTag>> = flow {
        val found = mutableListOf<DiscoveredTag>()
        emit(emptyList())
        repeat(2) {
            delay(if (found.isEmpty()) 1_800 else 1_200)
            val hex = { List(4) { "0123456789ABCDEF"[Random.nextInt(16)] }.joinToString("") }
            val a = hex(); val b = hex()
            found += DiscoveredTag(
                address = "E2:${a.take(2)}:${a.drop(2)}:${b.take(2)}:${b.drop(2)}:0${it}",
                serial = "AZ-$a-$b",
                rssiDbm = Random.nextInt(-72, -50),
            )
            emit(found.toList())
        }
    }
}
