package com.armor.app.sensors

import com.armor.app.requireService
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/** Accelerometer (with gravity) and gyroscope at ~50 Hz, delivered on [handler]'s thread. */
class PhoneMotionSource(
    context: Context,
    private val clock: RideClock,
    private val handler: Handler,
    private val onAccel: (tMs: Long, x: Double, y: Double, z: Double) -> Unit,
    private val onGyro: (tMs: Long, x: Double, y: Double, z: Double) -> Unit,
) {
    private val sensors = context.requireService<SensorManager>()
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val hasAccelerometer get() = accel != null
    val hasGyroscope get() = gyro != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val t = clock.fromElapsedNanos(event.timestamp)
            val v = event.values
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> onAccel(t, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                Sensor.TYPE_GYROSCOPE -> onGyro(t, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    fun start() {
        accel?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        gyro?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
    }

    fun stop() = sensors.unregisterListener(listener)
}
