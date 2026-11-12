package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/** One TCP connection to the launcher. Requests are sent one at a time. */
class RemoteClient(val host: String, val port: Int = RemoteProtocol.PORT) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val lock = Mutex()
    private var seq = 0L
    lateinit var hello: JSONObject
        private set
    val hostId: String get() = hello.optString("host")

    suspend fun connect(): JSONObject = withContext(Dispatchers.IO) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 15000
        socket = s
        reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))
        val h = JSONObject(reader!!.readLine() ?: error("لا رد من اللانشر"))
        check(h.optString("t") == "hello" && h.optInt("v") == RemoteProtocol.VERSION) { "إصدار اللانشر غير متوافق" }
        hello = h
        h
    }

    private fun exchange(msg: JSONObject): JSONObject {
        val w = writer ?: error("غير متصل")
        w.write(msg.toString()); w.write("\n"); w.flush()
        return JSONObject(reader!!.readLine() ?: error("انقطع الاتصال"))
    }

    suspend fun call(body: JSONObject): JSONObject = lock.withLock {
        withContext(Dispatchers.IO) {
            seq += 1
            exchange(RemoteProtocol.command(seq, body))
        }
    }

    fun close() { runCatching { socket?.close() }; socket = null; writer = null; reader = null }
}
