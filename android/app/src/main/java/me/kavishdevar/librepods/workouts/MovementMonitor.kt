package me.kavishdevar.librepods.workouts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

/** Walking/running heuristic only: 60 steps in 90 seconds; leave boost after 3 quiet minutes. */
internal class StepActivityWindow {
    private val steps = ArrayDeque<Long>()
    private var lastStep: Long? = null
    private var active = false
    fun step(time: Long) {
        if (lastStep != null && time <= lastStep!!) return
        if (lastStep != null && time-lastStep!! >= 180_000) active = false
        lastStep = time
        steps.addLast(time)
        while (steps.isNotEmpty() && time - steps.first() > 90_000) steps.removeFirst()
        if (steps.size >= 60) active = true
        while (steps.size > 60) steps.removeFirst()
    }
    fun isActive(now: Long): Boolean {
        if (lastStep == null || now-lastStep!! >= 180_000) active = false
        return active
    }
    fun reset() { steps.clear(); lastStep=null; active=false }
}

internal class MovementMonitor(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val window = StepActivityWindow()
    private var registered = false
    fun enable(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) { stop(); return false }
        if (registered) return true
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return false
        registered = try { manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL) } catch (_: SecurityException) { false }
        return registered
    }
    fun active(now: Long) = registered && window.isActive(now)
    fun stop() { if(registered) manager.unregisterListener(this); registered=false; window.reset() }
    override fun onSensorChanged(event: SensorEvent) { window.step(event.timestamp / 1_000_000) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
