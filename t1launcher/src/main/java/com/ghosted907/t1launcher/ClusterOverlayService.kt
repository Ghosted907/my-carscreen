package com.ghosted907.t1launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * A transparent, non-touch circle on the cluster compass. The rest of display 1 stays clear.
 */
class ClusterOverlayService : Service() {
    private var windowContext: Context? = null
    private var windowManager: WindowManager? = null
    private var disc: CompassDisc? = null
    private var bitmap: Bitmap? = null
    private var bitmapStamp = 0L
    private val prefs by lazy { T1Prefs(this) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("compass")) render()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs.listen(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        CompassImage.importDownload(this)
        if (!render()) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.unlisten(listener)
        remove()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "صورة البوصلة", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_t1)
            .setContentTitle("T1")
            .setContentText("صورة بدل البوصلة")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun render(): Boolean {
        if (!prefs.compassEnabled) {
            remove()
            Log.i(TAG, "compassOverlay disabled")
            return false
        }
        if (!Settings.canDrawOverlays(this)) {
            remove()
            Log.i(TAG, "compassOverlay overlay denied")
            return false
        }
        val file = CompassImage.file(this)
        if (!file.isFile) {
            remove()
            Log.i(TAG, "compassOverlay no image")
            return false
        }
        val bmp = currentBitmap(file) ?: run {
            remove()
            Log.i(TAG, "compassOverlay no image")
            return false
        }
        val displayId = Displays.clusterId(this) ?: 1
        val display = getSystemService(DisplayManager::class.java).getDisplay(displayId)
        if (display == null) {
            Log.i(TAG, "compassOverlay no display $displayId")
            return false
        }
        val diameter = prefs.compassD.coerceIn(40, 480)
        val (x, y) = CompassGeometry.origin(prefs.compassCx, prefs.compassCy, diameter)
        try {
            if (windowContext == null || boundDisplay != displayId) {
                remove()
                windowContext = createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null
                )
                windowManager = windowContext!!.getSystemService(WindowManager::class.java)
                boundDisplay = displayId
            }
            val params = layout(diameter, x, y)
            val view = disc ?: CompassDisc(windowContext!!).also { disc = it }
            view.photo = bmp
            view.ringColor = prefs.compassRingColor
            view.ringPx = prefs.compassRingPx.toFloat()
            view.invalidate()
            if (view.parent == null) windowManager?.addView(view, params)
            else windowManager?.updateViewLayout(view, params)
        } catch (t: Throwable) {
            Log.i(TAG, "compassOverlay failed ${t.javaClass.simpleName} ${t.message}")
            return false
        }
        Log.i(TAG, "compassOverlay shown cx=${prefs.compassCx} cy=${prefs.compassCy} d=$diameter")
        return true
    }

    private fun layout(diameter: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            diameter,
            diameter,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = x
        params.y = y
        return params
    }

    private fun currentBitmap(file: java.io.File): Bitmap? {
        val stamp = file.lastModified()
        if (bitmap != null && stamp == bitmapStamp) return bitmap
        val decoded = CompassImage.decode(file) ?: return null
        bitmap = decoded
        bitmapStamp = stamp
        return decoded
    }

    private fun remove() {
        val view = disc ?: return
        disc = null
        try {
            windowManager?.removeView(view)
        } catch (_: Throwable) {
        }
        Log.i(TAG, "compassOverlay hidden")
    }

    companion object {
        private const val TAG = "T1Launcher"
        private const val CHANNEL = "t1.compass"
        private const val NOTIFICATION_ID = 42
        private var boundDisplay = -1

        fun sync(context: Context) {
            val app = context.applicationContext
            CompassImage.importDownload(app)
            val prefs = T1Prefs(app)
            val intent = Intent(app, ClusterOverlayService::class.java)
            if (!prefs.compassEnabled || !Settings.canDrawOverlays(app) || !CompassImage.file(app).isFile) {
                app.stopService(intent)
                if (!prefs.compassEnabled) Log.i(TAG, "compassOverlay disabled")
                else if (!Settings.canDrawOverlays(app)) Log.i(TAG, "compassOverlay overlay denied")
                else Log.i(TAG, "compassOverlay no image")
                return
            }
            try {
                app.startForegroundService(intent)
            } catch (t: Throwable) {
                Log.i(TAG, "compassOverlay start failed ${t.javaClass.simpleName} ${t.message}")
            }
        }
    }
}

private class CompassDisc(context: Context) : View(context) {
    var photo: Bitmap? = null
    var ringColor: Int = Color.WHITE
    var ringPx: Float = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = photo ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val inset = ringPx / 2f
        val radius = min(w, h) / 2f - inset
        if (radius <= 0f) return
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(w / bmp.width, h / bmp.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((w - bmp.width * scale) / 2f, (h - bmp.height * scale) / 2f)
        shader.setLocalMatrix(matrix)
        fill.shader = shader
        canvas.drawCircle(cx, cy, radius, fill)
        if (ringPx > 0f) {
            stroke.shader = null
            stroke.color = ringColor
            stroke.strokeWidth = ringPx
            canvas.drawCircle(cx, cy, radius, stroke)
        }
    }
}
