package com.ghosted907.t1remote

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

private val RIYADH = android.icu.util.TimeZone.getTimeZone("Asia/Riyadh")

/** ICU skeleton formatting; the calendar (gregorian / islamic-umalqura) comes from the locale keyword. */
private fun fmt(skeleton: String, locale: String, at: Long): String = runCatching {
    android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, android.icu.util.ULocale(locale)).apply { timeZone = RIYADH }
        .format(java.util.Date(at))
}.getOrDefault("")

private fun riyadhHour(at: Long): Int = android.icu.util.Calendar.getInstance(RIYADH).apply { timeInMillis = at }
    .get(android.icu.util.Calendar.HOUR_OF_DAY)

@Composable
fun HomeTab(session: Session, openTab: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val live = rememberLive(session)
    var apps by remember { mutableStateOf(listOf<AppItem>()) }
    var home by remember { mutableStateOf(JSONObject()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    fun loadHome() {
        if (session.supports(RemoteProtocol.OP_HOME)) session.send(op(RemoteProtocol.OP_HOME), quiet = true) { home = it }
    }
    LaunchedEffect(session.epoch) {
        session.send(op(RemoteProtocol.OP_APPS), quiet = true) { d -> scope.launch { apps = parseApps(d, "apps") } }
        loadHome()
    }
    // Media title refresh (read only) every 5 s while Home is visible.
    LaunchedEffect(session.epoch, session.drops) {
        while (session.rev4) { delay(5000); if (session.connected) loadHome() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedsUpdate(session)
        HeaderCard(now)
        SummaryCard(live)
        TyresCard(live)
        LightsCard(live)
        ShortcutsCard(session, home, openTab)
        MediaCard(session, home)
        FavouritesCard(session, apps)
        NoteCard(session, home) { loadHome() }
        VoiceCard(session)
        Section("افتح صفحة في اللانشر", "تفتح الصفحة على شاشة السيارة.") {
            Flow { pages.forEach { (id, label) ->
                Pill(label, enabled = session.supports(RemoteProtocol.OP_PAGE)) { session.send(op(RemoteProtocol.OP_PAGE).put("page", id)) }
            } }
        }
        Section("وضع اللانشر على السيارة", "نهاري/ليلي هنا يغيّر شاشة السيارة فقط؛ مظهر الجوال من زر ☀/☾ أعلى التطبيق.") {
            val day = session.info.optBoolean("day")
            Flow {
                Pill("نهاري", selected = day, enabled = session.supports(RemoteProtocol.OP_SET)) {
                    session.send(op(RemoteProtocol.OP_SET).put("key", "day").put("value", true)) { session.refreshInfo() } }
                Pill("ليلي", selected = !day, enabled = session.supports(RemoteProtocol.OP_SET)) {
                    session.send(op(RemoteProtocol.OP_SET).put("key", "day").put("value", false)) { session.refreshInfo() } }
                Pill("شاشة داكنة", enabled = session.supports(RemoteProtocol.OP_OEM)) { session.send(op(RemoteProtocol.OP_OEM).put("target", "dark")) }
            }
        }
    }
}

@Composable
private fun HeaderCard(now: Long) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Greeting.forHour(riyadhHour(now)), color = Muted, fontSize = 15.sp)
            Text(fmt("hmm", "ar_SA", now), color = Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(fmt("EEEEdMMMMy", "ar_SA@calendar=gregorian", now), color = Ink, fontSize = 14.sp)
            Text(fmt("dMMMMy", "ar_SA@calendar=islamic-umalqura", now), color = Muted, fontSize = 14.sp)
            Text("توقيت الرياض", color = Muted, fontSize = 11.sp)
        }
        Image(painterResource(R.drawable.t1_hero), "Jetour T1", Modifier.width(170.dp).height(96.dp), contentScale = ContentScale.Fit)
    }
}

private fun cell(live: LiveData, key: String): Pair<String, Boolean> {
    val r = live[key] ?: return "غير متاح" to false
    if (live.stale()) return (r.last ?: r.value.takeIf { r.live })?.let { "$it ${r.unit}".trim() + " (قديمة)" to false } ?: ("غير متاح" to false)
    return r.display() to r.live
}

