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
import kotlin.math.roundToInt

private val Sand = T1Colors.gold
private val Mint = Color(0xFF94C9A5)
private val CardShape = RoundedCornerShape(16.dp)
private fun ink(day: Boolean) = if (day) T1Colors.dayText else T1Colors.nightText
private fun muted(day: Boolean) = if (day) T1Colors.dayMuted else T1Colors.nightMuted
private fun accent(day: Boolean) = if (day) Color(0xFF8A6828) else Sand
private fun surface(day: Boolean) = if (day) T1Colors.dayCard else T1Colors.nightCard

@Composable
fun T1Shell(page: Page, day: Boolean, snap: CarBridge.Snap, onPage: (Page) -> Unit,
            onMic: () -> Unit, onOemAssistant: () -> Unit, onDay: () -> Unit, content: @Composable () -> Unit) {
    val font = remember { FontFamily(Font(R.font.arabic)) }
    val colors = if (day) lightColorScheme(primary = Color(0xFF8A6828), background = T1Colors.dayBg,
        surface = T1Colors.dayCard, onSurface = T1Colors.dayText) else darkColorScheme(primary = Sand,
        background = T1Colors.nightBg, surface = T1Colors.nightCard, onSurface = T1Colors.nightText)
    MaterialTheme(colorScheme = colors, typography = Typography(bodyLarge = TextStyle(fontFamily = font))) {
        Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box {
                DashboardHeader(day, page, snap, onMic, onOemAssistant, onDay)
                SaudiTrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
            DashboardNavigation(page, day, onPage)
        }
    }
}

