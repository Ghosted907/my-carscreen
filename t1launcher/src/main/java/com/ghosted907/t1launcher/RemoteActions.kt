package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Maps phone commands to functions the launcher ALREADY has. No new launch, close or vehicle paths:
 *  open/move/front -> JctLaunch.startOnDisplayIndex (setLaunchDisplayId, NaviStart port)
 *  close           -> ClusterForceClose.close (existing guarded close)
 *  resize          -> HomeAppWindows.dock / fullscreen (existing freeform dock; only when freeform is on)
 *  vehicle         -> VehicleControl.send(id) with VehicleCommands ids (existing 9 approved commands)
 */
class RemoteActions(private val context: Context) {
    data class Out(val ok: Boolean, val msg: String, val data: JSONObject? = null)

    private val main = Handler(Looper.getMainLooper())
    private val vehicle by lazy { VehicleControl(context) }
    private var vehicleUsed = false
    private val art = RemoteArt(context)

    fun run(body: JSONObject): Out = when (body.getString("op")) {
        RemoteProtocol.OP_PING -> Out(true, "متصل", JSONObject()
            .put("vehicle", RemoteStore.vehicleAllowed(context))
            .put("freeform", HomeAppWindows.freeformEnabled(context))
            .put("cluster", clusterIndex() >= 0)
            .put("art", true)
            .put("close", ClusterForceClose.ENABLED))
        RemoteProtocol.OP_APPS -> apps()
        RemoteProtocol.OP_TASKS -> tasks()
        RemoteProtocol.OP_OPEN, RemoteProtocol.OP_MOVE -> open(body.getString("pkg"), body.getString("screen"))
        RemoteProtocol.OP_FRONT -> body.getString("pkg").let { open(it, RemoteStore.screenOf(context, it) ?: RemoteProtocol.SCREEN_MAIN) }
        RemoteProtocol.OP_CLOSE -> close(body.getString("pkg"))
        RemoteProtocol.OP_RESIZE -> resize(body.getString("pkg"), body.getString("preset"))
        RemoteProtocol.OP_VEHICLE -> vehicle(body.getString("cmd"))
        RemoteProtocol.OP_ART -> onMain { art.show(body) } ?: Out(false, "تعذر عرض اللوحة")
        RemoteProtocol.OP_ART_CLEAR -> onMain { art.clear(); Out(true, "تم إخفاء اللوحة") }
            ?: Out(false, "تعذر إخفاء اللوحة")
        else -> Out(false, "أمر غير معروف")
    }

