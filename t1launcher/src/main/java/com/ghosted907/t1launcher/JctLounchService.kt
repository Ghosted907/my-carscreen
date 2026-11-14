package com.ghosted907.t1launcher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout

/**
 * NaviStart `y0.a.k()` overlay type choice (j2/e.k, v4/d.k): TYPE_ACCESSIBILITY_OVERLAY (2032)
 * through the accessibility service when it is connected and "widgets_over_system" is on,
 * otherwise TYPE_APPLICATION_OVERLAY (2038).
 */
object JctOverlay {
    fun useA11y(context: Context): Boolean =
        T1TouchService.instance != null && JctStore.bool(context, JctStore.OVER_SYSTEM, true)

    fun type(context: Context): Int = if (useA11y(context)) 2032 else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    /** b2.d / b2.e: window manager for the default display or for displays[index]. */
    fun windowManager(context: Context, displayIndex: Int = -1): WindowManager? {
        val displays = JctLaunch.displays(context)
        val display: Display? = displays.getOrNull(displayIndex)
        val a11y = T1TouchService.instance
        if (useA11y(context) && a11y != null && (display == null || display.displayId == Display.DEFAULT_DISPLAY))
            return a11y.getSystemService(WindowManager::class.java)
        val base = if (display != null) context.createDisplayContext(display) else context
        return base.getSystemService(WindowManager::class.java)
    }

    fun dp(context: Context, v: Int): Int = Math.round(v * context.resources.displayMetrics.density)
}

object JctFloatingStyle {
    const val KEY = "floating_style"
    val labels = listOf("أيقونة التطبيق فقط", "دائري", "مربع", "مخفي", "كبسولة")
    fun selected(context: Context) = JctStore.int(context, KEY, 1).coerceIn(0, labels.lastIndex)
}

/**
 * Port of NaviStart `e0.w0` + `e0.v0`: draggable overlay window whose position is saved in
 * "<name>X"/"<name>Y" (default screen centre). Long-press 500 ms enters move mode (blinking
 * alpha 0→1→0, 1500 ms, repeat/reverse); a tap without move mode runs [onTap].
 */
class JctDragWindow(private val context: Context, private val name: String) {
    val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        JctOverlay.type(context),
        if (JctStore.bool(context, JctStore.OVER_SYSTEM, true)) 808 else 8,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        val m = context.resources.displayMetrics
        x = JctStore.int(context, name + "X", m.widthPixels / 2)
        y = JctStore.int(context, name + "Y", m.heightPixels / 2)
        title = "JCT_$name"
    }
    private var view: View? = null
    private var wm: WindowManager? = null
    private var tapAllowed = true
    private var blink: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var startX = 0; private var startY = 0; private var rawX = 0f; private var rawY = 0f

    fun attach(v: View, manager: WindowManager?, touchTarget: View, onTap: () -> Unit): Boolean {
        view = v; wm = manager
        if (manager == null) return false
        return try {
            manager.addView(v, params)
            touchTarget.setOnTouchListener { _, e -> onTouch(e, onTap) }
            true
        } catch (e: Exception) { Log.w("JctLounchB", "addView $name ${e.message}"); false }
    }

    private val enterMove = Runnable {
        tapAllowed = false
        val v = view ?: return@Runnable
        blink = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f, 0f).apply {
            duration = 1500; repeatCount = -1; repeatMode = ObjectAnimator.REVERSE; start()
        }
    }

    private fun onTouch(e: MotionEvent, onTap: () -> Unit): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                tapAllowed = true
                startX = params.x; startY = params.y; rawX = e.rawX; rawY = e.rawY
                handler.postDelayed(enterMove, 500)
            }
            MotionEvent.ACTION_UP -> {
                if (tapAllowed) onTap()
                blink?.takeIf { it.isRunning }?.cancel()
                view?.alpha = 1f
                handler.removeCallbacks(enterMove)
            }
            MotionEvent.ACTION_MOVE -> if (!tapAllowed) {
                params.x = Math.round(e.rawX - rawX) + startX
                params.y = Math.round(e.rawY - rawY) + startY
                JctStore.putInt(context, name + "X", params.x)
                JctStore.putInt(context, name + "Y", params.y)
                handler.post { runCatching { wm?.updateViewLayout(view, params) } }
            }
            else -> return false
        }
        return true
    }

    fun detach() {
        handler.removeCallbacksAndMessages(null)
        runCatching { wm?.removeView(view) }
        view = null
    }
}

