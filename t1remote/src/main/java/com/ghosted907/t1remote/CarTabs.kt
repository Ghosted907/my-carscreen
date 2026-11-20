package com.ghosted907.t1remote

import android.os.SystemClock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ---------------------------------------------------------------- Car (body controls)
private val carButtons = listOf(
    "النوافذ" to listOf("windows_open" to "فتح", "windows_close" to "إغلاق", "windows_vent" to "تهوية"),
    "فتحة السقف" to listOf("roof_open" to "فتح", "roof_close" to "إغلاق", "roof_tilt" to "رفع"),
    "الستارة" to listOf("shade_open" to "فتح", "shade_close" to "إغلاق", "shade_half" to "نصف")
)
private val oemScreens = listOf("main" to "إعدادات جيتور", "steering" to "المقود", "mirrors" to "المرايا", "windows" to "النوافذ",
    "tailgate" to "الباب الخلفي", "interconnection" to "الربط (CarPlay)", "devices" to "الأجهزة المتصلة", "brightness" to "سطوع الشاشة",
    "dark" to "إيقاف الشاشة")
private val bodyStatus = listOf("door_fl", "door_fr", "door_rl", "door_rr", "window_fl", "window_fr", "window_rl", "window_rr",
    "sunroof", "sunroof_position", "sunshade")

@Composable
private fun ReadingLine(live: LiveData, key: String, fallbackLabel: String? = null) {
    val r = live[key]
    val shown = r?.takeIf { !live.stale() }
    ValueLine(r?.label ?: fallbackLabel ?: key, shown?.display() ?: "غير متاح", shown?.live == true,
        shown?.let { Freshness.line(it) } ?: if (r != null) "القراءات متوقفة" else "")
}

@Composable
fun CarTab(session: Session) {
    val live = rememberLive(session)
    val gate = remember { VehicleGate() }
    var pending by remember { mutableStateOf<String?>(null) }
    var warn by remember { mutableStateOf("") }
    var probe by remember { mutableStateOf("") }
    LaunchedEffect(session.drops) { gate.onDisconnect()?.let { warn = it }; pending = null }
    val vehicleOn = session.info.optBoolean("vehicle", true)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("النوافذ والسقف والستارة", "اضغط مطوّلاً ثانية كاملة. أمر واحد في كل مرة، ولا يُكرَّر ولا يُعاد إرساله تلقائياً بعد انقطاع.") {
            if (!vehicleOn) Note("أوامر السيارة غير متاحة: فعّلها من إعدادات T1 على السيارة.", Warn)
            pending?.let { Note("بانتظار رد السيارة على «$it»… الأزرار مقفلة حتى يصل الرد.", Warn) }
            if (warn.isNotEmpty()) { Note(warn, Bad); Small("فهمت") { warn = "" } }
            carButtons.forEach { (title, row) ->
                Text(title, color = Ink, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (id, label) ->
                        HoldButton(label, session.connected && vehicleOn && pending == null, Modifier.weight(1f)) { held ->
                            if (!gate.tryStart(id)) return@HoldButton
                            pending = id
                            session.send(op(RemoteProtocol.OP_VEHICLE).put("cmd", id).put("held_ms", held),
                                onError = { gate.finish(); pending = null },
                                onLost = { warn = VehicleGate.lostMessage(id) }) { gate.finish(); pending = null }
                        }
                    }
                }
            }
        }
        Section("حالة الأبواب والنوافذ والسقف", "قراءة فقط من السيارة.") {
            bodyStatus.forEach { ReadingLine(live, it) }
        }
        Section("الباب الخلفي", "الحالة فقط. الفتح والإغلاق من الجوال غير متاح؛ الزر يفتح شاشة الباب الخلفي الأصلية على شاشة السيارة.") {
            ReadingLine(live, "tailgate_height")
            ReadingLine(live, "tailgate_motion")
            Small("فتح على شاشة السيارة: الباب الخلفي", enabled = session.supports(RemoteProtocol.OP_OEM)) {
                session.send(op(RemoteProtocol.OP_OEM).put("target", "tailgate"))
            }
        }
        Section("الأنوار", "قراءة فقط؛ لا يوجد أي أمر للأنوار.") {
            listOf("turn_left", "turn_right", "low_beam", "high_beam", "hazard").forEach { ReadingLine(live, it) }
        }
        Section("الكاميرا", "لا يوجد بث للجوال: تطبيق النظام يرسم الكاميرا على سطح خاص. الزر يفتحها على شاشة السيارة.") {
            val avm = live.summary.optString("avm_state", "")
            val text = when {
                live.receivedAt == 0L || live.stale() -> "غير متاح"
                avm == "open" -> "مفتوحة على السيارة"
                avm == "closed" -> "مغلقة"
                avm.isEmpty() && live.summary.optBoolean("avm") -> "مفتوحة على السيارة"
                else -> "غير معروفة"
            }
            ValueLine("حالة كاميرا ٣٦٠", text, avm == "open" || avm == "closed")
            Small("فتح على شاشة السيارة: كاميرا ٣٦٠", enabled = session.supports(RemoteProtocol.OP_PAGE)) {
                session.send(op(RemoteProtocol.OP_PAGE).put("page", "camera"))
            }
        }
        Section("شاشات النظام", "نفس أزرار صفحة التحكم في اللانشر. كلها «فتح على شاشة السيارة».") {
            Flow { oemScreens.forEach { (id, label) ->
                Pill(label, enabled = session.supports(RemoteProtocol.OP_OEM)) { session.send(op(RemoteProtocol.OP_OEM).put("target", id)) }
            } }
            Badge("فتح على شاشة السيارة")
        }
        Section("فحص خدمة السيارة", "قراءة فقط، لا ترسل أي أمر.") {
            Small("افحص", enabled = session.supports(RemoteProtocol.OP_PROBE)) { session.send(op(RemoteProtocol.OP_PROBE)) { probe = session.status } }
            if (probe.isNotEmpty()) Note(probe)
        }
    }
}

