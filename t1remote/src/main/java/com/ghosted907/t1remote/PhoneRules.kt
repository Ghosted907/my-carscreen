package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONObject
import kotlin.math.abs

/**
 * Pure phone-side rules (no Android types), unit-tested on the JVM:
 * reading freshness, A/C sent-vs-confirmed tracking, the one-at-a-time vehicle gate,
 * the Riyadh greeting and the on-demand voice whitelist.
 */

/** One vehicle reading as sent by OP_DATA. Fields added in rev 4 are optional, so rev-3 replies still parse. */
data class Reading(
    val key: String, val label: String, val group: String, val value: String, val unit: String, val status: String,
    val statusLabel: String = "", val raw: Double? = null, val ageMs: Long? = null, val last: String? = null,
    val confirmed: Boolean = false, val src: String = "", val detail: String = ""
) {
    val live get() = status == "LIVE"

    /** What the phone shows. Never a zero or a placeholder number for a missing reading. */
    fun display(): String = when {
        live -> "$value $unit".trim()
        last != null -> "$last $unit".trim() + " (قديمة)"
        else -> Freshness.statusText(status)
    }

    companion object {
        fun parse(o: JSONObject): Reading = Reading(
            o.optString("key"), o.optString("label"), o.optString("group"), o.optString("value"), o.optString("unit"),
            o.optString("status"), o.optString("status_label"),
            if (o.has("raw") && !o.isNull("raw")) o.optDouble("raw").takeIf { it.isFinite() } else null,
            if (o.has("age_ms")) o.optLong("age_ms") else null,
            if (o.has("last")) o.optString("last") else null,
            o.optBoolean("confirmed"), o.optString("src"), o.optString("detail"))
    }
}

object Freshness {
    fun statusText(status: String): String = when (status) {
        "LIVE" -> "مباشر"
        "STALE" -> "توقف التحديث"
        "WAITING" -> "بانتظار القراءة"
        "INVALID" -> "قيمة غير صالحة"
        "ERROR" -> "تعذرت القراءة"
        else -> "غير متاح"
    }

    /** "منذ ٣ ث" style age (Western digits for numbers, Arabic words). Null when the car sent no sample time. */
    fun age(ms: Long?): String? = when {
        ms == null || ms < 0 -> null
        ms < 1500 -> "الآن"
        ms < 60_000 -> "منذ ${ms / 1000} ث"
        ms < 3_600_000 -> "منذ ${ms / 60_000} د"
        else -> "منذ أكثر من ساعة"
    }

    /** Status line for a reading: state plus age when known. */
    fun line(r: Reading): String = listOfNotNull(r.statusLabel.ifEmpty { statusText(r.status) }, age(r.ageMs)).joinToString(" · ")

    /** The reply itself is old when the phone has not had a fresh one for a while (connection hiccup). */
    fun replyStale(receivedAt: Long, now: Long, limitMs: Long = 6000): Boolean = receivedAt <= 0 || now - receivedAt > limitMs
}

/** Tyre pressure: VehicleService keys report bar; the launcher shows psi (OffRoadPresentation.psi). */
object Tyres {
    const val BAR_TO_PSI = 14.5037738
    fun psi(bar: Double?): Double? = bar?.takeIf { it.isFinite() && it in 0.1..6.0 }?.times(BAR_TO_PSI)
    /** Positions around the top-view image (front at the top): fl top-left, fr top-right, rl bottom-left, rr bottom-right. */
    val ORDER = listOf("fl", "fr", "rl", "rr")
    val LABELS = mapOf("fl" to "أمامي أيسر", "fr" to "أمامي أيمن", "rl" to "خلفي أيسر", "rr" to "خلفي أيمن")
}

/**
 * A/C: what was SENT is kept apart from what the car CONFIRMS by reading back.
 * Each send gets a generation; a state reply requested before the latest send of an item is ignored
 * for that item, so an old reply never undoes a newer change.
 */
class ClimateSync(private val confirmMs: Long = 6000) {
    enum class State { UNKNOWN, LIVE, PENDING, CONFIRMED, UNCONFIRMED }
    data class Pending(val value: Double, val gen: Long, val sentAt: Long)

    private var gen = 0L
    private val pending = HashMap<String, Pending>()
    private val unconfirmed = HashMap<String, Double>()
    private val confirmedAt = HashMap<String, Long>()
    val live = HashMap<String, Double>()

    fun stamp(): Long = gen

    /** Before sending [value]; returns the generation of this send. */
    fun beginSend(item: String, value: Double, now: Long): Long {
        gen++
        pending[item] = Pending(value, gen, now)
        unconfirmed.remove(item); confirmedAt.remove(item)
        return gen
    }

    /** The launcher refused or the connection dropped: nothing is pending any more (and nothing is re-sent). */
    fun sendFailed(item: String, g: Long) { if (pending[item]?.gen == g) pending.remove(item) }

    /** A climate_state reply for a request made when stamp() was [requestStamp]. */
    fun onState(requestStamp: Long, state: Map<String, Double?>, now: Long) {
        state.forEach { (k, v) ->
            val p = pending[k]
            if (p != null && p.gen > requestStamp) return@forEach   // older than the latest change: ignore for k
            if (v == null) live.remove(k) else live[k] = v
            if (p != null) when {
                v != null && abs(v - p.value) < 1e-6 -> { pending.remove(k); confirmedAt[k] = now }
                now - p.sentAt > confirmMs -> { pending.remove(k); unconfirmed[k] = p.value }
            }
        }
    }

