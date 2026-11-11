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
        if (refuseOffMain()) return
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
        Log.i("T1Launcher", "addon slots $enabled")
    }

    override fun onResume() {
        super.onResume()
        refuseOffMain()
    }

    private fun refuseOffMain(): Boolean {
        if (!LauncherDisplay.isOffMain(this)) return false
        Log.i("T1Launcher", "addon refused display ${display?.displayId}")
        finish()
        return true
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
        val notifications = runCatching { activeNotifications }.getOrNull() ?: return null
        val token = notifications.firstNotNullOfOrNull { notification ->
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
        val controller = runCatching { T1NotificationListener.instance?.controller() }.getOrNull()
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
    /** Hosts NaviStart-style edge zones (MyA11yService + GestureZoneManager). */
    var zones: JctGestureZones? = null
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        // GestureDescription.Builder.setDisplayId exists only from API 33.
        // On Android 11 a gesture cannot be aimed at display 1.
        Log.i("T1Launcher", "touch service connected; display targeting requires API 33+")
        zones = JctGestureZones(this).also { it.refresh() }
        // Overlays switch to TYPE_ACCESSIBILITY_OVERLAY now that the service is up (y0.a.k()).
        JctLounchService.sync(this)
        if (JctUserAppsHide.enabled(this)) { JctHideZone.dismiss(); JctHideZone.show(this) }
    }

    override fun onDestroy() {
        zones?.remove(); zones = null
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
        ClusterForceClose.recover(context)
        Log.i("T1Launcher", "boot received")
        JctStartup.onBoot(context)
    }
}

fun homeEnabled(context: android.content.Context): Boolean {
    val state = context.packageManager.getComponentEnabledSetting(
        ComponentName(context, "com.ghosted907.t1launcher.HomeAlias")
    )
    return state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}


/** Startup hooks ported from NaviStart (LounchB.c, boot hide-zone, MyCarReceiver auto-start). */
object JctStartup {
    fun onProcess(context: android.content.Context) {
        JctMigration.run(context)
        JctLounchService.sync(context)
        if (JctUserAppsHide.enabled(context)) runCatching { JctHideZone.show(context) }
    }
    fun onBoot(context: android.content.Context) {
        onProcess(context)
        JctBootService.onCarStart(context)
    }
}

/**
 * One-time carry-over of the old launcher settings into NaviStart keys (launcher-specific, not a
 * JCarTools behaviour): old edge buttons -> multi-button slots, old edge strip -> two edge zones.
 */
object JctMigration {
    fun run(context: android.content.Context) {
        val jct = JctStore.p(context)
        if (jct.getBoolean("migrated_v1", false)) return
        val old = context.getSharedPreferences("t1", android.content.Context.MODE_PRIVATE)
        val targets = runCatching {
            val text = old.getString("edgeButtonsV1", null)
            if (text == null) listOf("__launcher__", "app.revanced.android.youtube") else {
                val arr = org.json.JSONObject(text).getJSONArray("buttons")
                (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optBoolean("enabled", true) }.map { it.getString("target") }
            }
        }.getOrDefault(emptyList())
        val e = jct.edit()
        targets.take(8).forEachIndexed { i, t ->
            val action = if (t == "__launcher__") JctActions.ENUM + JctEnum.RUN_START_APP_MENU.name
                else JctActions.APP + (android.content.ComponentName.unflattenFromString(t)?.packageName ?: t)
            e.putString(JctStore.MULTI_ACTION + i, action)
        }
        if (targets.isNotEmpty()) e.putBoolean(JctStore.MULTI_ON, true).putInt(JctStore.MULTI_COUNT, if (targets.size > 4) 8 else 6)
        if (old.getBoolean("edge", true)) {
            val right = old.getBoolean("edgeRight", true)
            val zones = if (right) listOf(JctZone.RIGHT_TOP, JctZone.RIGHT_BOTTOM) else listOf(JctZone.LEFT_TOP, JctZone.LEFT_BOTTOM)
            val zp = JctGestureZones.prefs(context).edit()
            zones.forEach { z -> zp.putBoolean("enabled_" + z.name, true)
                e.putString(JctStore.ZONE_ACTION + z.name, JctActions.ENUM + JctEnum.RUN_START_APP_MENU.name) }
            zp.apply()
        }
        e.putBoolean("migrated_v1", true).apply()
    }
}
