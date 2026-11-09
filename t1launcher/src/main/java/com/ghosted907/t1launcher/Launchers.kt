package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    val packageName: String
)

object Displays {
    /** Physical cluster: a non-default display near 1920×720. Never assume index 1. */
    fun clusterId(context: Context): Int? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        val displays = manager.displays
        val sized = displays.filter { it.displayId != Display.DEFAULT_DISPLAY }.map { display ->
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            Triple(display.displayId, metrics.widthPixels, metrics.heightPixels)
        }
        Log.i("T1Launcher", "displays " + sized.joinToString { "${it.first}:${it.second}x${it.third}" })
        // The car's cluster is display 1. Prefer it when that panel is attached.
        if (sized.any { it.first == 1 }) return 1
        return sized.firstOrNull { it.second >= 1600 && it.third in 640..800 }?.first
            ?: sized.firstOrNull()?.first
    }
}

object AppIndex {
    private val preferred = listOf(
        "com.google.android.youtube" to "YouTube",
        "com.waze" to "Waze",
        "com.google.android.apps.maps" to "الخرائط",
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

    fun favorites(all: List<LaunchableApp>): List<LaunchableApp> {
        val byPackage = all.associateBy { it.packageName }
        val picked = mutableListOf<LaunchableApp>()
        preferred.forEach { (pkg, _) -> byPackage[pkg]?.let { picked += it } }
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
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (!onCluster) {
            context.startActivity(intent)
            return
        }
        val rect = if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            bounds
        } else {
            val center = ClusterPresets.center()
            Rect(center.left, center.top, center.right, center.bottom)
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
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
