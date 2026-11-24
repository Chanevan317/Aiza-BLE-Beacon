package com.aiza.app.data

import android.os.SystemClock
import com.aiza.core.direction.pdr.HeadingFilter
import com.aiza.core.direction.pdr.PdrTracker
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.RssiSample
import com.aiza.platform.sensors.MotionSensors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Live signal from one tag during a search (FR-HC-02). Real version wraps platform:ble TagScanner. */
interface ProximitySource {
    fun rssi(address: String): Flow<RssiSample>
}

/**
 * Demo mode: a pretend tag placed in your real room, [distanceM] from where the search starts and
 * [offsetDeg] to the right of the way the phone points. Your real steps and heading (the same tracking the
 * wedge uses) move you relative to it, and the signal follows a standard indoor model with jitter and body
 * blocking. So walking around at home exercises the real hot/cold and wedge code end to end.
 */
class DemoProximitySource(
    private val sensors: MotionSensors,
    private val distanceM: Double = 4.0,
    private val offsetDeg: Double = 40.0,
) : ProximitySource {
    override fun rssi(address: String): Flow<RssiSample> = callbackFlow {
        val pdr = PdrTracker()
        var tag: Pair<Double, Double>? = null
        launch {
            sensors.samples().collect { s ->
                pdr.add(s)
                if (tag == null && s is HeadingSample) {
                    val b = Math.toRadians(s.headingDeg + offsetDeg)
                    tag = distanceM * sin(b) to distanceM * cos(b)
                }
            }
        }
        launch {
            // Same clock as ScanResult.timestampNanos and SensorEvent.timestamp (NFR-DAT-01).
            val (fallbackX, fallbackY) = 0.0 to distanceM // no compass (e.g. tests): tag straight ahead-north
            while (true) {
                val p = pdr.state
                val (tx, ty) = tag ?: (fallbackX to fallbackY)
                val d = max(0.3, hypot(tx - p.x, ty - p.y))
                val toTag = Math.toDegrees(atan2(tx - p.x, ty - p.y)).toFloat()
                val behind = p.headingDeg?.let { abs(HeadingFilter.angleDiff(toTag, it)) > 120f } ?: false
                val rssi = -59 - 20 * log10(d) - (if (behind) 4 else 0) + Random.nextDouble(-4.0, 4.0)
                trySend(RssiSample(SystemClock.elapsedRealtimeNanos(), address, rssi.toInt()))
                delay(50) // find mode advertises every 20–100 ms
            }
        }
        awaitClose()
    }.flowOn(Dispatchers.Default)
}
