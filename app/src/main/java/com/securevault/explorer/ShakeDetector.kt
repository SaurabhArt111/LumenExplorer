package com.securevault.explorer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Two firm shakes within ~1.2 s = panic lock. */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var sm: SensorManager? = null
    private var firstAt = 0L
    private var count = 0
    private var lastKick = 0L

    fun start(ctx: Context) {
        if (sm != null) return
        val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val acc = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        m.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
        sm = m
    }

    fun stop() {
        sm?.unregisterListener(this)
        sm = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) /
            SensorManager.GRAVITY_EARTH
        if (g < 2.8f) return
        val now = System.currentTimeMillis()
        if (now - lastKick < 250) return
        lastKick = now
        if (count == 0 || now - firstAt > 1200) {
            count = 1
            firstAt = now
        } else {
            count++
            if (count >= 2) {
                count = 0
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
