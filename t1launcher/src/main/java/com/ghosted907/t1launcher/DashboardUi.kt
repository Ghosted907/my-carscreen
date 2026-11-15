package com.ghosted907.t1launcher

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private val Sand = T1Colors.nightText // 1.15.7: no gold
private val Mint = Color(0xFF94C9A5)
private val JetourGold = Color(0xFFD5B36A) // emblem gold, used only for the JETOUR T1 wordmark
private val CardShape = RoundedCornerShape(16.dp)
private fun ink(day: Boolean) = if (day) T1Colors.dayText else T1Colors.nightText
private fun muted(day: Boolean) = if (day) T1Colors.dayMuted else T1Colors.nightMuted
private fun accent(day: Boolean) = ink(day) // 1.15.7: settings-button text colour, no gold
// 1.15.11: card/panel text on secondary pages (cream in day mode there; unchanged on Home and at night).
@Composable private fun cInk(day: Boolean) = t1CardText(day)
@Composable private fun cMuted(day: Boolean) = t1CardMuted(day)
@Composable private fun cAccent(day: Boolean) = t1CardText(day)
private fun surface(day: Boolean) = T1Colors.fill(if (day) T1Colors.dayCard else T1Colors.nightCard) // 1.15.8: 80%

@Composable
fun T1Shell(page: Page, day: Boolean, snap: CarBridge.Snap, onPage: (Page) -> Unit,
            onMic: () -> Unit, onOemAssistant: () -> Unit, onDay: () -> Unit,
            onBack: () -> Unit, content: @Composable () -> Unit) {
    val font = remember { FontFamily(Font(R.font.arabic)) }
    val colors = if (day) lightColorScheme(primary = T1Colors.dayText, onPrimary = T1Colors.nightText, background = T1Colors.dayBg,
        surface = T1Colors.dayCard, onSurface = T1Colors.dayText) else darkColorScheme(primary = T1Colors.nightText, onPrimary = T1Colors.dayText,
        background = T1Colors.nightBg, surface = T1Colors.nightCard, onSurface = T1Colors.nightText)
    MaterialTheme(colorScheme = colors, typography = Typography(bodyLarge = TextStyle(fontFamily = font))) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            val wallpaper = rememberLauncherWallpaper()
            if (wallpaper != null) Image(wallpaper, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else Image(androidx.compose.ui.res.painterResource(
                if (day) R.drawable.saudi_oem_landscape_day else R.drawable.saudi_oem_landscape),
                null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            val shade = if (day && page != Page.Home) T1Colors.dayBg else Color.Black
            val opacity = if (page == Page.Home) .36f else .58f
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to shade.copy(alpha = opacity),
                .38f to shade.copy(alpha = if (page == Page.Home) 0f else opacity),
                1f to shade.copy(alpha = if (page == Page.Home) .42f else .78f))))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // 1.15.9: the emblem/JETOUR chip and the day/night chip sit in the same top strip on
                // every page, so the sidebar starts at the same height everywhere and nothing is covered.
                AppsTopChips(day, onDay)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        DashboardNavigation(page, day, onPage, onBack)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(Modifier.weight(1f).fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                // 1.15.12: secondary pages use the sidebar colours (T1Colors.rail*) in both modes.
                                CompositionLocalProvider(LocalHomePage provides (page == Page.Home), LocalT1Day provides day) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardNavigation(page: Page, day: Boolean, onPage: (Page) -> Unit, onBack: () -> Unit) {
    Column(Modifier.width(88.dp).fillMaxHeight()
        .background(T1Colors.railBg(day)) // 1.15.8: 80%; 1.15.12: shared with secondary pages (#CCE9E6DA day / #CC07110F night)
        .border(width = 1.dp, color = accent(day).copy(alpha = .28f)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Page.entries.forEach { item ->
            val selected = item == page
            Box(Modifier.weight(1f).fillMaxWidth()
                .background(if (selected) accent(day).copy(alpha = .12f) else Color.Transparent)
                .clickable { onPage(item) }) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DashboardNavIcon(item, if (selected) accent(day) else muted(day))
                    Text(item.ar, color = if (selected) ink(day) else muted(day), fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
                }
                if (selected) Box(Modifier.align(Alignment.CenterEnd).width(3.dp)
                    .fillMaxHeight(.55f).background(accent(day)))
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("رجوع") }
    }
}

@Composable
private fun DashboardNavIcon(page: Page, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width
        val stroke = 1.7.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(s * x1, s * y1), Offset(s * x2, s * y2), stroke, cap = StrokeCap.Round)
        when (page) {
            Page.Home -> {
                line(.14f,.48f,.5f,.18f); line(.5f,.18f,.86f,.48f)
                line(.22f,.43f,.22f,.84f); line(.78f,.43f,.78f,.84f)
                line(.22f,.84f,.78f,.84f); line(.44f,.84f,.44f,.61f); line(.56f,.61f,.56f,.84f)
            }
            Page.Apps -> for (x in 0..1) for (y in 0..1)
                drawRoundRect(color, Offset(s*(.16f+x*.38f),s*(.16f+y*.38f)),
                    Size(s*.26f,s*.26f), CornerRadius(s*.035f))
            Page.Cluster -> {
                drawArc(color, 195f, 150f, false, Offset(s*.12f,s*.12f), Size(s*.76f,s*.76f),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                line(.5f,.55f,.73f,.34f); drawCircle(color, s*.055f, Offset(s*.5f,s*.55f))
            }
            Page.Camera -> {
                drawRoundRect(color, Offset(s*.12f,s*.25f), Size(s*.76f,s*.55f), CornerRadius(s*.07f), style = Stroke(stroke))
                drawCircle(color, s*.15f, Offset(s*.5f,s*.52f), style = Stroke(stroke))
                line(.29f,.25f,.36f,.16f); line(.36f,.16f,.64f,.16f)
            }
            Page.Vehicle -> {
                drawRoundRect(color, Offset(s*.12f,s*.38f), Size(s*.76f,s*.35f), CornerRadius(s*.09f), style = Stroke(stroke))
                line(.25f,.38f,.36f,.22f); line(.36f,.22f,.67f,.22f); line(.67f,.22f,.78f,.38f)
                drawCircle(color, s*.055f, Offset(s*.29f,s*.75f)); drawCircle(color,s*.055f,Offset(s*.71f,s*.75f))
            }
            Page.Controls -> {
                line(.18f,.3f,.82f,.3f); line(.18f,.68f,.82f,.68f)
                drawCircle(color,s*.1f,Offset(s*.4f,s*.3f)); drawCircle(color,s*.1f,Offset(s*.66f,s*.68f))
            }
            Page.Climate -> {
                line(.5f,.1f,.5f,.9f); line(.15f,.3f,.85f,.7f); line(.15f,.7f,.85f,.3f)
                line(.4f,.16f,.5f,.26f); line(.6f,.16f,.5f,.26f); line(.4f,.84f,.5f,.74f); line(.6f,.84f,.5f,.74f)
            }
            Page.Customize -> {
                drawCircle(color,s*.32f,Offset(s*.5f,s*.5f),style=Stroke(stroke))
                line(.5f,.06f,.5f,.18f); line(.5f,.82f,.5f,.94f)
                line(.06f,.5f,.18f,.5f); line(.82f,.5f,.94f,.5f)
            }
            Page.Settings -> {
                drawCircle(color,s*.3f,Offset(s*.5f,s*.5f),style=Stroke(stroke))
                drawCircle(color,s*.11f,Offset(s*.5f,s*.5f),style=Stroke(stroke))
                line(.5f,.06f,.5f,.2f); line(.5f,.8f,.5f,.94f)
                line(.06f,.5f,.2f,.5f); line(.8f,.5f,.94f,.5f)
            }
        }
    }
}

// 1.15.7: the top bar is gone. Since 1.15.9 every page shows two chips:
// emblem + wordmark at the start and the day/night toggle at the end.
@Composable
private fun AppsTopChips(day: Boolean, onDay: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.clip(RoundedCornerShape(12.dp))
            .background(ink(day).copy(alpha = .10f))
            .border(1.dp, ink(day).copy(alpha = .12f), RoundedCornerShape(12.dp))
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painter = androidx.compose.ui.res.painterResource(R.drawable.saudi_emblem_gold),
                contentDescription = "السيفان والنخلة", modifier = Modifier.size(width = 30.dp, height = 34.dp))
            // 1.15.9: the wordmark is gold on every page (the only gold besides the emblem).
            Text("JETOUR  T1", color = JetourGold, fontSize = 17.sp,
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
        }
        HeaderAction(if (day) "ليلي" else "نهاري", day, onDay)
    }
}

