package com.aiza.platform.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aiza.core.model.AccelSample
import com.aiza.core.model.GyroSample
import com.aiza.core.model.HeadingSample
import com.aiza.core.model.Sample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Raw accelerometer + gyroscope and rotation-vector heading (FR-DIR-01). No activity-recognition
 * permission needed for raw sensors. SensorEvent.timestamp is elapsedRealtimeNanos on modern devices
 * (verify per phone, SRS A-4).
 */
class MotionSensors(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)

    fun samples(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_GAME): Flow<Sample> = callbackFlow {
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val sample: Sample = when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> AccelSample(e.timestamp, e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_GYROSCOPE -> GyroSample(e.timestamp, e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, e.values)
                        SensorManager.getOrientation(rotation, orientation)
                        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        HeadingSample(e.timestamp, (deg + 360f) % 360f)
                    }
                    else -> return
                }
                trySend(sample)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ROTATION_VECTOR)
            .mapNotNull(sm::getDefaultSensor)
            .forEach { sm.registerListener(listener, it, samplingPeriodUs) }
        awaitClose { sm.unregisterListener(listener) }
    }
}
