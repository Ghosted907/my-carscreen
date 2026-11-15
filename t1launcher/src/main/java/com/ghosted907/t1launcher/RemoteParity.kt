package com.ghosted907.t1launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * T1 Remote 1.3.0 (protocol rev 3): everything the launcher screen offers, reachable from the phone,
 * by calling the SAME launcher functions the on-screen buttons call. Excluded on purpose: Wi-Fi,
 * device-owner app hiding, PIN/key pairing, voice. The only vehicle writes are the nine body
 * commands (RemoteActions.vehicle) and the A/C items (Climate, owner-approved).
 */
class RemoteParity(private val context: Context, private val art: RemoteArt) {
    private val main = Handler(Looper.getMainLooper())
    private val app get() = context.applicationContext as T1App
    private val probeControl by lazy { VehicleControl(context) }
    private var probeUsed = false

    fun ping(): JSONObject = JSONObject().put("rev", RemoteProtocol.REV)
        .put("ops", JSONArray(RemoteProtocol.OPS.toList())).put("climate", true)
        .put("day", app.prefs.day).put("version", versionName())

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    // ---------------- Home shortcuts ----------------
    fun shortcut(id: String): RemoteActions.Out {
        val apps = AppIndex.launchable(context)
        fun launch(pkg: String): Boolean = context.packageManager.getLaunchIntentForPackage(pkg)?.let { i ->
            onMain { LauncherDisplay.start(context, i); true } ?: false
        } ?: false
        val ok = when (id) {
            "carplay" -> AppIndex.find(apps, "com.autolink.carplaylink")?.let { launch(it.packageName) }
                ?: (onMain { LauncherDisplay.start(context, OemSettings.interconnectionIntent()); true } ?: false)
            "youtube" -> AppIndex.find(apps, "app.revanced.android.youtube", "com.google.android.youtube")?.let { launch(it.packageName) } ?: false
            "waze" -> AppIndex.find(apps, "com.waze")?.let { launch(it.packageName) } ?: false
            "bluetooth" -> AppIndex.find(apps, "com.autolink.bluetooth")?.let { launch(it.packageName) }
                ?: (onMain { LauncherDisplay.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); true } ?: false)
            "climate" -> launch("com.autolink.aircontroller")
            else -> false
        }
        val name = mapOf("carplay" to "CarPlay", "youtube" to "YouTube", "waze" to "Waze", "bluetooth" to "البلوتوث", "climate" to "شاشة المكيف")[id] ?: id
        return if (ok) RemoteActions.Out(true, "طُلب فتح $name على شاشة السيارة")
        else RemoteActions.Out(false, "$name غير مثبت أو تعذر فتحه")
    }

    // ---------------- Pages / OEM screens ----------------
    fun page(page: String): RemoteActions.Out {
        if (page == "installer") return if (onMain { AppIndex.openInstaller(context); true } == true)
            RemoteActions.Out(true, "فُتح مثبّت التطبيقات على الشاشة") else RemoteActions.Out(false, "تعذر فتح المثبّت")
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return if (onMain { LauncherDisplay.start(context, intent); true } == true) RemoteActions.Out(true, "فُتحت الصفحة على شاشة السيارة")
        else RemoteActions.Out(false, "تعذر فتح الصفحة")
    }

    fun oem(target: String): RemoteActions.Out {
        val ok = onMain {
            when (target) {
                "main" -> OemSettings.open(context)
                "steering", "mirrors", "windows", "tailgate" -> OemSettings.open(context, target)
                "interconnection" -> LauncherDisplay.start(context, OemSettings.interconnectionIntent())
                "dark" -> LauncherDisplay.start(context, Intent(context, JctBlackActivity::class.java))
                "devices" -> LauncherDisplay.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "brightness" -> LauncherDisplay.start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
                "overlay" -> LauncherDisplay.start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))
                "accessibility" -> LauncherDisplay.start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "listener" -> AppIndex.openListenerSettings(context)
                else -> return@onMain false
            }
            true
        } ?: false
        return if (ok) RemoteActions.Out(true, "فُتحت الشاشة على السيارة") else RemoteActions.Out(false, "تعذر فتح الشاشة")
    }

    // ---------------- Cluster page buttons ----------------
    fun cluster(action: String, value: Int): RemoteActions.Out {
        val car = app.car
        return when (action) {
            "classic" -> {
                app.prefs.savedTheme = car.snap.theme ?: app.prefs.savedTheme
                app.prefs.savedFont = car.snap.font ?: app.prefs.savedFont
                car.applyClassic(); car.applyFont(1)
                RemoteActions.Out(true, "طُلب قالب كلاسيكي للعدادات")
            }
            "theme" -> { car.applyTheme(value.coerceIn(1, 3)); RemoteActions.Out(true, "طُلب قالب العدادات $value") }
            "font" -> { car.applyFont(value.coerceIn(1, 2)); RemoteActions.Out(true, "طُلب خط العدادات $value") }
            "hide_addons", "stock" -> {
                app.prefs.hideAllSlots()
                onMain { ClusterAddonActivity.finishIfRunning() }
                RemoteActions.Out(true, if (action == "stock") "أُغلقت نافذة T1 على العدادات" else "أُخفيت الإضافات")
            }
            "close_app" -> RemoteActions.Out(false, "الإغلاق القوي معطل في هذا الإصدار")
            else -> RemoteActions.Out(false, "أمر العدادات غير معروف")
        }
    }

    // ---------------- One-shot buttons from the settings page ----------------
    fun run(id: String): RemoteActions.Out {
        val pkg = JctStore.str(context, JctStore.PKG, JctStore.PKG_DEFAULT)
        val ok: Boolean = when (id) {
            "floating_reset" -> { JctFloatingPosition.reset(context); onMain { JctLounchService.sync(context) }; true }
            "tasks_test" -> { onMain { JctLaunch.runTaskGroup(context, JctStore.TASKS) }; true }
            "black_test" -> onMain { JctLaunch.run(context, JctAction(JctType.ENUM, JctEnum.RUN_BLACK)); true } ?: false
            "split_open_1", "split_open_2", "split_open_3" -> {
                val a = JctActions.parse(JctStore.str(context, "split_preset_" + id.last(), ""))
                if (a.type != JctType.SPLIT) return RemoteActions.Out(false, "التخطيط غير محفوظ")
                onMain { JctLaunch.runSplit(context, a); true } ?: false
            }
            "cluster_open_cluster", "cluster_open_main" -> {
                if (pkg.isBlank()) return RemoteActions.Out(false, "اختر تطبيق العدادات أولاً")
                onMain { JctLaunch.runPkgOn(context, pkg, cluster = id == "cluster_open_cluster") } ?: false
            }
            "cluster_move" -> {
                if (pkg.isBlank()) return RemoteActions.Out(false, "اختر تطبيق العدادات أولاً")
                val toCluster = JctStore.int(context, JctStore.PKG_AREA, JctStore.AREA_GU) != JctStore.AREA_PRI
                onMain { JctLaunch.runPkgOn(context, pkg, cluster = toCluster) } ?: false
            }
            else -> false
        }
        return if (ok) RemoteActions.Out(true, "تم الطلب؛ تحقق من شاشة السيارة")
        else RemoteActions.Out(false, "تعذر التنفيذ؛ راجع التطبيق وفهرس الشاشة")
    }

    // ---------------- Shortcut-action catalog ----------------
    fun actions(): RemoteActions.Out {
        val list = JSONArray()
        jctCatalog(context).distinct().forEach { raw -> list.put(JSONObject().put("raw", raw).put("label", jctLabel(context, raw))) }
        return RemoteActions.Out(true, "${list.length()} إجراء", JSONObject().put("actions", list))
    }

    // ---------------- Settings ----------------
    fun settings(): RemoteActions.Out {
        val v = JSONObject()
        val c = context
        v.put("day", app.prefs.day)
        v.put("dest_cluster", app.prefs.destinationCluster)
        v.put("language", JctLang.saved(c))
        v.put("cluster_pkg", JctStore.str(c, JctStore.PKG, JctStore.PKG_DEFAULT))
        v.put("cluster_boot", JctStore.bool(c, JctStore.BOOT, false))
        v.put("gu_index", JctStore.int(c, JctStore.GU_INDEX, -1))
        v.put("pri_index", JctStore.int(c, JctStore.PRI_INDEX, -1))
        v.put("floating_style", JctFloatingStyle.selected(c).toString())
        v.put("floating_action", JctStore.str(c, JctStore.LOUNCHB_ACTION, JctActions.NOT_VALUE))
        v.put("over_system", JctStore.bool(c, JctStore.OVER_SYSTEM, true))
        v.put("tasks_boot", JctStore.bool(c, JctStore.TASKS_BOOT, false))
        v.put("tasks_delay", JctStore.int(c, JctStore.TASKS_DELAY, 1).coerceIn(1, 20))
        v.put("tasks", JSONArray().also { a -> JctLaunch.loadTasks(c).forEach { t ->
            a.put(JSONObject().put("phrase", t.phrase).put("enabled", t.enabled).put("matchFull", t.matchFull).put("action", t.action))
        } })
        (1..3).forEach { v.put("split_$it", JctStore.str(c, "split_preset_$it", "")) }
        v.put("zone_width", JctGestureZones.widthPercent(c))
        v.put("zone_sensitivity", JctGestureZones.sensitivityPercent(c))
        v.put("black_clock", JctStore.bool(c, JctBlackActivity.KEY_CLOCK, true))
        v.put("black_clock24", JctStore.bool(c, JctBlackActivity.KEY_CLOCK_24, true))
        v.put("black_brightness", JctStore.int(c, JctBlackActivity.KEY_BRIGHTNESS, 10).coerceIn(1, 100))
        JctZone.values().forEach { z ->
            if (z.name !in RemoteProtocol.ZONES) return@forEach
            v.put("zone_on_${z.name}", JctGestureZones.enabled(c, z))
            v.put("zone_style_${z.name}", JctGestureZones.style(c, z))
            v.put("zone_action_${z.name}", JctStore.str(c, JctStore.ZONE_ACTION + z.name, JctActions.NOT_VALUE))
        }
        // Human labels for every stored action so the phone does not need the catalog to show them.
        val labels = JSONObject()
        v.keys().forEach { k ->
            val raw = v.opt(k)
            if (raw is String && (k.contains("action") || k.startsWith("split_"))) labels.put(k, if (raw.isEmpty()) "" else jctLabel(c, raw))
        }
        val info = JSONObject()
            .put("displays", JctLaunch.describe(c))
            .put("display_count", JctLaunch.displays(c).size)
            .put("gu_resolved", JctLaunch.guIndex(c))
            .put("pri_resolved", JctLaunch.priIndex(c))
            .put("cluster_label", jctLabel(c, JctActions.APP + v.getString("cluster_pkg")))
            .put("overlay", Settings.canDrawOverlays(c))
            .put("accessibility", T1TouchService.instance != null)
            .put("freeform", HomeAppWindows.freeformEnabled(c))
            .put("hide_apps", JctUserAppsHide.enabled(c))
            .put("version", versionName())
            .put("floating_styles", JSONArray(JctFloatingStyle.choices.map { "$it:${JctFloatingStyle.labels[it]}" }))
            .put("zone_styles", JSONArray(JctGestureZones.styleLabels))
            .put("wallpaper", c.getSharedPreferences("launcher_wallpaper", Context.MODE_PRIVATE).getString("wallpaper_uri", "").orEmpty().isNotBlank())
        return RemoteActions.Out(true, "الإعدادات", JSONObject().put("values", v).put("labels", labels).put("info", info))
    }

    fun set(key: String, value: Any?): RemoteActions.Out {
        val c = context
        fun b() = value as Boolean
        fun i() = (value as Number).toInt()
        fun s() = value as String
        when (key) {
            "day" -> app.prefs.day = b()
            "dest_cluster" -> app.prefs.destinationCluster = b()
            "language" -> JctStore.putStr(c, JctLang.KEY, s())
            "cluster_pkg" -> {
                val pkg = s()
                if (pkg.isNotEmpty() && context.packageManager.getLaunchIntentForPackage(pkg) == null)
                    return RemoteActions.Out(false, "التطبيق غير مثبت")
                JctStore.putStr(c, JctStore.PKG, pkg)
            }
            "cluster_boot" -> JctStore.putBool(c, JctStore.BOOT, b())
            "gu_index" -> JctStore.putInt(c, JctStore.GU_INDEX, i().coerceAtMost(JctLaunch.displays(c).size - 1))
            "pri_index" -> JctStore.putInt(c, JctStore.PRI_INDEX, i().coerceAtMost(JctLaunch.displays(c).size - 1))
            "floating_style" -> { JctStore.putInt(c, JctFloatingStyle.KEY, s().toInt()); onMain { JctLounchService.sync(c) } }
            "floating_action" -> { if (!actionOk(s())) return badAction(); JctStore.putStr(c, JctStore.LOUNCHB_ACTION, s()); onMain { JctLounchService.sync(c) } }
            "over_system" -> { JctStore.putBool(c, JctStore.OVER_SYSTEM, b()); onMain { JctLounchService.sync(c) } }
            "tasks_boot" -> JctStore.putBool(c, JctStore.TASKS_BOOT, b())
            "tasks_delay" -> JctStore.putInt(c, JctStore.TASKS_DELAY, i())
            "tasks" -> {
                val a = value as JSONArray
                val list = (0 until a.length()).map { n -> a.getJSONObject(n).let {
                    JctLaunch.Task(it.optString("phrase").take(80), it.optBoolean("enabled", true), it.optBoolean("matchFull"), it.getString("action"))
                } }
                if (list.any { !actionOk(it.action) }) return badAction()
                JctLaunch.saveTasks(c, list)
            }
            "split_1", "split_2", "split_3" -> {
                val raw = s()
                if (raw.isNotEmpty() && !actionOk(raw)) return badAction()
                JctStore.putStr(c, "split_preset_" + key.last(), raw)
            }
            "zone_width" -> JctGestureZones.setWidthPercent(c, i())
            "zone_sensitivity" -> JctGestureZones.setSensitivityPercent(c, i())
            "black_clock" -> JctStore.putBool(c, JctBlackActivity.KEY_CLOCK, b())
            "black_clock24" -> JctStore.putBool(c, JctBlackActivity.KEY_CLOCK_24, b())
            "black_brightness" -> JctStore.putInt(c, JctBlackActivity.KEY_BRIGHTNESS, i())
            else -> {
                val zone = RemoteProtocol.ZONES.firstOrNull { key.endsWith("_$it") }?.let { z -> JctZone.values().firstOrNull { it.name == z } }
                    ?: return RemoteActions.Out(false, "إعداد غير معروف")
                when {
                    key.startsWith("zone_on_") -> JctGestureZones.setEnabled(c, zone, b())
                    key.startsWith("zone_style_") -> JctGestureZones.setStyle(c, zone, i())
                    key.startsWith("zone_action_") -> { if (!actionOk(s())) return badAction(); JctStore.putStr(c, JctStore.ZONE_ACTION + zone.name, s()) }
                    else -> return RemoteActions.Out(false, "إعداد غير معروف")
                }
            }
        }
        val note = when (key) {
            "language" -> "؛ تظهر اللغة بعد إعادة فتح T1"
            "gu_index", "pri_index" -> "؛ القيمة -1 تعني تلقائي"
            else -> ""
        }
        return RemoteActions.Out(true, "حُفظ الإعداد$note")
    }

    /** Settings only accept actions the launcher can run: installed app/split packages, known enums, the task profile. */
    private fun actionOk(raw: String): Boolean {
        val a = JctActions.parse(raw)
        if (raw == JctActions.NOT_VALUE) return true
        if (!JctActions.valid(a)) return false
        val pm = context.packageManager
        return when (a.type) {
            JctType.APP -> pm.getLaunchIntentForPackage(a.pkg!!) != null
            JctType.SPLIT -> pm.getLaunchIntentForPackage(a.pkg!!) != null && pm.getLaunchIntentForPackage(a.pkg2!!) != null
            JctType.CUSTOM_CMD -> a.profile == JctLaunch.PROFILE_TASKS
            JctType.ENUM -> true
        }
    }
    private fun badAction() = RemoteActions.Out(false, "الإجراء غير متاح على السيارة")

    fun pin(pkg: String, on: Boolean): RemoteActions.Out {
        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return RemoteActions.Out(false, "التطبيق غير مثبت")
        val now = app.prefs.pinnedApps
        if (on && pkg !in now && now.size >= 10) return RemoteActions.Out(false, "المفضلة ممتلئة (١٠ تطبيقات)")
        app.prefs.pinnedApps = if (on) now + pkg else now - pkg
        return RemoteActions.Out(true, if (on) "أُضيف للمفضلة" else "أُزيل من المفضلة")
    }

    // ---------------- Vehicle data (read-only) ----------------
    fun data(): RemoteActions.Out {
        val snap = app.car.snap
        val list = JSONArray()
        SignalCatalog.all.forEach { d ->
            val r = snap.reading(d.key)
            list.put(JSONObject().put("key", d.key).put("label", d.label).put("group", d.group.label)
                .put("value", d.format(r.current)).put("unit", d.unit).put("status", r.status.name)
                .also { o -> r.current?.let { o.put("raw", it) } })
        }
        val summary = JSONObject().put("car", snap.carOk).put("cluster", snap.clusterOk).put("vehicle", snap.vehicleOk)
            .put("live", snap.liveCount).put("total", SignalCatalog.all.size).put("note", snap.note)
            .put("avm", snap.avmOpen == true)
        snap.speed?.let { summary.put("speed", it.toDouble()) }
        snap.gear?.let { summary.put("gear", it) }
        snap.fuel?.let { summary.put("fuel", it) }
        snap.rangeKm?.let { summary.put("range", it) }
        return RemoteActions.Out(true, "${snap.liveCount} قراءة حية", JSONObject().put("summary", summary).put("readings", list))
    }

    // ---------------- A/C ----------------
    fun climate(item: String, value: Double): RemoteActions.Out {
        val (ok, msg) = Climate.send(context, item, value)
        if (ok) main.post { android.widget.Toast.makeText(context, "من الجوال: ${Climate.labels[item]}", android.widget.Toast.LENGTH_SHORT).show() }
        return RemoteActions.Out(ok, msg)
    }

    fun climateState(): RemoteActions.Out {
        val state = JSONObject()
        Climate.state(app.car.snap).forEach { (k, v) -> state.put(k, v ?: JSONObject.NULL) }
        val specs = JSONObject()
        RemoteProtocol.CLIMATE.forEach { (k, s) -> specs.put(k, JSONObject().put("min", s.min).put("max", s.max).put("step", s.step)) }
        return RemoteActions.Out(true, if (app.car.snap.vehicleOk) "حالة المكيف" else "خدمة السيارة غير متصلة",
            JSONObject().put("state", state).put("specs", specs).put("connected", app.car.snap.vehicleOk))
    }

    fun probe(): RemoteActions.Out {
        val r = AtomicReference<String>()
        val latch = CountDownLatch(1)
        main.post { probeUsed = true; probeControl.probe { r.set(it); latch.countDown() } }
        if (!latch.await(6, TimeUnit.SECONDS)) return RemoteActions.Out(false, "لم ترد خدمة السيارة")
        return RemoteActions.Out(true, r.get())
    }

    // ---------------- Cluster art board & stickers ----------------
    private val board get() = context.getSharedPreferences("settings_art", Context.MODE_PRIVATE)

    fun board(): RemoteActions.Out {
        val p = board
        val d = JSONObject()
            .put("caption", p.getString("caption", "").orEmpty())
            .put("caption_x", p.getFloat("caption_x", .04f).toDouble())
            .put("caption_y", p.getFloat("caption_y", .36f).coerceAtLeast(.32f).toDouble())
            .put("caption_size", p.getFloat("caption_size", 28f).toDouble())
            .put("caption_font", p.getInt("caption_font", 0))
            .put("caption_color", p.getInt("caption_color", android.graphics.Color.WHITE))
            .put("draw_color", p.getInt("draw_color", android.graphics.Color.rgb(244, 240, 229)))
            .put("sticker", p.getBoolean("sticker", true))
            .put("sticker_x", p.getFloat("sticker_x", .45f).toDouble())
            .put("sticker_y", p.getFloat("sticker_y", .44f).toDouble())
            .put("sticker_size", p.getFloat("sticker_size", 100f).coerceIn(40f, 240f).toDouble())
            .put("custom", p.getString("custom_sticker", "").orEmpty().isNotBlank())
            .put("custom_x", p.getFloat("custom_x", .72f).toDouble())
            .put("custom_y", p.getFloat("custom_y", .44f).toDouble())
            .put("custom_size", p.getFloat("custom_size", 100f).coerceIn(40f, 240f).toDouble())
            .put("strokes", runCatching { JSONArray(p.getString("strokes", "[]")) }.getOrDefault(JSONArray()))
            .put("fonts", JSONArray(BOARD_FONT_LABELS))
        if (d.getBoolean("custom")) d.put("custom_image", customThumb() ?: "")
        return RemoteActions.Out(true, "لوحة العدادات", d)
    }

    fun boardSet(body: JSONObject): RemoteActions.Out {
        val e = board.edit()
        if (body.has("caption")) e.putString("caption", body.getString("caption"))
        RemoteProtocol.BOARD_FLOATS.keys.forEach { k -> if (body.has(k)) e.putFloat(k, body.getDouble(k).toFloat()) }
        if (body.has("caption_font")) e.putInt("caption_font", body.getInt("caption_font"))
        if (body.has("caption_color")) e.putInt("caption_color", body.getLong("caption_color").toInt())
        if (body.has("draw_color")) e.putInt("draw_color", body.getLong("draw_color").toInt())
        if (body.has("sticker")) e.putBoolean("sticker", body.getBoolean("sticker"))
        if (body.optBoolean("strokes_clear", false)) e.putString("strokes", "[]")
        if (body.has("strokes")) e.putString("strokes", body.getJSONArray("strokes").toString())
        if (body.optBoolean("custom_remove", false)) {
            e.remove("custom_sticker"); File(context.filesDir, CUSTOM_FILE).delete()
        }
        if (body.has("custom_image")) {
            val file = saveImage(body.getString("custom_image"), CUSTOM_FILE, 1024, Bitmap.CompressFormat.PNG)
                ?: return RemoteActions.Out(false, "تعذر قراءة صورة الملصق")
            e.putString("custom_sticker", Uri.fromFile(file).toString() + "?t=" + System.currentTimeMillis())
            if (!body.has("custom_x")) e.putFloat("custom_x", .72f)
            if (!body.has("custom_y")) e.putFloat("custom_y", .44f)
        }
        e.apply()
        return RemoteActions.Out(true, "حُفظت اللوحة؛ تظهر في صفحة العدادات عند فتحها")
    }

    fun boardApply(): RemoteActions.Out = onMain { art.showBoard() } ?: RemoteActions.Out(false, "تعذر عرض اللوحة")

    fun asset(name: String): RemoteActions.Out {
        if (name != "emblem") return RemoteActions.Out(false, "ملف غير معروف")
        val png = runCatching {
            val d = context.getDrawable(R.drawable.saudi_emblem_gold)!!
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
            val k = 256f / maxOf(w, h)
            val b = Bitmap.createBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, b.width, b.height); d.draw(Canvas(b))
            encode(b, Bitmap.CompressFormat.PNG)
        }.getOrNull() ?: return RemoteActions.Out(false, "تعذر تجهيز الملصق")
        return RemoteActions.Out(true, "ملصق النخلة والسيفين", JSONObject().put("name", name).put("image", png))
    }

    // ---------------- Launcher wallpaper ----------------
    fun wallpaper(body: JSONObject): RemoteActions.Out {
        val prefs = context.getSharedPreferences("launcher_wallpaper", Context.MODE_PRIVATE)
        if (body.optBoolean("clear", false)) {
            prefs.edit().remove("wallpaper_uri").apply()
            File(context.filesDir, WALLPAPER_FILE).delete()
            return RemoteActions.Out(true, "أُزيلت خلفية اللانشر")
        }
        val file = saveImage(body.getString("image"), WALLPAPER_FILE, 2560, Bitmap.CompressFormat.JPEG)
            ?: return RemoteActions.Out(false, "تعذر قراءة الصورة")
        prefs.edit().putString("wallpaper_uri", Uri.fromFile(file).toString() + "?t=" + System.currentTimeMillis()).apply()
        return RemoteActions.Out(true, "تم تعيين خلفية اللانشر")
    }

    private fun customThumb(): String? = runCatching {
        val f = File(context.filesDir, CUSTOM_FILE)
        val b = if (f.exists()) BitmapFactory.decodeFile(f.path) else
            board.getString("custom_sticker", "")?.let { u -> context.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it) } }
        b?.let { encode(scaleDown(it, 256), Bitmap.CompressFormat.PNG) }
    }.getOrNull()

    /** Decodes a phone image, re-encodes it (drops anything that is not an image) and stores it privately. */
    private fun saveImage(base64: String, name: String, maxSide: Int, format: Bitmap.CompressFormat): File? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8000 || bounds.outHeight !in 1..8000) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val bitmap = scaleDown(decoded, maxSide)
        val file = File(context.filesDir, name)
        val tmp = File(context.filesDir, "$name.tmp")
        tmp.outputStream().use { bitmap.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 90, it) }
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) return null }
        file
    }.getOrNull()

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= maxSide) return b
        val k = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    private fun encode(b: Bitmap, format: Bitmap.CompressFormat): String {
        val out = ByteArrayOutputStream(); b.compress(format, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        val r = AtomicReference<T?>()
        val latch = CountDownLatch(1)
        main.post { try { r.set(runCatching(block).getOrNull()) } finally { latch.countDown() } }
        latch.await(5, TimeUnit.SECONDS)
        return r.get()
    }

    fun close() { if (probeUsed) probeControl.close() }

    companion object {
        const val CUSTOM_FILE = "remote_sticker.png"
        const val WALLPAPER_FILE = "remote_wallpaper.jpg"
        /** Same order as the launcher board's font list (index is what is saved). */
        val BOARD_FONT_LABELS = listOf("عربي", "حديث / Modern", "تقليدي / Serif", "يدوي / Handwriting", "أحادي / Mono",
            "القاهرة Cairo", "تجوال Tajawal", "أميري Amiri", "ريم كوفي Reem Kufi", "Lalezar عريض", "Orbitron", "Bebas Neue", "Pacifico")
    }
}
