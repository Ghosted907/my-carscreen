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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
    Controls("التحكم"),
    Customize("التخصيص"),
    Settings("الإعدادات")
}

object T1Colors {
    val nightBg = Color(0xFF0A1210)
    val nightCard = Color(0xFF17221E)
    val nightCard2 = Color(0xFF20362B)
    val nightLine = Color(0xFF425646)
    val nightMuted = Color(0xFFA4B8A9)
    val nightText = Color(0xFFF4F0E5)
    val nightRail = Color(0xFF101D17)
    val gold = Color(0xFFD5B36A)
    val goldSoft = Color(0xFFF0DDA6)
    val cyan = gold
    val cyanSoft = goldSoft
    val sand = gold
    val dayBg = Color(0xFFF2F0E6)
    val dayCard = Color(0xFFFCFBF4)
    val dayText = Color(0xFF123827)
    val dayMuted = Color(0xFF5E7465)
    val dayLine = Color(0xFFD8CFB4)
}

@Composable
private fun cardBrush(day: Boolean) = Brush.linearGradient(
    if (day) listOf(T1Colors.dayCard, Color(0xFFF0EBD9)) else listOf(T1Colors.nightCard, Color(0xFF0B271D))
)

@Composable
private fun DestChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Color(0xFF123827) else T1Colors.nightText,
        fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(if (selected) T1Colors.sand else Color(0xFF294435))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsPage(
    day: Boolean,
    toCluster: Boolean,
    apps: List<LaunchableApp>,
    pinned: Set<String>,
    recent: List<String>,
    onDestination: (Boolean) -> Unit,
    onPin: (LaunchableApp) -> Unit,
    onLaunch: (LaunchableApp) -> Unit
) {
    val context = LocalContext.current
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val recentItems = recent.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("كل التطبيقات", color = text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            DestChip("الجهاز اللوحي", !toCluster) { onDestination(false) }
            DestChip("لوحة العدادات", toCluster) { onDestination(true) }
        }
        Text(
            if (toCluster) "تشغيل على لوحة العدادات" else "تشغيل على الجهاز اللوحي",
            color = T1Colors.sand,
            fontSize = 13.sp
        )
        if (recentItems.isNotEmpty()) {
            Text("المستخدمة مؤخراً", color = text, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentItems.forEach { app ->
                    Text(app.label, color = text, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cardBrush(day))
                            .clickable { onLaunch(app) }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
        Text("اضغط مطولاً على التطبيق لتثبيته في مفضلة الرئيسية", color = T1Colors.sand,
            fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        LazyVerticalGrid(GridCells.Adaptive(140.dp), modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(cardBrush(day))
                        .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onPin(app) })
                        .heightIn(min = 100.dp).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppArtwork(app)
                    Spacer(Modifier.height(8.dp))
                    Text(app.label, color = text, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
                    if (app.packageName in pinned) Text("★ المفضلة", color = T1Colors.gold, fontSize = 11.sp)
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
    onApplyClassic: () -> Unit,
    onHide: () -> Unit,
    onStock: () -> Unit,
    onClear: () -> Unit
) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("العدادات", color = text, fontSize = 26.sp)
        Text("تشغيل التطبيق على لوحة العدادات بطريقة JCarTools: setLaunchDisplayId على فهرس الشاشة، بدون نافذة حرة.", color = muted, fontSize = 13.sp)
        JctClusterAppCard(day)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Step("كلاسيكي") { onApplyClassic() }
            Step("إخفاء الإضافات") { onHide() }
            Step("العدادات الأصلية") { onStock() }
            Step("إغلاق تطبيق العدادات") { onClear() }
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
        modifier = Modifier.padding(4.dp).border(1.dp, T1Colors.nightLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)
    )
}

@Composable
fun WindowsPage(day: Boolean, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as T1App
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        VehicleControlPanel(day, app.car.snap)
        Step("رجوع", onBack)
    }
}

@Composable
fun SettingsPage(
    day: Boolean,
    overlayOk: Boolean,
    onDay: () -> Unit,
    onOverlaySettings: () -> Unit,
    onRecheck: () -> Unit,
    onButtons: () -> Unit
) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val line = if (day) T1Colors.dayLine else T1Colors.nightLine
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Text("الإعدادات", color = text, fontSize = 28.sp)
        SettingsChip("تشغيل التطبيقات · الأزرار العائمة · مناطق الحافة · إخفاء التطبيقات", true, onButtons)
        if (!overlayOk) {
            Text(
                "الأزرار العائمة ومربع الاستعادة تحتاج صلاحية الظهور فوق التطبيقات.\nمن الكمبيوتر:\nadb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow\nثم أعد فتح T1 أو اضغط تحقّق.",
                color = text,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(T1Colors.goldSoft)
                    .padding(16.dp)
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChip("فتح إعدادات الظهور", true, onOverlaySettings)
                SettingsChip("تحقّق", false, onRecheck)
            }
        }
        Text(
            "الخدمة تبدأ مع الإقلاع ومع فتح T1. T1 لا يستبدل الشاشة الأصلية.",
            color = muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            if (day) "الوضع الليلي" else "وضع النهار",
            color = Color(0xFF123827),
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
        color = if (selected) Color(0xFF123827) else T1Colors.nightText,
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) T1Colors.gold else Color(0xFF173B2B))
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
            Text(action, color = Color(0xFF123827), modifier = Modifier.padding(top = 16.dp).background(T1Colors.gold, RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(16.dp))
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