/** Fires once after a continuous ~1 s hold. A shorter press does nothing; holding longer never repeats. */
@Composable
fun HoldButton(label: String, enabled: Boolean, modifier: Modifier, onHeld: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    Box(modifier.height(64.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Card2 else Card2.copy(alpha = .4f))
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
        Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxWidth(progress).height(64.dp).background(Good.copy(alpha = .35f))) }
        Text(label, color = if (enabled) Ink else Muted, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- A/C (owner-approved writes, RemoteProtocol.CLIMATE only)
private val blow = listOf(1 to "الوجه", 2 to "القدمين", 3 to "الزجاج", 4 to "الوجه والقدمين", 5 to "الزجاج والقدمين")
private val recirc = listOf(1 to "داخلي", 2 to "خارجي")
private val modes = listOf(1 to "اقتصادي", 2 to "مريح", 3 to "قوي")
private val switches = listOf("power" to "المكيف", "ac" to "A/C", "auto" to "AUTO", "sync" to "مزامنة", "rear_defrost" to "مزيل ضباب الخلفي")

@Composable
fun ClimateTab(session: Session) {
    val sync = remember { ClimateSync() }
    var tick by remember { mutableIntStateOf(0) }          // recomposition trigger for the plain ClimateSync maps
    var connected by remember { mutableStateOf(false) }
    var lastReply by remember { mutableLongStateOf(0L) }
    val enabled = session.connected && session.supports(RemoteProtocol.OP_CLIMATE)
    LaunchedEffect(session.drops) { sync.clearOnDisconnect(); tick++ }
    LaunchedEffect(session.epoch, session.connected) {
        var inFlight = false
        while (isActive && session.connected && session.supports(RemoteProtocol.OP_CLIMATE_STATE)) {
            if (!inFlight) {
                inFlight = true
                val stamp = sync.stamp()
                session.send(op(RemoteProtocol.OP_CLIMATE_STATE), quiet = true, onError = { inFlight = false }) { d ->
                    inFlight = false
                    connected = d.optBoolean("connected")
                    val s = d.optJSONObject("state") ?: JSONObject()
                    val state = RemoteProtocol.CLIMATE.keys.associateWith { k -> if (!s.has(k) || s.isNull(k)) null else s.optDouble(k).takeIf { it.isFinite() } }
                    sync.onState(stamp, state, SystemClock.elapsedRealtime())
                    lastReply = SystemClock.elapsedRealtime()
                    tick++
                }
            }
            delay(1500)
        }
    }
    val now = remember(tick) { SystemClock.elapsedRealtime() }
    fun set(item: String, v: Double) {
        val clamped = RemoteProtocol.climateValue(item, v) ?: return
        val g = sync.beginSend(item, clamped, SystemClock.elapsedRealtime()); tick++
        session.send(op(RemoteProtocol.OP_CLIMATE).put("item", item).put("value", clamped),
            onError = { sync.sendFailed(item, g); tick++ })
    }
    fun shown(k: String) = sync.shown(k)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedsUpdate(session)
        Note("«أُرسل» = طلبه الجوال. «تأكدت» = قرأته السيارة بعد الإرسال. لا يُعاد أي أمر تلقائياً.")
        if (session.rev3 && !connected) Note("لا تصل قراءات المكيف من السيارة الآن؛ القيم غير متاحة والأوامر ترسل دون تأكيد.", Warn)
        else if (Freshness.replyStale(lastReply, now)) Note("آخر قراءة للمكيف قديمة.", Warn)
        Section("التشغيل") {
            switches.forEach { (k, label) ->
                val v = shown(k)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(label + when (v) { null -> ": غير متاح"; else -> if (v >= 1.0) ": يعمل" else ": مطفأ" }, color = Ink, fontSize = 15.sp)
                        Text(sync.label(k, now), color = Muted, fontSize = 11.sp)
                    }
                    Pill("تشغيل", selected = v != null && v >= 1.0, enabled = enabled) { set(k, 1.0) }
                    Pill("إيقاف", selected = v != null && v < 1.0, enabled = enabled) { set(k, 0.0) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TempBox("السائق", shown("temp_l"), sync.label("temp_l", now), enabled, Modifier.weight(1f)) { set("temp_l", it) }
            TempBox("الراكب", shown("temp_r"), sync.label("temp_r", now), enabled, Modifier.weight(1f)) { set("temp_r", it) }
        }
        Section("سرعة المروحة: ${shown("fan")?.toInt() ?: "غير متاح"}", sync.label("fan", now)) {
            Flow { (1..7).forEach { lvl -> Pill("$lvl", selected = shown("fan")?.toInt() == lvl, enabled = enabled) { set("fan", lvl.toDouble()) } } }
        }
        Section("اتجاه الهواء", sync.label("blow", now)) { Flow { blow.forEach { (v, l) -> Pill(l, shown("blow")?.toInt() == v, enabled) { set("blow", v.toDouble()) } } } }
        Section("تدوير الهواء", sync.label("recirc", now)) { Flow { recirc.forEach { (v, l) -> Pill(l, shown("recirc")?.toInt() == v, enabled) { set("recirc", v.toDouble()) } } } }
        Section("وضع المكيف", sync.label("mode", now)) { Flow { modes.forEach { (v, l) -> Pill(l, shown("mode")?.toInt() == v, enabled) { set("mode", v.toDouble()) } } } }
        Small("فتح على شاشة السيارة: المكيف الأصلي", enabled = session.supports(RemoteProtocol.OP_SHORTCUT)) {
            session.send(op(RemoteProtocol.OP_SHORTCUT).put("id", "climate"))
        }
        Note("الحرارة ١٦–٣٠ بخطوة ٠٫٥، والمروحة ١–٧ (جدول RemoteProtocol.CLIMATE). تدفئة المقود والزجاج الأمامي والمقاعد غير متاحة من الجوال.")
    }
}

@Composable
private fun TempBox(title: String, value: Double?, status: String, enabled: Boolean, modifier: Modifier, onSet: (Double) -> Unit) {
    val spec = RemoteProtocol.CLIMATE.getValue("temp_l")
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(CardColor).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Muted, fontSize = 14.sp)
        Text(value?.let { String.format(Locale.US, "%.1f°", it) } ?: "غير متاح", color = if (value != null) Ink else Muted,
            fontSize = if (value != null) 34.sp else 18.sp)
        Text(status, color = Muted, fontSize = 11.sp)
        if (value != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("−", enabled = enabled && value > spec.min) { onSet(value - spec.step) }
            Pill("+", enabled = enabled && value < spec.max) { onSet(value + spec.step) }
        } else {
            // No read-back: the phone does not guess a starting value; pick an explicit target instead.
            Flow { listOf(18.0, 20.0, 22.0, 24.0, 26.0).forEach { t -> Pill("${t.toInt()}°", enabled = enabled) { onSet(t) } } }
        }
    }
}

