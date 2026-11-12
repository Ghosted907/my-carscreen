package com.ghosted907.t1remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.ghosted907.t1remote.protocol.RemoteProtocol

/** Finds launchers on the same Wi-Fi via Android NSD (mDNS / DNS-SD). */
class Discovery(context: Context, private val onChange: (List<Found>) -> Unit) {
    data class Found(val name: String, val host: String, val port: Int)

    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, Found>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        lock = runCatching {
            (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("t1remote").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { queue += info; next() } }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { found.remove(info.serviceName); onChange(found.values.toList()) }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(RemoteProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    private fun next() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, e: Int) { main.post { resolving = false; next() } }
            override fun onServiceResolved(i: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = i.host?.hostAddress
                main.post {
                    if (host != null) { found[i.serviceName] = Found(i.serviceName, host, i.port); onChange(found.values.toList()) }
                    resolving = false; next()
                }
            }
        })
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { lock?.release() }
    }
}