    private fun allowedApp(pkg: String) = ClusterMovePolicy.mayMoveToMain(pkg, context.packageName) &&
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun label(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun clusterIndex(): Int = if (JctLaunch.displays(context).size > 1) JctLaunch.priIndex(context) else -1

    private fun apps(): Out {
        val list = JSONArray()
        AppIndex.launchable(context).filter { ClusterMovePolicy.mayMoveToMain(it.packageName, context.packageName) }
            .distinctBy { it.packageName }.forEach { app ->
                list.put(JSONObject().put("pkg", app.packageName).put("label", app.label).put("icon", icon(app.packageName)))
            }
        return Out(true, "${list.length()} تطبيق", JSONObject().put("apps", list))
    }

    /** Small PNG icon, base64. Empty when it cannot be drawn. */
    private fun icon(pkg: String): String = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, 64, 64); d.draw(Canvas(b))
        val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, out); b.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrDefault("")

    /**
     * Android 11 does not let a normal app list other apps' running tasks, so this is the list the launcher
     * knows about: apps it sent to the cluster, apps opened from the phone, and its recent apps.
     */
    private fun tasks(): Out {
        val cluster = JctLaunch.trackedPackages(context)
        val seen = LinkedHashMap<String, String>()
        RemoteStore.remotePackages(context).forEach { seen[it] = RemoteStore.screenOf(context, it) ?: RemoteProtocol.SCREEN_MAIN }
        cluster.forEach { if (it !in seen) seen[it] = RemoteProtocol.SCREEN_CLUSTER }
        T1Prefs(context).recentApps.forEach { if (it !in seen) seen[it] = RemoteProtocol.SCREEN_MAIN }
        val list = JSONArray()
        seen.filterKeys { allowedApp(it) }.forEach { (pkg, screen) ->
            list.put(JSONObject().put("pkg", pkg).put("label", label(pkg)).put("screen", screen).put("icon", icon(pkg)))
        }
        return Out(true, "${list.length()} نافذة معروفة", JSONObject().put("tasks", list))
    }

    private fun open(pkg: String, screen: String): Out {
        if (!allowedApp(pkg)) return Out(false, "هذا التطبيق غير مسموح أو غير مثبت")
        val index = if (screen == RemoteProtocol.SCREEN_CLUSTER) clusterIndex() else JctLaunch.guIndex(context)
        if (index < 0) return Out(false, "شاشة العدادات غير متاحة الآن")
        val ok = onMain { JctLaunch.startOnDisplayIndex(context, pkg, index) } ?: false
        if (!ok) return Out(false, "تعذّر فتح ${label(pkg)}؛ قد تحتاج T1 صلاحية الظهور فوق التطبيقات")
        RemoteStore.rememberScreen(context, pkg, screen)
        val where = if (screen == RemoteProtocol.SCREEN_CLUSTER) "العدادات" else "الشاشة الرئيسية"
        return Out(true, "طُلب فتح ${label(pkg)} على $where؛ تحقق من ظهور التطبيق")
    }

    private fun close(pkg: String): Out {
        if (!allowedApp(pkg)) return Out(false, "هذا التطبيق غير مسموح")
        if (!ClusterForceClose.ENABLED) return Out(false, "إغلاق التطبيق الظاهر غير متاح في هذا الإصدار")
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        main.post { ClusterForceClose.close(context, pkg) { result.set(it); latch.countDown() } }
        if (!latch.await(8, TimeUnit.SECONDS)) return Out(false, "لم يرد اللانشر في الوقت")
        val msg = result.get()
        val closed = msg.startsWith("أُغلق")
        if (closed) RemoteStore.forget(context, pkg)
        return Out(closed, msg)
    }

    private fun resize(pkg: String, preset: String): Out {
        if (!allowedApp(pkg)) return Out(false, "هذا التطبيق غير مسموح")
        if (!HomeAppWindows.freeformEnabled(context))
            return Out(false, "تغيير الحجم غير مدعوم: وضع النوافذ الحرة (freeform) غير مفعّل في نظام السيارة")
        val component = context.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return Out(false, "لا يوجد نشاط تشغيل")
        val m = DisplayMetrics()
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(m)
        val b = RemoteProtocol.presetBounds(preset, m.widthPixels, m.heightPixels)
        val ok = onMain {
            if (b == null) HomeAppWindows.fullscreen(context, component)
            else HomeAppWindows.dock(context, ComponentName(component.packageName, component.className), Rect(b[0], b[1], b[2], b[3]))
            true
        } ?: false
        if (ok) RemoteStore.rememberScreen(context, pkg, RemoteProtocol.SCREEN_MAIN)
        return if (ok) Out(true, "طُلب تغيير حجم ${label(pkg)}؛ النظام قد لا يغيّر نافذة مفتوحة مسبقاً") else Out(false, "تعذّر تغيير الحجم")
    }

    private fun vehicle(id: String): Out {
        if (!RemoteStore.vehicleAllowed(context)) return Out(false, "أوامر النوافذ والسقف من الجوال مطفأة في إعدادات T1")
        val command = VehicleCommands.all.firstOrNull { it.id == id } ?: return Out(false, "أمر غير مسموح")
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        main.post {
            Toast.makeText(context, "من الجوال: ${command.label}", Toast.LENGTH_SHORT).show()
            vehicleUsed = true
            vehicle.send(command.id) { result.set(it); latch.countDown() }
        }
        // VehicleControl ignores a press while the previous one is still running.
        if (!latch.await(6, TimeUnit.SECONDS)) return Out(false, "الأمر السابق لم ينتهِ بعد أو لم ترد خدمة السيارة")
        val msg = result.get()
        return Out(msg.startsWith("أُرسل"), msg)
    }

    private fun <T> onMain(block: () -> T): T? {
        val r = AtomicReference<T?>()
        val latch = CountDownLatch(1)
        main.post { try { r.set(runCatching(block).getOrNull()) } finally { latch.countDown() } }
        latch.await(5, TimeUnit.SECONDS)
        return r.get()
    }

    fun close() { art.clear(); if (vehicleUsed) vehicle.close() }
}
