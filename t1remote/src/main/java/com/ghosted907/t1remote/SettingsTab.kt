package com.ghosted907.t1remote

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun op(name: String) = JSONObject().put("op", name)
data class ActionChoice(val raw: String, val label: String)

private val zoneNames = mapOf("LEFT_TOP" to "يسار أعلى", "LEFT_BOTTOM" to "يسار أسفل", "RIGHT_TOP" to "يمين أعلى",
    "RIGHT_BOTTOM" to "يمين أسفل", "BOTTOM_LEFT" to "أسفل يسار", "BOTTOM_RIGHT" to "أسفل يمين")
private val zoneStyles = listOf("مقبض مائل", "دائري", "مخفي", "سهم", "لسان")
private val langs = listOf("ar" to "العربية", "en" to "English", "system" to "لغة النظام")

/**
 * The launcher Settings page on the phone. Not offered on purpose: Wi-Fi, hiding apps (device owner),
 * device-owner provisioning, PIN pairing, system/OEM app changes.
 */
@Composable
fun SettingsTab(session: Session, store: PhoneStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf(JSONObject()) }
    var labels by remember { mutableStateOf(JSONObject()) }
    var info by remember { mutableStateOf(JSONObject()) }
    var catalog by remember { mutableStateOf(listOf<ActionChoice>()) }
    var picking by remember { mutableStateOf<((ActionChoice) -> Unit)?>(null) }
    var pickApps by remember { mutableStateOf(false) }
    var splitFor by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(store.autoConnect) }

    fun load() = session.send(op(RemoteProtocol.OP_SETTINGS), quiet = true) { d ->
        values = d.optJSONObject("values") ?: JSONObject(); labels = d.optJSONObject("labels") ?: JSONObject(); info = d.optJSONObject("info") ?: JSONObject()
    }
    fun loadCatalog(then: () -> Unit = {}) {
        if (catalog.isNotEmpty()) { then(); return }
        session.send(op(RemoteProtocol.OP_ACTIONS), quiet = true) { d ->
            val a = d.optJSONArray("actions") ?: JSONArray()
            catalog = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { ActionChoice(it.optString("raw"), it.optString("label")) } }
            then()
        }
    }
    fun set(key: String, value: Any) {
        val reason = RemoteProtocol.validateSetting(key, value)
        if (reason != null) { session.say(reason, false); return }
        session.send(op(RemoteProtocol.OP_SET).put("key", key).put("value", value)) { load(); if (key == "day") session.refreshInfo() }
    }
    fun pickAction(apps: Boolean = false, onPick: (ActionChoice) -> Unit) = loadCatalog { pickApps = apps; picking = onPick }
    fun label(key: String) = labels.optString(key).ifEmpty { values.optString(key) }

    LaunchedEffect(session.epoch) { if (session.supports(RemoteProtocol.OP_SETTINGS)) load() }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val encoded = withContext(Dispatchers.IO) { encodeImage(context, uri, 1600, Bitmap.CompressFormat.JPEG) }
            if (encoded == null) session.say("تعذر قراءة الصورة أو أنها كبيرة جداً", false)
            else session.send(op(RemoteProtocol.OP_WALLPAPER).put("image", encoded), timeoutMs = RemoteClient.IMAGE_TIMEOUT_MS) { load() }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("هذا الجوال") {
            SwitchLine("اتصال تلقائي بآخر سيارة", auto) { auto = it; store.autoConnect = it }
            Note("الاتصال بالبحث التلقائي على نفس الواي فاي، بدون رمز أو اقتران.")
        }
        if (!session.supports(RemoteProtocol.OP_SETTINGS)) { NeedsUpdate(session); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Note("إعدادات اللانشر ${info.optString("version")}", Ink)
            Row(Modifier.weight(1f)) {}
            Small("تحديث") { load() }
        }
        Section("عام") {
            SwitchLine("الوضع النهاري", values.optBoolean("day")) { set("day", it) }
            SwitchLine("الضغط على تطبيق يفتحه على العدادات", values.optBoolean("dest_cluster")) { set("dest_cluster", it) }
            Text("اللغة (تظهر بعد إعادة فتح T1)", color = Ink)
            Flow { langs.forEach { (v, l) -> Pill(l, values.optString("language") == v) { set("language", v) } } }
        }
        Section("خلفية اللانشر") {
            Note(if (info.optBoolean("wallpaper")) "خلفية مخصصة مفعّلة." else "الخلفية الافتراضية.")
            Flow {
                Pill("اختر صورة من الجوال") { wallpaperPicker.launch("image/*") }
                Pill("استعادة الافتراضية") { session.send(op(RemoteProtocol.OP_WALLPAPER).put("clear", true)) { load() } }
            }
        }
        Section("التطبيقات على لوحة العدادات") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التطبيق: " + (values.optString("cluster_pkg").takeIf { it.isNotEmpty() }?.let { info.optString("cluster_label").ifEmpty { it } } ?: "غير محدد"),
                    color = Ink, modifier = Modifier.weight(1f))
                Small("اختر") { pickAction(apps = true) { c -> set("cluster_pkg", c.raw.removePrefix("app:")) } }
            }
            SwitchLine("تشغيله تلقائياً على العدادات عند تشغيل السيارة", values.optBoolean("cluster_boot")) { set("cluster_boot", it) }
            val max = (info.optInt("display_count", 1) - 1).coerceIn(0, 8)
            Stepper("فهرس الجهاز اللوحي (-1 تلقائي) = ${info.optInt("gu_resolved")}", values.optInt("gu_index", -1), -1, max) { set("gu_index", it) }
            Stepper("فهرس العدادات (-1 تلقائي) = ${info.optInt("pri_resolved")}", values.optInt("pri_index", -1), -1, max) { set("pri_index", it) }
            Note(info.optString("displays"))
        }
        Section("تخطيطات تطبيقين", "تتطلب النوافذ الحرة في النظام.") {
            (1..3).forEach { i ->
                val key = "split_$i"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تخطيط $i: " + (label(key).ifEmpty { "غير محفوظ" }), color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Small("إعداد") { loadCatalog { splitFor = key } }
                    Small("افتح", enabled = values.optString(key).isNotEmpty()) { session.send(op(RemoteProtocol.OP_RUN).put("id", "split_open_$i")) }
                    Small("مسح", enabled = values.optString(key).isNotEmpty()) { set(key, "") }
                }
            }
        }
        Section("الزر العائم") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الإجراء: ${label("floating_action")}", color = Ink, modifier = Modifier.weight(1f))
                Small("تغيير") { pickAction { set("floating_action", it.raw) } }
            }
            Text("الشكل", color = Ink)
            Flow {
                val styles = info.optJSONArray("floating_styles")
                (0 until (styles?.length() ?: 0)).forEach { i ->
                    val parts = styles!!.getString(i).split(":", limit = 2)
                    val v = parts[0]; val l = parts.getOrElse(1) { parts[0] }
                    Pill(l, values.optString("floating_style") == v) { set("floating_style", v) }
                }
            }
            SwitchLine("الأدوات فوق عناصر النظام", values.optBoolean("over_system", true)) { set("over_system", it) }
            Small("إعادة الزر لمكانه") { session.send(op(RemoteProtocol.OP_RUN).put("id", "floating_reset")) }
        }
        Section("مجموعة المهام", "أوامر تُنفّذ بالترتيب عند تشغيل السيارة.") {
            SwitchLine("تشغيل مجموعة المهام تلقائياً", values.optBoolean("tasks_boot")) { set("tasks_boot", it) }
            Stepper("الفاصل بين الأوامر", values.optInt("tasks_delay", 1), 1, 20, suffix = " ث") { set("tasks_delay", it) }
            val tasks = values.optJSONArray("tasks") ?: JSONArray()
            fun saveTasks(list: List<JSONObject>) = set("tasks", JSONArray(list))
            val list = (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }
            list.forEachIndexed { i, t ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg).padding(8.dp)) {
                    SwitchLine("${i + 1}. " + (catalog.firstOrNull { it.raw == t.optString("action") }?.label ?: t.optString("action")), t.optBoolean("enabled", true)) { on ->
                        saveTasks(list.mapIndexed { j, o -> if (j == i) JSONObject(o.toString()).put("enabled", on) else o })
                    }
                    Flow {
                        Small("↑", enabled = i > 0) { saveTasks(list.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                        Small("↓", enabled = i < list.lastIndex) { saveTasks(list.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                        Small("حذف") { saveTasks(list.filterIndexed { j, _ -> j != i }) }
                    }
                }
            }
            Flow {
                Small("أضف أمراً", enabled = list.size < 20) { pickAction { c -> saveTasks(list + JSONObject().put("phrase", "").put("enabled", true).put("matchFull", false).put("action", c.raw)) } }
                Small("جرّب المجموعة") { session.send(op(RemoteProtocol.OP_RUN).put("id", "tasks_test")) }
                Small("أسماء الأوامر") { loadCatalog() }
            }
        }
        Section("مناطق السحب على الحواف") {
            Stepper("عرض المناطق", values.optInt("zone_width", 100), 75, 150, 5, "%") { set("zone_width", it) }
            Stepper("مسافة السحب", values.optInt("zone_sensitivity", 100), 75, 150, 5, "%") { set("zone_sensitivity", it) }
            RemoteProtocol.ZONES.forEach { z ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchLine(zoneNames[z] ?: z, values.optBoolean("zone_on_$z")) { set("zone_on_$z", it) }
                    Flow { zoneStyles.forEachIndexed { i, l -> Pill(l, values.optInt("zone_style_$z", -1) == i) { set("zone_style_$z", i) } } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الإجراء: ${label("zone_action_$z")}", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Small("تغيير") { pickAction { set("zone_action_$z", it.raw) } }
                    }
                }
            }
        }
        Section("الشاشة الداكنة") {
            SwitchLine("عرض الساعة", values.optBoolean("black_clock", true)) { set("black_clock", it) }
            SwitchLine("ساعة ٢٤", values.optBoolean("black_clock24", true)) { set("black_clock24", it) }
            Stepper("السطوع", values.optInt("black_brightness", 10), 1, 100, 5, "%") { set("black_brightness", it) }
            Small("جرّب الشاشة الداكنة") { session.send(op(RemoteProtocol.OP_RUN).put("id", "black_test")) }
        }
        Section("الصلاحيات", "تفتح شاشة الصلاحية على السيارة لتفعيلها هناك.") {
            Note("الظهور فوق التطبيقات: ${if (info.optBoolean("overlay")) "مفعّل" else "غير مفعّل"} · خدمة الإيماءات: ${if (info.optBoolean("accessibility")) "تعمل" else "متوقفة"}")
            Flow {
                Pill("الظهور فوق التطبيقات") { session.send(op(RemoteProtocol.OP_OEM).put("target", "overlay")) }
                Pill("إمكانية الوصول") { session.send(op(RemoteProtocol.OP_OEM).put("target", "accessibility")) }
                Pill("قراءة الإشعارات") { session.send(op(RemoteProtocol.OP_OEM).put("target", "listener")) }
                Pill("صفحة الصلاحيات") { session.send(op(RemoteProtocol.OP_PAGE).put("page", "permissions")) }
            }
        }
        Section("غير متاح من الجوال", null) {
            Note("الواي فاي، إخفاء التطبيقات (يحتاج مالك الجهاز)، تفعيل مالك الجهاز، تعديل تطبيقات النظام، والاقتران برمز. " +
                (if (info.optBoolean("hide_apps")) "إخفاء التطبيقات مفعّل حالياً في اللانشر." else ""))
        }
    }

    picking?.let { onPick ->
        ActionDialog(if (pickApps) catalog.filter { it.raw.startsWith("app:") } else catalog, onDismiss = { picking = null }) { picking = null; onPick(it) }
    }
    splitFor?.let { key -> SplitDialog(catalog.filter { it.raw.startsWith("app:") }, onDismiss = { splitFor = null }) { raw -> splitFor = null; set(key, raw) } }
}

