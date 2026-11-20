package com.ghosted907.t1remote

import android.content.Context

/** A car button fires only after a continuous hold; pure so it is unit-tested. */
object HoldPolicy {
    const val HOLD_MS = 1000L
    fun fires(pressedAt: Long, releasedAt: Long?, now: Long): Boolean =
        releasedAt == null && now - pressedAt >= HOLD_MS
}

/** Phone-side memory: the last launcher used (for auto-reconnect) and a manual address. No keys or PINs. */
class PhoneStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("t1remote", Context.MODE_PRIVATE)
    var lastHost: String
        get() = p.getString("last_host", "").orEmpty()
        set(v) = p.edit().putString("last_host", v).apply()
    var lastHostId: String
        get() = p.getString("last_host_id", "").orEmpty()
        set(v) = p.edit().putString("last_host_id", v).apply()
    var lastName: String
        get() = p.getString("last_name", "").orEmpty()
        set(v) = p.edit().putString("last_name", v).apply()
    var manualHost: String
        get() = p.getString("manual_host", "").orEmpty()
        set(v) = p.edit().putString("manual_host", v).apply()
    var autoConnect: Boolean
        get() = p.getBoolean("auto_connect", true)
        set(v) = p.edit().putBoolean("auto_connect", v).apply()
    var tab: Int
        get() = p.getInt("tab", 0)
        set(v) = p.edit().putInt("tab", v).apply()
    /** Phone look only (0 = follow the phone, 1 = day, 2 = night). Independent of the launcher's day/night. */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v).apply()
    fun forget() = p.edit().remove("last_host").remove("last_host_id").remove("last_name").apply()
}
