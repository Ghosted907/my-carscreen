package com.ghosted907.t1remote

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject

/** Latest OP_DATA reply. Reading requests only (never a command); polling stops when the screen leaves. */
class LiveData {
    var readings by mutableStateOf(mapOf<String, Reading>())
    var order by mutableStateOf(listOf<Reading>())
    var summary by mutableStateOf(JSONObject())
    var receivedAt by mutableLongStateOf(0L)
    var error by mutableStateOf("")

    fun apply(d: JSONObject) {
        val a = d.optJSONArray("readings")
        val list = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Reading::parse) }
        order = list
        readings = list.associateBy { it.key }
        summary = d.optJSONObject("summary") ?: JSONObject()
        receivedAt = SystemClock.elapsedRealtime()
        error = ""
    }

    fun clear() { readings = emptyMap(); order = emptyList(); summary = JSONObject(); receivedAt = 0L }

    operator fun get(key: String): Reading? = readings[key]

    /** Live reading or null, so callers can show "غير متاح" instead of a number. */
    fun live(key: String): Reading? = readings[key]?.takeIf { it.live && !stale() }

    fun stale(now: Long = SystemClock.elapsedRealtime()) = Freshness.replyStale(receivedAt, now)
}

/** Polls OP_DATA every [periodMs] while this composable is on screen and the link is up. */
@Composable
fun rememberLive(session: Session, periodMs: Long = 2000): LiveData {
    val live = remember { LiveData() }
    LaunchedEffect(session.epoch, session.drops) {
        if (!session.supports(RemoteProtocol.OP_DATA)) return@LaunchedEffect
        var inFlight = false   // one request at a time; a slow car never builds a queue
        while (isActive) {
            if (session.connected && !inFlight) {
                inFlight = true
                session.send(JSONObject().put("op", RemoteProtocol.OP_DATA), quiet = true,
                    onError = { inFlight = false; live.error = it }) { inFlight = false; live.apply(it) }
            }
            delay(periodMs)
        }
    }
    return live
}
