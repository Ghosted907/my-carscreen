package com.ghosted907.t1remote.protocol

import org.json.JSONObject

/**
 * Wire protocol shared by the T1 launcher (server) and the T1 Remote phone app (client).
 * Pure JVM code (org.json only) so it is unit-tested without Android.
 *
 * Transport: TCP on the same Wi-Fi, one UTF-8 JSON object per line. No pairing or keys:
 * anyone on the same network can send commands while the launcher's switch is on.
 *  server -> {"t":"hello","v":1,"name":..,"host":<launcher id>}
 *  client -> {"t":"cmd","seq":n,"body":{"op":..}}
 *  server -> {"t":"res","seq":n,"ok":bool,"msg":..,"data":{..}}
 * Vehicle writes stay limited to VEHICLE_IDS (nine body commands, long press) and the A/C items in
 * CLIMATE. There is no tailgate write: its OEM route is not validated, the phone only opens the OEM screen.
 */
object RemoteProtocol {
    const val VERSION = 2
    const val PORT = 47231
    const val SERVICE_TYPE = "_t1remote._tcp."
    const val SERVICE_NAME = "T1 Launcher"
    const val MAX_LINE = 1024 * 1024
    const val HOLD_MS = 1000L

    const val OP_PING = "ping"
    const val OP_APPS = "apps"
    const val OP_TASKS = "tasks"
    const val OP_OPEN = "open"
    const val OP_MOVE = "move"
    const val OP_FRONT = "front"
    const val OP_CLOSE = "close"
    const val OP_RESIZE = "resize"
    const val OP_VEHICLE = "vehicle"
    const val OP_ART = "art"
    const val OP_ART_CLEAR = "art_clear"

    // Revision 3 (T1 Remote 1.3.0): full launcher parity. VERSION stays 2 so older phones and
    // launchers still talk; a launcher advertises what it supports in ping data ("rev", "ops").
    const val REV = 4
    const val OP_SHORTCUT = "shortcut"        // Home cards: CarPlay, YouTube, Waze, Bluetooth, A/C app (no Wi-Fi)
    const val OP_PAGE = "page"                // open a launcher page on the car screen
    const val OP_OEM = "oem"                  // open an OEM / system screen the launcher already opens
    const val OP_CLUSTER = "cluster"          // cluster page buttons (classic, theme, font, hide add-ons ...)
    const val OP_RUN = "run"                  // one-shot launcher buttons from settings (test task group ...)
    const val OP_ACTIONS = "actions"          // shortcut-action catalog for pickers
    const val OP_DATA = "data"                // read-only vehicle readings
    const val OP_CLIMATE = "climate"          // A/C write (approved by the owner 2026-11-15)
    const val OP_CLIMATE_STATE = "climate_state"
    const val OP_SETTINGS = "settings"        // read all launcher settings
    const val OP_SET = "set"                  // change one launcher setting
    const val OP_PIN = "pin"                  // home favourites
    const val OP_BOARD = "board"              // cluster art board (text, drawing, stickers) state
    const val OP_BOARD_SET = "board_set"
    const val OP_BOARD_APPLY = "board_apply"
    const val OP_ASSET = "asset"              // launcher sticker artwork for the phone editor
    const val OP_PROBE = "vehicle_probe"      // read-only service check
    const val OP_WALLPAPER = "wallpaper"

    // Revision 4 (T1 Remote 1.4.0 / T1 Launcher 1.16.0). All read-only except OP_NOTE (launcher text, no vehicle).
    // A phone checks "rev" >= REV_4 or the op in "ops" before using any of these.
    const val REV_3 = 3
    const val REV_4 = 4
    const val OP_HOME = "home"                // home summary: saved note, shortcut availability, media title
    const val OP_NOTE = "note"                // save the home note ("ملاحظاتي"), text only
    const val OP_OFFROAD = "offroad"          // pitch / roll / heading / altitude measured on the car, with age and source
    const val OP_OEM_PROPS = "oem_props"      // OEM property catalog search; optional read of one page (never a write)

