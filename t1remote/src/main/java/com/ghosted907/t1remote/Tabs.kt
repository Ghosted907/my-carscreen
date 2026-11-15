package com.ghosted907.t1remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppItem(val pkg: String, val label: String, val screen: String?, val icon: ImageBitmap?,
                   val pinned: Boolean = false, val recent: Int = -1)

private fun op(name: String) = JSONObject().put("op", name)

/** Parses an app list off the main thread (icon decoding was on the UI thread in 1.2.0). */
private suspend fun parseApps(data: JSONObject, key: String): List<AppItem> = withContext(Dispatchers.Default) {
    val a = data.optJSONArray(key) ?: return@withContext emptyList()
    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let {
        AppItem(it.getString("pkg"), it.optString("label").ifEmpty { it.getString("pkg") }, it.optString("screen").ifEmpty { null },
            decodeImage(it.optString("icon")), it.optBoolean("pinned"), it.optInt("recent", -1))
    } }
}

@Composable
fun NeedsUpdate(session: Session) {
    if (!session.rev3) Note("هذه الميزة تحتاج تحديث اللانشر T1 على السيارة لإصدار يدعم T1 Remote 1.3.", Gold)
}

// ---------------------------------------------------------------- Home
private val shortcuts = listOf("carplay" to "CarPlay", "youtube" to "YouTube", "waze" to "Waze", "bluetooth" to "البلوتوث", "climate" to "شاشة المكيف")
private val pages = listOf("home" to "الرئيسية", "apps" to "التطبيقات", "cluster" to "العدادات", "camera" to "كاميرا ٣٦٠",
    "vehicle" to "بيانات السيارة", "controls" to "التحكم", "climate" to "التكييف", "customize" to "التخصيص",
    "settings" to "الإعدادات", "windows" to "التطبيقات المفتوحة", "permissions" to "الصلاحيات", "installer" to "تثبيت تطبيق")

