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

    /** Best-effort Android task reuse, not a guarantee that OEM policy permits reparenting. */
    fun launch(context: Context, component: ComponentName, bounds: Rect) {
        require(component.packageName != context.packageName) { "لا يمكن وضع اللانشر على العدادات" }
        val display = requireCluster(context)
        val intent = reuseIntent(context, component)
        context.startActivity(intent, freeformBundle(display, fit(context, display, bounds)))
        remember(context, component)
        reportLater(context, component.packageName, display)
    }

    private fun reuseIntent(context: Context, component: ComponentName): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(component.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        // Replace, do not OR with launcher defaults (which may include CLEAR_TOP).
        intent.flags = ClusterMovePolicy.REUSE_FLAGS
        return intent
    }

    fun moveToMain(context: Context, component: ComponentName) {
        require(ClusterMovePolicy.mayMoveToMain(component.packageName, context.packageName)) {
            "لن يُفتح اللانشر أو ثيم العدادات على الشاشة الرئيسية"
        }
        val display = Displays.mainId(context) ?: error("لا توجد شاشة رئيسية متصلة")
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display)
        context.startActivity(reuseIntent(context, component), options.toBundle())
        reportLater(context, component.packageName, display)
    }

    fun trackedPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("t1", Context.MODE_PRIVATE)
        return (prefs.getStringSet("clusterPackages", emptySet()).orEmpty() +
            listOf(prefs.getString("clusterPkg", "").orEmpty()) +
            prefs.all.keys.filter { it.startsWith("place_") }.map { it.removePrefix("place_") })
            .filter { ClusterMovePolicy.mayMoveToMain(it, context.packageName) }.toSet()
    }

    /** The old remove button now requests moving tracked apps home; no privileged removeTask. */
    fun clear(context: Context): String {
        if (Displays.clusterId(context) == null) return "لا توجد شاشة عدادات متصلة"
        val tracked = trackedPackages(context)
        if (tracked.isEmpty()) return "لا توجد تطبيقات عدادات متتبعة قابلة للنقل"
        val failures = mutableListOf<String>()
        tracked.forEach { pkg ->
            runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: error("لا يوجد مدخل تشغيل")
                moveToMain(context, intent.component ?: error("مدخل غير محدد"))
            }.onFailure { failures += pkg }
        }
        return "أُرسل طلب نقل ${tracked.size - failures.size} تطبيق إلى الشاشة الرئيسية دون إزالة المهام. " +
            "تعذر تأكيد انتقال النوافذ حتى تظهر على الشاشة المطلوبة؛ قد يرفض نظام السيارة النقل أو يفتح التطبيق إذا لم يكن يعمل." +
            if (failures.isEmpty()) "" else " تعذر إرسال الطلب: ${failures.joinToString("، ")}"
    }

    private fun reportLater(context: Context, pkg: String, display: Int) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val confirmed = visibleTasks(app).any {
                (it.baseActivity?.packageName == pkg || it.topActivity?.packageName == pkg) && taskDisplay(it) == display
            }
            android.widget.Toast.makeText(app, if (confirmed) "شوهد التطبيق على الشاشة المطلوبة؛ إعادة استخدام نفس المهمة غير مضمونة"
                else "تعذر تأكيد نقل التطبيق؛ تحقق من الشاشة المطلوبة، قد يمنع أندرويد نقل النافذة", android.widget.Toast.LENGTH_LONG).show()
        }, 900)
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
        val storage = context.getSharedPreferences("t1", Context.MODE_PRIVATE)
        storage.edit().putStringSet("clusterPackages", storage.getStringSet("clusterPackages", emptySet()).orEmpty() + component.packageName).apply()
        prefs.clusterPackage = component.packageName
        prefs.clusterClass = component.className
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
                it.name == "getTasks" && it.parameterTypes.size in 1..4 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType && it.parameterTypes.drop(1).all { type ->
                        type == Boolean::class.javaPrimitiveType || type == Int::class.javaPrimitiveType
                    }
            } ?: return@runCatching emptyList()
            @Suppress("UNCHECKED_CAST")
            val args = method.parameterTypes.mapIndexed { index, type ->
                if (index == 0) 100 else if (type == Boolean::class.javaPrimitiveType) false else -1
            }.toTypedArray()
            method.invoke(service, *args) as? List<ActivityManager.RunningTaskInfo> ?: emptyList()
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

    private fun root(error: Throwable): Throwable {
        return (error as? InvocationTargetException)?.targetException ?: error
    }
}
