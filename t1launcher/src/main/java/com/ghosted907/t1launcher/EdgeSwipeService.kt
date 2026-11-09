package com.ghosted907.t1launcher

import android.app.ActivityOptions
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
 * A narrow transparent strip on display 0. A sideways drag toward the center
 * brings T1 forward. The strip is hidden while T1 itself is in front.
 */
class EdgeSwipeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowContext: Context? = null
    private var windowManager: WindowManager? = null
    private var strip: EdgeStrip? = null
    private var touchGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val prefs = T1Prefs(this)
        if (!prefs.edgeEnabled || !Settings.canDrawOverlays(this)) {
            removeStrip()
            if (!prefs.edgeEnabled) Log.i(TAG, "edgeSwipe disabled")
            else Log.i(TAG, "edgeSwipe overlay denied")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (watch) attach() else removeStrip()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeStrip()
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
            .setContentText("سحب الحافة يفتح T1")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun attach() {
        try {
            val display = getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            if (display == null) {
                Log.i(TAG, "edgeSwipe no display 0")
                return
            }
            if (windowContext == null) {
                windowContext = createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null
                )
                windowManager = windowContext!!.getSystemService(WindowManager::class.java)
            }
            val wm = windowManager ?: run {
                Log.i(TAG, "edgeSwipe no window manager")
                return
            }
            val right = T1Prefs(this).edgeRight
            val params = layoutParams(display, right)
            val existing = strip
            if (existing == null) {
                val created = EdgeStrip(windowContext!!).apply {
                    fromRight = right
                    onOpen = { openLauncher() }
                    onRelease = { x, y, tap -> passthrough(x, y, tap) }
                }
                wm.addView(created, params)
                strip = created
                Log.i(TAG, "edgeSwipe attach ${if (right) "right" else "left"}")
            } else {
                existing.fromRight = right
                wm.updateViewLayout(existing, params)
            }
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe failed ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun layoutParams(display: Display, right: Boolean): WindowManager.LayoutParams {
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

    private fun openLauncher() {
        Log.i(TAG, "edgeSwipe open")
        watch = false
        removeStrip()
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        try {
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.i(TAG, "edgeSwipe open failed ${e.javaClass.simpleName} ${e.message}")
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

    companion object {
        private const val TAG = "T1Launcher"
        private const val CHANNEL = "t1.edge"
        private const val NOTIFICATION_ID = 41
        private const val STRIP_PX = 20

        @Volatile
        var watch: Boolean = true

        fun sync(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, EdgeSwipeService::class.java)
            val prefs = T1Prefs(app)
            if (!prefs.edgeEnabled) {
                app.stopService(intent)
                Log.i(TAG, "edgeSwipe disabled")
                return
            }
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
