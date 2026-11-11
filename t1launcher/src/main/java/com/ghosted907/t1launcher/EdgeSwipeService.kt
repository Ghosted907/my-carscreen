package com.ghosted907.t1launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
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

/**
 * Display 0 only. A narrow swipe strip, plus a small slanted tab on the right
 * edge. Both are hidden while T1 itself is in front. The tab window is only
 * as large as the button, so the rest of the screen still receives touches.
 */
class EdgeSwipeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowContext: Context? = null
    private var windowManager: WindowManager? = null
    private var strip: EdgeStrip? = null
    private var tab: EdgeTabView? = null
    private var youtubeTab: YouTubeEdgeView? = null
    private var touchGeneration = 0
    private val configured = mutableMapOf<String, Pair<ButtonConfig, EdgePullView>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        startInForeground()
        val prefs = T1Prefs(this)
        if (!Settings.canDrawOverlays(this)) {
            removeStrip()
            removeTab()
            removeYouTubeTab()
            removeConfigured()
            Log.i(TAG, "edgeSwipe overlay denied")
            running = false
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        render(prefs)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        removeStrip()
        removeTab()
        removeYouTubeTab()
        removeConfigured()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "سحب الحافة", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_t1)
            .setContentTitle("T1")
            .setContentText("سحب الحافة أو الزر الجانبي يفتح T1")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun render(prefs: T1Prefs) {
        // Legacy views are replaced by migrated default entries, not duplicated.
        removeTab(); removeYouTubeTab()
        if (!watch || launcherVisible) removeStrip()
        else if (prefs.edgeEnabled) attachStrip() else removeStrip()
        val wanted = prefs.buttons().filter { it.enabled &&
            (it.target != ButtonConfig.LAUNCHER || (watch && !launcherVisible && prefs.edgeTab)) }
        configured.keys.toList().filter { id -> wanted.none { it.id == id && it == configured[id]?.first } }.forEach { id ->
            configured.remove(id)?.second?.let { runCatching { windowManager?.removeView(it) } }
        }
        val display = mainDisplay() ?: return
        val wm = window() ?: return
        val size = android.graphics.Point(); display.getRealSize(size)
        wanted.filterNot { configured.containsKey(it.id) }.forEach { config ->
            try {
                val params = tabParams(size.y).apply {
                    gravity = Gravity.TOP or if (config.fromRight) Gravity.RIGHT else Gravity.LEFT
                    x = ((if (config.fromRight) 1f - config.x else config.x) * (size.x - EdgePullGesture.TIP_PX)).toInt()
                    y = (size.y * config.y - EdgeTab.HEIGHT_PX / 2f).toInt()
                        .coerceIn(0, (size.y - EdgeTab.HEIGHT_PX).coerceAtLeast(0))
                    setTitle("T1Button_" + config.id)
                }
                val view = EdgePullView(windowContext!!, config.fromRight).apply {
                    contentDescription = config.label
                    onWindowWidth = { resizePullWindow(this, it) }
                    onOpen = { openConfigured(config) }
                }
                wm.addView(view, params)
                configured[config.id] = config to view
            } catch (e: Exception) { Log.i(TAG, "button attach failed ${config.id}: ${e.message}") }
        }
    }

    private fun removeConfigured() {
        configured.values.forEach { (_, view) -> runCatching { windowManager?.removeView(view) } }
        configured.clear()
    }

    private fun openConfigured(config: ButtonConfig) {
        if (config.target == ButtonConfig.LAUNCHER) { openLauncher(); return }
        try {
            val component = android.content.ComponentName.unflattenFromString(config.target)
            val intent = if (component != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                else packageManager.getLaunchIntentForPackage(config.target)
            check(intent != null) { "التطبيق غير مثبت" }
            LauncherDisplay.start(this, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "تعذر فتح ${config.label}؛ اختر تطبيقاً مثبتاً من الإعدادات", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun mainDisplay(): Display? {
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null || display.displayId != Display.DEFAULT_DISPLAY) {
            Log.i(TAG, "edge overlay no display 0")
            return null
        }
        return display
    }

    private fun window(): WindowManager? {
        val display = mainDisplay() ?: return null
        if (windowContext == null) {
            windowContext = createDisplayContext(display).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null
            )
            windowManager = windowContext!!.getSystemService(WindowManager::class.java)
        }
        return windowManager
    }

    private fun attachStrip() {
        try {
            val display = mainDisplay() ?: return
            val wm = window() ?: run {
                Log.i(TAG, "edgeSwipe no window manager")
                return
            }
            val right = T1Prefs(this).edgeRight
            val params = stripParams(display, right)
            val existing = strip
            if (existing == null) {
                val created = EdgeStrip(windowContext!!).apply {
                    fromRight = right
                    onOpen = { openLauncher() }
                    onRelease = { x, y, tap -> passthrough(x, y, tap) }
                }
                wm.addView(created, params)
                strip = created
                Log.i(TAG, "edgeSwipe attach ${if (right) "right" else "left"} display=0")
            } else {
                existing.fromRight = right
                wm.updateViewLayout(existing, params)
            }
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun attachTab() {
        try {
            val display = mainDisplay() ?: return
            val wm = window() ?: return
            val size = android.graphics.Point()
            display.getRealSize(size)
            val params = tabParams(size.y)
            val existing = tab
            if (existing == null) {
                val created = EdgeTabView(windowContext!!).apply {
                    onOpen = { openLauncher() }
                    onWindowWidth = { resizePullWindow(this, it) }
                }
                wm.addView(created, params)
                tab = created
                Log.i(TAG, "edgeTab attach display=0 top=${params.y} ${params.width}x${params.height}")
            } else {
                params.width = existing.windowWidth
                wm.updateViewLayout(existing, params)
            }
        } catch (e: Exception) {
            Log.i(TAG, "edgeTab failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    /** Independent of the right launcher tab: available over every app on display 0. */
    private fun attachYouTubeTab() {
        try {
            val display = mainDisplay() ?: return
            val wm = window() ?: return
            val size = android.graphics.Point()
            display.getRealSize(size)
            val params = tabParams(size.y).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                y = (size.y * 0.64f - EdgeTab.HEIGHT_PX / 2f).toInt()
                    .coerceIn(0, (size.y - EdgeTab.HEIGHT_PX).coerceAtLeast(0))
                setTitle("T1YouTubeLeft")
            }
            val existing = youtubeTab
            if (existing == null) {
                val created = YouTubeEdgeView(windowContext!!).apply {
                    setOnClickListener { openYouTube() }
                    onWindowWidth = { resizePullWindow(this, it) }
                }
                wm.addView(created, params)
                youtubeTab = created
                Log.i(TAG, "youtubeTab attach left display=0 top=${params.y} ${params.width}x${params.height}")
            } else {
                params.width = existing.windowWidth
                wm.updateViewLayout(existing, params)
            }
        } catch (e: Exception) {
            Log.i(TAG, "youtubeTab failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun openYouTube() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("app.revanced.android.youtube")
            if (intent == null) {
                android.widget.Toast.makeText(this, "يوتيوب غير مثبت", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            LauncherDisplay.start(this, intent)
            Log.i(TAG, "youtubeTab open display=0")
        } catch (e: Exception) {
            Log.i(TAG, "youtubeTab open failed ${e.javaClass.simpleName} ${e.message}")
            android.widget.Toast.makeText(this, "تعذر فتح يوتيوب", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeYouTubeTab() {
        val view = youtubeTab ?: return
        youtubeTab = null
        try {
            windowManager?.removeView(view)
        } catch (e: Exception) {
            Log.i(TAG, "youtubeTab detach failed ${e.javaClass.simpleName} ${e.message}")
        }
    }
    private fun stripParams(display: Display, right: Boolean): WindowManager.LayoutParams {
        val size = android.graphics.Point()
        display.getRealSize(size)
        val top = statusBarPx(windowContext ?: this)
        val dock = (100f * size.y / 1080f).toInt().coerceAtLeast(1)
        val height = (size.y - top - dock).coerceAtLeast(1)
        val params = WindowManager.LayoutParams(
            STRIP_PX,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or if (right) Gravity.RIGHT else Gravity.LEFT
        params.x = 0
        params.y = top
        return params
    }

    private fun resizePullWindow(view: EdgePullView, width: Int) {
        if (!view.isAttachedToWindow) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = width.coerceIn(EdgePullGesture.TIP_PX, EdgeTab.WIDTH_PX)
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.i(TAG, "edgePull resize failed ${e.javaClass.simpleName} ${e.message}")
        }
    }
    private fun tabParams(screenHeight: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            EdgePullGesture.TIP_PX,
            EdgeTab.HEIGHT_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.RIGHT
        params.x = 0
        params.y = (screenHeight * 0.64f - EdgeTab.HEIGHT_PX / 2f).toInt()
            .coerceIn(0, (screenHeight - EdgeTab.HEIGHT_PX).coerceAtLeast(0))
        params.setTitle("T1LauncherRight")
        // Disable automatic WM move animations on these app windows only.
        // Otherwise expanding a right-gravity window trails the finger by 400ms.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            params.setCanPlayMoveAnimation(false)
        } else {
            try {
                val field = WindowManager.LayoutParams::class.java.getField("privateFlags")
                field.setInt(params, field.getInt(params) or 0x00000040)
            } catch (e: Exception) {
                Log.i(TAG, "edgePull noMoveAnimation unavailable ${e.javaClass.simpleName}")
            }
        }
        return params
    }

    private fun openLauncher() {
        Log.i(TAG, "edgeSwipe open")
        watch = false
        removeStrip()
        removeTab()
        configured.filterValues { it.first.target == ButtonConfig.LAUNCHER }.keys.toList().forEach { id ->
            configured.remove(id)?.second?.let { runCatching { windowManager?.removeView(it) } }
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        try {
            LauncherDisplay.start(this, intent)
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe open failed ${e.javaClass.simpleName} ${e.message}")
            watch = true
            render(T1Prefs(this))
        }
    }

    private fun passthrough(x: Float, y: Float, tap: Boolean) {
        Log.i(TAG, "edgeSwipe passthrough")
        val generation = ++touchGeneration
        setTouchable(false)
        if (tap) {
            handler.postDelayed({
                if (generation == touchGeneration) T1TouchService.tap(x, y)
            }, 40)
        }
        handler.postDelayed({
            if (generation == touchGeneration && watch) setTouchable(true)
        }, 320)
    }

    private fun setTouchable(touchable: Boolean) {
        val view = strip ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val block = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (touchable) params.flags and block.inv() else params.flags or block
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe passthrough failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun removeStrip() {
        val view = strip ?: return
        strip = null
        try {
            windowManager?.removeView(view)
            Log.i(TAG, "edgeSwipe detach")
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe detach failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun removeTab() {
        val view = tab ?: return
        tab = null
        try {
            windowManager?.removeView(view)
            Log.i(TAG, "edgeTab detach")
        } catch (e: Exception) {
            Log.i(TAG, "edgeTab detach failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    companion object {
        private const val TAG = "T1Launcher"
        private const val CHANNEL = "t1.edge"
        private const val NOTIFICATION_ID = 41
        private const val STRIP_PX = 20

        @Volatile
        var watch: Boolean = true

        /** True while MainActivity is in front. The overlays stay off in that case. */
        @Volatile
        var launcherVisible: Boolean = false

        /** True while this service is started and allowed to draw. */
        @Volatile
        var running: Boolean = false

        /**
         * Starts the overlay when T1 is not the foreground activity.
         * Used from application startup and from the touch service, so a
         * permission grant in the background still brings the button up.
         */
        fun arm(context: Context) {
            if (!launcherVisible) watch = true
            sync(context)
        }

        fun sync(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, EdgeSwipeService::class.java)
            if (!Settings.canDrawOverlays(app)) {
                app.stopService(intent)
                Log.i(TAG, "edgeSwipe overlay denied")
                return
            }
            try {
                app.startForegroundService(intent)
            } catch (e: Exception) {
                Log.i(TAG, "edgeSwipe start failed ${e.javaClass.simpleName} ${e.message}")
            }
        }
    }
}

private fun statusBarPx(context: Context): Int {
    val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}

private class EdgeStrip(context: Context) : View(context) {
    var fromRight: Boolean = true
    var onOpen: (() -> Unit)? = null
    var onRelease: ((x: Float, y: Float, tap: Boolean) -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var fired = false

    init {
        setBackgroundColor(0x01000000)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                fired = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fired && EdgeGesture.opens(fromRight, event.rawX - downX, event.rawY - downY)) {
                    fired = true
                    onOpen?.invoke()
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!fired && EdgeGesture.opens(fromRight, dx, dy)) {
                    fired = true
                    onOpen?.invoke()
                } else if (!fired) {
                    onRelease?.invoke(downX, downY, EdgeGesture.isTap(dx, dy, 24f))
                }
            }
        }
        return true
    }
}

private class EdgeTabView(context: Context) : EdgePullView(context, fromRight = true)
