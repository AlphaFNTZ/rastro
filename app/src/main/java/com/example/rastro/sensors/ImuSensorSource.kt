package com.example.rastro.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.example.rastro.s2.*

/** Aquisição com app visível. Serviço de primeiro plano pertence à próxima etapa. */
class ImuSensorSource(context: Context, private val buffer: CircularImuBuffer) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var thread: HandlerThread? = null

    fun iniciar(): Boolean {
        if (thread != null) return true
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        val worker = HandlerThread("rastro-imu-50hz").apply { start() }
        thread = worker
        val sucesso = manager.registerListener(this, sensor, 20_000, Handler(worker.looper))
        if (!sucesso) parar()
        return sucesso
    }

    fun parar() {
        manager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3 || event.timestamp <= 0) return
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        val qualidade = when (event.accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> QualidadeImu.SEM_CONTATO
            SensorManager.SENSOR_STATUS_UNRELIABLE -> QualidadeImu.NAO_CONFIAVEL
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> QualidadeImu.BAIXA
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> QualidadeImu.MEDIA
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> QualidadeImu.ALTA
            else -> QualidadeImu.DESCONHECIDA
        }
        buffer.adicionar(LeituraImu(event.timestamp, x, y, z, qualidade))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
