package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

/**
 * One TCP connection to the launcher. Requests go one at a time; each reply is matched by seq.
 * Any I/O error closes the socket, so a broken connection is never reused (the session reconnects).
 */
class RemoteClient(val host: String, val port: Int = RemoteProtocol.PORT) {
    @Volatile private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val lock = Mutex()
    private var seq = 0L
    var hello: JSONObject = JSONObject()
        private set
    val hostId: String get() = hello.optString("host")
    val isOpen: Boolean get() = socket?.let { it.isConnected && !it.isClosed } == true

    suspend fun connect(): JSONObject = withContext(Dispatchers.IO) {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = HELLO_TIMEOUT_MS
            s.tcpNoDelay = true
            s.keepAlive = true
            val r = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            val w = BufferedWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))
            val line = r.readLine() ?: throw IOException("لا رد من اللانشر")
            val h = runCatching { JSONObject(line) }.getOrNull() ?: throw IOException("رد غير مفهوم من اللانشر")
            ClientRules.helloError(h)?.let { throw IOException(it) }
            socket = s; reader = r; writer = w; hello = h
            h
        } catch (e: Exception) {
            runCatching { s.close() }
            throw IOException(ClientRules.arabic(e), e)
        }
    }

    /** Sends one command and waits for its reply. Throws IOException with an Arabic message on failure. */
    suspend fun call(body: JSONObject, timeoutMs: Int = CALL_TIMEOUT_MS): JSONObject = lock.withLock {
        withContext(Dispatchers.IO) {
            val s = socket ?: throw IOException("غير متصل")
            val w = writer ?: throw IOException("غير متصل")
            val r = reader ?: throw IOException("غير متصل")
            try {
                s.soTimeout = timeoutMs
                val mySeq = ++seq
                w.write(RemoteProtocol.command(mySeq, body).toString()); w.write("\n"); w.flush()
                while (true) {
                    val line = r.readLine() ?: throw IOException("أغلق اللانشر الاتصال")
                    val res = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (res.optString("t") != "res") continue
                    val got = res.optLong("seq", -1)
                    // A stale reply (from a request that timed out earlier) is skipped, never returned.
                    if (got == mySeq || got == 0L) return@withContext res
                }
                @Suppress("UNREACHABLE_CODE") throw IOException()
            } catch (e: Exception) {
                close()
                throw IOException(ClientRules.arabic(e), e)
            }
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; writer = null; reader = null
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5000
        const val HELLO_TIMEOUT_MS = 8000
        const val CALL_TIMEOUT_MS = 15000
        const val IMAGE_TIMEOUT_MS = 40000
    }
}

/** Pure client-side rules, unit-tested on the JVM. */
object ClientRules {
    /** Null when the hello is acceptable. Version 2 and newer are accepted (rev 3 keeps v=2). */
    fun helloError(h: JSONObject): String? = when {
        h.optString("t") != "hello" -> "رد غير متوقع من اللانشر"
        h.optInt("v", 0) < 2 -> "إصدار اللانشر قديم جداً؛ حدّث T1"
        else -> null
    }

    /** Reconnect delay after [attempt] failures: 1, 2, 4, 8, 15, 15 … seconds. */
    fun backoffMs(attempt: Int): Long = (1000L shl attempt.coerceIn(0, 4)).coerceAtMost(15000L)

    /** True when the launcher supports protocol revision 3 (the full-parity commands). */
    /** Rev 3 = the 1.3 feature set. Compares with 3, not REV, so a rev-3 launcher is not called outdated by a rev-4 phone. */
    fun supportsRev3(ping: JSONObject): Boolean = ping.optInt("rev", 2) >= RemoteProtocol.REV_3
    /** Rev 4 = home summary, note sync, offroad, OEM catalog, reading age (launcher 1.16.0). */
    fun supportsRev4(ping: JSONObject): Boolean = ping.optInt("rev", 2) >= RemoteProtocol.REV_4

    /** A one-shot run id is offered only when the launcher lists it (rev 4 sends run_ids); older launchers get the rev-3 list. */
    fun supportsRun(ping: JSONObject, id: String): Boolean {
        val ids = ping.optJSONArray("run_ids")
            ?: return supportsRev3(ping) && id in RemoteProtocol.RUN_IDS && id !in RemoteProtocol.RUN_IDS_REV4
        return (0 until ids.length()).any { ids.optString(it) == id }
    }

    fun supports(ping: JSONObject, op: String): Boolean {
        val ops = ping.optJSONArray("ops") ?: return op in LEGACY_OPS
        return (0 until ops.length()).any { ops.optString(it) == op }
    }

    val LEGACY_OPS = setOf(RemoteProtocol.OP_PING, RemoteProtocol.OP_APPS, RemoteProtocol.OP_TASKS, RemoteProtocol.OP_OPEN,
        RemoteProtocol.OP_MOVE, RemoteProtocol.OP_FRONT, RemoteProtocol.OP_CLOSE, RemoteProtocol.OP_RESIZE,
        RemoteProtocol.OP_VEHICLE, RemoteProtocol.OP_ART, RemoteProtocol.OP_ART_CLEAR)

    /** Valid IPv4 or host name typed by hand (manual fallback when discovery is blocked). */
    fun validHost(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 253) return false
        val parts = t.split(".")
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 && p == it.toString() } == true }) return true
        return Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,62})(\\.[A-Za-z0-9-]{1,63})*$").matches(t) && !t.all { it.isDigit() || it == '.' }
    }

    fun arabic(e: Throwable): String {
        val m = e.message
        if (m != null && m.any { it in '\u0600'..'\u06FF' }) return m
        return when (e) {
            is SocketTimeoutException -> "انتهت مهلة الرد من اللانشر"
            is java.net.ConnectException -> "اللانشر لا يستقبل الاتصال؛ تأكد أن T1 يعمل وأنكما على نفس الشبكة"
            is java.net.NoRouteToHostException -> "لا يوجد طريق للسيارة؛ تأكد من الواي فاي"
            is java.net.UnknownHostException -> "العنوان غير معروف"
            is java.net.SocketException -> "انقطع الاتصال بالسيارة"
            else -> "تعذّر الاتصال (${e.javaClass.simpleName})"
        }
    }
}
