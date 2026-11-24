package com.aiza.app.data

import android.os.SystemClock
import com.aiza.core.model.RssiSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Live signal from one tag during a search (FR-HC-02). Real version wraps platform:ble TagScanner. */
interface ProximitySource {
    fun rssi(address: String): Flow<RssiSample>
}

/**
 * Simulates someone walking towards a tag: signal drifts from about -88 to -48 dBm over ~40 s,
 * with a wrong turn in the middle and realistic jitter. Advertising every 50 ms like find mode.
 */
class DemoProximitySource : ProximitySource {
    override fun rssi(address: String): Flow<RssiSample> = flow {
        // Same clock as ScanResult.timestampNanos and SensorEvent.timestamp (NFR-DAT-01).
        val start = SystemClock.elapsedRealtimeNanos()
        var n = 0L
        while (true) {
            val t = n * 0.05 // simulated seconds since the search started
            val progress = (t / 40.0).coerceAtMost(1.0)
            val wrongTurn = if (t in 12.0..18.0) -8 * sin((t - 12) / 6 * PI) else 0.0
            val mean = -88 + 40 * progress + wrongTurn
            emit(RssiSample(start + n * 50_000_000L, address, (mean + Random.nextDouble(-4.0, 4.0)).toInt()))
            n++
            delay(50)
        }
    }
}
