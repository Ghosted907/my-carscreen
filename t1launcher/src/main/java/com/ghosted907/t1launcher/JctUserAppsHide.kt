package com.ghosted907.t1launcher

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Port of NaviStart `p0.d` (UserAppsHide) + `p0.b` (launcher shortcuts).
 *  - Without device owner: only this app's own launcher shortcuts (activity-aliases) are
 *    disabled with PackageManager.setComponentEnabledSetting(DISABLED, DONT_KILL_APP).
 *  - With device owner: additionally every installed non-system, non-updated-system app
 *    except this one is hidden with DevicePolicyManager.setApplicationHidden(admin, pkg, true),
 *    verified with isApplicationHidden.
 * Restore: 14 taps on the movable recovery square (p0.i/p0.h) or the broadcast
 * com.ghosted907.t1launcher.action.USER_APPS_HIDE_OPEN (UserAppsHideCommandReceiver).
 */
class JctUserAppsHide(context: Context) {
    private val ctx = context.applicationContext

    data class Result(val ok: Boolean, val success: Int, val fail: Int, val error: String?) {
        operator fun plus(o: Result) = Result(ok && o.ok, success + o.success, fail + o.fail, error ?: o.error)
    }

    /** p0.a: id, component, default enabled, locked (cannot be toggled by the user). */
    data class Shortcut(val id: String, val component: ComponentName, val labelAr: String, val defaultEnabled: Boolean, val locked: Boolean)

    val shortcuts = listOf(
        Shortcut("main", ComponentName(ctx.packageName, "${ctx.packageName}.LauncherMainActivity"), "T1", true, true)
    )

    fun shortcutEnabled(s: Shortcut) = JctStore.bool(ctx, SHORTCUT + s.id, s.defaultEnabled)

    /** f2 case 1: toggle a shortcut; applied immediately only when hide mode is off. */
    fun setShortcut(s: Shortcut, on: Boolean) {
        if (s.locked) return
        JctStore.putBool(ctx, SHORTCUT + s.id, on)
        if (!enabled(ctx)) ctx.packageManager.setComponentEnabledSetting(s.component, if (on) 1 else 2, 1)
    }

    private fun shortcutsWith(enabled: Boolean) = shortcuts.filter { shortcutEnabled(it) == enabled }.map { it.component }

    private val dpm get() = ctx.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(ctx, ClusterDeviceAdmin::class.java)

    fun isOwner(): Boolean = runCatching { dpm?.isDeviceOwnerApp(ctx.packageName) == true }.getOrDefault(false)

    /** p0.d.a: user apps = not FLAG_SYSTEM (1) and not FLAG_UPDATED_SYSTEM_APP (128), not us. */
    fun userApps(): List<String> = ctx.packageManager.getInstalledApplications(0).filter {
        it.packageName != ctx.packageName && it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }.map { it.packageName }.distinct()

    /** p0.d.d: hide. */
    fun hide(progress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val owner = isOwner()
        val apps = if (owner) userApps() else emptyList()
        val sc = shortcutsWith(true)
        val total = apps.size + sc.size
        var done = 0
        val tick = { done++; progress(done, total) }
        val r1 = if (owner) setHidden(apps, true, tick) else Result(true, 0, 0, null)
        val r2 = setComponents(sc, false, tick)
        if (r2.success > 0 || sc.isEmpty()) {
            JctStore.putStr(ctx, PACKAGES, apps.joinToString("\n"))
            JctStore.putStr(ctx, LAUNCHERS, sc.joinToString("\n") { it.flattenToString() })
            JctStore.putBool(ctx, ENABLED, true)
            JctStore.putBool(ctx, TRANSPARENT, true)
        }
        return r1 + r2
    }

