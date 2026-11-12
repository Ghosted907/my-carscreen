package com.ghosted907.t1remote

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Gold = Color(0xFFD5B36A)
private val Bg = Color(0xFF0A1210)
private val Card = Color(0xFF17221E)
private val Ink = Color(0xFFF4F0E5)
private val Muted = Color(0xFFA4B8A9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Bg, surface = Card)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(Bg).padding(16.dp)) { RemoteApp() }
                }
            }
        }
    }
}

private data class Item(val pkg: String, val label: String, val screen: String?, val icon: ImageBitmap?)

private fun decodeIcon(b64: String): ImageBitmap? = runCatching {
    if (b64.isEmpty()) return null
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

private fun screenName(s: String?) = if (s == RemoteProtocol.SCREEN_CLUSTER) "العدادات" else "الرئيسية"

@Composable
private fun RemoteApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var client by remember { mutableStateOf<RemoteClient?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(listOf<Discovery.Found>()) }
    var tab by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(listOf<Item>()) }
    var tasks by remember { mutableStateOf(listOf<Item>()) }
    var info by remember { mutableStateOf(JSONObject()) }

    DisposableEffect(Unit) {
        val d = Discovery(context) { found = it }
        d.start()
        onDispose { d.stop(); client?.close() }
    }

    fun disconnect(msg: String = "") { client?.close(); client = null; status = msg }

    fun connect(host: String) {
        if (busy) return
        busy = true; status = "جاري الاتصال بـ $host …"
        scope.launch {
            val c = RemoteClient(host)
            runCatching { c.connect() }.onSuccess {
                client = c
                status = "متصل بـ ${it.optString("name")}"
            }.onFailure { c.close(); status = "تعذّر الاتصال: ${it.message ?: it.javaClass.simpleName}" }
            busy = false
        }
    }

    /** Sends one command over the open connection. */
    fun send(body: JSONObject, after: (JSONObject) -> Unit = {}) {
        val c = client ?: return
        scope.launch {
            runCatching { c.call(body) }.onSuccess { r ->
                status = r.optString("msg")
                if (r.optBoolean("ok")) after(r)
            }.onFailure { disconnect("انقطع الاتصال: ${it.message ?: ""}") }
        }
    }

    fun refresh() {
        send(JSONObject().put("op", RemoteProtocol.OP_PING)) { info = it.optJSONObject("data") ?: JSONObject() }
        send(JSONObject().put("op", RemoteProtocol.OP_APPS)) { r ->
            val a = r.optJSONObject("data")?.optJSONArray("apps")
            apps = (0 until (a?.length() ?: 0)).map { i -> a!!.getJSONObject(i).let {
                Item(it.getString("pkg"), it.optString("label"), null, decodeIcon(it.optString("icon"))) } }
        }
        send(JSONObject().put("op", RemoteProtocol.OP_TASKS)) { r ->
            val a = r.optJSONObject("data")?.optJSONArray("tasks")
            tasks = (0 until (a?.length() ?: 0)).map { i -> a!!.getJSONObject(i).let {
                Item(it.getString("pkg"), it.optString("label"), it.optString("screen"), decodeIcon(it.optString("icon"))) } }
        }
    }

    LaunchedEffect(client) { if (client != null) refresh() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("T1 Remote · السعودية", color = Gold, fontSize = 26.sp)
        if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 14.sp)
        val c = client
        when {
            c == null -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("خلّ الجوال والسيارة على نفس الواي فاي (مثلاً هوتسبوت الجوال)، وشغّل «التحكم من الجوال» في إعدادات T1.", color = Ink, fontSize = 15.sp)
                Text("اللانشرات القريبة", color = Ink, fontSize = 18.sp)
                if (found.isEmpty()) Text("جاري البحث… تأكد إن «التحكم من الجوال» شغّال في T1 وإن الجوال على نفس الشبكة.", color = Muted)
                found.forEach { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { connect(f.host) }.padding(14.dp)) {
                        Text(f.name, color = Ink, fontSize = 17.sp)
                    }
                }
                Text("اضغط على اللانشر للاتصال. ما يحتاج رمز ولا عنوان.", color = Muted, fontSize = 13.sp)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("متصل بـ ${c.host}", color = Ink, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { refresh() }) { Text("تحديث") }
                    OutlinedButton(onClick = { disconnect() }) { Text("قطع") }
                }
                TabRow(selectedTabIndex = tab, containerColor = Card, contentColor = Gold) {
                    listOf("التطبيقات", "النوافذ", "السيارة", "لوحتي").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                when (tab) {
                    0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(apps, key = { it.pkg }) { app ->
                            AppRow(app) {
                                Small("الرئيسية") { send(JSONObject().put("op", "open").put("pkg", app.pkg).put("screen", "main")) { refresh() } }
                                if (info.optBoolean("cluster")) Small("العدادات") { send(JSONObject().put("op", "open").put("pkg", app.pkg).put("screen", "cluster")) { refresh() } }
                            }
                        }
                    }
                    1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("النوافذ اللي يعرفها اللانشر (اللي فتحها هو أو الجوال). أندرويد ما يسمح بقائمة كل التطبيقات المفتوحة.", color = Muted, fontSize = 13.sp) }
                        items(tasks, key = { it.pkg }) { t ->
                            AppRow(t, sub = "على ${screenName(t.screen)}") {
                                Small("قدّمها") { send(JSONObject().put("op", "front").put("pkg", t.pkg)) }
                                val other = if (t.screen == "cluster") "main" else "cluster"
                                if (other == "main" || info.optBoolean("cluster"))
                                    Small("انقل لـ${screenName(other)}") { send(JSONObject().put("op", "move").put("pkg", t.pkg).put("screen", other)) { refresh() } }
                                if (info.optBoolean("freeform")) {
                                    Small("نصف يمين") { send(JSONObject().put("op", "resize").put("pkg", t.pkg).put("preset", "right_half")) }
                                    Small("نصف يسار") { send(JSONObject().put("op", "resize").put("pkg", t.pkg).put("preset", "left_half")) }
                                    Small("كامل") { send(JSONObject().put("op", "resize").put("pkg", t.pkg).put("preset", "full")) }
                                }
                                if (info.optBoolean("close")) Small("إغلاق") {
                                    send(JSONObject().put("op", "close").put("pkg", t.pkg)) { refresh() }
                                }
                            }
                        }
                        if (!info.optBoolean("freeform")) item { Text("تغيير الحجم غير متاح: نظام السيارة ما فيه وضع النوافذ الحرة مفعّل.", color = Muted, fontSize = 13.sp) }
                        if (!info.optBoolean("close")) item { Text("إغلاق نافذة تطبيق آخر غير متاح في هذا الإصدار.", color = Muted, fontSize = 13.sp) }
                    }
                    3 -> ArtTab(info.optBoolean("art") && info.optBoolean("cluster")) { send(it) }
                    else -> CarTab(info.optBoolean("vehicle")) { id, held ->
                        send(JSONObject().put("op", "vehicle").put("cmd", id).put("held_ms", held))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(item: Item, sub: String? = null, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item.icon?.let { Image(it, null, Modifier.size(36.dp)) }
            Column { Text(item.label, color = Ink, fontSize = 16.sp); if (sub != null) Text(sub, color = Muted, fontSize = 12.sp) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}

@Composable
private fun Small(label: String, onClick: () -> Unit) {
    Text(label, color = Color(0xFF123827), fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

private val carButtons = listOf(
    "النوافذ" to listOf("windows_open" to "فتح", "windows_close" to "إغلاق", "windows_vent" to "تهوية"),
    "فتحة السقف" to listOf("roof_open" to "فتح", "roof_close" to "إغلاق", "roof_tilt" to "رفع"),
    "الستارة" to listOf("shade_open" to "فتح", "shade_close" to "إغلاق", "shade_half" to "نصف")
)

@Composable
private fun CarTab(allowed: Boolean, fire: (String, Long) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!allowed) Text("أوامر السيارة مطفأة في T1. فعّل «السماح بالنوافذ والسقف والستارة من الجوال» من إعدادات اللانشر ثم اضغط تحديث.", color = Gold, fontSize = 14.sp)
        Text("اضغط مطوّلاً ثانية كاملة لتنفيذ الأمر. رفع الإصبع قبلها يلغيه.", color = Muted, fontSize = 13.sp)
        carButtons.forEach { (title, row) ->
            Text(title, color = Ink, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, label) -> HoldButton(label, allowed, Modifier.weight(1f)) { held -> fire(id, held) } }
            }
        }
    }
}

/** Fires once after a continuous ~1 s hold. A shorter press does nothing. */
@Composable
private fun HoldButton(label: String, enabled: Boolean, modifier: Modifier, onHeld: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    Box(modifier.height(64.dp).clip(RoundedCornerShape(12.dp)).background(if (enabled) Card else Card.copy(alpha = .4f))
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(onPress = {
                val pressedAt = System.currentTimeMillis()
                var fired = false
                val job: Job = scope.launch {
                    while (true) {
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
        Box(Modifier.fillMaxSize().padding(end = 0.dp)) {
            Box(Modifier.fillMaxWidth(progress).height(64.dp).background(Gold.copy(alpha = .35f)))
        }
        Text(label, color = if (enabled) Ink else Muted, fontSize = 16.sp)
    }
}
