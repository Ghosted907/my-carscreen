package com.ghosted907.t1remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Connection life cycle for the UI: connect, keep-alive ping, auto-reconnect with back-off to the
 * last launcher, and a stop when the app goes to the background. One client at a time; a late
 * failure from an old client never tears down a newer one.
 */
class Session(private val scope: CoroutineScope, private val store: PhoneStore) {
    enum class State { Idle, Connecting, Connected, Reconnecting }

    var state by mutableStateOf(State.Idle); private set
    var status by mutableStateOf(""); private set
    var statusOk by mutableStateOf(true); private set
    var info by mutableStateOf(JSONObject()); private set
    var host by mutableStateOf(""); private set
    var name by mutableStateOf(""); private set
    /** Bumped after every (re)connect so screens reload their data. */
    var epoch by mutableIntStateOf(0); private set

    private var client: RemoteClient? = null
    private var connectJob: Job? = null
    private var keepAlive: Job? = null
    private var wanted = false
    private var foreground = true
    private var userStopped = false

    val connected get() = state == State.Connected
    val rev3 get() = ClientRules.supportsRev3(info)
    fun supports(op: String) = ClientRules.supports(info, op)

    fun say(msg: String, ok: Boolean = true) { status = msg; statusOk = ok }

    fun connect(target: String, label: String = "") {
        if (!ClientRules.validHost(target)) { say("العنوان غير صالح", false); return }
        wanted = true
        userStopped = false
        host = target
        if (label.isNotEmpty()) name = label
        dropClient()
        connectJob?.cancel()
        connectJob = scope.launch { loop(target, first = true) }
    }

    private suspend fun loop(target: String, first: Boolean) {
        var attempt = 0
        var initial = first
        while (scope.isActive && wanted && foreground) {
            state = if (initial) State.Connecting else State.Reconnecting
            say(if (initial) "جاري الاتصال بـ $target …" else "إعادة الاتصال بـ $target (محاولة ${attempt + 1}) …")
            val c = RemoteClient(target)
            val ok = runCatching { c.connect() }
            if (ok.isSuccess) {
                client = c
                val h = ok.getOrThrow()
                name = h.optString("name").ifEmpty { name }
                store.lastHost = target; store.lastHostId = c.hostId; store.lastName = name
                val ping = runCatching { c.call(JSONObject().put("op", RemoteProtocol.OP_PING)) }.getOrNull()
                if (ping == null || client !== c) { c.close(); client = null }
                else {
                    info = ping.optJSONObject("data") ?: JSONObject()
                    state = State.Connected
                    epoch++
                    say(if (rev3) "متصل بـ $name" else "متصل بـ $name — اللانشر قديم: بعض الميزات تحتاج تحديث T1")
                    startKeepAlive(c)
                    return
                }
            } else c.close()
            val reason = ok.exceptionOrNull()?.message ?: "تعذّر الاتصال"
            if (initial && attempt == 0) say("$reason — سأعيد المحاولة تلقائياً", false)
            else say("$reason — محاولة جديدة بعد ${ClientRules.backoffMs(attempt) / 1000} ث", false)
            state = State.Reconnecting
            delay(ClientRules.backoffMs(attempt))
            attempt++
            initial = false
        }
    }

    private fun startKeepAlive(c: RemoteClient) {
        keepAlive?.cancel()
        keepAlive = scope.launch {
            while (isActive && client === c) {
                delay(20000)
                if (client !== c) break
                runCatching { c.call(JSONObject().put("op", RemoteProtocol.OP_PING), 8000) }
                    .onSuccess { r -> r.optJSONObject("data")?.let { info = it } }
                    .onFailure { lost(c, it.message) }
            }
        }
    }

    /** Called when [c] failed. Ignored if a newer client already replaced it. */
    private fun lost(c: RemoteClient, reason: String?) {
        if (client !== c) return
        c.close(); client = null
        keepAlive?.cancel()
        say("انقطع الاتصال${reason?.let { ": $it" } ?: ""}", false)
        state = State.Reconnecting
        if (wanted && foreground) { connectJob?.cancel(); connectJob = scope.launch { loop(host, first = false) } }
        else state = State.Idle
    }

    /**
     * Sends one command. [after] runs only on an ok reply; [onError] on a refusal. The status line
     * shows the launcher's Arabic message unless [quiet].
     */
    fun send(body: JSONObject, quiet: Boolean = false, timeoutMs: Int = RemoteClient.CALL_TIMEOUT_MS,
             onError: (String) -> Unit = {}, after: (JSONObject) -> Unit = {}) {
        val c = client
        if (c == null || state != State.Connected) { if (!quiet) say("غير متصل بالسيارة", false); onError("غير متصل"); return }
        scope.launch {
            runCatching { c.call(body, timeoutMs) }.onSuccess { r ->
                val ok = r.optBoolean("ok")
                if (!quiet || !ok) say(r.optString("msg"), ok)
                if (ok) after(r.optJSONObject("data") ?: JSONObject()) else onError(r.optString("msg"))
            }.onFailure { e -> onError(e.message ?: ""); lost(c, e.message) }
        }
    }

    fun refreshInfo() = send(JSONObject().put("op", RemoteProtocol.OP_PING), quiet = true) { info = it }

    fun disconnect(msg: String = "تم قطع الاتصال") {
        wanted = false
        userStopped = true
        connectJob?.cancel(); keepAlive?.cancel()
        dropClient()
        state = State.Idle
        say(msg)
    }

    private fun dropClient() { client?.close(); client = null; keepAlive?.cancel() }

    /** App visible again: reconnect to the last launcher if the user did not disconnect. */
    fun onForeground() {
        foreground = true
        if (state == State.Idle && store.autoConnect && store.lastHost.isNotEmpty() && !userStopped)
            connect(store.lastHost, store.lastName)
    }

    /** App in background: free the socket so nothing leaks; [onForeground] restores it. */
    fun onBackground() {
        foreground = false
        val keep = wanted
        connectJob?.cancel(); keepAlive?.cancel()
        dropClient()
        state = State.Idle
        wanted = keep
    }

    /** A launcher found by discovery: reconnect automatically if it is the one used last time. */
    fun onFound(list: List<Discovery.Found>) {
        if (state == State.Connected || state == State.Connecting) return
        if (!store.autoConnect || userStopped || !foreground) return
        val last = store.lastHost
        val match = list.firstOrNull { it.host == last } ?: list.singleOrNull()?.takeIf { last.isEmpty() || state == State.Reconnecting }
        if (match != null && (state == State.Idle || match.host != host)) connect(match.host, match.name)
    }
}