    /** p0.d.f: restore. */
    fun restore(progress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val pkgs = JctStore.str(ctx, PACKAGES, "").split("\n").map { it.trim() }
            .filter { it.isNotEmpty() && it != ctx.packageName }.distinct()
        val saved = JctStore.str(ctx, LAUNCHERS, "").split("\n").mapNotNull { ComponentName.unflattenFromString(it.trim()) }
            .filter { it.packageName == ctx.packageName }.distinct()
        val known = shortcuts.map { it.component }
        val extra = saved.filterNot { it in known }
        val total = known.size + extra.size + pkgs.size
        var done = 0
        val tick = { done++; progress(done, total) }
        val r1 = if (isOwner() && pkgs.isNotEmpty()) setHidden(pkgs, false, tick) else Result(true, 0, 0, null)
        val r2 = setComponents(extra, true, tick) + setComponents(shortcutsWith(true), true, tick) + setComponents(shortcutsWith(false), false, tick)
        if (r2.success > 0 || r2.fail == 0) {
            JctStore.putStr(ctx, PACKAGES, ""); JctStore.putStr(ctx, LAUNCHERS, "")
            JctStore.putBool(ctx, ENABLED, false); JctStore.putBool(ctx, TRANSPARENT, false)
        }
        return r1 + r2
    }

    /** p0.d.i: COMPONENT_ENABLED_STATE_ENABLED(1)/DISABLED(2), DONT_KILL_APP(1). */
    private fun setComponents(list: List<ComponentName>, enable: Boolean, tick: () -> Unit): Result {
        var ok = 0; var fail = 0; var err: String? = null
        list.forEach {
            try { ctx.packageManager.setComponentEnabledSetting(it, if (enable) 1 else 2, 1); ok++ }
            catch (e: Exception) { fail++; if (err == null) err = "${e.javaClass.simpleName}: ${e.message}" }
            tick()
        }
        return Result(fail == 0, ok, fail, err)
    }

    /** p0.d.j */
    private fun setHidden(list: List<String>, hidden: Boolean, tick: () -> Unit): Result {
        val dpm = dpm ?: return Result(list.isEmpty(), 0, list.size, "DevicePolicyManager is null")
        var ok = 0; var fail = 0; var err: String? = null
        list.forEach { pkg ->
            try {
                var applied = dpm.setApplicationHidden(admin, pkg, hidden)
                runCatching { dpm.isApplicationHidden(admin, pkg) }.getOrNull()?.let { applied = it == hidden }
                if (applied) ok++ else { fail++; if (err == null) err = "$pkg: not applied" }
            } catch (e: Exception) { fail++; if (err == null) err = "$pkg: ${e.javaClass.simpleName}: ${e.message}" }
            tick()
        }
        return Result(fail == 0, ok, fail, err)
    }

    companion object {
        const val ENABLED = "user_apps_hide_enabled"
        const val PACKAGES = "user_apps_hide_packages"
        const val LAUNCHERS = "user_apps_hide_launchers"
        const val ZONE_X = "user_apps_hide_zone_x"
        const val ZONE_Y = "user_apps_hide_zone_y"
        const val TRANSPARENT = "user_apps_hide_zone_transparent"
        const val SHORTCUT = "launcher_shortcut_enabled_"
        const val ACTION_OPEN = "com.ghosted907.t1launcher.action.USER_APPS_HIDE_OPEN"
        fun enabled(c: Context) = JctStore.bool(c, ENABLED, false)
    }
}

/** Port of NaviStart `HideManager.UserAppsHideCommandReceiver` (exported, restore only). */
class UserAppsHideCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != JctUserAppsHide.ACTION_OPEN) return
        val app = context.applicationContext
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            val r = runCatching { JctUserAppsHide(app).restore() }.getOrNull()
            Log.i("UserAppsHideAdb", "open mode requested success=${r?.success} fail=${r?.fail}")
            Handler(Looper.getMainLooper()).post { JctHideZone.dismiss(); pending.finish() }
        }
    }
}

/**
 * Port of NaviStart `p0.i` + `p0.h`: 88 dp movable recovery square (flags 808), position in
 * user_apps_hide_zone_x/y. Nearly invisible (alpha 0.01) while apps are hidden. Each tap counts;
 * the counter resets after 1300 ms without taps; from the 4th tap the square gradually shows;
 * at 14 taps it restores and shows progress in %.
 */
object JctHideZone {
    private var view: TextView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var taps = 0
    private var busy = false
    private val h = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val reset = Runnable { taps = 0; idle() }
    var onRestored: ((JctUserAppsHide.Result) -> Unit)? = null

