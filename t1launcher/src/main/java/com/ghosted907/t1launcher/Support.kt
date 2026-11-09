package com.ghosted907.t1launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.ref.WeakReference

class ClusterAddonActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = T1Prefs(this)
        val enabled = prefs.enabledSlots()
        if (enabled.isEmpty()) {
            finish()
            return
        }
        running = WeakReference(this)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val root = FrameLayout(this)
        val label = TextView(this)
        label.text = enabled.joinToString("  ")
        label.setTextColor(Color.parseColor("#D4A84B"))
        label.textSize = 18f
        label.gravity = Gravity.CENTER
        root.addView(label, FrameLayout.LayoutParams(420, 80, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = 24
        })
        setContentView(root)
        window.decorView.post { ClusterLaunch.hideOwnCaption(window) }
        Log.i("T1Launcher", "addon slots $enabled")
    }

    override fun onDestroy() {
        if (running.get() == this) running = WeakReference(null)
        super.onDestroy()
    }

    companion object {
        private var running = WeakReference<ClusterAddonActivity>(null)
        fun finishIfRunning() {
            running.get()?.finish()
        }
    }
}

class T1NotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        Log.i("T1Launcher", "notification listener connected")
    }

    override fun onListenerDisconnected() {
        if (instance == this) instance = null
    }

    fun controller(): MediaController? {
        val token = activeNotifications?.firstNotNullOfOrNull { notification ->
            notification.notification.extras.getParcelable<android.media.session.MediaSession.Token>(
                android.app.Notification.EXTRA_MEDIA_SESSION
            )
        } ?: return null
        return MediaController(this, token)
    }

    companion object {
        var instance: T1NotificationListener? = null
    }
}

class MediaHub(private val context: android.content.Context) {
    data class State(val title: String?, val playing: Boolean)

    fun allowed(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    fun bind(onState: (State) -> Unit) {
        val controller = T1NotificationListener.instance?.controller()
        if (controller == null) {
            onState(State(if (allowed()) "لا يوجد مقطع نشط" else "اسمح بالإشعارات للتحكم", false))
            return
        }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        onState(State(controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE), playing))
    }

    fun toggle() {
        val controls = T1NotificationListener.instance?.controller()?.transportControls ?: return
        val playing = T1NotificationListener.instance?.controller()?.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controls.pause() else controls.play()
    }

    fun play() {
        T1NotificationListener.instance?.controller()?.transportControls?.play()
    }
}

class T1TouchService : android.accessibilityservice.AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        // GestureDescription.Builder.setDisplayId exists only from API 33.
        // On Android 11 a gesture cannot be aimed at display 1.
        Log.i("T1Launcher", "touch service connected; display targeting requires API 33+")
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: T1TouchService? = null

        /** Replays a tap on display 0 when this service is already enabled. */
        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = android.graphics.Path().apply {
                moveTo(x, y)
                lineTo(x, y + 1f)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            return try {
                service.dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.i("T1Launcher", "edgeSwipe passthrough failed ${e.javaClass.simpleName} ${e.message}")
                false
            }
        }
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("T1Launcher", "boot received")
        EdgeSwipeService.watch = true
        EdgeSwipeService.sync(context)
        ClusterOverlayService.sync(context)
    }
}

fun homeEnabled(context: android.content.Context): Boolean {
    val state = context.packageManager.getComponentEnabledSetting(
        ComponentName(context, "com.ghosted907.t1launcher.HomeAlias")
    )
    return state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}