@Composable
private fun DashboardNavigation(page: Page, day: Boolean, onPage: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp)
        .background(if (day) Color(0xFFE9E6DA) else Color(0xFF07110F))
        .border(width = 1.dp, color = accent(day).copy(alpha = .28f)),
        verticalAlignment = Alignment.CenterVertically) {
        Page.entries.forEach { item ->
            val selected = item == page
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onPage(item) }
                .padding(top = 5.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.width(34.dp).height(2.dp)
                    .background(if (selected) accent(day) else Color.Transparent))
                DashboardNavIcon(item, if (selected) accent(day) else muted(day))
                Text(item.ar, color = if (selected) ink(day) else muted(day), fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
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

@Composable
private fun DashboardHeader(day: Boolean, page: Page, snap: CarBridge.Snap,
                            onMic: () -> Unit, onOemAssistant: () -> Unit, onDay: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Row(Modifier.fillMaxWidth().height(52.dp).background(if (day) T1Colors.dayCard else Color(0xFF111D19))
        .padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.saudi_emblem_gold),
            contentDescription = "السيفان والنخلة", modifier = Modifier.size(width = 36.dp, height = 40.dp))
        Text("JETOUR  T1", color = accent(day), fontSize = 19.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
        Box(Modifier.width(1.dp).height(30.dp).background(if (day) T1Colors.dayLine else T1Colors.nightLine))
        Text(page.ar, color = ink(day), fontSize = 19.sp, modifier = Modifier.weight(1f))
        Text(if (snap.liveCount > 0) "● ${snap.liveCount} قراءة حية" else "○ بانتظار اتصال السيارة",
            color = if (snap.liveCount > 0) accent(day) else muted(day), fontSize = 12.sp)
        HeaderAction("مساعد", day, onOemAssistant)
        HeaderAction("صوت", day, onMic)
        HeaderAction(if (day) "ليلي" else "نهاري", day, onDay)
        Text(SimpleDateFormat("HH:mm", Locale.US).format(now), color = ink(day), fontSize = 24.sp,
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SaudiTrim(modifier: Modifier) {
    Canvas(modifier.height(7.dp)) {
        val gold = T1Colors.gold.copy(alpha = .55f)
        val middle = size.height / 2f
        drawLine(gold.copy(alpha = .35f), Offset(0f, middle), Offset(size.width, middle), 1.dp.toPx())
        val step = 28.dp.toPx()
        val half = 4.dp.toPx()
        var x = step / 2f
        while (x < size.width) {
            drawLine(gold, Offset(x - half, middle), Offset(x, 0f), 1.dp.toPx())
            drawLine(gold, Offset(x, 0f), Offset(x + half, middle), 1.dp.toPx())
            drawLine(gold, Offset(x + half, middle), Offset(x, size.height), 1.dp.toPx())
            drawLine(gold, Offset(x, size.height), Offset(x - half, middle), 1.dp.toPx())
            x += step
        }
    }
}

@Composable
private fun HeaderAction(label: String, day: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = muted(day), fontSize = 13.sp)
    }
}

@Composable
fun HomePage(day: Boolean, snap: CarBridge.Snap, apps: List<LaunchableApp>, toCluster: Boolean,
             onDestination: (Boolean) -> Unit,
             onOpenNav: () -> Unit, onQuick: (String) -> Unit, onApp: (LaunchableApp) -> Unit,
             dockEnabled: Boolean, waze: LaunchableApp?, youtube: LaunchableApp?,
             onDockBounds: (LaunchableApp, Rect) -> Unit, onExpand: (LaunchableApp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight <= 600.dp
        val heroHeight = (maxHeight * .42f).coerceIn(255.dp, 370.dp)
        val dockHeight = (maxHeight * .48f).coerceIn(278.dp, 350.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth().height(heroHeight).clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF101B17))) {
                Image(painter = androidx.compose.ui.res.painterResource(if (day) R.drawable.oem_t1_day else R.drawable.oem_t1_dark),
                    contentDescription = "سيارة جيتور T1", modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.35f))
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    listOf(Color(0xE8071711), Color(0x8C10251B), Color(0x0010251B)), startX = 0f)))
                Column(Modifier.align(Alignment.CenterEnd).width(260.dp).padding(start = 22.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("JETOUR  T1", color = T1Colors.goldSoft, fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif, letterSpacing = 2.sp)
                    Text("جاهزة لكل طريق", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (snap.liveCount > 0) "بيانات السيارة المباشرة" else "بانتظار بيانات السيارة",
                        color = Color(0xFFDDD6C4), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(snap.speed?.toInt()?.toString() ?: "—", color = Color.White, fontSize = 42.sp,
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
                        Text(snap.speedUnit, color = T1Colors.goldSoft, fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        HeroReading("الوقود", snap.fuel?.let { "$it%" } ?: "—")
                        HeroReading("المدى", snap.rangeKm?.let { "$it كم" } ?: "—")
                        HeroReading("القير", when (snap.gear) { 1 -> "P"; 2 -> "R"; 3 -> "N"; 4 -> "D"; else -> "—" })
                    }
                    Text("كل قراءات السيارة ←", color = T1Colors.goldSoft, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onQuick("vehicle") }
                            .padding(vertical = 4.dp))
                }
                SaudiTrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().height(dockHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeAppTile("Waze", "الملاحة المباشرة", waze, day, dockEnabled,
                    Modifier.weight(1.2f), onDockBounds, onExpand)
                HomeAppTile("YouTube", "الوسائط المرئية", youtube, day, dockEnabled,
                    Modifier.weight(1f), onDockBounds, onExpand)
            }
            if (!compact) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeQuickTile("MAP", "الملاحة", day, Modifier.weight(1f), onOpenNav)
                HomeQuickTile("360°", "الكاميرا", day, Modifier.weight(1f)) { onQuick("avm") }
                HomeQuickTile("CLUSTER", "العدادات", day, Modifier.weight(1f)) { onQuick("cluster") }
                HomeQuickTile("OEM", "التحكم", day, Modifier.weight(1f)) { onQuick("controls") }
                HomeQuickTile("APPS", "التطبيقات", day, Modifier.weight(1f)) { onQuick("apps") }
            }
            Column(panel(Modifier.fillMaxWidth(), day).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("التطبيقات المفضلة", color = ink(day), fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Choice("الجهاز اللوحي", !toCluster, day) { onDestination(false) }
                    Choice("لوحة العدادات", toCluster, day) { onDestination(true) }
                    ActionButton("عرض الكل", day) { onQuick("apps") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(apps, key = { it.component.flattenToString() }) { app ->
                        Column(Modifier.width(92.dp).clip(RoundedCornerShape(10.dp)).clickable { onApp(app) }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            AppArtwork(app)
                            Text(app.label, color = muted(day), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (apps.isEmpty()) item { Text("لا توجد تطبيقات مفضلة", color = muted(day), modifier = Modifier.padding(14.dp)) }
                }
            }
            MediaStrip(day)
            if (snap.note.isNotBlank()) Text(snap.note, color = accent(day), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HomeAppTile(title: String, subtitle: String, app: LaunchableApp?, day: Boolean,
                        dockEnabled: Boolean, modifier: Modifier,
                        onDockBounds: (LaunchableApp, Rect) -> Unit, onExpand: (LaunchableApp) -> Unit) {
    val view = LocalView.current
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(surface(day))
        .border(1.dp, if (day) T1Colors.dayLine else accent(day).copy(alpha = .24f), RoundedCornerShape(9.dp))) {
        Row(Modifier.fillMaxWidth().height(48.dp).clickable(enabled = app != null) { app?.let(onExpand) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (app != null) AppArtwork(app)
            Box(Modifier.weight(1f)) {
                Column {
                    Text(title, color = ink(day), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = muted(day), fontSize = 11.sp)
                }
            }
            Text("↗  ملء الشاشة", color = accent(day), fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(3.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (day) Color(0xFFE9EAD9) else Color(0xFF0B1411))
            .onGloballyPositioned { coordinates ->
                if (dockEnabled && app != null) {
                    val root = coordinates.boundsInRoot()
                    val origin = IntArray(2)
                    view.getLocationOnScreen(origin)
                    onDockBounds(app, Rect((origin[0] + root.left).roundToInt(),
                        (origin[1] + root.top).roundToInt(),
                        (origin[0] + root.right).roundToInt(),
                        (origin[1] + root.bottom).roundToInt()))
                }
            }, contentAlignment = Alignment.Center) {
            Text(when {
                app == null -> "$title غير مثبت"
                !dockEnabled -> "فعّل النوافذ العائمة لعرض التطبيق هنا"
                else -> "جاري عرض $title"
            }, color = muted(day), fontSize = 13.sp)
        }
    }
}

@Composable
private fun HeroReading(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFFDDD6C4), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 17.sp, fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeQuickTile(icon: String, label: String, day: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 90.dp).clip(RoundedCornerShape(8.dp))
        .background(surface(day)).border(1.dp, accent(day).copy(alpha = .2f), RoundedCornerShape(8.dp))
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
fun ControlsPage(day: Boolean, snap: CarBridge.Snap, onWindows: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(9.dp))) {
            Image(painter = androidx.compose.ui.res.painterResource(if (day) R.drawable.oem_t1_day else R.drawable.oem_t1_dark),
                contentDescription = "جيتور T1", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -.25f))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                listOf(Color(0xD50B1914), Color(0x70142018), Color.Transparent))))
            Column(Modifier.align(Alignment.CenterEnd).padding(24.dp)) {
                Text("المركبة", color = accent(day), fontSize = 14.sp, letterSpacing = 2.sp)
                Text("التحكم بالسيارة", color = Color.White, fontSize = 29.sp)
                Text("النوافذ · السقف · الستارة", color = Color(0xFFE4E5DA), fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                ActionButton("تفاصيل النوافذ", day, onClick = onWindows)
            }
            SaudiTrim(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        OemSettingsCard(day)
        VehicleControlPanel(day, snap)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightPill("إشارة يسار", snap.reading("turn_left"), day)
            LightPill("إشارة يمين", snap.reading("turn_right"), day)
            LightPill("منخفض", snap.reading("low_beam"), day)
            LightPill("عالٍ", snap.reading("high_beam"), day)
        }
    }
}

private fun panel(modifier: Modifier, day: Boolean) = modifier.clip(CardShape).background(surface(day))
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
    val tone = accent(day)
    Box(panel(modifier, day).background(Brush.radialGradient(listOf(if (day) Color(0xFFEAE3D3) else Color(0xFF1D4733), surface(day))))) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 30.dp)) {
            val diameter = minOf(size.width, size.height * 1.22f)
            val origin = Offset((size.width - diameter) / 2, 8f)
            drawArc(tone.copy(alpha = .16f), 145f, 250f, false, origin, Size(diameter, diameter), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            if (snap.speed != null) drawArc(tone, 145f, (snap.speed / 240f).coerceIn(0f, 1f) * 250f, false,
                origin, Size(diameter, diameter), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("السرعة", color = muted(day), fontSize = 13.sp)
            Text(snap.speed?.toInt()?.toString() ?: "—", color = ink(day), fontSize = 78.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light)
            Text(snap.speedUnit, color = tone, fontSize = 13.sp)
            Text(snap.reading(snap.speedKey).status.label, color = muted(day), fontSize = 11.sp)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(1 to "P", 2 to "R", 3 to "N", 4 to "D").forEach { (gear, label) ->
                    Text(label, color = if (snap.gear == gear) tone else muted(day).copy(alpha = .6f),
                        fontSize = 19.sp, fontFamily = FontFamily.SansSerif, fontWeight = if (snap.gear == gear) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun LightPill(label: String, reading: SignalReading, day: Boolean) {
    val color = when (reading.current) { 1.0 -> if (day) Color(0xFF246B4E) else Mint; else -> muted(day) }
    Text("$label · ${when (reading.current) { 1.0 -> "مضاء"; 0.0 -> "مطفأ"; else -> "—" }}", color = color, fontSize = 12.sp,
        modifier = Modifier.background(surface(day), RoundedCornerShape(30.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Shortcut(title: String, subtitle: String, day: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(panel(modifier, day).clickable(onClick = onClick).heightIn(min = 88.dp).padding(16.dp)) {
        Text(title, color = ink(day), fontSize = 17.sp)
        Text(subtitle, color = muted(day), fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
internal fun AppArtwork(app: LaunchableApp) {
    val context = LocalContext.current
    val icon = remember(app.component) { runCatching { context.packageManager.getActivityIcon(app.component).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
    if (icon != null) Image(icon, app.label, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
    else Box(Modifier.size(48.dp).background(Color(0xFF31513E), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(app.label.take(1), color = Sand)
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
            Text("الوسائط", color = accent(day), fontSize = 11.sp)
            Text(state.title ?: "لا يوجد مقطع نشط", color = ink(day), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("سيارتك بالتفصيل", color = ink(day), fontSize = 25.sp)
                Text("${snap.liveCount} / ${SignalCatalog.all.size} قراءة مباشرة  ·  ${catalog.size} خاصية في الفهرس", color = muted(day), fontSize = 12.sp)
            }
            ActionButton("إعادة الاتصال", day) { bridge.retry() }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("نظرة عامة", groupName == "OVERVIEW", day) { groupName = "OVERVIEW"; query = "" }
            Choice("الكل", groupName == "ALL", day) { groupName = "ALL"; query = "" }
            SignalGroup.entries.forEach { item ->
                Choice(item.label, item == group, day) { groupName = item.name; rawPage = 0; query = "" }
            }
        }
        if (groupName == "OVERVIEW") {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                        Text(property.name, color = ink(day), fontSize = 12.sp, maxLines = 3)
                        Text("${property.address} · ${property.type} · ${property.area}", color = muted(day), fontSize = 11.sp)
                        Text("معلومة في الفهرس · لا تُقرأ بهذا المسار", color = accent(day), fontSize = 12.sp)
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
private fun ReadingCard(definition: SignalDefinition, reading: SignalReading, nowMs: Long, day: Boolean, modifier: Modifier = Modifier) {
    var details by remember { mutableStateOf(false) }
    val live = reading.status == ReadingStatus.LIVE
    Column(panel(modifier, day).clickable { details = true }.heightIn(min = 130.dp).padding(16.dp)) {
        Text(definition.label, color = muted(day), fontSize = if (definition.group == SignalGroup.RAW) 12.sp else 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(definition.format(reading.current), color = ink(day), fontSize = 29.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
            Text(definition.unit, color = muted(day), fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(5.dp).background(if (live) (if (day) Color(0xFF276E53) else Mint) else muted(day), CircleShape))
            Text(reading.status.label, color = if (live) (if (day) Color(0xFF276E53) else Mint) else muted(day), fontSize = 11.sp)
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
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) accent(day).copy(alpha = if (day) .14f else .16f) else surface(day))
        .clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) accent(day) else muted(day), fontSize = 13.sp)
    }
}

@Composable
private fun ActionButton(label: String, day: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = accent(day).copy(alpha = if (enabled) 1f else .4f), fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}
