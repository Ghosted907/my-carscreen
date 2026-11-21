package com.ghosted907.t1remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ghosted907.t1remote.protocol.CloudConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { PhoneStore(this) }
            var theme by remember { mutableIntStateOf(store.theme) }
            val systemDark = isSystemInDarkTheme()
            PhoneTheme.day = when (theme) { 1 -> true; 2 -> false; else -> !systemDark }
            val scheme = if (PhoneTheme.day) lightColorScheme(primary = Btn, background = Bg, surface = CardColor, onSurface = Ink)
                else darkColorScheme(primary = Btn, background = Bg, surface = CardColor, onSurface = Ink)
            MaterialTheme(colorScheme = scheme) {
                // The phone UI is Arabic RTL on its own; the launcher's language setting never changes it.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(Bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        RemoteApp(store, theme) { theme = it; store.theme = it }
                    }
                }
            }
        }
    }
}

/** Tabs; Wi-Fi is deliberately absent. "المعروفة" = apps the launcher knows about, not every running app. */
private val tabs = listOf("الرئيسية", "التطبيقات", "المعروفة", "العدادات", "السيارة", "المكيف", "البيانات", "الإعدادات")

@Composable
private fun RemoteApp(store: PhoneStore, theme: Int, setTheme: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { Session(scope, store) }
    var found by remember { mutableStateOf(listOf<Discovery.Found>()) }
    var tab by remember { mutableIntStateOf(store.tab.coerceIn(0, tabs.lastIndex)) }
    val discovery = remember { Discovery(context) { found = it; session.onFound(it) } }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { discovery.start(); session.onForeground() }
                Lifecycle.Event.ON_STOP -> { discovery.stop(); session.onBackground() }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); discovery.stop(); session.onBackground() }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.saudi_emblem_gold), null, Modifier.size(30.dp))
            Text("T1 Remote", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(if (PhoneTheme.day) "☾" else "☀", color = Ink, fontSize = 20.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { setTheme(if (PhoneTheme.day) 2 else 1) }.padding(10.dp))
            val (dot, label) = when (session.state) {
                Session.State.Connected -> Good to "متصل"
                Session.State.Connecting -> Warn to "يتصل…"
                Session.State.Reconnecting -> Warn to "يعيد الاتصال…"
                Session.State.Idle -> Muted to "غير متصل"
            }
            Text("● $label", color = dot, fontSize = 14.sp)
        }
        if (session.status.isNotEmpty())
            Text(session.status, color = if (session.statusOk) Muted else Bad, fontSize = 14.sp, maxLines = 3)
        if (session.state == Session.State.Idle || (session.state != Session.State.Connected && session.epoch == 0)) {
            ConnectScreen(session, store, found, discovery.problem)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${session.name.ifEmpty { "T1" }} · ${session.host}", color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { session.disconnect() }) { Text("قطع") }
            }
            if (!session.connected) Note("الاتصال منقطع مؤقتاً؛ تُعاد المحاولة تلقائياً. لا يُعاد إرسال أي أمر.", Warn)
            val pick: (Int) -> Unit = { tab = it; store.tab = it }
            val page: @Composable () -> Unit = {
                when (tab) {
                    0 -> HomeTab(session) { pick(it) }
                    1 -> AppsTab(session)
                    2 -> WindowsTab(session)
                    3 -> ClusterTab(session)
                    4 -> CarTab(session)
                    5 -> ClimateTab(session)
                    6 -> DataTab(session)
                    else -> SettingsTab(session, store, theme, setTheme)
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                if (maxWidth > maxHeight && maxWidth >= 560.dp) {
                    // Landscape: the launcher's sidebar layout (rail on the start side, page beside it).
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.width(132.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(CardColor)
                            .verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tabs.forEachIndexed { i, t -> RailItem(t, tab == i) { pick(i) } }
                        }
                        Box(Modifier.weight(1f)) { page() }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrollableTabRow(selectedTabIndex = tab, containerColor = CardColor, contentColor = Accent, edgePadding = 4.dp) {
                        tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { pick(i) }, text = { Text(t, color = if (tab == i) Accent else Muted) }) }
                    }
                    Box(Modifier.weight(1f)) { page() }
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(session: Session, store: PhoneStore, found: List<Discovery.Found>, problem: String) {
    var manual by remember { mutableStateOf(store.manualHost) }
    var auto by remember { mutableStateOf(store.autoConnect) }
    var cloudCode by remember { mutableStateOf(store.cloudCode) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("اتصال عبر الإنترنت", "شغّل الربط عبر الإنترنت من إعدادات اللانشر، ثم انسخ رمز الربط هنا. الجوال والسيارة يقدرون يكونون على شبكتين مختلفتين.") {
            OutlinedTextField(cloudCode, { cloudCode = it.trim().take(80) }, singleLine = true,
                label = { Text("رمز الربط من السيارة") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Small("اتصل عبر الإنترنت", enabled = CloudConfig.pair(cloudCode) != null) { session.connectCloud(cloudCode) }
            if (store.cloudCode.isNotEmpty()) Small("نسيت هذا الربط") {
                session.disconnect(); store.forgetCloud(); cloudCode = ""; session.say("تم حذف رمز الربط من الجوال")
            }
            Note("تحتاج شاشة السيارة تكون شغالة ومتصلة بالإنترنت. الأوامر لا تُحفظ ولا يُعاد إرسالها بعد الانقطاع.")
        }
        Note("للاتصال المحلي فقط: خلّ الجوال والسيارة على نفس الواي فاي. يظهر اللانشر تلقائياً تحت اللانشرات القريبة.")
        Section("اللانشرات القريبة") {
            if (found.isEmpty()) Note(problem.ifEmpty { "جاري البحث… تأكد أن T1 يعمل على السيارة وأن الجوال على نفس الشبكة." })
            found.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card2).clickable { session.connect(f.host, f.name) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = Ink, fontSize = 17.sp)
                        Text(f.host, color = Muted, fontSize = 12.sp)
                    }
                    if (f.host == store.lastHost) Text("آخر اتصال", color = Accent, fontSize = 12.sp)
                }
            }
        }
        if (store.lastHost.isNotEmpty()) Section("آخر سيارة") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${store.lastName.ifEmpty { "T1" }} · ${store.lastHost}", color = Ink, modifier = Modifier.weight(1f))
                Small("اتصل") { session.connect(store.lastHost, store.lastName) }
                Small("انسَ") { store.forget(); session.say("تم نسيان آخر سيارة") }
            }
        }
        Section("عنوان يدوي", "إذا كان الراوتر يمنع البحث التلقائي: اكتب عنوان السيارة الظاهر في إعدادات T1 (التحكم من الجوال).") {
            OutlinedTextField(manual, { manual = it.trim().take(64) }, singleLine = true, label = { Text("مثال 192.168.43.20") }, modifier = Modifier.fillMaxWidth())
            Small("اتصل بهذا العنوان", enabled = ClientRules.validHost(manual)) { store.manualHost = manual; session.connect(manual) }
        }
        SwitchLine("اتصال تلقائي بآخر سيارة", auto) { auto = it; store.autoConnect = it }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Accent else Muted, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) SelFill else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp))
}
