package com.chahyn.photo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MotionState(
    val shake: Float = 0.2f,     // vitesse angulaire lissée (rad/s)
    val stable: Boolean = false, // posé / trépied depuis > 1,5 s
    val roll: Float = 0f,        // écart à l'horizontale (degrés), pour le niveau
    val flat: Boolean = false,   // téléphone à plat (vue de dessus)
    val tiltX: Float = 0f,       // pour le niveau à plat
    val tiltY: Float = 0f,
)

/** Gyroscope + gravité : détecte le trépied, les mouvements, et sert de niveau à bulle. */
class MotionSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val state = mutableStateOf(MotionState())
    private var shake = 0.2f
    private var stableSince = 0L
    private var lastPush = 0L
    private var roll = 0f
    private var flat = false
    private var tx = 0f
    private var ty = 0f

    fun start() {
        gyro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        grav?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (e.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                shake = shake * 0.9f + m * 0.1f
                if (shake < 0.008f) { // seuil très bas : une main, même immobile, tremble davantage
                    if (stableSince == 0L) stableSince = now
                } else stableSince = 0L
            }
            else -> {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                flat = abs(z) > 8.3f
                tx = x
                ty = y
                // angle du téléphone autour de l'axe de l'écran, ramené au quart de tour le plus proche
                val a = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                val nearest = (a / 90f).roundToInt() * 90f
                roll = roll * 0.7f + (a - nearest) * 0.3f
            }
        }
        if (now - lastPush > 80) {
            lastPush = now
            state.value = MotionState(
                shake = shake,
                stable = stableSince != 0L && now - stableSince > 1500,
                roll = roll,
                flat = flat,
                tiltX = tx,
                tiltY = ty,
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