@Composable
private fun Stat(label: String, value: Pair<String, Boolean>, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Card2).padding(10.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value.first, color = if (value.second) Ink else Muted, fontSize = if (value.second) 18.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SummaryCard(live: LiveData) {
    Section("ملخص السيارة", if (live.receivedAt == 0L) "بانتظار أول قراءة من السيارة…" else if (live.stale()) "القراءات متوقفة؛ القيم المعروضة قديمة." else "قراءة فقط، تتحدث كل ثانيتين.") {
        val outside = if (live.live("outside_oem") != null || live["outside_c"]?.live != true) "outside_oem" else "outside_c"
        val stats = listOf("السرعة" to "speed", "ناقل الحركة" to "gear", "دوران المحرك" to "rpm", "الوقود" to "fuel",
            "المدى" to "range", "الحرارة بالخارج" to outside, "وضع القيادة" to "drive_mode")
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, key) -> Stat(label, cell(live, key), Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TyreBox(live: LiveData, pos: String, modifier: Modifier) {
    val p = live.live("pressure_$pos")
    val t = live.live("temp_$pos")
    val psi = Tyres.psi(p?.raw)
    Column(modifier.width(108.dp).clip(RoundedCornerShape(12.dp)).background(Card2).padding(8.dp)) {
        Text(Tyres.LABELS.getValue(pos), color = Muted, fontSize = 11.sp)
        Text(psi?.let { String.format(Locale.US, "%.0f psi", it) } ?: "غير متاح", color = if (psi != null) Ink else Muted,
            fontSize = if (psi != null) 17.sp else 13.sp, fontWeight = FontWeight.SemiBold)
        Text(t?.display() ?: "الحرارة غير متاحة", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TyresCard(live: LiveData) {
    Section("الإطارات", "الضغط من خدمة السيارة (bar → psi مثل اللانشر). الأمام أعلى الصورة.") {
        // Absolute alignment: the front-left tyre stays at the physical top-left even in RTL.
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            Image(painterResource(R.drawable.t1_top_view), "T1", Modifier.align(Alignment.Center).height(240.dp), contentScale = ContentScale.Fit)
            TyreBox(live, "fl", Modifier.align(AbsoluteAlignment.TopLeft))
            TyreBox(live, "fr", Modifier.align(AbsoluteAlignment.TopRight))
            TyreBox(live, "rl", Modifier.align(AbsoluteAlignment.BottomLeft))
            TyreBox(live, "rr", Modifier.align(AbsoluteAlignment.BottomRight))
        }
        live.live("tire_warning")?.let { Note("تحذير ضغط الإطارات (خام): ${it.value}") }
    }
}

@Composable
private fun LightsCard(live: LiveData) {
    Section("الأنوار والإشارات", "قراءة فقط؛ لا يمكن تشغيل الأنوار من الجوال.") {
        Flow {
            listOf("turn_left" to "إشارة يسار", "turn_right" to "إشارة يمين", "low_beam" to "منخفض", "high_beam" to "عالي").forEach { (k, label) ->
                val r = live.live(k)
                val on = r != null && r.raw != null && r.raw != 0.0
                Badge("$label: ${r?.value ?: "غير متاح"}", if (r == null) Muted else if (on) Good else Ink)
            }
        }
    }
}

@Composable
private fun ShortcutsCard(session: Session, home: JSONObject, openTab: (Int) -> Unit) {
    Section("اختصارات الرئيسية", "تُنفَّذ على شاشة السيارة. نوافذ Waze وYouTube الحرة تبقى على شاشة السيارة ولا تُبث للجوال.") {
        val sc = home.optJSONObject("shortcuts")
        Flow {
            shortcuts.forEach { (id, label) ->
                val known = sc?.optJSONObject(id)
                val available = known?.optBoolean("available", true) ?: true
                Pill(if (available) label else "$label (غير مثبت)", enabled = available && session.supports(RemoteProtocol.OP_SHORTCUT)) {
                    session.send(op(RemoteProtocol.OP_SHORTCUT).put("id", id))
                }
            }
            Pill("تحكم المكيف من الجوال") { openTab(5) }
        }
    }
}

@Composable
private fun MediaCard(session: Session, home: JSONObject) {
    if (!session.rev4) return
    val media = home.optJSONObject("media") ?: JSONObject()
    Section("الوسائط على السيارة", "أزرار اللانشر نفسها؛ تحتاج صلاحية الإشعارات لـ T1 على السيارة.") {
        val title = media.optString("title")
        Text(title.ifEmpty { "لا يوجد عنوان متاح" }, color = if (title.isEmpty()) Muted else Ink, fontSize = 15.sp)
        if (media.has("playing")) Note(if (media.optBoolean("playing")) "يعمل الآن" else "متوقف")
        Flow {
            listOf("media_prev" to "⏮ السابق", "media_play_pause" to "⏯ تشغيل/إيقاف", "media_next" to "التالي ⏭").forEach { (id, label) ->
                Small(label, enabled = session.supportsRun(id)) { session.send(op(RemoteProtocol.OP_RUN).put("id", id)) }
            }
        }
    }
}

@Composable
private fun FavouritesCard(session: Session, apps: List<AppItem>) {
    val favourites = apps.filter { it.pinned }.take(10)
    val recent = apps.filter { it.recent >= 0 }.sortedBy { it.recent }.take(8)
    fun open(app: AppItem, screen: String) = session.send(op(RemoteProtocol.OP_OPEN).put("pkg", app.pkg).put("screen", screen))
    if (favourites.isNotEmpty()) Section("المفضلة (حتى ١٠)") {
        favourites.forEach { app ->
            AppRow(app) {
                Small("الرئيسية") { open(app, RemoteProtocol.SCREEN_MAIN) }
                if (session.info.optBoolean("cluster")) Small("العدادات") { open(app, RemoteProtocol.SCREEN_CLUSTER) }
            }
        }
    }
    if (recent.isNotEmpty()) Section("آخر التطبيقات") {
        recent.forEach { app ->
            AppRow(app) {
                Small("الرئيسية") { open(app, RemoteProtocol.SCREEN_MAIN) }
                if (session.info.optBoolean("cluster")) Small("العدادات") { open(app, RemoteProtocol.SCREEN_CLUSTER) }
            }
        }
    }
}

@Composable
private fun NoteCard(session: Session, home: JSONObject, reload: () -> Unit) {
    if (!session.rev4) return
    val carNote = home.optString("note")
    var draft by remember { mutableStateOf<String?>(null) }   // null = not edited, follow the car
    val text = draft ?: carNote
    Section("ملاحظاتي", "نفس ملاحظة الرئيسية في اللانشر. تُحفظ على السيارة عند الضغط على حفظ فقط.") {
        OutlinedTextField(text, { draft = it.take(RemoteProtocol.MAX_NOTE) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Small("حفظ على السيارة", enabled = draft != null && draft != carNote && session.supports(RemoteProtocol.OP_NOTE)) {
                val d = draft ?: return@Small
                session.send(op(RemoteProtocol.OP_NOTE).put("text", d)) { draft = null; reload() }
            }
            Small("تجاهل التعديل", enabled = draft != null) { draft = null }
            Small("تحديث من السيارة") { reload() }
            Text("${text.length}/${RemoteProtocol.MAX_NOTE}", color = Muted, fontSize = 11.sp)
        }
        if (draft != null && draft != carNote) Note("تعديل غير محفوظ", Warn)
    }
}

@Composable
private fun VoiceCard(session: Session) {
    var heard by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<VoiceMap.Match?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        heard = text
        match = VoiceMap.match(text)
        if (text.isNotEmpty() && match == null) session.say("لم أفهم الأمر: «$text»", false)
    }
    Section("أمر صوتي (اختياري)", "يعمل فقط عند الضغط، بمعرّف الصوت في الجوال. الأوامر المسموحة: كاميرا ٣٦٠، كلاسيك، إخفاء الإضافات، يوتيوب، ويز، كاربلي، بلوتوث، المكيف، الرئيسية. لا أوامر للنوافذ أو السقف أو المكيف.") {
        Small("🎤 تحدث") {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "قل الأمر")
            try { launcher.launch(intent) } catch (_: ActivityNotFoundException) { session.say("لا يوجد معرّف صوت على هذا الجوال", false) }
        }
        if (heard.isNotEmpty()) Note("سُمع: «$heard»")
    }
    match?.let { m ->
        AlertDialog(onDismissRequest = { match = null },
            title = { Text("تأكيد الأمر") },
            text = { Text("${m.label}؟") },
            confirmButton = { TextButton({ val body = m.body; match = null; session.send(body) }) { Text("نفّذ") } },
            dismissButton = { TextButton({ match = null }) { Text("إلغاء") } })
    }
}