/**
 * Port of NaviStart `n1.i` multibutton: centre 100 dp, satellites 90 dp with 50 dp badges.
 * Cardinal satellites sit 110 dp from centre, diagonals at ±85 dp translation (multibutton.xml).
 * Slot order: 0 top, 1 bottom, 2 left, 3 right, 4 top-left, 5 top-right, 6 bottom-left, 7 bottom-right.
 * 8 = all eight, 6 = the four cardinal ones (NaviStart's stored value). Auto-collapse after 10 s;
 * the centre fades to 0.3 after 10 s idle.
 */
class JctMultiButton(context: Context) : FrameLayout(context) {
    private val dp = { v: Int -> JctOverlay.dp(context, v) }
    val center = ImageView(context)
    private val slots = Array(8) { FrameLayout(context) }
    private val badges = Array(8) { ImageView(context) }
    private var open = false
    private val h = Handler(Looper.getMainLooper())
    private val collapse = Runnable { if (open) toggle() }
    private val dim = Runnable { if (!open) center.animate().alpha(0.3f).setDuration(300).start() }

    init {
        val size = dp(310)
        layoutParams = LayoutParams(size, size)
        minimumWidth = size; minimumHeight = size
        val offsets = listOf(0 to -110, 0 to 110, -110 to 0, 110 to 0, -85 to -85, 85 to -85, -85 to 85, 85 to 85)
        slots.forEachIndexed { i, f ->
            f.background = circle(Color.parseColor("#E6123827"), Color.parseColor("#D4A84B"))
            f.addView(badges[i], LayoutParams(dp(50), dp(50), Gravity.CENTER))
            f.translationX = dp(offsets[i].first).toFloat(); f.translationY = dp(offsets[i].second).toFloat()
            f.visibility = View.GONE
            addView(f, LayoutParams(dp(90), dp(90), Gravity.CENTER))
        }
        center.setImageResource(R.drawable.ic_t1)
        center.background = circle(Color.parseColor("#F0071B14"), Color.parseColor("#D4A84B"))
        val pad = dp(18); center.setPadding(pad, pad, pad, pad)
        addView(center, LayoutParams(dp(100), dp(100), Gravity.CENTER))
        h.postDelayed(dim, 10_000)
    }

    private fun circle(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(fill); setStroke(JctOverlay.dp(context, 3), stroke)
    }

    fun setActions(runs: List<() -> Unit>) = slots.forEachIndexed { i, f -> f.setOnClickListener { runs[i]() } }
    fun setImageRun(d: Array<Drawable?>) = badges.forEachIndexed { i, b -> b.setImageDrawable(d[i]) }

    /** n1.i.d / b: toggle open state. */
    fun toggle() {
        val count = JctStore.int(context, JctStore.MULTI_COUNT, 8)
        center.animate().alpha(1f).setDuration(300).start()
        if (open) {
            slots.forEach { show(it, false) }
            open = false
            h.removeCallbacks(dim); h.postDelayed(dim, 10_000)
            return
        }
        slots.forEachIndexed { i, f -> show(f, if (count == 8) true else if (count == 6) i < 4 else false) }
        open = true
        h.removeCallbacks(collapse); h.postDelayed(collapse, 10_000)
    }

    private fun pulse(a: Float) {
        AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(center, "scaleX", 1f, a, 1f), ObjectAnimator.ofFloat(center, "scaleY", 1f, a, 1f))
            duration = if (a > 1f) 300 else 250; interpolator = AccelerateDecelerateInterpolator(); start()
        }
    }

    private fun show(v: View, on: Boolean) {
        if (on && v.visibility != View.VISIBLE) {
            v.scaleX = 0.5f; v.scaleY = 0.5f; v.alpha = 0f; v.visibility = View.VISIBLE
            v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(800).setInterpolator(OvershootInterpolator()).start()
            pulse(1.1f)
        } else if (!on && v.visibility == View.VISIBLE) {
            v.animate().scaleX(0.5f).scaleY(0.5f).alpha(0f).setDuration(600).setInterpolator(AnticipateInterpolator())
                .withEndAction { v.visibility = View.GONE }.start()
            pulse(0.95f)
        }
    }
}

