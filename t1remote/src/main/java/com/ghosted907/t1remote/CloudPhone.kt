package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.CloudConfig
import com.ghosted907.t1remote.protocol.RemoteProtocol
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** No offline queue: a failed call closes the link and is never retried. */
class CloudPhone(private val pair: CloudConfig.Pair) {
    private sealed interface Event {
        data class Message(val value: JSONObject) : Event
        data class Failed(val reason: String) : Event
    }
    private val events = ArrayBlockingQueue<Event>(4)
    @Volatile private var socket: WebSocket? = null
    @Volatile var isOpen = false; private set
    @Volatile private var stopped = false

    fun connect(): JSONObject {
        val request = Request.Builder().url(pair.url).header("Authorization", "Bearer ${pair.secret}").build()
        socket = CloudConfig.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { if (stopped) ws.cancel() else isOpen = true }
            override fun onMessage(ws: WebSocket, text: String) {
                if (stopped) return
                if (text.length > RemoteProtocol.MAX_LINE + 10000) { fail("رد أكبر من الحد المسموح"); return }
                val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (data.optString("t") == "relay") return
                if (!events.offer(Event.Message(data))) fail("ازدحام الردود؛ أعد الاتصال")
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                fail(when (response?.code) {
                    401 -> "رمز الربط غير صحيح أو تم تغييره من السيارة"
                    409 -> "اللانشر غير متصل بالسيرفر؛ تأكد من تشغيل شاشة السيارة والإنترنت"
                    else -> "انقطع الاتصال بالسيرفر؛ تأكد من الإنترنت"
                })
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, null); fail(if (reason == "phone_replaced") "اتصل جوال آخر بنفس رمز الربط" else "انقطع اتصال اللانشر بالسيرفر")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { fail("أُغلق الاتصال بالسيرفر") }
        })
        return try {
            val hello = receive(15000)
            ClientRules.helloError(hello)?.let { throw IOException(it) }
            hello
        } catch (e: Exception) { close(); throw e }
    }

    fun call(command: JSONObject, timeoutMs: Int): JSONObject {
        if (!isOpen || socket?.send(command.toString()) != true) throw IOException("غير متصل بالسيرفر")
        val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        while (true) {
            val remaining = TimeUnit.NANOSECONDS.toMillis(until - System.nanoTime()).coerceAtLeast(0)
            val value = receive(remaining)
            if (value.optString("t") == "res" && value.optLong("seq", -1) == command.optLong("seq")) return value
        }
    }
    private fun receive(timeoutMs: Long): JSONObject = when (val event = events.poll(timeoutMs, TimeUnit.MILLISECONDS)) {
        is Event.Message -> event.value
        is Event.Failed -> throw IOException(event.reason)
        null -> throw IOException("انتهت مهلة الرد عبر الإنترنت؛ لم يُعد إرسال الأمر")
    }
    private fun fail(reason: String) {
        isOpen = false
        events.clear(); events.offer(Event.Failed(reason))
        socket?.cancel()
    }
    fun close() {
        stopped = true; isOpen = false
        events.clear(); events.offer(Event.Failed("تم قطع الاتصال"))
        socket?.cancel(); socket = null
    }
}
