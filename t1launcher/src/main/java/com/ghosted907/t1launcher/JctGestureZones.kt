package com.ghosted907.t1launcher

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
        val vertical = zone.name.startsWith("LEFT") || zone.name.startsWith("RIGHT")
        val restT = if (vertical) r.w else r.h
        val v = ZoneView(service, zone, style(service, zone), restT)
        val p = WindowManager.LayoutParams(r.w, r.h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            x = maxOf(0, r.x); y = maxOf(0, r.y); gravity = Gravity.TOP or Gravity.START; title = "GestureZone_${zone.name}"
        }
        var sx = 0f; var sy = 0f; var dx = 0f; var dy = 0f; var downAt = 0L
        val restX = p.x; val restY = p.y
        // While a finger is down the strip widens so the handle can slide out of the edge with it.
        val pullPx = Math.round(PULL_DP * service.resources.displayMetrics.density)
        fun widen(on: Boolean) {
            if (vertical) {
                p.width = if (on) maxOf(r.w, pullPx) else r.w
                p.x = if (zone.name.startsWith("RIGHT")) restX + r.w - p.width else restX
            } else {
                p.height = if (on) maxOf(r.h, pullPx) else r.h
                p.y = restY + r.h - p.height
            }
            runCatching { wm.updateViewLayout(v, p) }
        }
        v.onReleased = { widen(false) }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; dx = 0f; dy = 0f; downAt = e.eventTime; widen(true); v.feedback(0f, e.x, e.y) }
                MotionEvent.ACTION_MOVE -> {
                    dx = e.rawX - sx; dy = e.rawY - sy
                    v.feedback((kotlin.math.hypot(dx, dy) / 250f).coerceIn(0f, 1f), e.x, e.y)
                }
                MotionEvent.ACTION_UP -> {
                    dx = e.rawX - sx; dy = e.rawY - sy
                    val threshold = JctZone.THRESHOLD_DP * service.resources.displayMetrics.density * sensitivityPercent(service) / 100f
                    val swiped = JctZone.fires(zone, dx, dy, threshold)
                    val tapped = style(service, zone) != STYLE_HIDDEN && e.eventTime - downAt < 450 &&
                        kotlin.math.hypot(dx, dy) < threshold / 3f
                    if (swiped || tapped) runCatching { JctLaunch.runKey(service, JctStore.ZONE_ACTION + zone.name) }
                    v.feedback(-1f, e.x, e.y)
                }
                MotionEvent.ACTION_CANCEL -> v.feedback(-1f, e.x, e.y)
                MotionEvent.ACTION_OUTSIDE -> Unit
            }
            true
        }
        try { wm.addView(v, p); views[zone] = v } catch (e: Exception) { Log.w("GestureZone", "add ${zone.name} ${e.message}") }
    }

    fun remove() {
        views.values.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }

    /** The handle follows the finger and expands as the inward swipe progresses. */
    private class ZoneView(context: Context, val zone: JctZone, private val style: Int, private val restThick: Int) : View(context) {
        var onReleased: (() -> Unit)? = null
        private var level = -1f
        private var touchX = -1f
        private var touchY = -1f
        private var release: ValueAnimator? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D4A84B") }
        init { setBackgroundColor(0) }
        fun feedback(l: Float, x: Float, y: Float) {
            release?.cancel()
            touchX = x; touchY = y
            if (l < 0f && level >= 0f) {
                release = ValueAnimator.ofFloat(level, 0f).apply {
                    duration = 180
                    addUpdateListener { level = it.animatedValue as Float; invalidate() }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { level = -1f; invalidate(); onReleased?.invoke() }
                    })
                    start()
                }
            } else { level = l; invalidate() }
        }
        override fun onDraw(c: Canvas) {
            if (style == STYLE_HIDDEN && level < 0f) return
            val vertical = zone.name.startsWith("LEFT") || zone.name.startsWith("RIGHT")
            val progress = level.coerceAtLeast(0f)
            val full = if (vertical) width.toFloat() else height.toFloat()
            // Rest: handle sits in the thin strip. Dragging: it slides out with the finger.
            val drawn = (restThick + (full - restThick) * progress).coerceIn(1f, full)
            val center = if (vertical) (if (level < 0f) height / 2f else touchY.coerceIn(18f, height - 18f))
                else (if (level < 0f) width / 2f else touchX.coerceIn(18f, width - 18f))
            val length = (28f + 30f * progress) * resources.displayMetrics.density
            paint.alpha = if (level < 0f) 165 else (170 + 75 * progress).toInt().coerceIn(0, 255)
            val bounds = when {
                zone.name.startsWith("LEFT") -> RectF(2f + drawn - restThick, center - length / 2, drawn - 2f, center + length / 2)
                vertical -> RectF(width - drawn + 2f, center - length / 2, width - drawn + restThick - 2f, center + length / 2)
                else -> RectF(center - length / 2, height - drawn + 2f, center + length / 2, height - drawn + restThick - 2f)
            }
            when (style) {
                STYLE_CIRCLE -> c.drawOval(bounds, paint)
                STYLE_ARROW -> {
                    val p = Path()
                    if (vertical) {
                        val direction = if (zone.name.startsWith("LEFT")) 1f else -1f
                        val x = bounds.centerX()
                        p.moveTo(x - direction * 5f, center - 10f); p.lineTo(x + direction * 6f, center)
                        p.lineTo(x - direction * 5f, center + 10f)
                    } else {
                        val y = bounds.centerY()
                        p.moveTo(center - 10f, y + 5f); p.lineTo(center, y - 6f)
                        p.lineTo(center + 10f, y + 5f)
                    }
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * resources.displayMetrics.density
                    c.drawPath(p, paint); paint.style = Paint.Style.FILL
                }
                STYLE_SLANT -> {
                    val p = Path().apply {
                        moveTo(bounds.left + 8f, bounds.top); lineTo(bounds.right, bounds.top)
                        lineTo(bounds.right - 8f, bounds.bottom); lineTo(bounds.left, bounds.bottom); close()
                    }
                    c.drawPath(p, paint)
                }
                else -> c.drawRoundRect(bounds, 10f, 10f, paint)
            }
        }
    }

    companion object {
        const val FILE = "gesture_zones"
        private const val WIDTH = "strip_width_percent"
        private const val SENSITIVITY = "swipe_distance_percent"
        private const val PULL_DP = 96
        const val STYLE_SLANT = 0
        const val STYLE_CIRCLE = 1
        const val STYLE_HIDDEN = 2
        const val STYLE_ARROW = 3
        const val STYLE_TAB = 4
        val styleLabels = listOf("مقبض مائل", "دائري", "مخفي", "سهم", "لسان")
        fun style(c: Context, z: JctZone) = prefs(c).getInt("style_" + z.name, STYLE_HIDDEN).coerceIn(0, 4)
        fun setStyle(c: Context, z: JctZone, value: Int) {
            prefs(c).edit().putInt("style_" + z.name, value.coerceIn(0, 4)).apply()
            T1TouchService.instance?.zones?.refresh()
        }
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