    val OPS = setOf(OP_PING, OP_APPS, OP_TASKS, OP_OPEN, OP_MOVE, OP_FRONT, OP_CLOSE, OP_RESIZE, OP_VEHICLE, OP_ART, OP_ART_CLEAR,
        OP_SHORTCUT, OP_PAGE, OP_OEM, OP_CLUSTER, OP_RUN, OP_ACTIONS, OP_DATA, OP_CLIMATE, OP_CLIMATE_STATE,
        OP_SETTINGS, OP_SET, OP_PIN, OP_BOARD, OP_BOARD_SET, OP_BOARD_APPLY, OP_ASSET, OP_PROBE, OP_WALLPAPER,
        OP_HOME, OP_NOTE, OP_OFFROAD, OP_OEM_PROPS)

    /** Ops a launcher of revision 3 (1.15.x) answers; anything else needs REV_4. */
    val OPS_REV3 = OPS - setOf(OP_HOME, OP_NOTE, OP_OFFROAD, OP_OEM_PROPS)
    const val MAX_NOTE = 2000
    const val MAX_QUERY = 60
    /** Same page size as the launcher's OEM catalog grid (VehiclePage). */
    const val OEM_PAGE = 24

    /** Home cards. Wi-Fi is deliberately not reachable from the phone. */
    val SHORTCUTS = listOf("carplay", "youtube", "waze", "bluetooth", "climate")
    val PAGES = listOf("home", "apps", "cluster", "camera", "vehicle", "controls", "climate", "customize", "settings",
        "windows", "permissions", "installer")
    val OEM_TARGETS = listOf("main", "steering", "mirrors", "windows", "tailgate", "interconnection", "dark",
        "devices", "brightness", "overlay", "accessibility", "listener")
    val CLUSTER_ACTIONS = listOf("classic", "hide_addons", "stock", "theme", "font", "close_app")
    val RUN_IDS = listOf("floating_reset", "tasks_test", "black_test", "split_open_1", "split_open_2", "split_open_3",
        "cluster_open_cluster", "cluster_open_main", "cluster_move",
        // rev 4: the launcher's own JctEnum actions (same JctLaunch.run the floating button and edge zones call).
        "media_play_pause", "media_next", "media_prev", "recent_apps", "app_menu", "global_home", "global_back")
    /** RUN_IDS added in rev 4 (a rev-3 launcher answers "أمر غير معروف"). */
    val RUN_IDS_REV4 = setOf("media_play_pause", "media_next", "media_prev", "recent_apps", "app_menu", "global_home", "global_back")
    val ASSETS = listOf("emblem")
    const val MAX_IMAGE = 900000

    /** A/C items and their accepted ranges (same table drives the launcher clamp). */
    data class ClimateSpec(val min: Double, val max: Double, val step: Double)
    val CLIMATE = linkedMapOf(
        "power" to ClimateSpec(0.0, 1.0, 1.0),
        "ac" to ClimateSpec(0.0, 1.0, 1.0),
        "auto" to ClimateSpec(0.0, 1.0, 1.0),
        "sync" to ClimateSpec(0.0, 1.0, 1.0),
        "rear_defrost" to ClimateSpec(0.0, 1.0, 1.0),
        "recirc" to ClimateSpec(1.0, 2.0, 1.0),       // 1 inside, 2 outside
        "blow" to ClimateSpec(1.0, 5.0, 1.0),         // face, foot, defrost, face+foot, defrost+foot
        "mode" to ClimateSpec(1.0, 3.0, 1.0),         // eco, comfort, strong
        "fan" to ClimateSpec(1.0, 7.0, 1.0),
        "temp_l" to ClimateSpec(16.0, 30.0, 0.5),
        "temp_r" to ClimateSpec(16.0, 30.0, 0.5)
    )

    /** Clamp to the range and snap to the step. Pure, so the launcher UI and server share it. */
    fun climateValue(item: String, value: Double): Double? {
        val spec = CLIMATE[item] ?: return null
        if (!value.isFinite()) return null
        val c = value.coerceIn(spec.min, spec.max)
        val snapped = spec.min + Math.round((c - spec.min) / spec.step) * spec.step
        return snapped.coerceIn(spec.min, spec.max)
    }