// ---------------------------------------------------------------- Vehicle data (read-only)
@Composable
fun DataTab(session: Session) {
    var section by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Flow {
            Pill("القراءات", selected = section == 0) { section = 0 }
            Pill("الطرق الوعرة", selected = section == 1, enabled = session.supports(RemoteProtocol.OP_OFFROAD)) { section = 1 }
            Pill("فهرس OEM", selected = section == 2, enabled = session.supports(RemoteProtocol.OP_OEM_PROPS)) { section = 2 }
        }
        NeedsRev4(session, "الطرق الوعرة وفهرس OEM وعمر القراءات")
        Box(Modifier.weight(1f)) {
            when (section) {
                1 -> OffRoadSection(session)
                2 -> OemSection(session)
                else -> ReadingsSection(session)
            }
        }
    }
}

@Composable
private fun ReadingsSection(session: Session) {
    val live = rememberLive(session)
    var liveOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    val all = live.order
    val groups = all.map { it.group }.distinct()
    val shown = all.filter { (!liveOnly || it.live) && (group.isEmpty() || it.group == group) &&
        (query.isBlank() || it.label.contains(query, true) || it.key.contains(query, true) || it.group.contains(query, true)) }
    val stale = live.receivedAt != 0L && live.stale()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NeedsUpdate(session)
                val s = live.summary
                Note("قراءة فقط · كل ثانيتين. حية ${s.optInt("live")} من ${s.optInt("total", all.size)} في SignalCatalog. " +
                    "«غير متاح» = لا قراءة؛ القيم المتوقفة مُعلّمة ولا تُعرض كأنها حية.")
                if (stale) Note("توقف وصول القراءات؛ كل القيم المعروضة قديمة.", Warn)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(30) }, singleLine = true, label = { Text("بحث") }, modifier = Modifier.weight(1f))
                    Pill(if (liveOnly) "الحية فقط" else "الكل", selected = liveOnly) { liveOnly = !liveOnly }
                }
                Flow {
                    Pill("كل المجموعات", selected = group.isEmpty()) { group = "" }
                    groups.forEach { g -> Pill(g, selected = group == g) { group = g } }
                }
                Note("${shown.size} عنصر معروض")
            }
        }
        var lastGroup = ""
        shown.sortedBy { groups.indexOf(it.group) }.forEach { r ->
            if (r.group != lastGroup) { lastGroup = r.group; val g = r.group
                item(key = "g_$g") { Text(g, color = Accent, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) } }
            item(key = r.key) { ReadingCard(r, stale) }
        }
    }
}

