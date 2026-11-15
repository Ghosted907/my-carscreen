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
import androidx.compose.ui.graphics.compositeOver
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
    Climate("التكييف"),
    Customize("التخصيص"),
    Settings("الإعدادات")
}

object T1Colors {
    val nightBg = Color(0xFF081C14)
    val nightCard = Color(0xFF17221E)
    val nightCard2 = Color(0xFF20362B)
    val nightLine = Color(0xFF425646)
    val nightMuted = Color(0xFFA4B8A9)
    val nightText = Color(0xFFF4F0E5)
    val nightRail = Color(0xFF101D17)
    // 1.15.7: gold removed from the UI (only the emblem image stays gold).
    // Former gold names now map to the settings-button text colour.
    val gold = Color(0xFFF4F0E5)
    val goldSoft = Color(0xFFF4F0E5)
    val chipSelected = Color(0xFF2F6B4C)
    val cyan = gold
    val cyanSoft = goldSoft
    val sand = gold
    val dayBg = Color(0xFFF3F1E7)
    val dayCard = Color(0xFFFCFBF4)
    val dayText = Color(0xFF123827)
    val dayMuted = Color(0xFF5E7465)
    val dayLine = Color(0xFFCFD8D0)
    // 1.15.8: buttons, tiles and cards on every page except Home and menus are
    // 80% opaque (alpha 0xCC, 20% see-through). Text and icons stay fully opaque.
    const val BUTTON_ALPHA = .8f
    fun fill(c: Color) = c.copy(alpha = BUTTON_ALPHA)

    // 1.15.12: secondary pages copy the left sidebar (DashboardNavigation) colours exactly.
    val railDay = Color(0xCCE9E6DA)     // sidebar background, day (80% alpha)
    val railNight = Color(0xCC07110F)   // sidebar background, night (80% alpha)
    fun railBg(day: Boolean) = if (day) railDay else railNight
    /** Sidebar label/icon colour (unselected) = muted(day). */
    fun railText(day: Boolean) = if (day) dayMuted else nightMuted
    /** Sidebar selected label colour = ink(day). */
    fun railSelText(day: Boolean) = if (day) dayText else nightText
    /** Sidebar selected item fill = accent(day) at 12% over the sidebar background. */
    fun railSelFill(day: Boolean) = railSelText(day).copy(alpha = .12f)
}

/** True while composing the Home page (original colours). Everything else is a secondary page. */
val LocalHomePage = androidx.compose.runtime.staticCompositionLocalOf { false }
/** Day/night for the secondary-page sidebar colours. */
val LocalT1Day = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Content background: unchanged on Home, the sidebar background on secondary pages. */
@Composable
fun Modifier.t1Bg(original: Color, shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape): Modifier =
    if (LocalHomePage.current) background(original, shape) else background(T1Colors.railBg(LocalT1Day.current), shape)

@Composable
fun Modifier.t1Bg(original: Brush, shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape): Modifier =
    if (LocalHomePage.current) background(original, shape) else background(T1Colors.railBg(LocalT1Day.current), shape)

/** Single-colour fill for Material components (buttons, switch tracks, chips). */
@Composable
fun t1Solid(original: Color): Color = if (LocalHomePage.current) original else T1Colors.railBg(LocalT1Day.current)

/** Selected state on secondary pages: the sidebar's selected fill (accent at 12%) over the background. */
@Composable
fun Modifier.t1Selected(selected: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (selected && !LocalHomePage.current) background(T1Colors.railSelFill(LocalT1Day.current), shape) else this

/** Text/icons on secondary-page cards and buttons: the sidebar label colour. */
@Composable
fun t1CardText(day: Boolean): Color =
    if (!LocalHomePage.current) T1Colors.railText(day) else if (day) T1Colors.dayText else T1Colors.nightText

/** Subtitle variant of [t1CardText] (the sidebar has one label colour, so it is the same). */
@Composable
fun t1CardMuted(day: Boolean): Color =
    if (!LocalHomePage.current) T1Colors.railText(day) else if (day) T1Colors.dayMuted else T1Colors.nightMuted

/** Selected item text: the sidebar selected label colour. */
@Composable
fun t1SelText(day: Boolean): Color = if (day) T1Colors.dayText else T1Colors.nightText

/** Content colour for buttons on secondary pages (null = Material default on Home). */
@Composable
fun t1OnButton(): Color? = if (LocalHomePage.current) null else T1Colors.railText(LocalT1Day.current)

@Composable
fun t1ChipColors(): androidx.compose.material3.SelectableChipColors {
    val day = LocalT1Day.current
    return if (LocalHomePage.current) androidx.compose.material3.FilterChipDefaults.filterChipColors()
    else androidx.compose.material3.FilterChipDefaults.filterChipColors(
        containerColor = T1Colors.railBg(day), labelColor = T1Colors.railText(day),
        selectedContainerColor = T1Colors.railSelFill(day).compositeOver(T1Colors.railBg(day)),
        selectedLabelColor = T1Colors.railSelText(day))
}

/** Switch colours on secondary pages, built from the sidebar tokens. */
@Composable
fun t1SwitchColors(): androidx.compose.material3.SwitchColors {
    val day = LocalT1Day.current
    return if (LocalHomePage.current) androidx.compose.material3.SwitchDefaults.colors()
    else androidx.compose.material3.SwitchDefaults.colors(
        checkedTrackColor = T1Colors.railSelFill(day).compositeOver(T1Colors.railBg(day)),
        checkedThumbColor = T1Colors.railSelText(day), checkedBorderColor = T1Colors.railSelText(day),
        uncheckedTrackColor = T1Colors.railBg(day), uncheckedThumbColor = T1Colors.railText(day),
        uncheckedBorderColor = T1Colors.railText(day))
}

/** 1.15.8: OutlinedButton with an 80%-opaque fill instead of a transparent one. */
@Composable
fun T1OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                     border: androidx.compose.foundation.BorderStroke? = null,
                     content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
        containerColor = t1Solid(T1Colors.fill(MaterialTheme.colorScheme.surface)),
        contentColor = t1OnButton() ?: MaterialTheme.colorScheme.primary)
    if (border == null) androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier,
        enabled = enabled, colors = colors, content = content)
    else androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier,
        enabled = enabled, colors = colors, border = border, content = content)
}

