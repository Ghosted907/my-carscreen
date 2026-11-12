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

/** Transparent accessibility overlays for user-assigned inward edge swipes. */
class JctGestureZones(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val views = HashMap<JctZone, View>()

    fun refresh() {
        remove()
        val prefs = prefs(service)
        val enabled = JctZone.values().filter { prefs.getBoolean("enabled_" + it.name, false) }
        if (enabled.isEmpty()) return
        val t = JctZone.thickness(service.resources.displayMetrics.density) * widthPercent(service) / 100
        val m = DisplayMetrics()
        runCatching { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m) }
        if (m.widthPixels <= 0 || m.heightPixels <= 0) m.setTo(service.resources.displayMetrics)
        enabled.forEach { z -> JctZone.geometry(z, m.widthPixels, m.heightPixels, t)?.let { add(z, it) } }
    }

    private fun add(zone: JctZone, r: JctZone.Rect) {
        val v = ZoneView(service, zone)
        val p = WindowManager.LayoutParams(r.w, r.h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            x = maxOf(0, r.x); y = maxOf(0, r.y); gravity = Gravity.TOP or Gravity.START; title = "GestureZone_${zone.name}"
        }
        var sx = 0f; var sy = 0f; var dx = 0f; var dy = 0f
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; dx = 0f; dy = 0f; v.feedback(0f) }
                MotionEvent.ACTION_MOVE -> {
                    dx = e.rawX - sx; dy = e.rawY - sy
                    v.feedback((kotlin.math.hypot(dx, dy) / 250f).coerceIn(0f, 1f))
                }
                MotionEvent.ACTION_UP -> {
                    dx = e.rawX - sx; dy = e.rawY - sy
                    val threshold = JctZone.THRESHOLD_DP * service.resources.displayMetrics.density * sensitivityPercent(service) / 100f
                    if (JctZone.fires(zone, dx, dy, threshold)) runCatching { JctLaunch.runKey(service, JctStore.ZONE_ACTION + zone.name) }
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

    /** Small feedback flash while a shortcut is pulled. */
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
        private const val WIDTH = "strip_width_percent"
        private const val SENSITIVITY = "swipe_distance_percent"
        fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        fun widthPercent(c: Context) = prefs(c).getInt(WIDTH, 100).coerceIn(75, 150)
        fun sensitivityPercent(c: Context) = prefs(c).getInt(SENSITIVITY, 100).coerceIn(75, 150)
        fun setWidthPercent(c: Context, value: Int) {
            prefs(c).edit().putInt(WIDTH, value.coerceIn(75, 150)).apply()
            T1TouchService.instance?.zones?.refresh()
        }
        fun setSensitivityPercent(c: Context, value: Int) {
            prefs(c).edit().putInt(SENSITIVITY, value.coerceIn(75, 150)).apply()
        }
        fun setEnabled(c: Context, z: JctZone, on: Boolean) {
            prefs(c).edit().putBoolean("enabled_" + z.name, on).apply()
            T1TouchService.instance?.zones?.refresh()
        }
        fun enabled(c: Context, z: JctZone) = prefs(c).getBoolean("enabled_" + z.name, false)
    }
}
