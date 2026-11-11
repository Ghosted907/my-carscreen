package com.ghosted907.t1launcher

import android.content.Context

class T1Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("t1", Context.MODE_PRIVATE)

    var day: Boolean
        get() = prefs.getBoolean("day", false)
        set(value) = prefs.edit().putBoolean("day", value).apply()

    var boot: Boolean
        get() = prefs.getBoolean("boot", false)
        set(value) = prefs.edit().putBoolean("boot", value).apply()

    /** Edge swipe is on until the owner turns it off. Right edge is the default. */
    var edgeEnabled: Boolean
        get() = prefs.getBoolean("edge", true)
        set(value) = prefs.edit().putBoolean("edge", value).apply()

    var edgeRight: Boolean
        get() = prefs.getBoolean("edgeRight", true)
        set(value) = prefs.edit().putBoolean("edgeRight", value).apply()

    /** Right-edge tap tab. On until the owner turns it off. */
    var edgeTab: Boolean
        get() = prefs.getBoolean("edgeTab", true)
        set(value) = prefs.edit().putBoolean("edgeTab", value).apply()

    var destinationCluster: Boolean
        get() = prefs.getBoolean("destCluster", false)
        set(value) = prefs.edit().putBoolean("destCluster", value).apply()

    private fun appList(key: String): List<String> = prefs.getString(key, "")
        .orEmpty().split('|').filter { it.isNotBlank() }.distinct()

    var pinnedApps: List<String>
        get() = appList("pinnedApps")
        set(value) = prefs.edit().putString("pinnedApps", value.distinct().take(10).joinToString("|")).apply()

    var recentApps: List<String>
        get() = appList("recentApps")
        set(value) = prefs.edit().putString("recentApps", value.distinct().take(8).joinToString("|")).apply()

    var clusterPackage: String
        get() = prefs.getString("clusterPkg", "") ?: ""
        set(value) = prefs.edit().putString("clusterPkg", value).apply()


    var savedTheme: Int
        get() = prefs.getInt("savedTheme", 2)
        set(value) = prefs.edit().putInt("savedTheme", value).apply()
    var savedFont: Int
        get() = prefs.getInt("savedFont", 1)
        set(value) = prefs.edit().putInt("savedFont", value).apply()

    fun slot(id: String): Boolean = prefs.getBoolean("slot_$id", false)

    fun setSlot(id: String, on: Boolean) {
        prefs.edit().putBoolean("slot_$id", on).apply()
    }

    fun hideAllSlots() {
        val editor = prefs.edit()
        slots.forEach { editor.putBoolean("slot_$it", false) }
        editor.apply()
    }

    fun enabledSlots(): List<String> = slots.filter { slot(it) }

    companion object {
        val slots = listOf("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10")
    }
}
