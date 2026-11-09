package com.ghosted907.t1launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Bundle
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Page(val ar: String) {
    Home("الرئيسية"),
    Apps("التطبيقات"),
    Cluster("العدادات"),
    Camera("الكاميرا"),
    Vehicle("بيانات السيارة"),
    Customize("التخصيص"),
    Settings("الإعدادات")
}

object T1Colors {
    val nightBg = Color(0xFF101419)
    val nightCard = Color(0xFF1C252C)
    val nightCard2 = Color(0xFF202A31)
    val nightLine = Color(0xFF303B43)
    val nightMuted = Color(0xFF81929B)
    val nightText = Color(0xFFEDF2F3)
    val nightRail = Color(0xFF141A1F)
    val cyan = Color(0xFF8BD3D5)
    val cyanSoft = Color(0xFFB1E8E8)
    val sand = Color(0xFFC6BAA2)
    val dayBg = Color(0xFFE7EEF1)
    val dayCard = Color(0xFFF7FAFB)
    val dayText = Color(0xFF1A2428)
    val dayMuted = Color(0xFF5C6E76)
    val dayLine = Color(0xFFD5DEE2)
}

@Composable
fun T1Shell(
    page: Page,
    day: Boolean,
    snap: CarBridge.Snap,
    onPage: (Page) -> Unit,
    onMic: () -> Unit,
    onOemAssistant: () -> Unit,
    content: @Composable () -> Unit
) {
    val bg = if (day) T1Colors.dayBg else T1Colors.nightBg
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val arabic = FontFamily(Font(R.font.arabic))
    MaterialTheme(typography = Typography(bodyLarge = TextStyle(fontFamily = arabic))) {
    Row(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp, vertical = 12.dp)) {
            Header(day, snap, text, muted, onMic, onOemAssistant)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        Rail(page, day, onPage)
    }
    }
}

@Composable
private fun Header(day: Boolean, snap: CarBridge.Snap, text: Color, muted: Color, onMic: () -> Unit, onOemAssistant: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when (hour) {
        in 5..11 -> "صباح الخير يا وليد"
        in 12..16 -> "مساء النور يا وليد"
        else -> "مساء الخير يا وليد"
    }
    val clock = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    val temp = snap.temp?.let { "${it.toInt()}°" } ?: "--°"
    Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(hello, color = text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text("كل ما تحتاجه، في مكان واحد", color = muted, fontSize = 14.sp)
        }
        Mic(onMic, onOemAssistant)
        Spacer(Modifier.width(16.dp))
        Text(clock, color = text, fontSize = 34.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(16.dp))
        Text(temp, color = if (day) T1Colors.dayMuted else T1Colors.sand, fontSize = 22.sp)
        Spacer(Modifier.width(16.dp))
        val mode = if (snap.theme == 2) "كلاسيكي" else if (snap.theme != null) "ثيم ${snap.theme}" else "كلاسيكي"
        Pill(if (day) T1Colors.dayLine else Color(0xFF3B4448), text, "$mode · هدوء")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Mic(onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF35565E), Color(0xFF263D46))))
            .border(1.dp, Color(0xFF55747E), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) { Text("🎤", fontSize = 20.sp) }
}