@Composable
private fun ReadingCard(r: Reading, stale: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardColor).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val value = if (stale && r.live) "${r.value} ${r.unit}".trim() + " (قديمة)" else r.display()
            Text(value, color = if (r.live && !stale) Good else Muted, fontSize = 14.sp)
        }
        val extra = listOfNotNull(Freshness.line(r), r.raw?.let { "خام: ${trim(it)}" },
            if (r.confirmed) "مؤكد" else "غير مؤكد", r.key).joinToString(" · ")
        Text(extra, color = Muted, fontSize = 11.sp)
        if (r.detail.isNotBlank()) Text(r.detail, color = Muted, fontSize = 11.sp)
    }
}

private fun trim(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e12) v.toLong().toString() else String.format(Locale.US, "%.3f", v)

@Composable
private fun OffRoadSection(session: Session) {
    var data by remember { mutableStateOf(JSONObject()) }
    var at by remember { mutableLongStateOf(0L) }
    LaunchedEffect(session.epoch, session.drops) {
        var inFlight = false
        while (isActive) {
            if (session.connected && session.supports(RemoteProtocol.OP_OFFROAD) && !inFlight) {
                inFlight = true
                session.send(op(RemoteProtocol.OP_OFFROAD), quiet = true, onError = { inFlight = false }) {
                    inFlight = false; data = it; at = SystemClock.elapsedRealtime()
                }
            }
            delay(1000)
        }
    }
    val stale = at != 0L && Freshness.replyStale(at, SystemClock.elapsedRealtime(), 4000)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("الطرق الوعرة", "من حساسات السيارة فقط (لا تُستخدم حساسات الجوال). تعمل على السيارة أثناء فتح هذه الصفحة وتتوقف بعد ٣٠ ثانية من إغلاقها.") {
            if (at == 0L) Note("بانتظار أول قراءة…")
            if (stale) Note("القراءات متوقفة؛ القيم قديمة.", Warn)
            listOf(Triple("pitch", "الميل الطولي", "°"), Triple("roll", "الميل الجانبي", "°"),
                Triple("heading", "الاتجاه", "°"), Triple("altitude", "الارتفاع", " م")).forEach { (key, label, unit) ->
                val o = data.optJSONObject(key)
                val v = o?.takeIf { it.has("value") && !stale }?.optDouble("value")?.takeIf { it.isFinite() }
                val text = when {
                    v == null -> if (o?.optString("status") == "STALE") "توقف التحديث" else "غير متاح"
                    key == "heading" -> String.format(Locale.US, "%.0f%s", v, unit) + (Compass.label(v)?.let { " $it" } ?: "")
                    key == "altitude" -> String.format(Locale.US, "%.0f%s", v, unit)
                    else -> String.format(Locale.US, "%.1f%s", v, unit)
                }
                val sub = listOfNotNull(o?.optString("source")?.takeIf { it.isNotEmpty() }, Freshness.age(o?.takeIf { it.has("age_ms") }?.optLong("age_ms")))
                    .joinToString(" · ")
                ValueLine(label, text, v != null, sub)
            }
            data.optJSONObject("steering")?.let { s ->
                val r = Reading.parse(s)
                ValueLine(r.label, if (stale) "غير متاح" else r.display(), r.live && !stale, Freshness.line(r))
            }
            if (data.has("location_permission") && !data.optBoolean("location_permission"))
                Note("الارتفاع يحتاج صلاحية الموقع لـ T1 على السيارة (تُمنح من شاشة السيارة).", Warn)
        }
    }
}