@Composable
private fun SaudiTrim(modifier: Modifier, tone: Color) {
    Canvas(modifier.height(7.dp)) {
        val line = tone.copy(alpha = .55f)
        val middle = size.height / 2f
        drawLine(line.copy(alpha = .35f), Offset(0f, middle), Offset(size.width, middle), 1.dp.toPx())
        val step = 28.dp.toPx()
        val half = 4.dp.toPx()
        var x = step / 2f
        while (x < size.width) {
            drawLine(line, Offset(x - half, middle), Offset(x, 0f), 1.dp.toPx())
            drawLine(line, Offset(x, 0f), Offset(x + half, middle), 1.dp.toPx())
            drawLine(line, Offset(x + half, middle), Offset(x, size.height), 1.dp.toPx())
            drawLine(line, Offset(x, size.height), Offset(x - half, middle), 1.dp.toPx())
            x += step
        }
    }
}

@Composable
private fun HeaderAction(label: String, day: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp))
        .background(ink(day).copy(alpha = .10f))
        .border(1.dp, ink(day).copy(alpha = .12f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        .widthIn(min = 72.dp).heightIn(min = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = ink(day), fontSize = 14.sp)
    }
}

@Composable
fun HomePage(apps: List<LaunchableApp>) {
    val context = LocalContext.current
    val interconnection = remember(context, apps) {
        OemSettings.interconnectionIntent().takeIf {
            it.resolveActivity(context.packageManager) != null
        }
    }
    val shortcuts = listOf(
        HomeShortcut("Apple CarPlay", ShortcutArt.CarPlay, AppIndex.find(apps, "com.autolink.carplaylink")),
        HomeShortcut("يوتيوب", ShortcutArt.Video, AppIndex.find(apps, "app.revanced.android.youtube", "com.google.android.youtube")),
        HomeShortcut("ويز", ShortcutArt.Map, AppIndex.find(apps, "com.waze")),
        HomeShortcut("البلوتوث", ShortcutArt.Bluetooth, AppIndex.find(apps, "com.autolink.bluetooth"),
            android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
        HomeShortcut("الواي فاي", ShortcutArt.Wifi, settings = android.provider.Settings.ACTION_WIFI_SETTINGS),
        HomeShortcut("التكييف", ShortcutArt.Climate, AppIndex.find(apps, "com.autolink.aircontroller"))
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = (maxWidth * .29f).coerceIn(204.dp, 360.dp).coerceAtMost(maxWidth)
        val cardHeight = (maxHeight * .26f).coerceIn(116.dp, 172.dp).coerceAtMost(maxHeight)
        LazyRow(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shortcuts, key = { it.art.name }) { shortcut ->
                val intent = shortcut.app?.let { android.content.Intent.makeMainActivity(it.component) }
                    ?: shortcut.settings?.let { android.content.Intent(it) }
                    ?: if (shortcut.art == ShortcutArt.CarPlay) interconnection else null
                Box(Modifier.size(cardWidth, cardHeight).clip(CardShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF121619), Color(0xFF20282D))))
                    .border(1.dp, Brush.linearGradient(listOf(shortcut.art.tint.copy(alpha = .5f),
                        Color.White.copy(alpha = .12f))), CardShape)
                    .clickable(enabled = intent != null, role = androidx.compose.ui.semantics.Role.Button) {
                        runCatching { LauncherDisplay.start(context, requireNotNull(intent)) }.onFailure {
                            android.widget.Toast.makeText(context, "تعذر فتح ${shortcut.label}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                    ShortcutArtwork(shortcut.art, Modifier.align(Alignment.CenterEnd)
                        .fillMaxWidth(.56f).fillMaxHeight())
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.55f)
                        .padding(start = 16.dp, end = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(shortcut.label, color = Color.White, fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val subtitle = when {
                            intent == null -> "غير متاح"
                            shortcut.art == ShortcutArt.CarPlay && shortcut.app == null -> "ربط الهاتف"
                            else -> null
                        }
                        if (subtitle != null) Text(subtitle, color = Color(0xFFCDD5DA), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private data class HomeShortcut(
    val label: String,
    val art: ShortcutArt,
    val app: LaunchableApp? = null,
    val settings: String? = null
)

@Composable
private fun HomeQuickTile(icon: String, label: String, day: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 90.dp).clip(RoundedCornerShape(8.dp))
        .t1Bg(surface(day)).border(1.dp, accent(day).copy(alpha = .2f), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        DashboardNavIcon(when (icon) {
            "MAP" -> Page.Home; "360°" -> Page.Camera; "CLUSTER" -> Page.Cluster
            "OEM" -> Page.Controls; else -> Page.Apps
        }, accent(day))
        Text(label, color = ink(day), fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun ControlsPage(day: Boolean, snap: CarBridge.Snap, onTheme: (Boolean) -> Unit, onClassic: () -> Unit, onWindows: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val stageHeight = (maxHeight * .62f).coerceIn(340.dp, 540.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(stageHeight).clip(RoundedCornerShape(4.dp))
            .t1Bg(Brush.verticalGradient(if (day)
                listOf(Color(0xFFD8DED7), Color(0xFFAEBEB4), Color(0xFF849D8E))
                else listOf(Color(0xFF263731), Color(0xFF35423C), Color(0xFF101A16))))) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.oem_t1_cutout_saudi),
                        contentDescription = "جيتور T1", modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = ContentScale.Fit)
                    Column(Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                        Text("JETOUR T1", color = cAccent(day), fontSize = 14.sp, letterSpacing = 2.sp)
                        Text("التحكم بالمركبة", color = cInk(day), fontSize = 25.sp)
                    }
                }
                Column(Modifier.width(225.dp).fillMaxHeight().padding(end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    TailgateGauge(day, snap.reading("tailgate_height").current)
                    Text("ارتفاع الباب الخلفي الكهربائي", color = cInk(day), fontSize = 14.sp,
                        textAlign = TextAlign.Center)
                    Text("قراءة مباشرة عند الاتصال", color = cMuted(day), fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    ActionButton("النوافذ بالتفصيل", day, onClick = onWindows)
                }
            }
            }
            SaudiTrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), cInk(day))
        }
        VehicleControlPanel(day, snap)
        OemControlTiles(day, onTheme, onClassic)
        OemSettingsCard(day)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightPill("إشارة يسار", snap.reading("turn_left"), day)
            LightPill("إشارة يمين", snap.reading("turn_right"), day)
            LightPill("منخفض", snap.reading("low_beam"), day)
            LightPill("عالٍ", snap.reading("high_beam"), day)
        }
    }
    }
}