@Composable
private fun Pill(border: Color, text: Color, label: String) {
    Text(
        label,
        color = text,
        fontSize = 14.sp,
        modifier = Modifier.border(1.dp, border, RoundedCornerShape(30.dp)).padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun Rail(page: Page, day: Boolean, onPage: (Page) -> Unit) {
    val bg = if (day) Color(0xFFF2F6F7) else T1Colors.nightRail
    val idle = if (day) T1Colors.dayMuted else Color(0xFF85939C)
    Column(
        Modifier.width(168.dp).fillMaxHeight().background(bg).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("T1", color = if (day) T1Colors.dayText else Color(0xFFE4E9E9), fontSize = 22.sp, letterSpacing = 4.sp)
        Text("YOUR SPACE", color = idle, fontSize = 9.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Page.entries.forEach { item ->
            val active = item == page
            Text(
                item.ar,
                color = if (active) T1Colors.cyanSoft else idle,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Color(0xFF1C393F) else Color.Transparent)
                    .clickable { onPage(item) }
                    .padding(horizontal = 10.dp, vertical = 14.dp)
                    .fillMaxWidth()
            )
        }
        Spacer(Modifier.weight(1f))
        Text("HOME / 01", color = idle, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun HomePage(
    day: Boolean,
    snap: CarBridge.Snap,
    apps: List<LaunchableApp>,
    toCluster: Boolean,
    onDestination: (Boolean) -> Unit,
    onOpenNav: () -> Unit,
    onQuick: (String) -> Unit,
    onApp: (LaunchableApp) -> Unit
) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1.15f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MapCard(Modifier.weight(1.46f).fillMaxHeight(), day, onOpenNav)
            MediaCard(Modifier.weight(1f).fillMaxHeight(), day)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Quick("كاميرا 360", snap.avmOpen?.let { if (it) "مفتوحة" else "مقفلة" } ?: "--", true, day) { onQuick("avm") }
            Quick("شغّل على العدادات", null, false, day) { onQuick("cluster") }
            Quick("قوالب الكلاسيكي", null, false, day) { onQuick("classic") }
            Quick("إخفاء الإضافات", null, false, day) { onQuick("hide") }
            Quick("العدادات الأصلية", null, false, day) { onQuick("stock") }
            Quick("النوافذ والفتحة", "ضغطة واحدة", false, day) { onQuick("windows") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.weight(0.95f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VehicleCard(Modifier.weight(0.92f).fillMaxHeight(), day, snap)
            FavoritesCard(Modifier.weight(1.08f).fillMaxHeight(), day, text, muted, apps, toCluster, onDestination, onApp)
        }
    }
}

@Composable
private fun cardBrush(day: Boolean) = if (day) {
    Brush.linearGradient(listOf(T1Colors.dayCard, Color(0xFFEEF3F5)))
} else {
    Brush.linearGradient(listOf(T1Colors.nightCard2, Color(0xFF192127)))
}

@Composable
private fun MapCard(modifier: Modifier, day: Boolean, onOpen: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(cardBrush(day))
            .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("250 م", color = T1Colors.cyanSoft, fontSize = 22.sp)
            Text("انعطف يساراً", color = if (day) T1Colors.dayMuted else Color(0xFFC7D4D9), fontSize = 14.sp)
        }
        Text(
            "Waze",
            color = if (day) T1Colors.dayText else Color(0xFFD1DDDD),
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                .background(Color(0xCC131C20), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.Bottom) {
            Text("12", color = if (day) T1Colors.dayText else Color.White, fontSize = 26.sp)
            Text("  دقيقة   ·   8.4 كم", color = if (day) T1Colors.dayMuted else Color(0xFF94A8B0), fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text("فتح الملاحة", color = T1Colors.cyanSoft, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MediaCard(modifier: Modifier, day: Boolean) {
    val context = LocalContext.current
    val media = remember { MediaHub(context) }
    var title by remember { mutableStateOf(if (media.allowed()) "بانتظار المقطع" else "اسمح بالإشعارات") }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        media.bind { state ->
            title = state.title ?: title
            playing = state.playing
        }
    }
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(cardBrush(day))
            .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("الآن تستمع", color = if (day) T1Colors.dayMuted else Color(0xFFA9B5BA), fontSize = 14.sp)
            Text("BLUETOOTH AUDIO", color = if (day) T1Colors.dayMuted else Color(0xFF81949D), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 24.sp, maxLines = 1)
        Text(if (media.allowed()) "مكتبتك الموسيقية" else "التحكم يظهر بعد منح إذن الإشعارات", color = if (day) T1Colors.dayMuted else Color(0xFF9AAAB2), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton("▶") { if (media.allowed()) media.play() else AppIndex.openListenerSettings(context) }
            Spacer(Modifier.width(12.dp))
            TextButton(if (playing) "Ⅱ" else "▶") { if (media.allowed()) media.toggle() else AppIndex.openListenerSettings(context) }
        }
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).border(1.dp, Color(0xFF8AA4A6), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = T1Colors.cyanSoft, fontSize = 18.sp) }
}

@Composable
private fun Quick(title: String, sub: String?, accent: Boolean, day: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val border = when {
        !enabled -> if (day) T1Colors.dayLine else Color(0xFF373F45)
        accent -> Color(0xFF608F94)
        else -> if (day) T1Colors.dayLine else Color(0xFF344149)
    }
    Column(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp))
            .background(if (enabled) cardBrush(day) else Brush.linearGradient(listOf(Color(0xFF232A2F), Color(0xFF1B2227))))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = if (enabled) (if (day) T1Colors.dayText else T1Colors.nightText) else Color(0xFF8A969C), fontSize = 16.sp, maxLines = 2)
        if (sub != null) Text(sub, color = if (enabled) T1Colors.cyanSoft else T1Colors.sand, fontSize = 12.sp)
    }
}

@Composable
private fun VehicleCard(modifier: Modifier, day: Boolean, snap: CarBridge.Snap) {
    val text = if (day) T1Colors.dayText else Color(0xFFE7EEF0)
    val muted = if (day) T1Colors.dayMuted else Color(0xFF8298A2)
    val bridge = (LocalContext.current.applicationContext as T1App).car
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(cardBrush(day))
            .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("بيانات السيارة", color = text, fontSize = 16.sp)
            Text(if (snap.carOk || snap.clusterOk) "مباشر" else "غير متصل", color = T1Colors.sand, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    listOf("P", "R", "N", "D").forEach { letter ->
                        val on = bridge.gearLetter(snap.gear) == letter
                        Text(
                            letter,
                            color = if (on) T1Colors.cyanSoft else muted,
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .background(if (on) Color(0xFF294A50) else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            fontSize = 16.sp
                        )
                    }
                }
                Text(bridge.modeLabel(snap.driveMode), color = Color(0xFFA2BDA8), fontSize = 12.sp)
            }
            Metric(num(snap.speed) , "كم/س", "السرعة", text, muted)
            Metric(snap.rangeKm?.toString() ?: "--", "كم", "المدى", text, muted)
            Metric(snap.fuel?.toString() ?: "--", "%", "الوقود", text, muted)
        }
        Text("«افتح يوتيوب على العدادات»   «شغّل كاميرا ٣٦٠»   «طبّق القالب الليلي»", color = muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        if (snap.note.isNotBlank()) Text(snap.note, color = T1Colors.sand, fontSize = 12.sp)
    }
}

@Composable
private fun Metric(value: String, unit: String, label: String, text: Color, muted: Color) {
    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.End) {
        Text(value, color = text, fontSize = 28.sp, fontFamily = FontFamily.SansSerif)
        Text("$unit  $label", color = muted, fontSize = 12.sp)
    }
}