    /** Launcher settings reachable from the phone. Wi-Fi, device owner, app hiding and PINs are absent. */
    sealed class Setting {
        object Bool : Setting()
        data class Range(val min: Int, val max: Int) : Setting()
        data class Choice(val values: List<String>) : Setting()
        object Pkg : Setting()
        object Action : Setting()
        object Split : Setting()
        object Tasks : Setting()
    }
    val ZONES = listOf("LEFT_TOP", "LEFT_BOTTOM", "RIGHT_TOP", "RIGHT_BOTTOM", "BOTTOM_LEFT", "BOTTOM_RIGHT")
    val SETTINGS: Map<String, Setting> = linkedMapOf<String, Setting>(
        "day" to Setting.Bool,
        "dest_cluster" to Setting.Bool,
        "language" to Setting.Choice(listOf("ar", "en", "system")),
        "cluster_pkg" to Setting.Pkg,
        "cluster_boot" to Setting.Bool,
        "gu_index" to Setting.Range(-1, 8),
        "pri_index" to Setting.Range(-1, 8),
        "floating_style" to Setting.Choice(listOf("0", "1", "2", "4")),
        "floating_action" to Setting.Action,
        "over_system" to Setting.Bool,
        "tasks_boot" to Setting.Bool,
        "tasks_delay" to Setting.Range(1, 20),
        "tasks" to Setting.Tasks,
        "split_1" to Setting.Split, "split_2" to Setting.Split, "split_3" to Setting.Split,
        "zone_width" to Setting.Range(75, 150),
        "zone_sensitivity" to Setting.Range(75, 150),
        "black_clock" to Setting.Bool,
        "black_clock24" to Setting.Bool,
        "black_brightness" to Setting.Range(1, 100)
    ).apply {
        ZONES.forEach { z ->
            put("zone_on_$z", Setting.Bool)
            put("zone_style_$z", Setting.Range(0, 4))
            put("zone_action_$z", Setting.Action)
        }
    }

    private val actionRegex = Regex("^(enum:[A-Za-z_]{1,40}|app:[A-Za-z][A-Za-z0-9_.]{1,200}|profile:[A-Za-z0-9_]{1,60}|split:[A-Za-z0-9_.]{3,200},[A-Za-z0-9_.]{3,200},[12],(30|50|70))$")
    fun validAction(raw: String?): Boolean = raw != null && raw.length <= 420 && actionRegex.matches(raw)

    /** Reason when [value] does not fit setting [key]; null when acceptable. */
    fun validateSetting(key: String, value: Any?): String? {
        val spec = SETTINGS[key] ?: return "إعداد غير معروف"
        return when (spec) {
            Setting.Bool -> if (value is Boolean) null else "القيمة يجب أن تكون تشغيل أو إيقاف"
            is Setting.Range -> if (value is Number && value.toDouble() % 1.0 == 0.0 && value.toInt() in spec.min..spec.max) null else "القيمة خارج المدى"
            is Setting.Choice -> if (value?.toString() in spec.values) null else "خيار غير معروف"
            Setting.Pkg -> if (value is String && (value.isEmpty() || validPackage(value))) null else "اسم التطبيق غير صالح"
            Setting.Action -> if (value is String && validAction(value)) null else "الإجراء غير صالح"
            Setting.Split -> if (value is String && (value.isEmpty() || (value.startsWith("split:") && validAction(value)))) null else "التخطيط غير صالح"
            Setting.Tasks -> {
                val a = value as? org.json.JSONArray ?: return "قائمة المهام غير صالحة"
                if (a.length() > 20) return "المهام أكثر من ٢٠"
                for (i in 0 until a.length()) {
                    val t = a.optJSONObject(i) ?: return "قائمة المهام غير صالحة"
                    if (!validAction(t.optString("action"))) return "إجراء مهمة غير صالح"
                }
                null
            }
        }
    }

    /** Art-board fields the phone may change, each with its accepted range. */
    val BOARD_FLOATS = mapOf(
        "caption_x" to (0.0 to 0.70), "caption_y" to (0.32 to 0.85), "caption_size" to (16.0 to 120.0),
        "sticker_x" to (0.0 to 1.0), "sticker_y" to (0.0 to 1.0), "sticker_size" to (40.0 to 240.0),
        "custom_x" to (0.0 to 1.0), "custom_y" to (0.0 to 1.0), "custom_size" to (40.0 to 240.0)
    )
    const val BOARD_FONTS = 13
    const val MAX_STROKES = 300
    const val MAX_POINTS = 1500

    const val SCREEN_MAIN = "main"
    const val SCREEN_CLUSTER = "cluster"
    val SCREENS = setOf(SCREEN_MAIN, SCREEN_CLUSTER)

