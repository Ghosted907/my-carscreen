package com.ghosted907.t1launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Port of NaviStart `GestureZoneManager` (d7.b + a7.g case 17 + o0.*), hosted by the
 * accessibility service like NaviStart's MyA11yService:
 *  - up to six transparent windows, WindowManager.LayoutParams(w, h, 2032, 552, -3),
 *    gravity TOP|START; 2032 = TYPE_ACCESSIBILITY_OVERLAY, 552 = NOT_FOCUSABLE|NOT_TOUCH_MODAL|LAYOUT_NO_LIMITS.
 *  - thickness round(32 dp * density); side zones are half the screen height; bottom zones
 *    share the width between the side strips.
 *  - on release, when the accumulated pull (capped at 500 px) is > 90 px, the zone's action
 *    "GestureZoneManager<ZONE>" runs through the common action dispatcher.
 *  - prefs file "gesture_zones", key "enabled_<ZONE>" (default off).
 */
class JctGestureZones(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val views = HashMap<JctZone, View>()

    fun refresh() {
        remove()
        val prefs = prefs(service)
        val enabled = JctZone.values().filter { prefs.getBoolean("enabled_" + it.name, false) }
        if (enabled.isEmpty()) return
        val t = JctZone.thickness(service.resources.displayMetrics.density)
        val m = DisplayMetrics()
        runCatching { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m) }
        if (m.widthPixels <= 0 || m.heightPixels <= 0) m.setTo(service.resources.displayMetrics)
        enabled.forEach { z -> JctZone.geometry(z, m.widthPixels, m.heightPixels, t)?.let { add(z, it) } }
    }

    private fun add(zone: JctZone, r: JctZone.Rect) {
        val v = ZoneView(service, zone)
        val p = WindowManager.LayoutParams(r.w, r.h, 2032, 552, PixelFormat.TRANSLUCENT).apply {
            x = maxOf(0, r.x); y = maxOf(0, r.y); gravity = Gravity.TOP or Gravity.START; title = "GestureZone_${zone.name}"
        }
        var sx = 0f; var sy = 0f; var dist = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; dist = 0f; v.feedback(0f) }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    dist = minOf(Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat(), JctZone.MAX_PULL_PX)
                    v.feedback(minOf(1f, dist / JctZone.MAX_PULL_PX))
                }
                MotionEvent.ACTION_UP -> {
                    if (dist > JctZone.THRESHOLD_PX) runCatching { JctLaunch.runKey(service, JctStore.ZONE_ACTION + zone.name) }
                    v.feedback(-1f)
                }
                MotionEvent.ACTION_CANCEL -> v.feedback(-1f)
            }
            true
        }
        try { wm.addView(v, p); views[zone] = v } catch (e: Exception) { Log.w("GestureZone", "add ${zone.name} ${e.message}") }
    }

    fun remove() {
        views.values.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }

    /** Own drawing; NaviStart's o0.d artwork is not copied. */
    private class ZoneView(context: Context, val zone: JctZone) : View(context) {
        private var level = -1f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4A84B") }
        init { setBackgroundColor(0) }
        fun feedback(l: Float) { level = l; invalidate() }
        override fun onDraw(c: Canvas) {
            if (level < 0f) return
            paint.alpha = (60 + 160 * level).toInt().coerceIn(0, 255)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    companion object {
        const val FILE = "gesture_zones"
        fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        fun setEnabled(c: Context, z: JctZone, on: Boolean) {
            prefs(c).edit().putBoolean("enabled_" + z.name, on).apply()
            T1TouchService.instance?.zones?.refresh()
        }
        fun enabled(c: Context, z: JctZone) = prefs(c).getBoolean("enabled_" + z.name, false)
    }
}