private fun num(speed: Float?) = speed?.let { it.toInt().toString() } ?: "--"

@Composable
private fun FavoritesCard(
    modifier: Modifier,
    day: Boolean,
    text: Color,
    muted: Color,
    apps: List<LaunchableApp>,
    toCluster: Boolean,
    onDestination: (Boolean) -> Unit,
    onApp: (LaunchableApp) -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(cardBrush(day))
            .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تطبيقاتك المفضلة", color = text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            DestChip("الشاشة", !toCluster) { onDestination(false) }
            DestChip("العدادات", toCluster) { onDestination(true) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.take(5).forEach { app ->
                Column(Modifier.weight(1f).clickable { onApp(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFF35434C)),
                        contentAlignment = Alignment.Center
                    ) { Text(app.label.take(1), color = T1Colors.cyanSoft) }
                    Text(app.label, color = muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Text("المتاجر: RuStore · متجر التطبيقات", color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DestChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFFD8E9EB) else Color(0xFF7E929D),
        fontSize = 13.sp,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF344951) else Color(0xFF121B21))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
fun AppsPage(day: Boolean, toCluster: Boolean, onDestination: (Boolean) -> Unit, onLaunch: (LaunchableApp) -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppIndex.launchable(context) }
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("كل التطبيقات", color = text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            DestChip("الشاشة", !toCluster) { onDestination(false) }
            DestChip("العدادات", toCluster) { onDestination(true) }
        }
        Text(
            if (toCluster) "الوجهة: شاشة العدادات" else "الوجهة: الشاشة الرئيسية",
            color = T1Colors.sand,
            fontSize = 13.sp
        )
        LazyVerticalGrid(GridCells.Adaptive(140.dp), modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(cardBrush(day)).clickable { onLaunch(app) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(app.label, color = text, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(cardBrush(day)).clickable { AppIndex.openInstaller(context) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { Text("تثبيت APK", color = text, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
fun ClusterPage(
    day: Boolean,
    snap: CarBridge.Snap,
    prefs: T1Prefs,
    freeform: Boolean,
    onApplyClassic: () -> Unit,
    onHide: () -> Unit,
    onStock: () -> Unit,
    onLaunchSample: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit
) {
    var x by remember { mutableIntStateOf(prefs.clusterX) }
    var y by remember { mutableIntStateOf(prefs.clusterY) }
    var w by remember { mutableIntStateOf(prefs.clusterW) }
    var h by remember { mutableIntStateOf(prefs.clusterH) }
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    fun save(nx: Int, ny: Int, nw: Int, nh: Int) {
        val box = ClusterPresets.custom(nx, ny, nw, nh)
        x = box.left
        y = box.top
        w = box.width
        h = box.height
        prefs.clusterX = x
        prefs.clusterY = y
        prefs.clusterW = w
        prefs.clusterH = h
    }
    fun preset(box: ClusterPresets.Box) {
        save(box.left, box.top, box.width, box.height)
        onApply()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("مساحة تطبيقك على العدادات", color = text, fontSize = 26.sp)
        Text("القياس بنقاط 1920×720. تطبيق يعيد النافذة الحرة على شاشة العدادات.", color = muted, fontSize = 13.sp)
        if (!freeform) {
            Text(
                "الوضع الحر غير مفعّل، فالقياس يُتجاهل والتطبيق يفتح ملء الشاشة.\n${ClusterLaunch.FREEFORM_COMMANDS}\nثم أعد تشغيل السيارة.",
                color = Color(0xFF173B47),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(Color(0xFFE7C98A), RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.height(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.4f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color(0xFF0B1014)).padding(12.dp)) {
                Text("DISPLAY  ·  ${Displays.clusterId(LocalContext.current)?.toString() ?: "1"}", color = muted, fontSize = 12.sp)
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp).border(1.dp, Color(0xFF3D4D57), RoundedCornerShape(16.dp))) {
                    val left = maxWidth * (x / 1920f)
                    val top = maxHeight * (y / 720f)
                    val boxW = maxWidth * (w / 1920f)
                    val boxH = maxHeight * (h / 720f)
                    Box(
                        Modifier.offset(left, top).size(boxW, boxH).background(Color(0x6638C8C8), RoundedCornerShape(6.dp))
                    )
                    Text("$x $y  ${x + w} ${y + h}", color = T1Colors.cyanSoft, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("الموضع $x,$y    القياس ${w}×${h}", color = text)
                Row {
                    Step("- س") { save(x - 40, y, w, h) }
                    Step("+ س") { save(x + 40, y, w, h) }
                    Step("- ص") { save(x, y - 40, w, h) }
                    Step("+ ص") { save(x, y + 40, w, h) }
                }
                Row {
                    Step("- عرض") { save(x, y, w - 40, h) }
                    Step("+ عرض") { save(x, y, w + 40, h) }
                    Step("- ارتفاع") { save(x, y, w, h - 40) }
                    Step("+ ارتفاع") { save(x, y, w, h + 40) }
                }
                Text(
                    if (prefs.clusterPackage.isBlank()) "لم يُفتح تطبيق على العدادات بعد." else "على العدادات: ${prefs.clusterPackage}",
                    color = muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Step("كامل") { preset(ClusterPresets.full()) }
            Step("وسط") { preset(ClusterPresets.center()) }
            Step("نصف أيسر") { preset(ClusterPresets.leftHalf()) }
            Step("نصف أيمن") { preset(ClusterPresets.rightHalf()) }
            Step("مخصص") { save(x, y, w, h) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Step("تطبيق") { onApply() }
            Step("أزل التطبيق من العدادات") { onClear() }
            Step("فتح Waze على العدادات") { onLaunchSample() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Step("كلاسيكي") { onApplyClassic() }
            Step("إخفاء الإضافات") { onHide() }
            Step("العدادات الأصلية") { onStock() }
            Text("ثيم ${snap.theme ?: "--"}  خط ${snap.font ?: "--"}", color = muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = T1Colors.cyanSoft,
        fontSize = 14.sp,
        modifier = Modifier.padding(4.dp).border(1.dp, Color(0xFF435964), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@Composable
fun WindowsPage(day: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val commands = remember { WindowCommands.buttons(context) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("النوافذ والفتحة", color = text, fontSize = 26.sp, modifier = Modifier.weight(1f))
            Text(
                "إيقاف / سكّر الكل",
                color = Color(0xFF173B47),
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color(0xFFE7C98A), RoundedCornerShape(12.dp))
                    .clickable { WindowCommands.sendStop(context) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
        Text("يُرسل إلى مساعد السيارة. ضغطة واحدة، أثناء القيادة أيضاً. الإيقاف يبقى ظاهراً.", color = muted, fontSize = 13.sp)
        LazyVerticalGrid(
            GridCells.Fixed(2),
            modifier = Modifier.padding(top = 12.dp).weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(commands, key = { it.id }) { command ->
                Text(
                    command.label,
                    color = text,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBrush(day))
                        .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(16.dp))
                        .clickable { WindowCommands.send(context, command) }
                        .padding(12.dp)
                )
            }
        }
        Text("رجوع", color = T1Colors.cyanSoft, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
    }
}

@Composable
fun SettingsPage(
    day: Boolean,
    edgeOn: Boolean,
    edgeRight: Boolean,
    overlayOk: Boolean,
    onDay: () -> Unit,
    onEdge: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit,
    onOverlaySettings: () -> Unit,
    onRecheck: () -> Unit
) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val line = if (day) T1Colors.dayLine else T1Colors.nightLine
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("الإعدادات", color = text, fontSize = 28.sp)
        if (!overlayOk) {
            Text(
                "سحب الحافة يحتاج صلاحية الظهور فوق التطبيقات.\nمن الكمبيوتر:\nadb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow\nثم أعد فتح T1 أو اضغط تحقّق.",
                color = text,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE7C98A))
                    .padding(16.dp)
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChip("فتح إعدادات الظهور", true, onOverlaySettings)
                SettingsChip("تحقّق", false, onRecheck)
            }
        }
        Text("سحب الحافة", color = text, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp))
        Text(
            if (edgeOn) "مفعّل. اسحب من حافة الشاشة نحو الوسط لفتح T1 فوق أي تطبيق." else "متوقف. الحافة لا تعترض اللمس.",
            color = muted,
            fontSize = 14.sp
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsChip(if (edgeOn) "مفعّل" else "متوقف", edgeOn, { onEdge(!edgeOn) })
            SettingsChip("اليمين", edgeRight, { onRight(true) })
            SettingsChip("اليسار", !edgeRight, { onRight(false) })
        }
        Text(
            "الخدمة تبدأ مع الإقلاع ومع فتح T1. T1 لا يستبدل الشاشة الأصلية.",
            color = muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            if (day) "الوضع الليلي" else "وضع النهار",
            color = Color(0xFF173B47),
            modifier = Modifier
                .padding(top = 12.dp)
                .background(T1Colors.cyan, RoundedCornerShape(12.dp))
                .border(1.dp, line, RoundedCornerShape(12.dp))
                .clickable(onClick = onDay)
                .padding(16.dp)
        )
    }
}

@Composable
private fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF173B47) else T1Colors.nightText,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) T1Colors.cyan else Color(0xFF1C393F))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
fun SimplePage(title: String, body: String, day: Boolean, action: String? = null, onAction: (() -> Unit)? = null) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text(title, color = text, fontSize = 28.sp)
        Text(body, color = muted, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        if (action != null && onAction != null) {
            Text(action, color = Color(0xFF173B47), modifier = Modifier.padding(top = 16.dp).background(T1Colors.cyan, RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(16.dp))
        }
    }
}

@Composable
fun VoiceButton(onHeard: (String) -> Unit) {
    val context = LocalContext.current
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListen(context, onHeard) else Toast.makeText(context, "الميكروفون مرفوض", Toast.LENGTH_SHORT).show()
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(context, "لا يوجد تعرّف صوت على هذا الجهاز", Toast.LENGTH_LONG).show()
        return
    }
    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (granted) startListen(context, onHeard) else grant.launch(Manifest.permission.RECORD_AUDIO)
}

fun beginVoice(context: android.content.Context, onHeard: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Toast.makeText(context, "لا يوجد تعرّف صوت على هذا الجهاز", Toast.LENGTH_LONG).show()
        return
    }
    startListen(context, onHeard)
}

private fun startListen(context: android.content.Context, onHeard: (String) -> Unit) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(bundle: Bundle?) {
            val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onHeard(text)
            recognizer.destroy()
        }
        override fun onError(error: Int) {
            Toast.makeText(context, "تعذّر التعرّف ($error)", Toast.LENGTH_SHORT).show()
            recognizer.destroy()
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    recognizer.startListening(intent)
}