/** Port of NaviStart `LounchB` service: single floating button + multi button on the head unit display. */
class JctLounchService : Service() {
    private var single: JctDragWindow? = null
    private var multi: JctDragWindow? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        removeAll()
        if (!Settings.canDrawOverlays(this) && !JctOverlay.useA11y(this)) { stopSelf(); return START_NOT_STICKY }
        val on = JctStore.bool(this, JctStore.LOUNCHB_TRUE, true)
        val multiOn = JctStore.bool(this, JctStore.MULTI_ON, false)
        if (!on && !multiOn) { stopSelf(); return START_NOT_STICKY }
        if (on) {
            val root = FrameLayout(this)
            val style = JctFloatingStyle.selected(this)
            // Show the icon of the app the button opens (fallback: T1 logo).
            val action = JctActions.parse(JctStore.str(this, JctStore.LOUNCHB_ACTION, JctActions.NOT_VALUE))
            val appIcon = if (action.type == JctType.APP && action.pkg != null)
                runCatching { packageManager.getApplicationIcon(action.pkg) }.getOrNull() else null
            val img = ImageView(this).apply {
                if (appIcon != null) setImageDrawable(appIcon) else setImageResource(R.drawable.ic_t1)
                background = if (style == 0 || style == 3) null else GradientDrawable().apply {
                    shape = if (style == 1) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                    cornerRadius = JctOverlay.dp(context, if (style == 4) 50 else 16).toFloat()
                    setColor(Color.parseColor("#F0123827"))
                    setStroke(JctOverlay.dp(context, 3), Color.parseColor("#D4A84B"))
                }
                val p = JctOverlay.dp(context, 20); setPadding(p, p, p, p)
                if (style == 3) alpha = 0f
            }
            val w = JctOverlay.dp(this, if (style == 4) 140 else 100)
            root.addView(img, FrameLayout.LayoutParams(w, JctOverlay.dp(this, 100)))
            if (style != 3) {
                // Smooth appearance instead of popping in.
                img.alpha = 0f; img.scaleX = .6f; img.scaleY = .6f
                img.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
            }
            single = JctDragWindow(this, "LounchB").also {
                val bar = LinearLayout(this).apply {
                    addView(root)
                    addView(TextView(this@JctLounchService).apply {
                        text = "↶"
                        contentDescription = "رجوع في اللانشر"
                        gravity = Gravity.CENTER
                        textSize = 30f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(0x55222222)
                        setOnClickListener { openLauncher("back") }
                    }, LinearLayout.LayoutParams(JctOverlay.dp(this@JctLounchService, 48), JctOverlay.dp(this@JctLounchService, 48)))
                }
                it.attach(bar, JctOverlay.windowManager(this), root) {
                    if (action.enumValue in setOf(JctEnum.Not_value, JctEnum.GLOBAL_HOME, JctEnum.GLOBAL_BACK)) openLauncher("home")
                    else JctLaunch.runKey(this, JctStore.LOUNCHB_ACTION)
                }
            }
        }
        if (multiOn) {
            val mb = JctMultiButton(this)
            mb.setActions((0 until 8).map { i -> { JctLaunch.runKey(this, JctStore.MULTI_ACTION + i); mb.toggle() } })
            mb.setImageRun(icons(this))
            multi = JctDragWindow(this, "multi_Button_view").also {
                it.attach(mb, JctOverlay.windowManager(this, JctLaunch.guIndex(this)), mb) { mb.toggle() }
            }
        }
        return START_STICKY
    }

    private fun openLauncher(page: String) {
        LauncherDisplay.start(this, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page))
    }

    private fun removeAll() { single?.detach(); multi?.detach(); single = null; multi = null }

    override fun onDestroy() { removeAll(); super.onDestroy() }

    companion object {
        /** r0.b.a: badge = app icon of each assigned slot. */
        fun icons(context: Context): Array<Drawable?> = Array(8) { i ->
            val a = JctActions.parse(JctStore.str(context, JctStore.MULTI_ACTION + i, JctActions.NOT_VALUE))
            if (a.type == JctType.APP && a.pkg != null) runCatching { context.packageManager.getApplicationIcon(a.pkg) }.getOrNull() else null
        }

        /** LounchB.c */
        fun sync(context: Context) {
            val i = Intent(context.applicationContext, JctLounchService::class.java)
            if (JctStore.bool(context, JctStore.LOUNCHB_TRUE, true) || JctStore.bool(context, JctStore.MULTI_ON, false))
                runCatching { context.startService(i) } else context.stopService(i)
        }
    }
}