@Composable
private fun ActionDialog(options: List<ActionChoice>, onDismiss: () -> Unit, onPick: (ActionChoice) -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        title = { Text("اختر") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(q, { q = it.take(30) }, singleLine = true, label = { Text("بحث") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(options.filter { q.isBlank() || it.label.contains(q, true) || it.raw.contains(q, true) }, key = { it.raw }) { c ->
                        Text(c.label, color = Ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardColor)
                            .clickable { onPick(c) }.padding(12.dp))
                    }
                }
            }
        })
}

@Composable
private fun SplitDialog(apps: List<ActionChoice>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var a by remember { mutableStateOf<ActionChoice?>(null) }
    var b by remember { mutableStateOf<ActionChoice?>(null) }
    var orient by remember { mutableStateOf(1) }
    var ratio by remember { mutableStateOf(50) }
    var choosing by remember { mutableStateOf(0) }
    if (choosing != 0) { ActionDialog(apps, onDismiss = { choosing = 0 }) { if (choosing == 1) a = it else b = it; choosing = 0 }; return }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = a != null && b != null, onClick = {
            onDone("split:${a!!.raw.removePrefix("app:")},${b!!.raw.removePrefix("app:")},$orient,$ratio")
        }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        title = { Text("تخطيط تطبيقين") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Small("التطبيق الأول: ${a?.label ?: "اختر"}") { choosing = 1 }
                Small("التطبيق الثاني: ${b?.label ?: "اختر"}") { choosing = 2 }
                Flow { Pill("جنباً إلى جنب", orient == 1) { orient = 1 }; Pill("فوق وتحت", orient == 2) { orient = 2 } }
                Flow { listOf(30, 50, 70).forEach { r -> Pill("$r:${100 - r}", ratio == r) { ratio = r } } }
            }
        })
}
