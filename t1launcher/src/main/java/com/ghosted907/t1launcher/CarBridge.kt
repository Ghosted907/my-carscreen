package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** OEM calls run off the UI thread. An independent heartbeat expires blocked/stalled reads. */
class CarBridge(private val context: Context) {
    data class Snap(
        val speed: Float? = null, val rpm: Float? = null, val temp: Float? = null,
        val fuel: Int? = null, val rangeKm: Int? = null, val driveMode: Int? = null,
        val gear: Int? = null, val theme: Int? = null, val font: Int? = null,
        val dayNight: Int? = null, val avmOpen: Boolean? = null, val speedClosed: Boolean = false,
        val carOk: Boolean = false, val clusterOk: Boolean = false, val vehicleOk: Boolean = false,
        val note: String = "", val readings: Map<String, SignalReading> = emptyMap(),
        val nowMs: Long = 0, val speedKey: String = "speed", val speedUnit: String = "كم/س"
    ) {
        fun reading(key: String) = readings[key] ?: SignalReading()
        val liveCount: Int get() = SignalCatalog.all.count { reading(it.key).status == ReadingStatus.LIVE }
    }

    @Volatile var snap = Snap(); private set
    @Volatile var onChange: ((Snap) -> Unit)? = null
    var onSpeedClose: (() -> Unit)? = null
    val oemProperties: List<OemProperty> by lazy {
        context.assets.open("oem_properties.tsv").bufferedReader().use { OemCatalogParser.parse(it.lineSequence()) }
    }
    private val store = TelemetryStore()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "t1-telemetry").apply { isDaemon = true } }
    @Volatile private var running = false
    @Volatile private var extra = emptyList<SignalDefinition>()
    @Volatile private var note = ""
    private val attempted = mutableMapOf<String, Long>()
    private var sawAvmOpen = false

    private val car = Endpoint("android.autolink.IAutoLinkCar", "com.autolink.carservice", CAR_DESC)
    private val cluster = Endpoint("com.autolink.clusterinteraction.CiService", "com.autolink.clusterinteraction", CI_DESC,
        "com.autolink.clusterinteraction.CiService")
    private val vehicle = Endpoint("com.autolink.vehicleservice", "com.autolink.app.vehicleservice", VEHICLE_DESC)
    private val endpoints get() = listOf(car, cluster, vehicle)

    private inner class Endpoint(val action: String, val pkg: String, val descriptor: String, val className: String? = null) : ServiceConnection {
        @Volatile var binder: IBinder? = null
        @Volatile var ready = false
        @Volatile var generation = 0
        var bound = false
        var retryAt = 0L
        var connectedAt = 0L

        fun connect() {
            if (!running || bound || SystemClock.elapsedRealtime() < retryAt) return
            retryAt = SystemClock.elapsedRealtime() + 10000
            val intent = Intent(action).setPackage(pkg)
            className?.let { intent.component = ComponentName(pkg, it) }
            bound = try { context.bindService(intent, this, Context.BIND_AUTO_CREATE) } catch (_: Exception) { false }
            connectedAt = SystemClock.elapsedRealtime()
            if (!bound) invalidate()
        }

        @Synchronized fun invalidate() {
            generation++
            binder = null
            ready = false
            store.disconnect((SignalCatalog.all + extra).filter { endpoint(it.source) === this })
        }

        fun release() {
            invalidate()
            if (bound) runCatching { context.unbindService(this) }
            bound = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!running) { release(); return }
            binder = service
            val version = ++generation
            worker.execute {
                val matches = runCatching { service?.interfaceDescriptor == descriptor }.getOrDefault(false)
                if (generation == version && running) {
                    ready = matches
                    if (!matches) main.post { release() }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { release() }
        override fun onBindingDied(name: ComponentName?) { release() }
        override fun onNullBinding(name: ComponentName?) { release() }
    }

    private fun endpoint(source: SignalSource): Endpoint = when (source) {
        SignalSource.CAR_INT, SignalSource.CAR_FLOAT -> car
        SignalSource.CLUSTER_INT, SignalSource.CLUSTER_FLOAT -> cluster
        SignalSource.VEHICLE_INT, SignalSource.VEHICLE_FLOAT -> vehicle
    }

    fun start() {
        if (running) return
        running = true
        endpoints.forEach { it.connect() }
        worker.scheduleWithFixedDelay({
            if (running) runCatching { poll() }.onFailure { note = "تعذرت دورة القراءة؛ ستتم إعادة المحاولة" }
        }, 0, 250, TimeUnit.MILLISECONDS)
        main.post(heartbeat)
    }

    fun stop() {
        running = false
        main.removeCallbacks(heartbeat)
        endpoints.forEach { it.release() }
        worker.shutdownNow()
        onChange = null
        onSpeedClose = null
    }

    fun setExtraProperties(properties: List<OemProperty>) {
        val allowed = oemProperties.associateBy { it.address }
        extra = properties.take(24).mapNotNull { allowed[it.address]?.definition() }.distinctBy { it.key }
    }

    fun retry() {
        endpoints.filter { !it.ready }.forEach { it.release(); it.retryAt = 0; it.connect() }
        worker.execute { attempted.clear() }
    }

    private fun poll() {
        if (onChange == null) return
        val definitions = (SignalCatalog.all + extra).sortedBy { if (it.key.endsWith("_valid")) 0 else 1 }
        for (definition in definitions) {
            if (!running) return
            val service = endpoint(definition.source)
            if (!service.ready) continue
            val now = SystemClock.elapsedRealtime()
            if (now - (attempted[definition.key] ?: -definition.periodMs) < definition.periodMs) continue
            attempted[definition.key] = now
            val connection = service.binder ?: continue
            val generation = service.generation
            try {
                val raw = read(connection, definition)
                val validKey = when (definition.key) { "speed" -> "speed_valid"; "rpm" -> "rpm_valid"; else -> null }
                val validity = validKey?.let { store.snapshot(SignalCatalog.all, now)[it]?.current }
                synchronized(service) {
                    if (service.generation == generation && service.ready) {
                        store.record(definition, if (validKey != null && validity != 1.0) null else raw,
                            now, if (validKey != null && validity != 1.0) "صلاحية الإشارة غير مؤكدة" else "")
                    }
                }
            } catch (e: Exception) {
                if (service.generation == generation) store.record(definition, null, SystemClock.elapsedRealtime(),
                    if (e is SecurityException) "الخدمة رفضت القراءة" else "تعذرت القراءة")
                if (e is android.os.DeadObjectException) main.post { if (service.generation == generation) service.release() }
            }
        }
    }

    private fun read(binder: IBinder, definition: SignalDefinition): Double {
        val service = endpoint(definition.source)
        val code = when (definition.source) {
            SignalSource.CAR_INT -> 2
            SignalSource.CAR_FLOAT -> 9
            SignalSource.VEHICLE_INT -> 5
            SignalSource.VEHICLE_FLOAT -> 6
            else -> {
                require(SignalCatalog.clusterReads[definition.address]?.source == definition.source)
                definition.address
            }
        }
        val float = definition.source == SignalSource.CAR_FLOAT || definition.source == SignalSource.CLUSTER_FLOAT || definition.source == SignalSource.VEHICLE_FLOAT
        return transact(binder, service.descriptor, code, {
            when (definition.source) {
                SignalSource.CAR_INT, SignalSource.CAR_FLOAT -> { writeInt(definition.address); writeInt(0) }
                SignalSource.VEHICLE_INT, SignalSource.VEHICLE_FLOAT -> writeInt(definition.address)
                else -> Unit
            }
        }) { if (float) readFloat().toDouble() else readInt().toDouble() }
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            endpoints.forEach {
                if (it.bound && it.binder == null && now - it.connectedAt > 10000) it.release()
                it.connect()
            }
            val readings = store.snapshot(SignalCatalog.all + extra, now)
            fun value(key: String) = readings[key]?.current
            val displaySpeed = TelemetryPresentation.speed(readings)
            val avmMode = value("avm_mode")?.toInt()
            val closedAfterOpen = avmMode == 4 && sawAvmOpen
            if (avmMode == 3) sawAvmOpen = true
            if (avmMode == 4) sawAvmOpen = false
            snap = Snap(
                speed = displaySpeed.value?.toFloat(), rpm = value("rpm")?.toFloat(), temp = value("outside_c")?.toFloat(),
                fuel = value("fuel")?.toInt(), rangeKm = value("range")?.toInt(), driveMode = value("drive_mode")?.toInt(),
                gear = value("gear")?.toInt(), theme = value("theme")?.toInt(), font = value("font")?.toInt(),
                avmOpen = when (avmMode) { 3 -> true; 4 -> false; else -> value("avm")?.let { it == 1.0 } },
                carOk = car.ready, clusterOk = cluster.ready, vehicleOk = vehicle.ready, note = note,
                readings = readings, nowMs = now, speedKey = displaySpeed.key,
                speedUnit = displaySpeed.unit
            )
            onChange?.invoke(snap)
            if (closedAfterOpen) onSpeedClose?.invoke()
            main.postDelayed(this, 500)
        }
    }

    fun gearLetter(value: Int?): String = SignalCatalog.byKey.getValue("gear").labels[value] ?: ""
    fun modeLabel(value: Int?): String = SignalCatalog.byKey.getValue("drive_mode").labels[value] ?: "—"
    fun applyClassic() = applyTheme(2)
    fun applyTheme(theme: Int) { if (theme in 1..3) appearance(205, theme) }
    fun applyFont(font: Int) { if (font in 1..2) appearance(215, font) }
    fun applyDayNight(mode: Int) { if (mode in 0..1) appearance(74, mode) }
    fun applyWallpaperStyle(style: Int) { if (style in 0..2) appearance(219, style) }

    private fun appearance(code: Int, value: Int) {
        require(code in setOf(205, 215, 74, 219))
        worker.execute {
            val binder = cluster.binder
            if (binder == null || !cluster.ready) { note = "خدمة العدادات غير متصلة"; return@execute }
            note = try {
                transact(binder, CI_DESC, code, { writeInt(value) }) { Unit }
                attempted.remove("theme")
                attempted.remove("font")
                ""
            } catch (_: Exception) { "تعذر تطبيق مظهر العدادات" }
        }
    }

    fun openAvm() {
        try {
            context.startService(Intent().setClassName("com.autolink.app.avm", "com.autolink.app.avm.AvmService").putExtra("key_type", 1))
        } catch (_: Exception) { note = "تعذر فتح كاميرا ٣٦٠" }
    }

    private fun <T> transact(binder: IBinder, descriptor: String, code: Int, write: Parcel.() -> Unit, read: Parcel.() -> T): T {
        if (binder.interfaceDescriptor != descriptor) throw RemoteException("OEM interface mismatch")
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken(descriptor)
            request.write()
            if (!binder.transact(code, request, reply, 0)) throw RemoteException("OEM transaction unsupported")
            reply.readException()
            return reply.read()
        } finally { reply.recycle(); request.recycle() }
    }

    companion object {
        const val TAG = "T1Launcher"
        private const val CAR_DESC = "com.autolink.adapterinterface.car.IALCar"
        private const val CI_DESC = "com.autolink.adapterinterface.clusterinteraction.IClusterInteraction"
        private const val VEHICLE_DESC = "com.autolink.app.vehicleservice.IVehicleControl"
    }
}
