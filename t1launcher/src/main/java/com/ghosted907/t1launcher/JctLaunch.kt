package com.ghosted907.t1launcher

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Preferences shared by the launch controls and their background services. */
object JctStore {
    const val FILE = "jct"
    fun p(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun str(c: Context, k: String, d: String) = p(c).getString(k, d) ?: d
    fun putStr(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int) = p(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = p(c).edit().putInt(k, v).apply()

    // Existing keys stay stable so settings from earlier T1 versions keep working.
    const val PKG = "pkg"
    const val PKG_DEFAULT = ""
    const val BOOT = "boot"                                // auto-start "pkg" on the cluster
    const val LOUNCHB_TRUE = "LounchBTrue"                 // single floating button on/off
    const val LOUNCHB_ACTION = "LounchB_set_new"           // floating button action
    const val MULTI_ON = "sw_multi_button"                 // multi button on/off
    const val MULTI_COUNT = "multi_use_eight_buttons"
    const val MULTI_ACTION = "multi_action_"               // + 0..7
    const val OVER_SYSTEM = "widgets_over_system"          // default true
    const val TASKS = "run_app_more_settings"              // task group JSON
    const val TASKS_DELAY = "run_app_more_settings_DELAY"  // seconds, default 1
    const val TASKS_BOOT = "sw_run_app_more"
    const val ZONE_ACTION = "GestureZoneManager"           // + zone name
    // Display indices can be overridden when the OEM reports an unexpected order.
    const val GU_INDEX = "gu_display_index"
    const val PRI_INDEX = "pri_display_index"
    // The destination used by the last successful manual launch.
    const val PKG_AREA = "pkg_area"
    const val AREA_PRI = 2
    const val AREA_GU = 3
}

/** Android display launches and reusable launcher shortcut execution. */
object JctLaunch {
    private const val TAG = "JctLaunch"
    const val PROFILE_TASKS = JctStore.TASKS

    fun displays(context: Context): Array<Display> =
        context.getSystemService(DisplayManager::class.java)?.displays ?: emptyArray()

    /** Index of display 0 in getDisplays(); override with "gu_display_index". */
    fun guIndex(context: Context): Int {
        val saved = JctStore.int(context, JctStore.GU_INDEX, -1)
        if (saved >= 0) return saved
        return displays(context).indexOfFirst { it.displayId == Display.DEFAULT_DISPLAY }.coerceAtLeast(0)
    }

    /** Index of the 1920x720 cluster panel in getDisplays(); override with "pri_display_index". */
    fun priIndex(context: Context): Int {
        val saved = JctStore.int(context, JctStore.PRI_INDEX, -1)
        if (saved >= 0) return saved
        val all = displays(context)
        val panels = all.map { d ->
            val m = DisplayMetrics(); d.getRealMetrics(m)
            ClusterMovePolicy.Panel(d.displayId, m.widthPixels, m.heightPixels)
        }
        val id = ClusterMovePolicy.cluster(panels) ?: return if (all.size > 1) 1 else -1
        return all.indexOfFirst { it.displayId == id }
    }

    fun describe(context: Context): String = displays(context).mapIndexed { i, d ->
        val m = DisplayMetrics(); d.getRealMetrics(m)
        "[$i] id=${d.displayId} ${m.widthPixels}×${m.heightPixels}"
    }.joinToString("\n")

    /** Returns false when the selected app or display is unavailable. */
    fun startOnDisplayIndex(context: Context, pkg: String, d: Int): Boolean {
        return try {
            if (pkg.isBlank()) return false
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            val displays = displays(context)
            if (d < 0 || displays.size <= d) return false
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displays[d].displayId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, options.toBundle())
            if (d == priIndex(context)) rememberCluster(context, pkg)
            Log.i(TAG, "start $pkg d=$d id=${displays[d].displayId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "start $pkg d=$d failed ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Save the chosen app and destination only after Android accepts the launch. */
    fun runPkgOn(context: Context, pkg: String, cluster: Boolean): Boolean {
        val index = if (cluster) priIndex(context) else guIndex(context)
        if (!startOnDisplayIndex(context, pkg, index)) return false
        JctStore.putStr(context, JctStore.PKG, pkg)
        JctStore.putInt(context, JctStore.PKG_AREA, if (cluster) JctStore.AREA_PRI else JctStore.AREA_GU)
        return true
    }

    /** Dispatcher used by floating buttons, edge shortcuts, and task groups. */
    fun run(context: Context, action: JctAction) {
        when (action.type) {
            JctType.ENUM -> runEnum(context, action.enumValue ?: return)
            JctType.APP -> startOnDisplayIndex(context, action.pkg ?: return, guIndex(context))
            JctType.SPLIT -> runSplit(context, action)
            JctType.CUSTOM_CMD -> if (action.profile == PROFILE_TASKS) runTaskGroup(context, PROFILE_TASKS)
        }
    }

    fun runKey(context: Context, key: String) =
        run(context, JctActions.parse(JctStore.str(context, key, JctActions.NOT_VALUE)))

    private fun runEnum(context: Context, e: JctEnum) {
        when (e) {
            JctEnum.Not_value -> Unit
            JctEnum.RUN_START_APP_MENU -> LauncherDisplay.start(context,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, "apps")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            JctEnum.GLOBAL_BACK -> T1TouchService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            JctEnum.GLOBAL_HOME -> T1TouchService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            JctEnum.MEDIA_PLAY -> T1NotificationListener.instance?.controller()?.transportControls?.play()
            JctEnum.MEDIA_PAUSE -> T1NotificationListener.instance?.controller()?.transportControls?.pause()
            JctEnum.MEDIA_NEXT -> T1NotificationListener.instance?.controller()?.transportControls?.skipToNext()
            JctEnum.MEDIA_BLACK -> T1NotificationListener.instance?.controller()?.transportControls?.skipToPrevious()
            JctEnum.GO_TO_PP -> runPkgOn(context, storedPkg(context), cluster = true)
            JctEnum.GO_TO_GU -> runPkgOn(context, storedPkg(context), cluster = false)
            JctEnum.TOGGLE_GU_PP -> runPkgOn(context, storedPkg(context),
                cluster = JctStore.int(context, JctStore.PKG_AREA, JctStore.AREA_GU) != JctStore.AREA_PRI)
            JctEnum.RUN_BLACK -> startOwnOnDisplayIndex(context, Intent(context, JctBlackActivity::class.java), guIndex(context))
            JctEnum.RECENT_TASK_MANAGER -> {
                val done = T1TouchService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) == true
                if (!done) runEnum(context, JctEnum.RUN_START_APP_MENU)
            }
            JctEnum.MEDIA_PLAY_PAUSE -> T1NotificationListener.instance?.controller()?.let { c ->
                if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            }
        }
    }

    private fun storedPkg(context: Context) = JctStore.str(context, JctStore.PKG, JctStore.PKG_DEFAULT)

    /** Open one of our activities on a selected display. */
    fun startOwnOnDisplayIndex(context: Context, intent: Intent, d: Int): Boolean = try {
        val displays = displays(context)
        if (d < 0 || d >= displays.size) false else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displays[d].displayId).toBundle())
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "start own d=$d failed ${e.javaClass.simpleName}: ${e.message}"); false
    }