    /** Value to show: the pending one while waiting, else the read-back. Null = unknown (never a default). */
    fun shown(item: String): Double? = pending[item]?.value ?: live[item]

    fun state(item: String, now: Long): State = when {
        pending.containsKey(item) -> State.PENDING
        confirmedAt[item]?.let { now - it < 4000 } == true -> State.CONFIRMED
        unconfirmed.containsKey(item) && live[item] != unconfirmed[item] -> State.UNCONFIRMED
        live.containsKey(item) -> State.LIVE
        else -> State.UNKNOWN
    }

    fun label(item: String, now: Long): String = when (state(item, now)) {
        State.PENDING -> "أُرسل · بانتظار تأكيد السيارة"
        State.CONFIRMED -> "تأكدت من قراءة السيارة"
        State.UNCONFIRMED -> "أُرسل ولم تؤكده القراءة"
        State.LIVE -> "قراءة السيارة"
        State.UNKNOWN -> "غير متاح"
    }

    fun clearOnDisconnect() { pending.clear(); live.clear(); unconfirmed.clear(); confirmedAt.clear() }
}

/**
 * Body commands (windows / roof / shade): one at a time. While one is waiting for the launcher's reply all
 * buttons are locked; a dropped connection never re-sends (the command may already have run on the car).
 */
class VehicleGate {
    var pending: String? = null
        private set

    fun tryStart(id: String): Boolean {
        if (pending != null || id !in RemoteProtocol.VEHICLE_IDS) return false
        pending = id
        return true
    }

    fun finish() { pending = null }

    /** Returns the warning to show when the link dropped mid-command, or null when nothing was pending. */
    fun onDisconnect(): String? {
        val id = pending ?: return null
        pending = null
        return lostMessage(id)
    }

    companion object {
        fun lostMessage(id: String) = "انقطع الاتصال أثناء أمر «$id». قد يكون نُفّذ على السيارة؛ تحقق منها. لن يُعاد الإرسال تلقائياً."
    }
}

object Compass {
    private val names = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")
    /** Heading in degrees (any range) -> one of eight Arabic directions; null for a missing or broken value. */
    fun label(deg: Double?): String? {
        if (deg == null || !deg.isFinite()) return null
        val d = ((deg % 360) + 360) % 360
        return names[((d + 22.5) / 45).toInt() % 8]
    }
}

object Greeting {
    /** Riyadh hour 0..23 -> greeting, same idea as the launcher clock card. */
    fun forHour(hour: Int): String = when (hour) {
        in 4..11 -> "صباح الخير"
        in 12..23 -> "مساء الخير"
        else -> "مساء الخير"
    }
}

/**
 * Optional phone voice (on demand, system recogniser, no background recording). Mirrors the launcher's
 * handleVoice whitelist (camera, classic, hide add-ons, YouTube, Waze) plus a few pages. Never a vehicle,
 * tailgate or A/C write; the user confirms before anything is sent.
 */
object VoiceMap {
    data class Match(val label: String, val body: JSONObject)

    fun match(text: String): Match? {
        val t = text.trim()
        if (t.isEmpty()) return null
        fun op(name: String) = JSONObject().put("op", name)
        return when {
            t.contains("٣٦٠") || t.contains("360") || t.contains("كاميرا") ->
                Match("فتح كاميرا ٣٦٠ على شاشة السيارة", op(RemoteProtocol.OP_PAGE).put("page", "camera"))
            t.contains("كلاسيك") -> Match("قالب العدادات الكلاسيكي", op(RemoteProtocol.OP_CLUSTER).put("action", "classic").put("value", 0))
            t.contains("إخفاء") || t.contains("اخفاء") -> Match("إخفاء إضافات T1 على العدادات", op(RemoteProtocol.OP_CLUSTER).put("action", "hide_addons").put("value", 0))
            t.contains("يوتيوب") || t.contains("YouTube", true) -> Match("فتح YouTube على شاشة السيارة", op(RemoteProtocol.OP_SHORTCUT).put("id", "youtube"))
            t.contains("ويز") || t.contains("Waze", true) -> Match("فتح Waze على شاشة السيارة", op(RemoteProtocol.OP_SHORTCUT).put("id", "waze"))
            t.contains("كاربلي") || t.contains("كار بلاي") || t.contains("CarPlay", true) -> Match("فتح CarPlay على شاشة السيارة", op(RemoteProtocol.OP_SHORTCUT).put("id", "carplay"))
            t.contains("بلوتوث") -> Match("فتح البلوتوث على شاشة السيارة", op(RemoteProtocol.OP_SHORTCUT).put("id", "bluetooth"))
            t.contains("مكيف") || t.contains("تكييف") -> Match("فتح صفحة المكيف على شاشة السيارة", op(RemoteProtocol.OP_PAGE).put("page", "climate"))
            t.contains("رئيسي") -> Match("فتح رئيسية T1 على شاشة السيارة", op(RemoteProtocol.OP_PAGE).put("page", "home"))
            else -> null
        }
    }
}
