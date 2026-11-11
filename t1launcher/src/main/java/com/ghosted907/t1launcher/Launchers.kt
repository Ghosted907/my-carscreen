package com.ghosted907.t1launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    val packageName: String
)

/** Display 0 for T1 itself and for apps opened on the main screen. */
object LauncherDisplay {
    private const val WINDOWING_MODE_FULLSCREEN = 1
    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private var moves = 0

    fun bundle(): Bundle {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        return options.toBundle() ?: Bundle()
    }

    fun start(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, bundle())
    }

    /** True only when the activity is attached to a display that is not display 0. */
    fun isOffMain(activity: Activity): Boolean {
        val id = activity.display?.displayId ?: return false
        return id != Display.DEFAULT_DISPLAY && id != Display.INVALID_DISPLAY
    }

    fun noteOnMain() {
        moves = 0
    }

    /**
     * Opens MainActivity on display 0.
     * CLEAR_TASK is paired with NEW_TASK so singleTask does not reuse a task left on the cluster.
     */
    fun moveToMain(activity: Activity) {
        if (moves >= 3) {
            Log.i("T1Launcher", "launcher display move stopped")
            return
        }
        moves += 1
        val intent = Intent(activity.applicationContext, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        val bundle = options.toBundle() ?: Bundle()
        bundle.putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FULLSCREEN)
        activity.applicationContext.startActivity(intent, bundle)
    }
}

object Displays {
    private fun panels(context: Context): List<ClusterMovePolicy.Panel> {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return manager.displays.map { display ->
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            ClusterMovePolicy.Panel(display.displayId, metrics.widthPixels, metrics.heightPixels)
        }
    }
    fun mainId(context: Context): Int? = ClusterMovePolicy.main(panels(context))
    fun clusterId(context: Context): Int? = ClusterMovePolicy.cluster(panels(context))
}

object AppIndex {
    private val preferred = listOf(
        "com.google.android.apps.maps" to "الخرائط",
        "com.google.android.youtube" to "YouTube",
        "com.waze" to "Waze",
        "com.android.chrome" to "المتصفح",
        "com.android.vending" to "متجر التطبيقات",
        "ru.vk.store" to "RuStore"
    )

    fun launchable(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            LaunchableApp(
                label = info.loadLabel(pm)?.toString() ?: activity.packageName,
                component = ComponentName(activity.packageName, activity.name),
                packageName = activity.packageName
            )
        }.sortedBy { it.label }
    }

    fun favorites(all: List<LaunchableApp>, pinned: List<String> = emptyList()): List<LaunchableApp> {
        val byPackage = all.associateBy { it.packageName }
        val picked = mutableListOf<LaunchableApp>()
        pinned.forEach { pkg -> byPackage[pkg]?.let { picked += it } }
        preferred.forEach { (pkg, _) -> byPackage[pkg]?.let { if (picked.none { p -> p.packageName == pkg }) picked += it } }
        all.firstOrNull { it.packageName.contains("carplay", true) || it.packageName.contains("autolink", true) && it.label.contains("car", true) }
            ?.let { if (picked.none { p -> p.packageName == it.packageName }) picked += it }
        if (picked.size < 8) {
            all.filter { candidate -> picked.none { it.packageName == candidate.packageName } }
                .take(8 - picked.size)
                .forEach { picked += it }
        }
        return picked.take(10)
    }

    fun find(all: List<LaunchableApp>, vararg packages: String): LaunchableApp? =
        packages.firstNotNullOfOrNull { pkg -> all.firstOrNull { it.packageName == pkg } }

    fun launch(context: Context, app: LaunchableApp, onCluster: Boolean, bounds: Rect?) {
        if (!onCluster) {
            ClusterLaunch.moveToMain(context, app.component)
            return
        }
        val rect = if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            bounds
        } else {
            val right = ClusterPresets.rightSide()
            Rect(right.left, right.top, right.right, right.bottom)
        }
        ClusterLaunch.launch(context, app.component, rect)
    }

    fun openInstaller(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm = context.packageManager
        val candidates = listOf(
            ComponentName("com.android.packageinstaller", "com.android.packageinstaller.PackageInstallerActivity"),
            ComponentName("com.google.android.packageinstaller", "com.android.packageinstaller.PackageInstallerActivity")
        )
        val found = candidates.firstOrNull { runCatching { pm.getActivityInfo(it, 0) }.isSuccess }
        if (found != null) {
            intent.component = found
            LauncherDisplay.start(context, intent)
        } else {
            LauncherDisplay.start(
                context,
                Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openListenerSettings(context: Context) {
        LauncherDisplay.start(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
