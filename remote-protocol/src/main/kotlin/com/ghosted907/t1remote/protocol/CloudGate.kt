package com.ghosted907.t1remote.protocol

/** A relay clock anchored to monotonic time: car clock settings cannot extend command life. */
class CloudGate {
    private var serverTime = 0L
    private var anchor = 0L
    private var session = ""
    private var lastSeq = 0L
    @Synchronized fun ready(serverMillis: Long, elapsedMillis: Long) {
        serverTime = serverMillis; anchor = elapsedMillis; session = ""; lastSeq = 0
    }
    @Synchronized fun begin(value: String) { session = value; lastSeq = 0 }
    @Synchronized fun end(value: String) { if (session == value) { session = ""; lastSeq = 0 } }
    @Synchronized fun reset() { session = ""; serverTime = 0; lastSeq = 0 }
    @Synchronized fun accept(value: String, seq: Long, deadline: Long, elapsedMillis: Long): Boolean {
        val now = serverTime + elapsedMillis - anchor
        if (serverTime <= 0 || value.isEmpty() || value != session || seq <= lastSeq || seq <= 0 || deadline < now || deadline > now + 20000) return false
        lastSeq = seq
        return true
    }
}