    /** Place two launchable activities in the launcher's existing freeform dock. */
    fun runSplit(context: Context, action: JctAction) {
        val main = Handler(Looper.getMainLooper())
        val a = action.pkg ?: return
        val b = action.pkg2 ?: return
        if (!HomeAppWindows.freeformEnabled(context)) {
            main.post { Toast.makeText(context, tr(context,
                "تقسيم الشاشة يحتاج تفعيل النوافذ الحرة (freeform) في النظام",
                "Split screen needs freeform window support enabled on the system"), Toast.LENGTH_LONG).show() }
            return
        }
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val m = DisplayMetrics(); display.getRealMetrics(m)
        val (ra, rb) = JctSplit.bounds(m.widthPixels, m.heightPixels, action.orientation, action.ratio)
        val ok = listOf(a to ra, b to rb).all { (pkg, r) ->
            runCatching {
                val component = context.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return@runCatching false
                HomeAppWindows.dock(context, component, android.graphics.Rect(r.x, r.y, r.x + r.w, r.y + r.h)); true
            }.getOrElse { Log.w(TAG, "split $pkg failed ${it.message}"); false }
        }
        if (!ok) main.post { Toast.makeText(context, tr(context, "تعذّر فتح أحد تطبيقي التقسيم", "Could not open one of the split apps"), Toast.LENGTH_LONG).show() }
    }