@Composable
private fun OemControlTiles(day: Boolean, onTheme: (Boolean) -> Unit, onClassic: () -> Unit) {
    val context = LocalContext.current
    val controls = listOf(
        "عدادات كلاسيكية" to "cluster", "ضبط مرآة الرؤية الخلفية" to "mirrors",
        "تحذير مغادرة المسار" to "", "التحكم في نزول المنحدرات" to "",
        "تبريد المركبة على الطرق الوعرة" to "", "الإضاءة المحيطة" to "",
        "الباب الخلفي الكهربائي" to "tailgate", "التشغيل والإيقاف التلقائي" to "",
        "ESP OFF" to "", "رادار العبور" to "", "إيقاف تشغيل الشاشة" to "dark",
        "شعاع ذكي عالي / منخفض" to "", "ذاكرة المقعد" to "", "الأجهزة المتصلة" to "devices",
        "سطوع الشاشة" to "brightness", "شاشة توقف" to "dark", "AUTO" to "brightness",
        "الوضع النهاري" to "day", "الوضع الليلي" to "night"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        controls.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, action) ->
                    T1OutlinedButton(modifier = Modifier.weight(1f).heightIn(min = 72.dp), onClick = {
                        runCatching {
                            when (action) {
                                "cluster" -> onClassic()
                                "day" -> onTheme(true)
                                "night" -> onTheme(false)
                                "dark" -> LauncherDisplay.start(context, android.content.Intent(context, JctBlackActivity::class.java))
                                "devices" -> LauncherDisplay.start(context, android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                                "brightness" -> LauncherDisplay.start(context, android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
                                else -> OemSettings.open(context, action)
                            }
                        }.onFailure {
                            android.widget.Toast.makeText(context, "التطبيق المطلوب غير متاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = cInk(day), textAlign = TextAlign.Center)
                            if (action.isEmpty()) Text("إعدادات جيتور", color = cMuted(day), fontSize = 11.sp)
                        }
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun TailgateGauge(day: Boolean, value: Double?) {
    val gold = cAccent(day)
    val arcTrack = cInk(day).copy(alpha = .28f)
    Box(Modifier.size(165.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 7.dp.toPx()
            val inset = 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val top = Offset(inset, inset)
            drawArc(arcTrack, 145f, 250f, false, top, arcSize,
                style = Stroke(width, cap = StrokeCap.Round))
            value?.let { drawArc(gold, 145f, (it / 100.0).coerceIn(0.0, 1.0).toFloat() * 250f,
                false, top, arcSize, style = Stroke(width, cap = StrokeCap.Round)) }
        }
        Text(value?.let { "${it.toInt()}%" } ?: "—", color = cInk(day), fontSize = 38.sp,
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun panel(modifier: Modifier, day: Boolean) = modifier.clip(CardShape).t1Bg(surface(day))
    .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, CardShape)

@Composable
private fun MainMetrics(day: Boolean, snap: CarBridge.Snap, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf("fuel", "range"), listOf("rpm", "drive_mode")).forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.forEach { key ->
                    ReadingCard(SignalCatalog.byKey.getValue(key), snap.reading(key), snap.nowMs, day, Modifier.weight(1f).heightIn(min = 132.dp))
                }
            }
        }
    }
}

@Composable
private fun SpeedPanel(day: Boolean, snap: CarBridge.Snap, modifier: Modifier) {
    val tone = cAccent(day)
    Box(panel(modifier, day).t1Bg(Brush.radialGradient(listOf(if (day) Color(0xFFEAE3D3) else Color(0xFF1D4733), surface(day))))) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 30.dp)) {
            val diameter = minOf(size.width, size.height * 1.22f)
            val origin = Offset((size.width - diameter) / 2, 8f)
            drawArc(tone.copy(alpha = .16f), 145f, 250f, false, origin, Size(diameter, diameter), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            if (snap.speed != null) drawArc(tone, 145f, (snap.speed / 240f).coerceIn(0f, 1f) * 250f, false,
                origin, Size(diameter, diameter), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("السرعة", color = cMuted(day), fontSize = 13.sp)
            Text(snap.speed?.toInt()?.toString() ?: "—", color = cInk(day), fontSize = 78.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
            Text(snap.speedUnit, color = tone, fontSize = 13.sp)
            Text(snap.reading(snap.speedKey).status.label, color = cMuted(day), fontSize = 11.sp)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(1 to "P", 2 to "R", 3 to "N", 4 to "D").forEach { (gear, label) ->
                    Text(label, color = if (snap.gear == gear) tone else cMuted(day).copy(alpha = .6f),
                        fontSize = 19.sp, fontFamily = FontFamily.SansSerif, fontWeight = if (snap.gear == gear) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun LightPill(label: String, reading: SignalReading, day: Boolean) {
    val color = when (reading.current) { 1.0 -> if (day) Color(0xFF246B4E) else Mint; else -> cMuted(day) }
    Text("$label · ${when (reading.current) { 1.0 -> "مضاء"; 0.0 -> "مطفأ"; else -> "—" }}", color = color, fontSize = 12.sp,
        modifier = Modifier.t1Bg(surface(day), RoundedCornerShape(30.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Shortcut(title: String, subtitle: String, day: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(panel(modifier, day).clickable(onClick = onClick).heightIn(min = 88.dp).padding(16.dp)) {
        Text(title, color = cInk(day), fontSize = 17.sp)
        Text(subtitle, color = cMuted(day), fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
internal fun AppArtwork(app: LaunchableApp) {
    val context = LocalContext.current
    val icon = remember(app.component) { runCatching { context.packageManager.getActivityIcon(app.component).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
    if (icon != null) Image(icon, app.label, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
    else Box(Modifier.size(48.dp).t1Bg(Color(0xFF31513E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(app.label.take(1), color = t1OnButton() ?: Sand)
    }
}

@Composable
private fun MediaStrip(day: Boolean) {
    val context = LocalContext.current
    val media = remember { MediaHub(context) }
    var state by remember { mutableStateOf(MediaHub.State(null, false)) }
    LaunchedEffect(media) { while (true) { media.bind { state = it }; delay(1000) } }
    Row(panel(Modifier.fillMaxWidth(), day).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("الوسائط", color = cAccent(day), fontSize = 11.sp)
            Text(state.title ?: "لا يوجد مقطع نشط", color = cInk(day), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ActionButton(if (media.allowed()) (if (state.playing) "إيقاف مؤقت" else "تشغيل") else "تفعيل التحكم", day) {
            if (media.allowed()) media.toggle() else AppIndex.openListenerSettings(context)
        }
    }
}

@Composable
fun VehiclePage(day: Boolean, snap: CarBridge.Snap, bridge: CarBridge) {
    var groupName by rememberSaveable { mutableStateOf("OVERVIEW") }
    var query by rememberSaveable { mutableStateOf("") }
    var rawPage by rememberSaveable { mutableIntStateOf(0) }
    val group = SignalGroup.entries.firstOrNull { it.name == groupName }
    val catalog = remember(bridge) { bridge.oemProperties }
    val filteredRaw = remember(query, catalog) { catalog.filter { it.name.contains(query, true) || it.address.toString().contains(query) } }
    val pageCount = ((filteredRaw.size + 23) / 24).coerceAtLeast(1)
    val shownRaw = filteredRaw.drop(rawPage.coerceAtMost(pageCount - 1) * 24).take(24)
    DisposableEffect(groupName, query, rawPage) {
        bridge.setExtraProperties(if (group == SignalGroup.RAW) shownRaw else emptyList())
        onDispose { bridge.setExtraProperties(emptyList()) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (groupName == "OVERVIEW") "OFF ROAD  ·  JETOUR T1" else "قراءات المركبة",
                    color = if (groupName == "OVERVIEW") accent(day) else ink(day), fontSize = 17.sp)
            }
            Text("${snap.liveCount} قراءة مباشرة", color = muted(day), fontSize = 12.sp)
            TextButton(onClick = { bridge.retry() }) { Text("إعادة الاتصال", color = accent(day), fontSize = 12.sp) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            VehicleTab("نظرة عامة", groupName == "OVERVIEW", day) { groupName = "OVERVIEW"; query = "" }
            VehicleTab("الكل", groupName == "ALL", day) { groupName = "ALL"; query = "" }
            SignalGroup.entries.forEach { item ->
                VehicleTab(item.label, item == group, day) { groupName = item.name; rawPage = 0; query = "" }
            }
        }
        if (groupName == "OVERVIEW") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                DrivingEssentials(day, snap)
                Spacer(Modifier.height(10.dp))
                VehicleOverview(day, snap)
            }
        } else {
        OutlinedTextField(value = query, onValueChange = { query = it; rawPage = 0 }, singleLine = true,
            placeholder = { Text(if (group == SignalGroup.RAW) "ابحث باسم الخاصية أو رقمها" else "ابحث عن قراءة") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        if (group == SignalGroup.RAW) {
            Text("الفهرس يشمل طرزًا متعددة. تعرض الإشارات الرقمية قيمًا خامًا؛ تُقرأ الصفحة الحالية فقط.", color = muted(day), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("السابق", day, rawPage > 0) { rawPage-- }
                Text("${rawPage.coerceAtMost(pageCount - 1) + 1} / $pageCount  ·  ${filteredRaw.size} نتيجة", color = ink(day), fontSize = 13.sp)
                ActionButton("التالي", day, rawPage < pageCount - 1) { rawPage++ }
            }
            LazyVerticalGrid(GridCells.Adaptive(230.dp), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shownRaw, key = { it.name }) { property ->
                    val definition = property.definition()
                    if (definition != null) ReadingCard(definition, snap.reading(definition.key), snap.nowMs, day)
                    else Column(panel(Modifier.fillMaxWidth(), day).padding(16.dp)) {
                        Text(property.name, color = cInk(day), fontSize = 12.sp, maxLines = 3)
                        Text("${property.address} · ${property.type} · ${property.area}", color = cMuted(day), fontSize = 11.sp)
                        Text("معلومة في الفهرس · لا تُقرأ بهذا المسار", color = cAccent(day), fontSize = 12.sp)
                    }
                }
            }
        } else {
            val definitions = SignalCatalog.all.filter { (group == null || it.group == group) &&
                (it.label.contains(query, true) || it.key.contains(query, true) || it.address.toString().contains(query)) }
            Text("اضغط أي قراءة لعرض المصدر وآخر تحديث. «خام» تعني أن الوحدة أو تفسير القيمة لم يُطابقا بعد.", color = muted(day), fontSize = 12.sp)
            if (definitions.isEmpty()) Text("لا توجد نتائج", color = muted(day))
            LazyVerticalGrid(GridCells.Adaptive(215.dp), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(definitions, key = { it.key }) { definition -> ReadingCard(definition, snap.reading(definition.key), snap.nowMs, day) }
            }
        }
        }
    }
}

@Composable
private fun VehicleTab(label: String, selected: Boolean, day: Boolean, onClick: () -> Unit) {
    Column(Modifier.height(38.dp).clickable(onClick = onClick).padding(horizontal = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (selected) ink(day) else muted(day), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        Box(Modifier.width(30.dp).height(2.dp).background(if (selected) accent(day) else Color.Transparent))
    }
}

@Composable
private fun ReadingCard(definition: SignalDefinition, reading: SignalReading, nowMs: Long, day: Boolean, modifier: Modifier = Modifier) {
    var details by remember { mutableStateOf(false) }
    val live = reading.status == ReadingStatus.LIVE
    Column(panel(modifier, day).clickable { details = true }.heightIn(min = 130.dp).padding(16.dp)) {
        Text(definition.label, color = cMuted(day), fontSize = if (definition.group == SignalGroup.RAW) 12.sp else 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(definition.format(reading.current), color = cInk(day), fontSize = 29.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            Text(definition.unit, color = cMuted(day), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(5.dp).background(if (live) (if (day) Color(0xFF276E53) else Mint) else cMuted(day), CircleShape))
            Text(reading.status.label, color = if (live) (if (day) Color(0xFF276E53) else Mint) else cMuted(day), fontSize = 11.sp)
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(definition.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${definition.format(reading.current)} ${definition.unit} · ${reading.status.label}")
                Text(definition.reference, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(if (definition.confirmed) "المعنى موثق في ملفات المشروع والنظام." else definition.note)
                Text(reading.sampledAtMs?.let { "آخر قراءة ناجحة قبل ${((nowMs - it).coerceAtLeast(0) / 1000)} ثانية" } ?: "لم تصل قراءة ناجحة بعد")
                if (reading.status != ReadingStatus.LIVE && reading.value != null) Text("آخر قيمة محفوظة، وليست حية: ${definition.format(reading.value)}")
                if (reading.detail.isNotBlank()) Text(reading.detail)
            }
        }, confirmButton = { TextButton(onClick = { details = false }) { Text("إغلاق") } })
}

@Composable
private fun Choice(label: String, selected: Boolean, day: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).t1Bg(if (selected) accent(day).copy(alpha = if (day) .14f else .16f) else surface(day))
        .t1Selected(selected, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) t1SelText(day) else cMuted(day), fontSize = 13.sp)
    }
}

@Composable
private fun ActionButton(label: String, day: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).t1Bg(surface(day))
        .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = cAccent(day).copy(alpha = if (enabled) 1f else .4f), fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}
