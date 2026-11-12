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

/** NaviStart `h0.c.o()` key/value store, kept in its own file so keys match NaviStart 1:1. */
object JctStore {
    const val FILE = "jct"
    fun p(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun str(c: Context, k: String, d: String) = p(c).getString(k, d) ?: d
    fun putStr(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int) = p(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = p(c).edit().putInt(k, v).apply()

    // Keys copied from NaviStart 1.4.1
    const val PKG = "pkg"                                  // app chosen for "launch on cluster/tablet"
    const val PKG_DEFAULT = "ru.yandex.yandexnavi"         // NaviStart's default value of "pkg"
    const val BOOT = "boot"                                // auto-start "pkg" on the cluster
    const val LOUNCHB_TRUE = "LounchBTrue"                 // single floating button on/off
    const val LOUNCHB_ACTION = "LounchB_set_new"           // floating button action
    const val MULTI_ON = "sw_multi_button"                 // multi button on/off
    const val MULTI_COUNT = "multi_use_eight_buttons"      // 8 = eight buttons, 6 = four buttons (NaviStart value)
    const val MULTI_ACTION = "multi_action_"               // + 0..7
    const val OVER_SYSTEM = "widgets_over_system"          // default true
    const val TASKS = "run_app_more_settings"              // task group JSON
    const val TASKS_DELAY = "run_app_more_settings_DELAY"  // seconds, default 1
    const val TASKS_BOOT = "sw_run_app_more"
    const val ZONE_ACTION = "GestureZoneManager"           // + zone name
    // Launcher-specific: NaviStart takes these from its car profile (getGuDisplayID / getPriDisplayID).
    const val GU_INDEX = "gu_display_index"
    const val PRI_INDEX = "pri_display_index"
    // 1.11.0: where "pkg" was last sent (2 = cluster, 3 = head unit), for CarRunApp_TOGGLE_GU_PP.
    // Same values as NaviStart's "areaBoot"; kept under its own key so the boot choice is untouched.
    const val PKG_AREA = "pkg_area"
    const val AREA_PRI = 2
    const val AREA_GU = 3
}

/**
 * JCarTools launch path, 1:1 with `boot.onStartCommand` / `j0.c.g` / `a1.c` case 6:
 *   intent = packageManager.getLaunchIntentForPackage(pkg)
 *   intent.addFlags(FLAG_ACTIVITY_NEW_TASK)                       (268435456)
 *   options = ActivityOptions.makeBasic().setLaunchDisplayId(displays[d].displayId)
 *   startActivity(intent, options.toBundle())
 * where `d` is an INDEX into DisplayManager.getDisplays(), not a display id.
 */
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

    /** Shared core. Returns false (like NaviStart, silently) when it cannot launch. */
    fun startOnDisplayIndex(context: Context, pkg: String, d: Int): Boolean {
        return try {
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

    /** MainActivity "تشغيل على لوحة العدادات" / "تشغيل على الجهاز اللوحي": save "pkg", then the boot service with extra d. */
    fun runPkgOn(context: Context, pkg: String, cluster: Boolean) {
        JctStore.putStr(context, JctStore.PKG, pkg)
        JctStore.putInt(context, JctStore.PKG_AREA, if (cluster) JctStore.AREA_PRI else JctStore.AREA_GU)
        JctBootService.start(context, if (cluster) priIndex(context) else guIndex(context))
    }

    /** j0.c.g: action dispatcher used by floating/multi buttons, edge zones, task groups. */
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

    /** e0.z.e for the enum actions this port carries. */
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
            // e0.z.e case 1 / 6 / 9: the stored "pkg" on the cluster, on the head unit, or on the other one.
            JctEnum.GO_TO_PP -> runPkgOn(context, storedPkg(context), cluster = true)
            JctEnum.GO_TO_GU -> runPkgOn(context, storedPkg(context), cluster = false)
            JctEnum.TOGGLE_GU_PP -> runPkgOn(context, storedPkg(context),
                cluster = JctStore.int(context, JctStore.PKG_AREA, JctStore.AREA_GU) != JctStore.AREA_PRI)
            // e0.z.e case 2: BlackActivity with setLaunchDisplayId on the head-unit display.
            JctEnum.RUN_BLACK -> startOwnOnDisplayIndex(context, Intent(context, JctBlackActivity::class.java), guIndex(context))
            // Code 74 opens NaviStart's own recents fragment; here the system recents, else T1's apps page (recent list).
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

    /** T1's own activity on a display index, same ActivityOptions as NaviStart (r5.k.D). */
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

    /**
     * Split pair. NaviStart hands SPLIT to a closed native proxy; its readable test path (o1.c) uses
     * ActivityOptions windowing mode + launch bounds. T1 does the same through the existing freeform
     * launcher (HomeAppWindows.dock) on display 0, so it only works when freeform support is on.
     */
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

    // ---- task group (run_app_more_settings_fragment / j0.c.a) ----
    data class Task(val phrase: String, val enabled: Boolean, val matchFull: Boolean, val action: String)

    fun loadTasks(context: Context, key: String = JctStore.TASKS): List<Task> = try {
        val a = JSONArray(JctStore.str(context, key, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Task(it.optString("phrase"), it.optBoolean("enabled", true), it.optBoolean("matchFull"), it.optString("action", JctActions.NOT_VALUE))
        } }
    } catch (e: Exception) { emptyList() }

    /** j0.c.i: same JSON fields. */
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
        loadTasks(app, key).map { JctActions.parse(it.action) }.filter { JctActions.valid(it) }
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
 * Port of NaviStart `boot` service display path: extra int "d" (> -1) is the index into
 * getDisplays(); the package comes from stored "pkg" (default ru.yandex.yandexnavi);
 * launch only when more than one display exists.
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

        /** MyCarReceiver: cluster app and task group after start, cluster ~10 s later. */
        fun onCarStart(context: Context) {
            val app = context.applicationContext
            val h = Handler(Looper.getMainLooper())
            if (JctStore.bool(app, JctStore.BOOT, false)) h.postDelayed({ start(app, JctLaunch.priIndex(app)) }, 10_000L)
            if (JctStore.bool(app, JctStore.TASKS_BOOT, false)) JctLaunch.runTaskGroup(app, JctStore.TASKS)
        }
    }
}
