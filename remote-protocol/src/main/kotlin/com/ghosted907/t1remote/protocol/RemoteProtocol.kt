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
    val OPS = setOf(OP_PING, OP_APPS, OP_TASKS, OP_OPEN, OP_MOVE, OP_FRONT, OP_CLOSE, OP_RESIZE, OP_VEHICLE, OP_ART, OP_ART_CLEAR)

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
            OP_PING, OP_APPS, OP_TASKS, OP_ART_CLEAR -> null
            OP_ART -> when {
                body.optString("image").length !in 1..900000 -> "حجم الصورة غير صالح"
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
            else -> "أمر غير معروف"
        }
    }

    /** Freeform bounds for a preset on a main display of [w]x[h]. Pure, so it is tested. */
    fun presetBounds(preset: String, w: Int, h: Int): IntArray? = when (preset) {
        "right_half" -> intArrayOf(w / 2, 0, w, h)
        "left_half" -> intArrayOf(0, 0, w / 2, h)
        "center" -> intArrayOf(w / 6, h / 8, w - w / 6, h - h / 8)
        else -> null // "full" means fullscreen, no bounds
    }
}
