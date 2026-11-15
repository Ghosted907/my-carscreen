package com.ghosted907.t1remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Bg, surface = CardColor)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().background(Bg).padding(horizontal = 14.dp, vertical = 12.dp)) { RemoteApp() }
                }
            }
        }
    }
}

/** Tabs; Wi-Fi is the one launcher feature deliberately absent. */
private val tabs = listOf("الرئيسية", "التطبيقات", "النوافذ", "العدادات", "السيارة", "المكيف", "البيانات", "الإعدادات")

@Composable
private fun RemoteApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PhoneStore(context) }
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
            Text("T1 Remote", color = Gold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            val (dot, label) = when (session.state) {
                Session.State.Connected -> Good to "متصل"
                Session.State.Connecting -> Gold to "يتصل…"
                Session.State.Reconnecting -> Gold to "يعيد الاتصال…"
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
            ScrollableTabRow(selectedTabIndex = tab, containerColor = CardColor, contentColor = Gold, edgePadding = 4.dp) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i; store.tab = i }, text = { Text(t) }) }
            }
            if (!session.connected) Note("الاتصال منقطع مؤقتاً؛ تُعاد المحاولة تلقائياً.", Gold)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeTab(session)
                    1 -> AppsTab(session)
                    2 -> WindowsTab(session)
                    3 -> ClusterTab(session)
                    4 -> CarTab(session)
                    5 -> ClimateTab(session)
                    6 -> DataTab(session)
                    else -> SettingsTab(session, store)
                }
            }
        }
    }
}

@Composable
private fun ConnectScreen(session: Session, store: PhoneStore, found: List<Discovery.Found>, problem: String) {
    var manual by remember { mutableStateOf(store.manualHost) }
    var auto by remember { mutableStateOf(store.autoConnect) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Note("خلّ الجوال والسيارة على نفس الواي فاي (مثلاً هوتسبوت الجوال). اللانشر يظهر هنا تلقائياً، بدون رمز أو اقتران.")
        Section("اللانشرات القريبة") {
            if (found.isEmpty()) Note(problem.ifEmpty { "جاري البحث… تأكد أن T1 يعمل على السيارة وأن الجوال على نفس الشبكة." })
            found.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg).clickable { session.connect(f.host, f.name) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = Ink, fontSize = 17.sp)
                        Text(f.host, color = Muted, fontSize = 12.sp)
                    }
                    if (f.host == store.lastHost) Text("آخر اتصال", color = Gold, fontSize = 12.sp)
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