    /** Resize presets, applied by the launcher's existing freeform dock (only if freeform is on). */
    val RESIZE_PRESETS = listOf("full", "right_half", "left_half", "center")

    /**
     * The nine launcher commands already approved. Ids are the launcher's VehicleCommands ids.
     * Tailgate is deliberately absent and can never pass validation.
     */
    val VEHICLE_IDS = listOf(
        "windows_open", "windows_close", "windows_vent",
        "roof_open", "roof_close", "roof_tilt",
        "shade_open", "shade_close", "shade_half"
    )

    private val pkgRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val hexChars = "0123456789abcdef"

    fun randomHex(bytes: Int): String {
        val r = java.security.SecureRandom()
        return (1..bytes * 2).map { hexChars[r.nextInt(16)] }.joinToString("")
    }

    fun validPackage(pkg: String?): Boolean = !pkg.isNullOrEmpty() && pkg.length <= 200 && pkgRegex.matches(pkg)

    // ---- message builders ----
    fun hello(name: String, host: String): JSONObject = JSONObject()
        .put("t", "hello").put("v", VERSION).put("name", name).put("host", host)

    fun command(seq: Long, body: JSONObject): JSONObject =
        JSONObject().put("t", "cmd").put("seq", seq).put("body", body)

    fun result(seq: Long, ok: Boolean, msg: String, data: JSONObject? = null): JSONObject = JSONObject()
        .put("t", "res").put("seq", seq).put("ok", ok).put("msg", msg).also { if (data != null) it.put("data", data) }

    /** Server side: parses one line into an allowed command body, or a rejection reason. */
    sealed class Parsed {
        data class Ok(val seq: Long, val body: JSONObject) : Parsed()
        data class Rejected(val seq: Long, val msg: String) : Parsed()
    }

    fun parse(line: String): Parsed {
        val env = runCatching { JSONObject(line) }.getOrNull() ?: return Parsed.Rejected(0, "رسالة غير صالحة")
        val seq = env.optLong("seq", 0L)
        if (env.optString("t") != "cmd") return Parsed.Rejected(seq, "رسالة غير معروفة")
        val body = env.optJSONObject("body") ?: return Parsed.Rejected(seq, "رسالة غير صالحة")
        validate(body)?.let { return Parsed.Rejected(seq, it) }
        return Parsed.Ok(seq, body)
    }

