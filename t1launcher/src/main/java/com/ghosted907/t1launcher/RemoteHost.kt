package com.ghosted907.t1launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log
import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors

/**
 * "التحكم من الجوال": off by default. When off, nothing listens and nothing else changes.
 * No pairing: any phone on the same network can connect while it is on.
 */
object RemoteStore {
    private const val FILE = "t1_remote"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) = p(c).edit().putBoolean("enabled", on).apply()

    /** Second, separate switch for the nine approved window/sunroof/sunshade commands. Off by default. */
    fun vehicleAllowed(c: Context) = p(c).getBoolean("vehicle", false)
    fun setVehicleAllowed(c: Context, on: Boolean) = p(c).edit().putBoolean("vehicle", on).apply()

    fun hostId(c: Context): String = p(c).getString("host", null) ?: RemoteProtocol.randomHex(8).also {
        p(c).edit().putString("host", it).apply()
    }

    /** Where the phone was told each app is (main / cluster), for the "open windows" list. */
    fun rememberScreen(c: Context, pkg: String, screen: String) =
        p(c).edit().putString("screen_$pkg", screen).apply()
    fun screenOf(c: Context, pkg: String): String? = p(c).getString("screen_$pkg", null)
    fun remotePackages(c: Context): List<String> = p(c).all.keys.filter { it.startsWith("screen_") }.map { it.removePrefix("screen_") }
    fun forget(c: Context, pkg: String) = p(c).edit().remove("screen_$pkg").apply()
}

object RemoteHost {
    fun onProcess(context: Context) {
        if (RemoteStore.enabled(context)) start(context)
    }

    fun setEnabled(context: Context, on: Boolean) {
        RemoteStore.setEnabled(context, on)
        if (on) start(context) else context.stopService(Intent(context, RemoteHostService::class.java))
    }

    private fun start(context: Context) {
        runCatching { context.startForegroundService(Intent(context.applicationContext, RemoteHostService::class.java)) }
            .onFailure { Log.w("T1Remote", "start failed ${it.message}") }
    }

    /** IPv4 addresses of this head unit on Wi-Fi / hotspot, for manual entry on the phone. */
    fun addresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }.filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" }
            .filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())
}

/** Small TCP server, alive only while the switch is on. Commands go to RemoteActions. */
class RemoteHostService : Service() {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(5)
    private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private lateinit var actions: RemoteActions

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        actions = RemoteActions(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!RemoteStore.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        foreground()
        if (server == null) listen()
        return START_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "T1 remote", NotificationManager.IMPORTANCE_LOW))
        startForeground(78, Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stat_t1)
            .setContentTitle("التحكم من الجوال مفعّل").setContentText("أطفئه من إعدادات T1").build())
    }

    private fun listen() {
        val s = runCatching {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(RemoteProtocol.PORT), 8) }
        }.getOrElse { Log.w(TAG, "bind failed ${it.message}"); stopSelf(); return }
        server = s
        register()
        Thread({
            while (!s.isClosed) {
                val socket = runCatching { s.accept() }.getOrNull() ?: break
                if (clients.size >= 4) { runCatching { socket.close() }; continue }
                clients += socket
                pool.execute { serve(socket) }
            }
        }, "t1-remote-accept").start()
    }

    private fun register() {
        val manager = getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = RemoteProtocol.SERVICE_NAME; serviceType = RemoteProtocol.SERVICE_TYPE; port = RemoteProtocol.PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "nsd ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(TAG, "nsd failed $e") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { nsd = manager; nsdListener = listener }
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 10 * 60 * 1000
            socket.tcpNoDelay = true
            val reader = InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            fun send(o: JSONObject) { writer.write(o.toString()); writer.write("\n"); writer.flush() }
            send(RemoteProtocol.hello(RemoteProtocol.SERVICE_NAME, RemoteStore.hostId(this)))
            while (RemoteStore.enabled(this)) {
                val line = readLine(reader) ?: break
                when (val p = RemoteProtocol.parse(line)) {
                    is RemoteProtocol.Parsed.Rejected -> send(RemoteProtocol.result(p.seq, false, p.msg))
                    is RemoteProtocol.Parsed.Ok -> {
                        val out = runCatching { actions.run(p.body) }
                            .getOrElse { RemoteActions.Out(false, "تعذّر التنفيذ: ${it.javaClass.simpleName}") }
                        send(RemoteProtocol.result(p.seq, out.ok, out.msg, out.data))
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "client closed ${e.javaClass.simpleName}")
        } finally {
            clients -= socket
            runCatching { socket.close() }
        }
    }

    /** Line reader with a hard size cap, so a client cannot exhaust memory. */
    private fun readLine(r: Reader): String? {
        val sb = StringBuilder()
        while (true) {
            val c = r.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            sb.append(c.toChar())
            if (sb.length > RemoteProtocol.MAX_LINE) return null
        }
    }

    override fun onDestroy() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }
        runCatching { server?.close() }
        synchronized(clients) { clients.forEach { runCatching { it.close() } } }
        pool.shutdownNow()
        actions.close()
        server = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "T1Remote"
        private const val CH = "t1_remote"
    }
}
