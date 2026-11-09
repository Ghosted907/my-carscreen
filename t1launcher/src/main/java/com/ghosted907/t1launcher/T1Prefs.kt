package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect

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

    var destinationCluster: Boolean
        get() = prefs.getBoolean("destCluster", false)
        set(value) = prefs.edit().putBoolean("destCluster", value).apply()

    var clusterW: Int
        get() = prefs.getInt("cw", 800)
        set(value) = prefs.edit().putInt("cw", value).apply()
    var clusterH: Int
        get() = prefs.getInt("ch", 560)
        set(value) = prefs.edit().putInt("ch", value).apply()
    var clusterX: Int
        get() = prefs.getInt("cx", 560)
        set(value) = prefs.edit().putInt("cx", value).apply()
    var clusterY: Int
        get() = prefs.getInt("cy", 80)
        set(value) = prefs.edit().putInt("cy", value).apply()

    var clusterPackage: String
        get() = prefs.getString("clusterPkg", "") ?: ""
        set(value) = prefs.edit().putString("clusterPkg", value).apply()

    var clusterClass: String
        get() = prefs.getString("clusterCls", "") ?: ""
        set(value) = prefs.edit().putString("clusterCls", value).apply()

    fun clusterComponent(): ComponentName? {
        if (clusterPackage.isBlank() || clusterClass.isBlank()) return null
        return ComponentName(clusterPackage, clusterClass)
    }

    var savedTheme: Int
        get() = prefs.getInt("savedTheme", 2)
        set(value) = prefs.edit().putInt("savedTheme", value).apply()
    var savedFont: Int
        get() = prefs.getInt("savedFont", 1)
        set(value) = prefs.edit().putInt("savedFont", value).apply()

    fun bounds(): Rect = Rect(clusterX, clusterY, clusterX + clusterW, clusterY + clusterH)

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