    private fun bg(color: String) {
        val v = view ?: return
        v.background = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = JctOverlay.dp(v.context, 10).toFloat() }
    }

    private fun transparent(c: Context) = JctStore.bool(c, JctUserAppsHide.TRANSPARENT, false)

    /** p0.i.e */
    private fun idle() {
        val v = view ?: return
        if (busy) return
        v.text = ""
        v.alpha = if (transparent(v.context)) 0.01f else 1f
        bg(if (JctUserAppsHide.enabled(v.context)) "#22111111" else "#AA111111")
    }

    /** p0.i.f: 8 blinks, 180 ms, "!" on orange #DDF57C00. */
    private fun blink(i: Int) {
        val v = view ?: return
        if (busy) return
        if (i >= 8) { idle(); return }
        val on = i % 2 == 0
        v.animate().cancel(); v.text = if (on) "!" else ""; v.alpha = if (on) 1f else 0.25f
        bg(if (on) "#DDF57C00" else "#AA111111")
        h.postDelayed({ blink(i + 1) }, 180)
    }

    /** p0.i.h */
    fun show(context: Context) {
        val c = context.applicationContext
        if (view != null) { idle(); return }
        val v = TextView(c).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 18f
            typeface = Typeface.DEFAULT_BOLD; includeFontPadding = false
        }
        view = v; bg("#AA111111"); v.alpha = if (transparent(c)) 0.01f else 1f
        val size = JctOverlay.dp(c, 88)
        val m = c.resources.displayMetrics
        val p = WindowManager.LayoutParams(size, size, JctOverlay.type(c), 808, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = JctStore.int(c, JctUserAppsHide.ZONE_X, maxOf(0, (m.widthPixels - size) / 2))
            y = JctStore.int(c, JctUserAppsHide.ZONE_Y, maxOf(0, (m.heightPixels - size) / 2))
            title = "UserAppsHide"
        }
        params = p
        v.setOnTouchListener(Touch(c))
        wm = JctOverlay.windowManager(c)
        try { wm?.addView(v, p) } catch (e: Exception) { Log.w("UserAppsHide", "overlay unavailable: ${e.message}"); view = null; wm = null; return }
        h.postDelayed({ blink(1) }, 600)
    }

    fun dismiss() {
        h.removeCallbacksAndMessages(null)
        runCatching { wm?.removeView(view) }
        view = null; wm = null; params = null; taps = 0; busy = false
    }

    private class Touch(val c: Context) : android.view.View.OnTouchListener {
        var sx = 0f; var sy = 0f; var px = 0; var py = 0; var moved = false
        override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
            if (busy) return true
            val p = params ?: return false
            val manager = wm ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = p.x; py = p.y; moved = false; return true }
                MotionEvent.ACTION_UP -> {
                    JctStore.putInt(c, JctUserAppsHide.ZONE_X, maxOf(0, p.x)); JctStore.putInt(c, JctUserAppsHide.ZONE_Y, maxOf(0, p.y))
                    if (!moved) {
                        h.removeCallbacks(reset)
                        taps++
                        Log.i("UserAppsHide", "zone click count=$taps")
                        if (JctUserAppsHide.enabled(c) && taps >= 4) {
                            view?.animate()?.cancel()
                            view?.animate()?.alpha(minOf(1f, maxOf(0, taps - 4) / 10f) * 0.56f + 0.16f)?.setDuration(160)?.start()
                            bg("#AA111111")
                        }
                        if (taps >= 14 && JctUserAppsHide.enabled(c)) {
                            busy = true; h.removeCallbacks(reset); progress(0, 1)
                            worker.execute {
                                val r = runCatching { JctUserAppsHide(c).restore { d, t -> h.post { progress(d, t) } } }
                                    .getOrElse { JctUserAppsHide.Result(false, 0, 1, it.message) }
                                h.post { busy = false; taps = 0; onRestored?.invoke(r); idle() }
                            }
                            return true
                        }
                        h.postDelayed(reset, 1300)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = maxOf(0, Math.round(e.rawX - sx) + px); p.y = maxOf(0, Math.round(e.rawY - sy) + py)
                    runCatching { manager.updateViewLayout(v, p) }
                    moved = moved || Math.abs(e.rawX - sx) > JctOverlay.dp(c, 4) || Math.abs(e.rawY - sy) > JctOverlay.dp(c, 4)
                    return true
                }
            }
            return false
        }
    }

    /** p0.i.i */
    private fun progress(done: Int, total: Int) {
        val v = view ?: return
        val pct = if (total <= 0) 0 else minOf(100, Math.round(done * 100f / total))
        v.animate().cancel(); v.alpha = 1f; v.text = "$pct%"; bg("#CC111111")
    }
}