@Composable
fun HomeTab(session: Session) {
    val scope = rememberCoroutineScope()
    var favourites by remember { mutableStateOf(listOf<AppItem>()) }
    LaunchedEffect(session.epoch) {
        session.send(op(RemoteProtocol.OP_APPS), quiet = true) { d -> scope.launch { favourites = parseApps(d, "apps").filter { it.pinned } } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeedsUpdate(session)
        Section("اختصارات الرئيسية", "تفتح على شاشة السيارة مثل بطاقات اللانشر. (الواي فاي غير متاح من الجوال.)") {
            Flow { shortcuts.forEach { (id, label) ->
                Pill(label, enabled = session.supports(RemoteProtocol.OP_SHORTCUT)) { session.send(op(RemoteProtocol.OP_SHORTCUT).put("id", id)) }
            } }
        }
        if (favourites.isNotEmpty()) Section("المفضلة") {
            favourites.forEach { app ->
                AppRow(app) {
                    Small("الرئيسية") { session.send(op("open").put("pkg", app.pkg).put("screen", "main")) }
                    if (session.info.optBoolean("cluster")) Small("العدادات") { session.send(op("open").put("pkg", app.pkg).put("screen", "cluster")) }
                }
            }
        }
        Section("افتح صفحة في اللانشر", "تفتح الصفحة على شاشة السيارة.") {
            Flow { pages.forEach { (id, label) ->
                Pill(label, enabled = session.supports(RemoteProtocol.OP_PAGE)) { session.send(op(RemoteProtocol.OP_PAGE).put("page", id)) }
            } }
        }
        Section("الوضع") {
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

// ---------------------------------------------------------------- Apps
@Composable
fun AppsTab(session: Session) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(listOf<AppItem>()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var destCluster by remember { mutableStateOf(false) }
    fun load() {
        loading = true
        session.send(op(RemoteProtocol.OP_APPS), quiet = true, onError = { loading = false }) { d ->
            destCluster = d.optBoolean("dest_cluster")
            scope.launch { apps = parseApps(d, "apps"); loading = false }
        }
    }
    LaunchedEffect(session.epoch) { load() }
    val cluster = session.info.optBoolean("cluster")
    val shown = apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        .sortedWith(compareByDescending<AppItem> { it.pinned }.thenBy { it.label })
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(40) }, singleLine = true, label = { Text("بحث") }, modifier = Modifier.weight(1f))
                    Small(if (loading) "…" else "تحديث", enabled = !loading) { load() }
                }
                if (session.supports(RemoteProtocol.OP_SET)) SwitchLine("وجهة الضغط في اللانشر: العدادات", destCluster) { v ->
                    session.send(op(RemoteProtocol.OP_SET).put("key", "dest_cluster").put("value", v)) { destCluster = v }
                }
                if (session.supports(RemoteProtocol.OP_PAGE)) Small("تثبيت تطبيق من ملف APK على السيارة") { session.send(op(RemoteProtocol.OP_PAGE).put("page", "installer")) }
                Note("${apps.size} تطبيق. ★ = في مفضلة الرئيسية (حتى ١٠).")
            }
        }
        items(shown, key = { it.pkg }) { app ->
            AppRow(app) {
                Small("الرئيسية") { session.send(op("open").put("pkg", app.pkg).put("screen", "main")) }
                if (cluster) Small("العدادات") { session.send(op("open").put("pkg", app.pkg).put("screen", "cluster")) }
                if (session.supports(RemoteProtocol.OP_PIN)) Small(if (app.pinned) "★ إزالة" else "☆ مفضلة") {
                    session.send(op(RemoteProtocol.OP_PIN).put("pkg", app.pkg).put("on", !app.pinned)) {
                        apps = apps.map { if (it.pkg == app.pkg) it.copy(pinned = !app.pinned) else it }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(item: AppItem, sub: String? = null, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardColor).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item.icon?.let { Image(it, null, Modifier.size(36.dp)) }
            Column(Modifier.weight(1f)) {
                Text((if (item.pinned) "★ " else "") + item.label, color = Ink, fontSize = 16.sp)
                if (sub != null) Text(sub, color = Muted, fontSize = 12.sp)
            }
        }
        Row(Modifier.padding(top = 8.dp)) { Flow { actions() } }
    }
}

// ---------------------------------------------------------------- Windows
private fun screenName(s: String?) = if (s == RemoteProtocol.SCREEN_CLUSTER) "العدادات" else "الرئيسية"

@Composable
fun WindowsTab(session: Session) {
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(listOf<AppItem>()) }
    fun load() = session.send(op(RemoteProtocol.OP_TASKS), quiet = true) { d -> scope.launch { tasks = parseApps(d, "tasks") } }
    LaunchedEffect(session.epoch) { load() }
    val info = session.info
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Note("النوافذ التي يعرفها اللانشر (فتحها هو أو الجوال). أندرويد لا يسمح بقائمة كل التطبيقات المفتوحة.")
                Small("تحديث") { load() }
            }
        }
        if (tasks.isEmpty()) item { Note("لا توجد نوافذ معروفة بعد.") }
        items(tasks, key = { it.pkg }) { t ->
            AppRow(t, sub = "على ${screenName(t.screen)}") {
                Small("قدّمها") { session.send(op("front").put("pkg", t.pkg)) }
                val other = if (t.screen == RemoteProtocol.SCREEN_CLUSTER) RemoteProtocol.SCREEN_MAIN else RemoteProtocol.SCREEN_CLUSTER
                if (other == RemoteProtocol.SCREEN_MAIN || info.optBoolean("cluster"))
                    Small("انقل لـ${screenName(other)}") { session.send(op("move").put("pkg", t.pkg).put("screen", other)) { load() } }
                if (info.optBoolean("freeform")) {
                    Small("نصف يمين") { session.send(op("resize").put("pkg", t.pkg).put("preset", "right_half")) }
                    Small("نصف يسار") { session.send(op("resize").put("pkg", t.pkg).put("preset", "left_half")) }
                    Small("وسط") { session.send(op("resize").put("pkg", t.pkg).put("preset", "center")) }
                    Small("كامل") { session.send(op("resize").put("pkg", t.pkg).put("preset", "full")) }
                }
                if (info.optBoolean("close")) Small("إغلاق") { session.send(op("close").put("pkg", t.pkg)) { load() } }
            }
        }
        if (!info.optBoolean("freeform")) item { Note("تغيير الحجم غير متاح: وضع النوافذ الحرة غير مفعّل في نظام السيارة.") }
        if (!info.optBoolean("close")) item { Note("إغلاق نافذة تطبيق آخر غير متاح في هذا الإصدار من اللانشر.") }
    }
}
