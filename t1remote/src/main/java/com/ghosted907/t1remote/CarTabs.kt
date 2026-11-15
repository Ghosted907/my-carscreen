package com.ghosted907.t1remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private fun op(name: String) = JSONObject().put("op", name)

// ---------------------------------------------------------------- Car (body controls)
private val carButtons = listOf(
    "النوافذ" to listOf("windows_open" to "فتح", "windows_close" to "إغلاق", "windows_vent" to "تهوية"),
    "فتحة السقف" to listOf("roof_open" to "فتح", "roof_close" to "إغلاق", "roof_tilt" to "رفع"),
    "الستارة" to listOf("shade_open" to "فتح", "shade_close" to "إغلاق", "shade_half" to "نصف")
)
private val oemScreens = listOf("main" to "إعدادات جيتور", "steering" to "المقود", "mirrors" to "المرايا", "windows" to "النوافذ",
    "tailgate" to "الباب الخلفي", "interconnection" to "الربط (CarPlay)", "devices" to "الأجهزة المتصلة", "brightness" to "سطوع الشاشة",
    "dark" to "إيقاف الشاشة")

@Composable
fun CarTab(session: Session) {
    var probe by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("النوافذ والسقف والستارة", "اضغط مطوّلاً ثانية كاملة لتنفيذ الأمر. رفع الإصبع قبلها يلغيه.") {
            if (!session.info.optBoolean("vehicle", true)) Note("أوامر السيارة غير متاحة في هذا اللانشر.", Gold)
            carButtons.forEach { (title, row) ->
                Text(title, color = Ink, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (id, label) ->
                        HoldButton(label, session.connected, Modifier.weight(1f)) { held ->
                            session.send(op(RemoteProtocol.OP_VEHICLE).put("cmd", id).put("held_ms", held))
                        }
                    }
                }
            }
        }
        Section("الباب الخلفي", "فتح/إيقاف الباب الخلفي من الجوال غير متاح: مسار النظام الأصلي لم يُتحقق منه بعد. يفتح هنا شاشة الباب الخلفي على السيارة، وارتفاعه يظهر في تبويب البيانات.") {
            Small("افتح شاشة الباب الخلفي على السيارة", enabled = session.supports(RemoteProtocol.OP_OEM)) {
                session.send(op(RemoteProtocol.OP_OEM).put("target", "tailgate"))
            }
        }
        Section("شاشات النظام", "نفس أزرار صفحة التحكم في اللانشر؛ تفتح على شاشة السيارة.") {
            Flow { oemScreens.forEach { (id, label) ->
                Pill(label, enabled = session.supports(RemoteProtocol.OP_OEM)) { session.send(op(RemoteProtocol.OP_OEM).put("target", id)) }
            } }
        }
        Section("الكاميرا", "بث كاميرا ٣٦٠ للجوال غير ممكن: تطبيق النظام يرسمها على سطح خاص بلا واجهة. يفتحها هنا على شاشة السيارة.") {
            Small("افتح كاميرا ٣٦٠ على السيارة", enabled = session.supports(RemoteProtocol.OP_PAGE)) { session.send(op(RemoteProtocol.OP_PAGE).put("page", "camera")) }
        }
        Section("فحص خدمة السيارة", "قراءة فقط، لا ترسل أي أمر.") {
            Small("افحص", enabled = session.supports(RemoteProtocol.OP_PROBE)) { session.send(op(RemoteProtocol.OP_PROBE)) { probe = session.status } }
            if (probe.isNotEmpty()) Note(probe)
        }
    }
}