    /** Validates a command body. Returns null when allowed, otherwise an Arabic reason. */
    fun validate(body: JSONObject): String? {
        val op = body.optString("op")
        if (op !in OPS) return "أمر غير معروف"
        return when (op) {
            OP_PING, OP_APPS, OP_TASKS, OP_ART_CLEAR, OP_ACTIONS, OP_DATA, OP_CLIMATE_STATE, OP_SETTINGS,
            OP_BOARD, OP_BOARD_APPLY, OP_PROBE, OP_HOME, OP_OFFROAD -> null
            OP_NOTE -> when (val t = body.opt("text")) {
                !is String -> "الملاحظة غير صالحة"
                else -> if (t.length > MAX_NOTE) "الملاحظة أطول من $MAX_NOTE حرف" else null
            }
            OP_OEM_PROPS -> when {
                body.has("query") && body.opt("query") !is String -> "البحث غير صالح"
                body.optString("query").length > MAX_QUERY -> "البحث طويل جداً"
                body.has("page") && (body.opt("page") !is Number || body.optInt("page", -1) !in 0..1000) -> "رقم الصفحة غير صالح"
                body.has("watch") && body.opt("watch") !is Boolean -> "قيمة القراءة غير صالحة"
                else -> null
            }
            OP_ART -> when {
                body.optString("image").length !in 1..MAX_IMAGE -> "حجم الصورة غير صالح"
                !body.optDouble("x").let { it.isFinite() && it in 0.0..1.0 } -> "الموضع غير صالح"
                !body.optDouble("y").let { it.isFinite() && it in 0.0..1.0 } -> "الموضع غير صالح"
                else -> null
            }
            OP_OPEN, OP_MOVE -> when {
                !validPackage(body.optString("pkg")) -> "اسم التطبيق غير صالح"
                body.optString("screen") !in SCREENS -> "الشاشة غير صالحة"
                else -> null
            }
            OP_FRONT, OP_CLOSE -> if (!validPackage(body.optString("pkg"))) "اسم التطبيق غير صالح" else null
            OP_RESIZE -> when {
                !validPackage(body.optString("pkg")) -> "اسم التطبيق غير صالح"
                body.optString("preset") !in RESIZE_PRESETS -> "مقاس غير معروف"
                else -> null
            }
            OP_VEHICLE -> when {
                body.optString("cmd") !in VEHICLE_IDS -> "أمر السيارة غير مسموح"
                body.optLong("held_ms", 0L) < HOLD_MS -> "يلزم ضغط مطوّل ثانية كاملة"
                else -> null
            }
            OP_SHORTCUT -> if (body.optString("id") !in SHORTCUTS) "اختصار غير معروف" else null
            OP_PAGE -> if (body.optString("page") !in PAGES) "صفحة غير معروفة" else null
            OP_OEM -> if (body.optString("target") !in OEM_TARGETS) "شاشة غير معروفة" else null
            OP_CLUSTER -> when (body.optString("action")) {
                "theme" -> if (body.optInt("value", 0) in 1..3) null else "قالب غير معروف"
                "font" -> if (body.optInt("value", 0) in 1..2) null else "خط غير معروف"
                in CLUSTER_ACTIONS -> null
                else -> "أمر العدادات غير معروف"
            }
            OP_RUN -> if (body.optString("id") !in RUN_IDS) "أمر غير معروف" else null
            OP_CLIMATE -> {
                val item = body.optString("item")
                val v = body.optDouble("value")
                when {
                    item !in CLIMATE -> "أمر التكييف غير معروف"
                    !v.isFinite() -> "قيمة التكييف غير صالحة"
                    else -> null
                }
            }
            OP_SET -> validateSetting(body.optString("key"), body.opt("value"))
            OP_PIN -> if (!validPackage(body.optString("pkg")) || !body.has("on")) "اسم التطبيق غير صالح" else null
            OP_BOARD_SET -> validateBoard(body)
            OP_ASSET -> if (body.optString("name") !in ASSETS) "ملف غير معروف" else null
            OP_WALLPAPER -> if (body.optBoolean("clear", false) || body.optString("image").length in 1..MAX_IMAGE) null
                else "حجم الصورة غير صالح"
            else -> "أمر غير معروف"
        }
    }

    private fun validateBoard(body: JSONObject): String? {
        BOARD_FLOATS.forEach { (k, r) ->
            if (body.has(k)) { val v = body.optDouble(k); if (!v.isFinite() || v < r.first || v > r.second) return "قيمة اللوحة خارج المدى" }
        }
        if (body.has("caption") && body.optString("caption").length > 120) return "النص أطول من ١٢٠ حرفاً"
        if (body.has("caption_font") && body.optInt("caption_font", -1) !in 0 until BOARD_FONTS) return "خط غير معروف"
        if (body.has("custom_image") && body.optString("custom_image").length !in 1..MAX_IMAGE) return "حجم الصورة غير صالح"
        listOf("sticker", "custom_remove", "strokes_clear").forEach { k ->
            if (body.has(k) && body.opt(k) !is Boolean) return "قيمة اللوحة غير صالحة"
        }
        listOf("caption_color", "draw_color").forEach { k ->
            if (body.has(k) && body.opt(k) !is Number) return "لون غير صالح"
        }
        if (body.has("strokes")) {
            val a = body.optJSONArray("strokes") ?: return "الرسم غير صالح"
            if (a.length() > MAX_STROKES) return "الرسم كبير جداً"
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: return "الرسم غير صالح"
                val pts = s.optJSONArray("points") ?: return "الرسم غير صالح"
                if (pts.length() > MAX_POINTS) return "الرسم كبير جداً"
                for (j in 0 until pts.length()) {
                    val p = pts.optJSONArray(j) ?: return "الرسم غير صالح"
                    val x = p.optDouble(0); val y = p.optDouble(1)
                    if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) return "الرسم غير صالح"
                }
            }
        }
        return null
    }

    /** Freeform bounds for a preset on a main display of [w]x[h]. Pure, so it is tested. */
    fun presetBounds(preset: String, w: Int, h: Int): IntArray? = when (preset) {
        "right_half" -> intArrayOf(w / 2, 0, w, h)
        "left_half" -> intArrayOf(0, 0, w / 2, h)
        "center" -> intArrayOf(w / 6, h / 8, w - w / 6, h - h / 8)
        else -> null // "full" means fullscreen, no bounds
    }
}
