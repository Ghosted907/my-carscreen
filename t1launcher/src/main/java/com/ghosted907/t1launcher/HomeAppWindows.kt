package com.ghosted907.t1launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Display

/** Opens the real third-party task over a matching launcher card on display 0. */
internal object HomeAppWindows {
    private const val TAG = "T1HomeWindows"
    private const val FREEFORM = 5
    private const val FULLSCREEN = 1
    private val requestedWindows = mutableMapOf<String, Pair<Int, Rect?>>()

    fun freeformEnabled(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)

    fun dock(context: Context, component: ComponentName, bounds: Rect) {
        require(bounds.width() >= 220 && bounds.height() >= 220) { "نافذة التطبيق صغيرة جداً" }
        start(context, component, FREEFORM, bounds)
    }

    fun fullscreen(context: Context, component: ComponentName) {
        start(context, component, FULLSCREEN, null)
    }

    private fun start(context: Context, component: ComponentName, mode: Int, bounds: Rect?) {
        val intent = context.packageManager.getLaunchIntentForPackage(component.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
        val request = mode to bounds?.let(::Rect)
        // OEM Android 11 preserves the old task's fullscreen geometry on a reused launch.
        // Recreate its activities only when the requested geometry changes.
        val recreate = requestedWindows[component.packageName] != request
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or if (recreate) {
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        } else {
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        if (bounds != null) options.setLaunchBounds(bounds)
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, mode)
        }.onFailure { Log.i(TAG, "windowing mode method unavailable: ${it.javaClass.simpleName}") }
        val bundle = options.toBundle() ?: Bundle()
        bundle.putInt("android.activity.windowingMode", mode)
        bundle.putInt("android.activity.activityWindowingMode", mode)
        context.startActivity(intent, bundle)
        requestedWindows[component.packageName] = request
        Log.i(TAG, "launch ${component.packageName} mode=$mode bounds=$bounds")
    }
}
