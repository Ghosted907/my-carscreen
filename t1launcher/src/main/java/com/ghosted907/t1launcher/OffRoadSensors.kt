package com.ghosted907.t1launcher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Read-only OEM pitch/roll callback + device compass/GPS, while the page is visible.
 * No cached/demo values are presented as live. No writes to cluster compass/altitude.
 */
class OffRoadSensors(private val context: Context) : SensorEventListener, LocationListener, ServiceConnection {
    data class Value(val number: Double, val at: Long = SystemClock.elapsedRealtime()) {
        fun live(now: Long) = number.takeIf { now - at in 0..15000 }
    }
    var pitch by mutableStateOf<Value?>(null); private set
    var roll by mutableStateOf<Value?>(null); private set
    var heading by mutableStateOf<Value?>(null); private set
    var altitude by mutableStateOf<Value?>(null); private set
    private val main = Handler(Looper.getMainLooper())
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var binder: IBinder? = null
    private var bound = false
    @Volatile private var active = false
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gps = context.getSystemService(LocationManager::class.java)
    private val desc = "com.autolink.adapterinterface.misc.IALMisc"
    private val callbackDesc = "com.autolink.adapterinterface.misc.IPitchRollAngleCallback"
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(callbackDesc); return true }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(callbackDesc)
            val p = data.readFloat().toDouble(); val r = data.readFloat().toDouble()
            main.post {
                if (active) {
                    pitch = p.takeIf { it.isFinite() && it in -180.0..180.0 }?.let { Value(it) }
                    roll = r.takeIf { it.isFinite() && it in -180.0..180.0 }?.let { Value(it) }
                }
            }
            reply?.writeNoException()
            return true
        }
    }
    fun start() {
        if (active) return
        active = true
        bound = runCatching { context.bindService(Intent("android.autolink.IAutoLinkMisc").setPackage("com.autolink.miscservice"), this, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startGps()
    }
    fun startGps() {
        if (!active || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        runCatching { gps?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, this, Looper.getMainLooper()) }
    }
    override fun onLocationChanged(location: Location) {
        if (!active || !location.hasAltitude()) return
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (age in 0..15000000000L && location.altitude.isFinite()) altitude = Value(location.altitude)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
        val matrix = FloatArray(9); val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        SensorManager.getOrientation(matrix, orientation)
        heading = Value((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) heading = null }
    private fun subscribe(b: IBinder, code: Int) {
        if (b.interfaceDescriptor != desc) return
        val d = Parcel.obtain(); val r = Parcel.obtain()
        try { d.writeInterfaceToken(desc); d.writeStrongBinder(callback); if (b.transact(code, d, r, 0)) r.readException() }
        finally { d.recycle(); r.recycle() }
    }
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (!active || service == null) return
        binder = service
        worker.execute { runCatching { if (active) subscribe(service, 5) } }
    }
    override fun onServiceDisconnected(name: ComponentName?) { binder = null; pitch = null; roll = null }
    fun close() {
        active = false
        sensors?.unregisterListener(this); runCatching { gps?.removeUpdates(this) }
        val b = binder; binder = null
        worker.execute { if (b != null) runCatching { subscribe(b, 6) } }
        worker.shutdown()
        if (bound) runCatching { context.unbindService(this) }
        bound = false
    }
}