    // Task group entries retain their saved JSON shape from earlier T1 versions.
    data class Task(val phrase: String, val enabled: Boolean, val matchFull: Boolean, val action: String)

    fun loadTasks(context: Context, key: String = JctStore.TASKS): List<Task> = try {
        val a = JSONArray(JctStore.str(context, key, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Task(it.optString("phrase"), it.optBoolean("enabled", true), it.optBoolean("matchFull"), it.optString("action", JctActions.NOT_VALUE))
        } }
    } catch (e: Exception) { emptyList() }

    fun saveTasks(context: Context, tasks: List<Task>, key: String = JctStore.TASKS) {
        val a = JSONArray()
        tasks.forEach { a.put(JSONObject().put("phrase", it.phrase).put("enabled", it.enabled).put("matchFull", it.matchFull).put("action", it.action)) }
        JctStore.putStr(context, key, a.toString())
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    /** Commands top to bottom, DELAY seconds apart, valid actions only. */
    fun runTaskGroup(context: Context, key: String) {
        val app = context.applicationContext
        val delay = JctStore.int(app, JctStore.TASKS_DELAY, 1).coerceIn(1, 20) * 1000L
        val main = Handler(Looper.getMainLooper())
        loadTasks(app, key).filter { it.enabled }.map { JctActions.parse(it.action) }.filter { JctActions.valid(it) }
            .filterNot { it.type == JctType.CUSTOM_CMD && it.profile == key }
            .forEachIndexed { i, action ->
                scheduler.schedule({ main.post { run(app, action) } }, delay * (i + 1), TimeUnit.MILLISECONDS)
            }
    }

    // ---- packages sent to the cluster (used by force-close guard) ----
    fun trackedPackages(context: Context): Set<String> =
        JctStore.p(context).getStringSet("cluster_packages", emptySet()).orEmpty()
            .filter { ClusterMovePolicy.mayMoveToMain(it, context.packageName) }.toSet()

    private fun rememberCluster(context: Context, pkg: String) {
        val p = JctStore.p(context)
        p.edit().putStringSet("cluster_packages", p.getStringSet("cluster_packages", emptySet()).orEmpty() + pkg).apply()
    }
}

/**
 * Handles delayed cluster launch after vehicle startup. The extra is a display index.
 */
class JctBootService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        val d = intent?.extras?.getInt(EXTRA_D, -1) ?: -1
        if (d > -1) {
            val pkg = JctStore.str(this, JctStore.PKG, JctStore.PKG_DEFAULT)
            if (JctLaunch.displays(this).size > 1) JctLaunch.startOnDisplayIndex(this, pkg, d)
            else Log.e("JctBoot", "Start App ... single display")
        }
        stopForeground(true)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "T1", NotificationManager.IMPORTANCE_LOW))
        startForeground(77, Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_stat_t1).setContentTitle("T1").build())
    }

    companion object {
        const val EXTRA_D = "d"
        private const val CH = "jct_boot"
        fun start(context: Context, d: Int) {
            val i = Intent(context.applicationContext, JctBootService::class.java).putExtra(EXTRA_D, d)
            context.startForegroundService(i)
        }

        /** Queue optional startup shortcuts after the system reports car start. */
        fun onCarStart(context: Context) {
            val app = context.applicationContext
            val h = Handler(Looper.getMainLooper())
            if (JctStore.bool(app, JctStore.BOOT, false)) h.postDelayed({ start(app, JctLaunch.priIndex(app)) }, 10_000L)
            if (JctStore.bool(app, JctStore.TASKS_BOOT, false)) JctLaunch.runTaskGroup(app, JctStore.TASKS)
        }
    }
}
