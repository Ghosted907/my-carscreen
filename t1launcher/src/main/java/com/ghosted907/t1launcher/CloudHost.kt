package com.ghosted907.t1launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.ghosted907.t1remote.protocol.CloudConfig
import com.ghosted907.t1remote.protocol.CloudGate
import com.ghosted907.t1remote.protocol.RemoteProtocol
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

object CloudStore {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("t1_cloud", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun enable(c: Context, value: Boolean) { prefs(c).edit().putBoolean("enabled", value).commit() }
    data class Keys(val room: String, val host: String, val phone: String) { val code get() = "$room:$phone" }
    @Synchronized fun keys(c: Context): Keys {
        val p = prefs(c)
        val room = p.getString("room", null)
        if (room != null && p.contains("host") && p.contains("phone"))
            return Keys(room, p.getString("host", "").orEmpty(), p.getString("phone", "").orEmpty())
        return reset(c)
    }
    @Synchronized fun reset(c: Context): Keys {
        val k = Keys(RemoteProtocol.randomHex(12), RemoteProtocol.randomHex(32), RemoteProtocol.randomHex(16))
        check(prefs(c).edit().putString("room", k.room).putString("host", k.host).putString("phone", k.phone).commit())
        return k
    }
}

object CloudStatus { @Volatile var message: String = "الربط عبر الإنترنت متوقف" }

/** Authenticated outbound connection. The existing RemoteActions validator is used unchanged. */
class CloudHost(private val context: Context, private val actions: RemoteActions) {
    private val main = Handler(Looper.getMainLooper())
    private val work = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(8))
    private val gate = CloudGate()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var generation = 0
    private var identity = ""
    private var attempt = 0
    private var closed = false

    fun refresh() {
        if (closed) return
        if (!CloudStore.enabled(context)) { stopLink(); CloudStatus.message = "الربط عبر الإنترنت متوقف"; return }
        val k = CloudStore.keys(context)
        if (identity == k.room) return
        stopLink(); identity = k.room; attempt = 0
        connect(generation, k)
    }
    private fun connect(lease: Int, keys: CloudStore.Keys) {
        if (closed || lease != generation || !CloudStore.enabled(context)) return
        CloudStatus.message = "جاري الاتصال بالسيرفر…"
        val request = Request.Builder().url("${CloudConfig.RELAY}/v1/rooms/${keys.room}/host")
            .header("Authorization", "Bearer ${keys.host}")
            .header("X-T1-Phone-Hash", CloudConfig.sha256(keys.phone)).build()
        val ws = CloudConfig.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (lease != generation || closed) { ws.cancel(); return }
                socket = ws; attempt = 0; CloudStatus.message = "متصل بالسيرفر · جاهز لربط الجوال"
            }
            override fun onMessage(ws: WebSocket, text: String) {
                if (lease != generation || socket !== ws || !CloudStore.enabled(context)) return
                if (text.length > RemoteProtocol.MAX_LINE + 10000) { ws.cancel(); return }
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                val session = m.optString("session")
                when (m.optString("t")) {
                    "relay" -> when (m.optString("event")) {
                        "ready" -> gate.ready(m.optLong("serverTime"), SystemClock.elapsedRealtime())
                        "phone_connected" -> {
                            gate.begin(session); CloudStatus.message = "الجوال متصل عبر الإنترنت"
                            ws.send(JSONObject().put("t", "hello").put("session", session)
                                .put("hello", RemoteProtocol.hello(RemoteProtocol.SERVICE_NAME, RemoteStore.hostId(context))).toString())
                        }
                        "phone_disconnected" -> { gate.end(session); CloudStatus.message = "متصل بالسيرفر · الجوال غير متصل" }
                    }
                    "forward" -> {
                        val command = m.optJSONObject("command") ?: return
                        val seq = command.optLong("seq", -1)
                        val task = Runnable {
                            if (lease != generation || socket !== ws || !CloudStore.enabled(context)) return@Runnable
                            if (!gate.accept(session, seq, m.optLong("deadline"), SystemClock.elapsedRealtime())) {
                                reply(ws, session, RemoteProtocol.result(seq, false, "انتهت صلاحية الأمر أو الاتصال؛ لم يُنفذ")); return@Runnable
                            }
                            val result = when (val parsed = RemoteProtocol.parse(command.toString())) {
                                is RemoteProtocol.Parsed.Rejected -> RemoteProtocol.result(parsed.seq, false, parsed.msg)
                                is RemoteProtocol.Parsed.Ok -> {
                                    val out = runCatching { actions.run(parsed.body) }.getOrElse { RemoteActions.Out(false, "تعذّر التنفيذ") }
                                    RemoteProtocol.result(parsed.seq, out.ok, out.msg, out.data)
                                }
                            }
                            if (lease == generation && socket === ws) reply(ws, session, result)
                        }
                        try { work.execute(task) } catch (_: java.util.concurrent.RejectedExecutionException) {
                            reply(ws, session, RemoteProtocol.result(seq, false, "اللانشر مشغول؛ لم يُنفذ الأمر"))
                        }
                    }
                }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { retry(lease, keys) }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, null); retry(lease, keys) }
        })
        if (lease == generation) socket = ws else ws.cancel()
    }
    private fun reply(ws: WebSocket, session: String, response: JSONObject) {
        ws.send(JSONObject().put("t", "reply").put("session", session).put("response", response).toString())
    }
    private fun retry(lease: Int, keys: CloudStore.Keys) {
        main.post {
            if (closed || lease != generation) return@post
            socket?.cancel(); socket = null; gate.reset(); work.queue.clear()
            generation++
            val next = generation
            CloudStatus.message = "غير متصل بالسيرفر · إعادة المحاولة تلقائياً"
            val delay = (1000L shl attempt++.coerceAtMost(5)).coerceAtMost(30000)
            main.postDelayed({ connect(next, keys) }, delay)
        }
    }
    private fun stopLink() {
        generation++; identity = ""; main.removeCallbacksAndMessages(null)
        socket?.cancel(); socket = null; gate.reset(); work.queue.clear()
    }
    fun close() { closed = true; stopLink(); work.shutdownNow() }
}
