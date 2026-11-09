package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only car access plus the already-tested cluster appearance calls
 * (theme, font, day/night, wallpaper style). No HAL setIntProperty, no AVM
 * SWT/OFF writes, no window or sunroof commands.
 */
class CarBridge(private val context: Context) {
    data class Snap(
        val speed: Float? = null,
        val rpm: Float? = null,
        val temp: Float? = null,
        val fuel: Int? = null,
        val rangeKm: Int? = null,
        val driveMode: Int? = null,
        val gear: Int? = null,
        val theme: Int? = null,
        val font: Int? = null,
        val dayNight: Int? = null,
        val avmOpen: Boolean? = null,
        val speedClosed: Boolean = false,
        val carOk: Boolean = false,
        val clusterOk: Boolean = false,
        val note: String = ""
    )

    @Volatile var snap: Snap = Snap()
        private set
    var onChange: ((Snap) -> Unit)? = null
    var onSpeedClose: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var car: IBinder? = null
    private var cluster: IBinder? = null
    private var vehicle: IBinder? = null
    private var carBound = false
    private var clusterBound = false
    private var vehicleBound = false
    private var thread: Thread? = null
    private var sawAvmOpen = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        bind()
        thread = Thread({
            while (running.get()) {
                poll()
                try {
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                    if (!running.get()) return@Thread
                }
            }
        }, "t1-car").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        unbindAll()
    }

    fun gearLetter(value: Int?): String = when (value) {
        1 -> "P"
        2 -> "R"
        3 -> "N"
        4 -> "D"
        else -> ""
    }

    fun modeLabel(value: Int?): String = when (value) {
        0 -> "ECO"
        1 -> "NORMAL"
        2 -> "SPORT"
        3 -> "SNOW"
        4 -> "MUD"
        5 -> "ROCK"
        6 -> "OFF-ROAD"
        7 -> "SAND"
        else -> "--"
    }

    fun applyClassic() = applyTheme(2)

    fun applyTheme(theme: Int) {
        if (theme !in 1..3) return
        transactCluster(205, theme, "setThemeToCluster")
        readClusterAppearance()
    }

    fun applyFont(font: Int) {
        if (font !in 1..2) return
        transactCluster(215, font, "setClusterFont")
        readClusterAppearance()
    }

    fun applyDayNight(mode: Int) {
        if (mode !in 0..1) return
        transactCluster(74, mode, "setDayNightMode")
    }

    /** Wallpaper style index only (0–2). Not an image upload. */
    fun applyWallpaperStyle(style: Int) {
        if (style !in 0..2) return
        transactCluster(219, style, "notifyClusterWallpaperChanged")
    }

    fun openAvm() {
        val intent = Intent().setClassName(
            "com.autolink.app.avm",
            "com.autolink.app.avm.AvmService"
        ).putExtra("key_type", 1)
        try {
            context.startService(intent)
            Log.i(TAG, "AVM startService key_type=1")
        } catch (e: Exception) {
            Log.i(TAG, "AVM startService failed ${e.javaClass.simpleName} ${e.message}")
            publish(snap.copy(note = "تعذّر طلب الكاميرا / AVM request failed"))
        }
    }

    private fun bind() {
        clusterBound = bind(
            Intent(CI_ACTION).setComponent(ComponentName(CI_PKG, CI_CLASS)),
            clusterConn
        )
        carBound = bind(
            Intent(CAR_ACTION).setPackage(CAR_PKG),
            carConn
        )
        vehicleBound = bind(
            Intent(VEHICLE_ACTION).setPackage(VEHICLE_PKG),
            vehicleConn
        )
        Log.i(TAG, "bind cluster=$clusterBound car=$carBound vehicle=$vehicleBound")
    }

    private fun bind(intent: Intent, conn: ServiceConnection): Boolean = try {
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    } catch (e: SecurityException) {
        Log.i(TAG, "bind denied ${intent.`package` ?: intent.component} ${e.message}")
        false
    }

    private fun unbindAll() {
        listOf(clusterConn to clusterBound, carConn to carBound, vehicleConn to vehicleBound)
            .forEach { (conn, bound) ->
                if (bound) {
                    try {
                        context.unbindService(conn)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
        clusterBound = false
        carBound = false
        vehicleBound = false
        cluster = null
        car = null
        vehicle = null
    }

    private val clusterConn = conn { cluster = it }
    private val carConn = conn { car = it }
    private val vehicleConn = conn { vehicle = it }

    private fun conn(assign: (IBinder?) -> Unit) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            assign(service)
            Log.i(TAG, "connected $name")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            assign(null)
            Log.i(TAG, "disconnected $name")
        }
    }

    private fun poll() {
        var next = snap.copy(note = "")
        val ci = cluster
        if (ci != null && descriptor(ci, CI_DESC)) {
            next = next.copy(clusterOk = true)
            readInt(ci, CI_DESC, 206)?.let { next = next.copy(theme = it) }
            readInt(ci, CI_DESC, 216)?.let { next = next.copy(font = it) }
            readInt(ci, CI_DESC, 56)?.let { if (it in 0..100) next = next.copy(fuel = it) }
            readInt(ci, CI_DESC, 57)?.let { if (it in 0..5000) next = next.copy(rangeKm = it) }
            readInt(ci, CI_DESC, 58)?.let { if (it in 0..8) next = next.copy(driveMode = it) }
            readFloat(ci, CI_DESC, 102)?.let { if (it in 0f..400f) next = next.copy(speed = it) }
            readInt(ci, CI_DESC, 106)?.let { next = next.copy(avmOpen = it != 0) }
        }
        val hal = car
        if (hal != null && descriptor(hal, CAR_DESC)) {
            next = next.copy(carOk = true)
            readFloat(hal, CAR_DESC, 9, SPEED)?.let { if (it in 0f..400f) next = next.copy(speed = it) }
            readFloat(hal, CAR_DESC, 9, RPM)?.let { if (it in 0f..20000f) next = next.copy(rpm = it) }
            readFloat(hal, CAR_DESC, 9, TEMP)?.let { if (it in -60f..80f) next = next.copy(temp = it) }
            readIntProp(hal, GEAR)?.let { next = next.copy(gear = it) }
        }
        val veh = vehicle
        if (veh != null && descriptor(veh, VEHICLE_DESC)) {
            readVehicleInt(veh, 2103)?.let { mode ->
                val open = mode == 3
                val closed = mode == 4
                if (closed && sawAvmOpen) {
                    sawAvmOpen = false
                    next = next.copy(avmOpen = false, speedClosed = true)
                    main.post { onSpeedClose?.invoke() }
                    Log.i(TAG, "AVM closed (CarAvmMode 4), not reopening")
                } else if (open) {
                    sawAvmOpen = true
                    next = next.copy(avmOpen = true, speedClosed = false)
                } else if (closed) {
                    next = next.copy(avmOpen = false)
                }
            }
        }
        publish(next)
    }

    private fun readClusterAppearance() {
        val ci = cluster ?: return
        val theme = readInt(ci, CI_DESC, 206)
        val font = readInt(ci, CI_DESC, 216)
        publish(snap.copy(theme = theme ?: snap.theme, font = font ?: snap.font, clusterOk = true))
        Log.i(TAG, "readback theme=$theme font=$font")
    }

    private fun transactCluster(code: Int, value: Int, name: String) {
        val binder = cluster
        if (binder == null || !descriptor(binder, CI_DESC)) {
            publish(snap.copy(note = "خدمة العدادات غير متصلة"))
            Log.i(TAG, "$name skipped, CiService missing")
            return
        }
        if (code != 205 && code != 215 && code != 74 && code != 219) return
        try {
            call<Unit>(binder, CI_DESC, code, { writeInt(value) }, null)
            Log.i(TAG, "$name $value ok")
        } catch (e: Exception) {
            Log.i(TAG, "$name ${e.javaClass.simpleName} ${e.message}")
            publish(snap.copy(note = "$name فشل"))
        }
    }

    private fun readInt(binder: IBinder, desc: String, code: Int): Int? = try {
        call(binder, desc, code, {}, { readInt() })
    } catch (e: Exception) {
        if (e is DeadObjectException) cluster = null
        null
    }

    private fun readFloat(binder: IBinder, desc: String, code: Int): Float? = try {
        call(binder, desc, code, {}, { readFloat() })
    } catch (_: Exception) {
        null
    }

    private fun readIntProp(binder: IBinder, prop: Int): Int? = try {
        call(binder, CAR_DESC, 2, {
            writeInt(prop)
            writeInt(0)
        }, { readInt() })?.takeUnless { it == -1 || it == -127 }
    } catch (_: Exception) {
        null
    }

    private fun readFloat(binder: IBinder, desc: String, code: Int, prop: Int): Float? = try {
        call(binder, desc, code, {
            writeInt(prop)
            writeInt(0)
        }, { readFloat() })?.takeUnless { it == -1f || it.isNaN() }
    } catch (_: Exception) {
        null
    }

    private fun readVehicleInt(binder: IBinder, key: Int): Int? = try {
        call(binder, VEHICLE_DESC, 5, { writeInt(key) }, { readInt() })
    } catch (_: Exception) {
        null
    }

    private fun <T> call(
        binder: IBinder,
        desc: String,
        code: Int,
        write: Parcel.() -> Unit,
        read: (Parcel.() -> T)?
    ): T? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(desc)
            data.write()
            val ok = binder.transact(code, data, reply, 0)
            reply.readException()
            if (!ok) throw RemoteException("transact false $code")
            return read?.invoke(reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun descriptor(binder: IBinder, expected: String): Boolean = try {
        binder.interfaceDescriptor == expected
    } catch (_: RemoteException) {
        false
    }

    private fun publish(next: Snap) {
        snap = next
        main.post { onChange?.invoke(next) }
    }

    companion object {
        const val TAG = "T1Launcher"
        private const val CI_PKG = "com.autolink.clusterinteraction"
        private const val CI_CLASS = "com.autolink.clusterinteraction.CiService"
        private const val CI_ACTION = "com.autolink.clusterinteraction.CiService"
        private const val CI_DESC = "com.autolink.adapterinterface.clusterinteraction.IClusterInteraction"
        private const val CAR_PKG = "com.autolink.carservice"
        private const val CAR_ACTION = "android.autolink.IAutoLinkCar"
        private const val CAR_DESC = "com.autolink.adapterinterface.car.IALCar"
        private const val VEHICLE_PKG = "com.autolink.app.vehicleservice"
        private const val VEHICLE_ACTION = "com.autolink.vehicleservice"
        private const val VEHICLE_DESC = "com.autolink.app.vehicleservice.IVehicleControl"
        private const val SPEED = 559941888
        private const val RPM = 559944245
        private const val TEMP = 559940264
        private const val GEAR = 557843105
    }
}
