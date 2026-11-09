package com.ghosted907.t1launcher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.view.Window
import java.lang.reflect.InvocationTargetException

/**
 * Puts an app on the cluster as a freeform window. Bounds are 1920×720 pixels
 * on that display. The system draws the small caption; this process can only
 * hide the caption on its own window.
 */
object ClusterLaunch {
    private const val TAG = "T1Launcher"
    private const val WINDOWING_MODE_FREEFORM = 5
    private const val WINDOWING_MODE_FULLSCREEN = 1
    private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
    private const val KEY_ACTIVITY_WINDOWING_MODE = "android.activity.activityWindowingMode"
    const val FREEFORM_COMMANDS =
        "adb shell settings put global enable_freeform_support 1\nadb shell settings put global force_resizable_activities 1"

    fun freeformEnabled(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    } catch (t: Throwable) {
        Log.i(TAG, "freeform read failed ${t.javaClass.simpleName}")
        false
    }

    fun launch(context: Context, component: ComponentName, bounds: Rect) {
        val display = requireCluster(context)
        val rect = fit(context, display, bounds)
        val task = findTask(context, component.packageName, display)
        if (task != null && task.display == display && resizeTask(task.id, rect)) {
            remember(context, component)
            return
        }
        if (task != null && (task.display == -1 || task.display == display)) removeTask(task.id)
        finishOwnTask(context, component.packageName)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        context.startActivity(intent, freeformBundle(display, rect))
        remember(context, component)
        Log.i(
            TAG,
            "cluster launch ${component.flattenToShortString()} display=$display freeform bounds=${rect.left} ${rect.top} ${rect.right} ${rect.bottom}"
        )
    }