@Composable
private fun OemSection(session: Session) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var watch by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(JSONObject()) }
    fun load() {
        if (!session.supports(RemoteProtocol.OP_OEM_PROPS)) return
        session.send(op(RemoteProtocol.OP_OEM_PROPS).put("query", query.take(RemoteProtocol.MAX_QUERY)).put("page", page).put("watch", watch), quiet = true) { result = it }
    }
    LaunchedEffect(session.epoch, query, page, watch) {
        delay(300)
        load()
        while (watch && isActive) { delay(2000); if (session.connected) load() }
    }
    val items: JSONArray = result.optJSONArray("items") ?: JSONArray()
    val pages = result.optInt("pages", 1)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Note("فهرس خصائص النظام في اللانشر (قراءة فقط، لا كتابة). ${result.optInt("total")} من ${result.optInt("catalog_total")} خاصية.")
                OutlinedTextField(query, { query = it.take(RemoteProtocol.MAX_QUERY); page = 0 }, singleLine = true,
                    label = { Text("بحث بالاسم أو الرقم") }, modifier = Modifier.fillMaxWidth())
                SwitchLine("اقرأ قيم هذه الصفحة من السيارة", watch) { watch = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small("السابق", enabled = page > 0) { page-- }
                    Text("صفحة ${result.optInt("page") + 1} من $pages", color = Ink, fontSize = 14.sp)
                    Small("التالي", enabled = page + 1 < pages) { page++ }
                }
            }
        }
        (0 until items.length()).forEach { i ->
            val o = items.optJSONObject(i) ?: return@forEach
            item(key = "oem_${o.optInt("address")}_${o.optInt("area")}_$i") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardColor).padding(10.dp)) {
                    Text(o.optString("name"), color = Ink, fontSize = 13.sp)
                    Text("0x" + Integer.toHexString(o.optInt("address")) + " · ${o.optString("type")} · area ${o.optInt("area")}" +
                        if (!o.optBoolean("readable")) " · غير قابلة للقراءة" else "", color = Muted, fontSize = 11.sp)
                    o.optJSONObject("reading")?.let { val r = Reading.parse(it)
                        Text("${r.display()} · ${Freshness.line(r)}", color = if (r.live) Good else Muted, fontSize = 12.sp) }
                }
            }
        }
    }
}