/** Fires once after a continuous ~1 s hold. A shorter press does nothing. */
@Composable
fun HoldButton(label: String, enabled: Boolean, modifier: Modifier, onHeld: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    Box(modifier.height(64.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Bg else Bg.copy(alpha = .4f))
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                val pressedAt = System.currentTimeMillis()
                var fired = false
                val job: Job = scope.launch {
                    while (isActive) {
                        val now = System.currentTimeMillis()
                        progress = ((now - pressedAt).toFloat() / HoldPolicy.HOLD_MS).coerceAtMost(1f)
                        if (HoldPolicy.fires(pressedAt, null, now)) { fired = true; onHeld(now - pressedAt); break }
                        delay(16)
                    }
                }
                tryAwaitRelease()
                if (!fired) job.cancel()
                progress = 0f
            })
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxWidth(progress).height(64.dp).background(Gold.copy(alpha = .35f))) }
        Text(label, color = if (enabled) Ink else Muted, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- A/C (owner-approved writes)
private val blow = listOf(1 to "الوجه", 2 to "القدمين", 3 to "الزجاج", 4 to "الوجه والقدمين", 5 to "الزجاج والقدمين")
private val recirc = listOf(1 to "داخلي", 2 to "خارجي")
private val modes = listOf(1 to "اقتصادي", 2 to "مريح", 3 to "قوي")

@Composable
fun ClimateTab(session: Session) {
    val live = remember { mutableStateMapOf<String, Double>() }
    val sent = remember { mutableStateMapOf<String, Double>() }
    var connected by remember { mutableStateOf(false) }
    var inFlight by remember { mutableStateOf(false) }
    val enabled = session.connected && session.supports(RemoteProtocol.OP_CLIMATE)
    LaunchedEffect(session.epoch, session.connected) {
        while (isActive && session.connected && session.supports(RemoteProtocol.OP_CLIMATE_STATE)) {
            if (!inFlight) {
                inFlight = true
                session.send(op(RemoteProtocol.OP_CLIMATE_STATE), quiet = true, onError = { inFlight = false }) { d ->
                    inFlight = false
                    connected = d.optBoolean("connected")
                    val s = d.optJSONObject("state") ?: JSONObject()
                    RemoteProtocol.CLIMATE.keys.forEach { k -> if (s.isNull(k)) live.remove(k) else live[k] = s.optDouble(k) }
                }
            }
            delay(2000)
        }
    }
    fun value(k: String): Double? = live[k] ?: sent[k]
    fun set(item: String, v: Double) {
        val clamped = RemoteProtocol.climateValue(item, v) ?: return
        session.send(op(RemoteProtocol.OP_CLIMATE).put("item", item).put("value", clamped)) { sent[item] = clamped }
    }
    fun toggle(item: String) = set(item, if ((value(item) ?: 0.0) >= 1.0) 0.0 else 1.0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedsUpdate(session)
        if (session.rev3 && !connected) Note("لا تصل قراءات المكيف من السيارة الآن؛ الأوامر تُرسل وتظهر النتيجة على شاشة المكيف.", Gold)
        Section("التشغيل") {
            Flow {
                listOf("power" to "المكيف", "ac" to "A/C", "auto" to "AUTO", "sync" to "مزامنة", "rear_defrost" to "مزيل ضباب الخلفي").forEach { (k, label) ->
                    val on = (value(k) ?: 0.0) >= 1.0
                    Pill("$label ${if (on) "✓" else ""}", selected = on, enabled = enabled) { toggle(k) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TempBox("السائق", value("temp_l"), enabled, Modifier.weight(1f)) { set("temp_l", it) }
            TempBox("الراكب", value("temp_r"), enabled, Modifier.weight(1f)) { set("temp_r", it) }
        }
        Section("سرعة المروحة ${value("fan")?.toInt() ?: "—"}") {
            Flow { (1..7).forEach { lvl -> Pill("$lvl", selected = value("fan")?.toInt() == lvl, enabled = enabled) { set("fan", lvl.toDouble()) } } }
        }
        Section("اتجاه الهواء") { Flow { blow.forEach { (v, l) -> Pill(l, value("blow")?.toInt() == v, enabled) { set("blow", v.toDouble()) } } } }
        Section("تدوير الهواء") { Flow { recirc.forEach { (v, l) -> Pill(l, value("recirc")?.toInt() == v, enabled) { set("recirc", v.toDouble()) } } } }
        Section("وضع المكيف") { Flow { modes.forEach { (v, l) -> Pill(l, value("mode")?.toInt() == v, enabled) { set("mode", v.toDouble()) } } } }
        Small("افتح شاشة المكيف الأصلية على السيارة", enabled = session.supports(RemoteProtocol.OP_SHORTCUT)) {
            session.send(op(RemoteProtocol.OP_SHORTCUT).put("id", "climate"))
        }
        Note("الحرارة ١٦–٣٠ بخطوة ٠٫٥، والمروحة ١–٧. تدفئة المقود والزجاج الأمامي غير متاحة من الجوال.")
    }
}

@Composable
private fun TempBox(title: String, value: Double?, enabled: Boolean, modifier: Modifier, onSet: (Double) -> Unit) {
    val spec = RemoteProtocol.CLIMATE.getValue("temp_l")
    val cur = value ?: 22.0
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(CardColor).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Muted, fontSize = 14.sp)
        Text(value?.let { String.format(Locale.US, "%.1f°", it) } ?: "—", color = Ink, fontSize = 34.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("−", enabled = enabled && cur > spec.min) { onSet(cur - spec.step) }
            Pill("+", enabled = enabled && cur < spec.max) { onSet(cur + spec.step) }
        }
    }
}

// ---------------------------------------------------------------- Vehicle data (read-only)
private data class Reading(val key: String, val label: String, val group: String, val value: String, val unit: String, val status: String)

@Composable
fun DataTab(session: Session) {
    var readings by remember { mutableStateOf(listOf<Reading>()) }
    var summary by remember { mutableStateOf(JSONObject()) }
    var liveOnly by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var inFlight by remember { mutableStateOf(false) }
    LaunchedEffect(session.epoch, session.connected) {
        while (isActive && session.connected && session.supports(RemoteProtocol.OP_DATA)) {
            if (!inFlight) {
                inFlight = true
                session.send(op(RemoteProtocol.OP_DATA), quiet = true, onError = { inFlight = false }) { d ->
                    inFlight = false
                    summary = d.optJSONObject("summary") ?: JSONObject()
                    val a = d.optJSONArray("readings")
                    readings = (0 until (a?.length() ?: 0)).mapNotNull { i -> a!!.optJSONObject(i)?.let {
                        Reading(it.optString("key"), it.optString("label"), it.optString("group"), it.optString("value"), it.optString("unit"), it.optString("status"))
                    } }
                }
            }
            delay(2000)
        }
    }
    val shown = readings.filter { (!liveOnly || it.status == "LIVE") && (query.isBlank() || it.label.contains(query, true) || it.group.contains(query, true)) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeedsUpdate(session)
                Note("قراءة فقط · تتحدّث كل ثانيتين. حية ${summary.optInt("live")} من ${summary.optInt("total")}" +
                    (if (summary.optBoolean("avm")) " · كاميرا ٣٦٠ مفتوحة" else ""))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(30) }, singleLine = true, label = { Text("بحث") }, modifier = Modifier.weight(1f))
                    Pill(if (liveOnly) "الحية فقط" else "الكل", selected = liveOnly) { liveOnly = !liveOnly }
                }
            }
        }
        var lastGroup = ""
        shown.sortedBy { it.group }.forEach { r ->
            if (r.group != lastGroup) { lastGroup = r.group; val g = r.group; item(key = "g_$g") { Text(g, color = Gold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) } }
            item(key = r.key) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardColor).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${r.value} ${r.unit}".trim(), color = if (r.status == "LIVE") Good else Muted, fontSize = 14.sp)
                }
            }
        }
    }
}