/** 1.15.8: filled Button at 80% opacity. */
@Composable
fun T1Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
             content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = t1Solid(T1Colors.fill(MaterialTheme.colorScheme.primary)),
            contentColor = t1OnButton() ?: MaterialTheme.colorScheme.onPrimary), content = content)
}

@Composable
private fun cardBrush(day: Boolean): Brush = if (!LocalHomePage.current) androidx.compose.ui.graphics.SolidColor(T1Colors.railBg(day)) else Brush.linearGradient(
    (if (day) listOf(T1Colors.dayCard, Color(0xFFF0EBD9)) else listOf(T1Colors.nightCard, Color(0xFF0B271D))).map(T1Colors::fill)
)

@Composable
private fun DestChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (LocalHomePage.current) T1Colors.nightText else if (selected) t1SelText(LocalT1Day.current) else T1Colors.railText(LocalT1Day.current),
        fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)
            .clip(RoundedCornerShape(12.dp)).t1Bg(T1Colors.fill(if (selected) T1Colors.chipSelected else Color(0xFF294435)))
            .t1Selected(selected, RoundedCornerShape(12.dp))
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
            color = text,
            fontSize = 13.sp
        )
        if (recentItems.isNotEmpty()) {
            Text("المستخدمة مؤخراً", color = text, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentItems.forEach { app ->
                    Text(app.label, color = t1CardText(day), fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cardBrush(day))
                            .clickable { onLaunch(app) }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
        Text("اضغط مطولاً على التطبيق لتثبيته في مفضلة الرئيسية", color = text,
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
                    Text(app.label, color = t1CardText(day), fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
                    if (app.packageName in pinned) Text("★ المفضلة", color = t1CardText(day), fontSize = 11.sp)
                }
            }
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(cardBrush(day)).clickable { AppIndex.openInstaller(context) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { Text("تثبيت APK", color = t1CardText(day), fontSize = 14.sp) }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val display = JctLaunch.displays(context).getOrNull(JctLaunch.priIndex(context))
        ?.takeUnless { it.displayId == android.view.Display.DEFAULT_DISPLAY }
    val displaySize = android.graphics.Point().also { display?.getRealSize(it) }
    val ratio = if (displaySize.x > 0 && displaySize.y > 0) displaySize.x.toFloat() / displaySize.y else 1920f / 720f
    val boardWidth = minOf(maxWidth, maxHeight * ratio)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsArtBoard(day, Modifier.align(Alignment.CenterHorizontally).width(boardWidth).height(boardWidth / ratio))
        Spacer(Modifier.height(16.dp))
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
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = t1OnButton() ?: T1Colors.nightText,
        fontSize = 14.sp,
        modifier = Modifier.padding(4.dp).t1Bg(T1Colors.fill(Color(0xFF173B2B)), RoundedCornerShape(12.dp)).border(1.dp, T1Colors.nightLine, RoundedCornerShape(12.dp))
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
    onPermissions: () -> Unit
) {
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("الإعدادات", color = text, fontSize = 28.sp)
        SettingsChip(if (day) "الوضع الليلي" else "وضع النهار", true, onDay)
        LauncherWallpaperSettings()
        SettingsChip("إدارة أذونات اللانشر", false, onPermissions)
        RemoteSettingsCard(day)
        JctSettingsContent(day)
        if (!overlayOk) {
            Text(
                "الأزرار العائمة ومربع الاستعادة تحتاج صلاحية الظهور فوق التطبيقات.\nمن الكمبيوتر:\nadb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow\nثم أعد فتح T1 أو اضغط تحقّق.",
                color = t1CardText(day),
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .t1Bg(T1Colors.fill(if (day) T1Colors.dayCard else T1Colors.nightCard2))
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
    }
}

@Composable
private fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (LocalHomePage.current) T1Colors.nightText else if (selected) t1SelText(LocalT1Day.current) else T1Colors.railText(LocalT1Day.current),
        fontSize = 15.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .t1Bg(T1Colors.fill(if (selected) T1Colors.chipSelected else Color(0xFF173B2B)))
            .t1Selected(selected, RoundedCornerShape(12.dp))
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
            Text(action, color = t1OnButton() ?: T1Colors.nightText, modifier = Modifier.padding(top = 16.dp).t1Bg(T1Colors.fill(Color(0xFF173B2B)), RoundedCornerShape(12.dp)).clickable(onClick = onAction).padding(16.dp))
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