    /** Moves the last cluster app back to the main screen so the stock gauges show. */
    fun clear(context: Context) {
        val prefs = T1Prefs(context)
        val component = prefs.clusterComponent()
        ClusterAddonActivity.finishIfRunning()
        if (component == null) {
            Log.i(TAG, "cluster clear none")
            return
        }
        val display = Displays.clusterId(context)
        val task = findTask(context, component.packageName, display)
        if (task != null && (task.display == -1 || display == null || task.display == display)) {
            removeTask(task.id)
        }
        finishOwnTask(context, component.packageName)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = Display.DEFAULT_DISPLAY
        val bundle = options.toBundle() ?: Bundle()
        bundle.putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FULLSCREEN)
        context.startActivity(intent, bundle)
        Log.i(TAG, "cluster clear ${component.packageName} -> display 0")
    }

    /** Hides the freeform caption on a window in this process. Other apps keep the system bar. */
    fun hideOwnCaption(window: Window) {
        try {
            val decor = window.decorView
            val field = generateSequence(decor.javaClass as Class<*>) { it.superclass }
                .firstNotNullOfOrNull { clazz ->
                    runCatching { clazz.getDeclaredField("mDecorCaptionView") }.getOrNull()
                } ?: run {
                Log.i(TAG, "caption hide skipped absent")
                return
            }
            field.isAccessible = true
            val caption = field.get(decor) as? View
            if (caption == null) {
                Log.i(TAG, "caption hide skipped absent")
                return
            }
            caption.visibility = View.GONE
            Log.i(TAG, "caption hidden")
        } catch (t: Throwable) {
            Log.i(TAG, "caption hide skipped ${t.javaClass.simpleName}")
        }
    }

    private fun requireCluster(context: Context): Int {
        return Displays.clusterId(context) ?: throw IllegalStateException("لا توجد شاشة عدادات")
    }

    private fun fit(context: Context, displayId: Int, bounds: Rect): Rect {
        val (width, height) = displaySize(context, displayId)
        val box = ClusterPresets.custom(bounds.left, bounds.top, bounds.width(), bounds.height(), width, height)
        return Rect(box.left, box.top, box.right, box.bottom)
    }

    private fun displaySize(context: Context, displayId: Int): Pair<Int, Int> {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return ClusterPresets.DESIGN_W to ClusterPresets.DESIGN_H
        val display = manager.getDisplay(displayId) ?: return ClusterPresets.DESIGN_W to ClusterPresets.DESIGN_H
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val width = metrics.widthPixels.takeIf { it > 0 } ?: ClusterPresets.DESIGN_W
        val height = metrics.heightPixels.takeIf { it > 0 } ?: ClusterPresets.DESIGN_H
        return width to height
    }

    private fun freeformBundle(displayId: Int, bounds: Rect): Bundle {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        options.setLaunchBounds(bounds)
        try {
            val method = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            method.invoke(options, WINDOWING_MODE_FREEFORM)
            Log.i(TAG, "cluster setLaunchWindowingMode ok")
        } catch (t: Throwable) {
            Log.i(TAG, "cluster setLaunchWindowingMode reflection ${root(t).javaClass.simpleName}")
        }
        val bundle = options.toBundle() ?: Bundle()
        bundle.putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
        bundle.putInt(KEY_ACTIVITY_WINDOWING_MODE, WINDOWING_MODE_FREEFORM)
        return bundle
    }

    private fun remember(context: Context, component: ComponentName) {
        val prefs = T1Prefs(context)
        prefs.clusterPackage = component.packageName
        prefs.clusterClass = component.className
    }

    private data class TaskRef(val id: Int, val display: Int)

    private fun findTask(context: Context, packageName: String, clusterDisplay: Int?): TaskRef? {
        val tasks = visibleTasks(context)
        val tagged = tasks.map { info ->
            TaskRef(info.taskId, taskDisplay(info)) to (info.baseActivity?.packageName ?: info.topActivity?.packageName)
        }.filter { it.second == packageName }
        val match = tagged.firstOrNull { clusterDisplay != null && it.first.display == clusterDisplay }?.first
            ?: tagged.firstOrNull()?.first
        if (match != null) Log.i(TAG, "cluster task ${match.id} display=${match.display}")
        return match
    }

    @Suppress("DEPRECATION")
    private fun visibleTasks(context: Context): List<ActivityManager.RunningTaskInfo> {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val own = runCatching { manager.getRunningTasks(40) ?: emptyList() }.getOrElse {
            Log.i(TAG, "cluster getRunningTasks skipped ${it.javaClass.simpleName}")
            emptyList()
        }
        val more = runCatching {
            val atm = Class.forName("android.app.ActivityTaskManager")
            val service = atm.getMethod("getService").invoke(null)
            val method = service.javaClass.methods.firstOrNull {
                it.name == "getTasks" && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
            } ?: return@runCatching emptyList()
            @Suppress("UNCHECKED_CAST")
            method.invoke(service, 40) as? List<ActivityManager.RunningTaskInfo> ?: emptyList()
        }.getOrElse {
            Log.i(TAG, "cluster getTasks skipped ${root(it).javaClass.simpleName}")
            emptyList()
        }
        return (own + more).distinctBy { it.taskId }
    }

    private fun taskDisplay(info: ActivityManager.RunningTaskInfo): Int {
        return try {
            val field = info.javaClass.getDeclaredField("displayId")
            field.isAccessible = true
            field.getInt(info)
        } catch (t: Throwable) {
            -1
        }
    }

    private fun resizeTask(taskId: Int, bounds: Rect): Boolean {
        return try {
            val service = taskService() ?: return false
            val method = service.javaClass.methods.firstOrNull {
                it.name == "resizeTask" && it.parameterTypes.size == 3
            } ?: return false
            method.invoke(service, taskId, bounds, 1)
            Log.i(TAG, "cluster resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}")
            true
        } catch (t: Throwable) {
            val cause = root(t)
            Log.i(TAG, "cluster resize denied ${cause.javaClass.simpleName} ${cause.message}")
            false
        }
    }

    private fun removeTask(taskId: Int) {
        try {
            val service = taskService() ?: return
            val method = service.javaClass.methods.firstOrNull {
                it.name == "removeTask" && it.parameterTypes.size == 1
            } ?: return
            method.invoke(service, taskId)
            Log.i(TAG, "cluster removeTask $taskId")
        } catch (t: Throwable) {
            Log.i(TAG, "cluster removeTask skipped ${root(t).javaClass.simpleName}")
        }
    }

    private fun finishOwnTask(context: Context, packageName: String) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        manager.appTasks.forEach { task ->
            val info = task.taskInfo
            val pkg = info.baseActivity?.packageName ?: info.baseIntent?.component?.packageName
            if (pkg == packageName) {
                Log.i(TAG, "cluster finish own task ${info.taskId}")
                task.finishAndRemoveTask()
            }
        }
    }

    private fun taskService(): Any? {
        val atm = Class.forName("android.app.ActivityTaskManager")
        return atm.getMethod("getService").invoke(null)
    }

    private fun root(error: Throwable): Throwable {
        return (error as? InvocationTargetException)?.targetException ?: error
    }
}
