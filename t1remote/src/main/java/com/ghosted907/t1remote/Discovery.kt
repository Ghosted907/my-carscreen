package com.ghosted907.t1remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.ghosted907.t1remote.protocol.RemoteProtocol

/**
 * Finds launchers on the same Wi-Fi via Android NSD (mDNS / DNS-SD). Plain discovery, no pairing.
 * Fixes from 1.2.0: a resolve that never answers no longer blocks the queue (timeout), a failed
 * start is retried, stop() resets every state, and discovery restarts periodically while nothing is found.
 */
class Discovery(context: Context, private val onChange: (List<Found>) -> Unit) {
    data class Found(val name: String, val host: String, val port: Int)

    private val app = context.applicationContext
    private val nsd: NsdManager? = app.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, Found>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving: String? = null
    private var generation = 0
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var running = false
    var problem: String = ""
        private set

    fun start() {
        if (running) return
        running = true
        generation++
        lock = runCatching {
            (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("t1remote").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        begin()
    }

    private fun begin() {
        val manager = nsd ?: run { problem = "البحث التلقائي غير مدعوم في هذا الجوال؛ استخدم العنوان اليدوي"; return }
        val gen = generation
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) { main.post { problem = "" } }
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {
                main.post {
                    if (gen != generation || !running) return@post
                    listener = null
                    problem = "تعذّر بدء البحث ($e)؛ إعادة المحاولة…"
                    main.postDelayed({ if (gen == generation && running && listener == null) begin() }, 3000)
                }
            }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType?.contains("t1remote") == false) return
                main.post { if (gen == generation && running) { queue.removeAll { it.serviceName == info.serviceName }; queue += info; next() } }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (gen == generation && found.remove(info.serviceName) != null) onChange(found.values.toList()) }
            }
        }
        listener = l
        runCatching { manager.discoverServices(RemoteProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { listener = null; problem = "تعذّر بدء البحث؛ استخدم العنوان اليدوي" }
        // While nothing is found, restart discovery every 20 s (some routers drop the first queries).
        main.postDelayed(refresh, 20000)
    }

    private val refresh: Runnable = Runnable {
        if (!running) return@Runnable
        if (found.isEmpty()) { stopListener(); generation++; resolving = null; queue.clear(); begin() }
        else main.postDelayed(refreshAgain, 20000)
    }
    private val refreshAgain: Runnable = Runnable { if (running) main.post(refresh) }

    private fun next() {
        if (resolving != null || !running) return
        val info = queue.removeFirstOrNull() ?: return
        val manager = nsd ?: return
        val name = info.serviceName
        val gen = generation
        resolving = name
        val timeout = Runnable { if (resolving == name && gen == generation) { resolving = null; next() } }
        main.postDelayed(timeout, 6000)
        @Suppress("DEPRECATION")
        runCatching {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) {
                    main.post {
                        main.removeCallbacks(timeout)
                        if (gen != generation) return@post
                        if (resolving == name) resolving = null
                        // FAILURE_ALREADY_ACTIVE (3): try again shortly.
                        if (e == NsdManager.FAILURE_ALREADY_ACTIVE) main.postDelayed({ if (running && gen == generation) { queue += info; next() } }, 800)
                        next()
                    }
                }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = i.host?.hostAddress
                    main.post {
                        main.removeCallbacks(timeout)
                        if (gen != generation) return@post
                        if (host != null && running) { found[i.serviceName] = Found(i.serviceName, host, i.port); onChange(found.values.toList()) }
                        if (resolving == name) resolving = null
                        next()
                    }
                }
            })
        }.onFailure { main.removeCallbacks(timeout); resolving = null; main.post { next() } }
    }

    private fun stopListener() {
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
    }

    fun stop() {
        running = false
        generation++
        main.removeCallbacksAndMessages(null)
        stopListener()
        queue.clear()
        resolving = null
        runCatching { lock?.release() }
        lock = null
    }
}
