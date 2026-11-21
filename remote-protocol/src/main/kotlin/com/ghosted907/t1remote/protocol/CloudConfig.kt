package com.ghosted907.t1remote.protocol

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** The endpoint is pinned in the app, never taken from a pairing code or a received command. */
object CloudConfig {
    const val RELAY = "https://t1-remote-relay.twf62yk7bv.workers.dev"
    private val codePattern = Regex("^([a-f0-9]{24}):([a-f0-9]{32})$")
    data class Pair(val room: String, val secret: String) {
        val code get() = "$room:$secret"
        val url get() = "$RELAY/v1/rooms/$room/phone"
    }
    fun pair(code: String): Pair? = codePattern.matchEntire(code.trim().lowercase())?.let {
        Pair(it.groupValues[1], it.groupValues[2])
    }
    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    // OkHttp handles TLS hostname validation, masking, fragmentation and WebSocket control frames.
    // Protocol pings keep the link alive without waking a hibernating Durable Object.
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    }
}
