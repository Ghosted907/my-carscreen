package com.ghosted907.t1launcher

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Gold = T1Colors.gold
private fun tone(day: Boolean) = if (day) T1Colors.dayText else T1Colors.nightText
private fun tile(day: Boolean) = if (day) T1Colors.dayCard else T1Colors.nightCard
private fun detailGold(day: Boolean) = if (day) Color(0xFF8A6828) else Gold
private fun fmt(v: Double?, digits: Int = 0): String = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"

@Composable
fun MediaLaunchPanel(day: Boolean) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Waze · ويز" to "com.waze", "YouTube · يوتيوب" to "app.revanced.android.youtube").forEach { (name, pkg) ->
            Column(Modifier.weight(1f).height(160.dp).background(tile(day), RoundedCornerShape(20.dp)).clickable {
                if (!JctLaunch.startOnDisplayIndex(context, pkg, JctLaunch.guIndex(context)))
                    Toast.makeText(context, "تعذر فتح $name", Toast.LENGTH_SHORT).show()
            }.padding(22.dp), verticalArrangement = Arrangement.Center) {
                Text(if (pkg == "com.waze") "◇" else "▷", color = Gold, fontSize = 35.sp)
                Text(name, color = tone(day), fontSize = 24.sp)
                Text("فتح على الشاشة الرئيسية", color = Gold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CompactReadings(day: Boolean, snap: CarBridge.Snap, basics: Boolean = false) {
    val keys = if (basics) listOf("drive_mode", "gear", "fuel", "range", "outside_oem", "odometer")
        else listOf(snap.speedKey, "fuel", "range", "drive_mode", "rpm")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .clip(RoundedCornerShape(14.dp)).background(tile(day))
        .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(14.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        keys.forEach { key ->
            val definition = SignalCatalog.byKey.getValue(key)
            val reading = snap.reading(key)
            Column(Modifier.widthIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(28.dp).height(2.dp).background(detailGold(day)))
                Text(definition.label.removeSuffix(" · مرشح"), color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 11.sp)
                Text("${definition.format(reading.current)} ${definition.unit}", color = tone(day), fontSize = 18.sp, maxLines = 1)
                if (reading.current == null) Text(reading.status.label, color = detailGold(day), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun OemSettingsCard(day: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().background(tile(day), RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text("▣  الإعدادات", fontSize = 22.sp, color = tone(day), modifier = Modifier.clickable { OemSettings.open(context) }.padding(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("◎ الدركسون" to "steering", "◒ المرايات" to "mirrors", "▱ الأبواب والنوافذ" to "windows").forEach { (label, id) ->
                OutlinedButton(onClick = { OemSettings.open(context, id) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
}

@Composable
fun VehicleControlPanel(day: Boolean, snap: CarBridge.Snap) {
    val context = LocalContext.current
    val control = remember { VehicleControl(context) }
    var status by remember { mutableStateOf("اختر أمراً للتحكم في المركبة") }
    DisposableEffect(control) { onDispose { control.close() } }
    Column(Modifier.fillMaxWidth().background(tile(day), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val groups = listOf(
            "التحكم في النوافذ" to listOf("التهوية" to "windows_vent", "مغلق" to "windows_close", "مفتوح" to "windows_open"),
            "التحكم في فتحة السقف" to listOf("إمالة لأعلى" to "roof_tilt", "مغلق" to "roof_close", "مفتوح" to "roof_open"),
            "التحكم في الستارة الشمسية" to listOf("نصف مفتوح" to "shade_half", "مغلق" to "shade_close", "مفتوح" to "shade_open")
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 1050.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    groups.forEach { (title, commands) ->
                        SegmentedVehicleControl(title, commands, day, Modifier.weight(1f)) { id ->
                            control.send(id) { status = it }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    groups.forEach { (title, commands) ->
                        SegmentedVehicleControl(title, commands, day, Modifier.fillMaxWidth()) { id ->
                            control.send(id) { status = it }
                        }
                    }
                }
            }
        }
        val stationary = VehicleCommands.stationary(snap.reading("speed").current, snap.reading("gear").current)
        // Disabled until the exact OEM start/stop request (not feedback props 2051/2052) is verified.
        OutlinedButton(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("الباب الخلفي · غير متاح من اللانشر") }
        Text(if (!stationary) "يلزم توقف السيارة للتحكم بالباب الخلفي." else "تحكم الباب الخلفي غير متاح حالياً.",
            color = Gold, fontSize = 12.sp)
        TextButton(onClick = { control.probe { status = it } }) { Text("فحص الاتصال للقراءة فقط") }
        Text(status, color = tone(day), fontSize = 12.sp)
    }
}

@Composable
private fun SegmentedVehicleControl(title: String, commands: List<Pair<String, String>>,
                                    day: Boolean, modifier: Modifier = Modifier, onCommand: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = tone(day), fontSize = 18.sp, maxLines = 1)
        Row(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(7.dp))
            .background(if (day) Color(0xFFE4E7DE) else Color(0xFF292D2C)),
            verticalAlignment = Alignment.CenterVertically) {
            commands.forEachIndexed { index, (label, id) ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onCommand(id) },
                    contentAlignment = Alignment.Center) {
                    Text(label, color = tone(day), fontSize = 17.sp, textAlign = TextAlign.Center,
                        maxLines = 2, modifier = Modifier.padding(horizontal = 3.dp))
                }
                if (index < commands.lastIndex) Box(Modifier.width(1.dp).height(34.dp)
                    .background(detailGold(day).copy(alpha = .34f)))
            }
        }
    }
}

@Composable
fun VehicleOverview(day: Boolean, snap: CarBridge.Snap) {
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenHeightDp <= 760
    val sensors = remember { OffRoadSensors(context) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    DisposableEffect(sensors) { sensors.start(); onDispose { sensors.close() } }
    LaunchedEffect(Unit) { while (true) { delay(500); now = android.os.SystemClock.elapsedRealtime() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) sensors.startGps()
    }
    val gold = detailGold(day)
    val pitch = sensors.pitch?.live(now)
    val roll = sensors.roll?.live(now)
    val heading = sensors.heading?.live(now)
    val altitude = sensors.altitude?.live(now)
    val mode = SignalCatalog.byKey.getValue("drive_mode").format(snap.reading("drive_mode").current)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(if (day)
                listOf(Color(0xFFE8E9DF), Color(0xFFCED8CA))
                else listOf(Color(0xFF1B2A26), Color(0xFF09120F))))
            .border(1.dp, gold.copy(alpha = .28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("المركبة  ·  JETOUR T1", color = tone(day), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("ضغط الإطارات وحرارتها  /  مؤشرات الطرق الوعرة", color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 13.sp)
                }
                Text("$mode  ◇", color = gold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp))
                TextButton(onClick = { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("GPS للارتفاع", color = gold)
                }
            }
            Row(Modifier.fillMaxWidth().height(if (compact) 280.dp else 430.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CompassDial(day, heading, compact, Modifier.weight(.23f).fillMaxHeight())
                Box(Modifier.weight(.40f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val platform = Color(0xFF456B3F).copy(alpha = if (day) .32f else .43f)
                        drawOval(platform, topLeft = Offset(size.width * .08f, size.height * .61f),
                            size = androidx.compose.ui.geometry.Size(size.width * .84f, size.height * .33f))
                        drawOval(gold.copy(alpha = .46f), topLeft = Offset(size.width * .08f, size.height * .61f),
                            size = androidx.compose.ui.geometry.Size(size.width * .84f, size.height * .33f),
                            style = Stroke(1.dp.toPx()))
                    }
                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.oem_t1_cutout_saudi),
                        contentDescription = "جيتور T1 المجسمة", modifier = Modifier.fillMaxSize(.94f),
                        contentScale = ContentScale.Fit)
                }
                Row(Modifier.weight(.37f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Column(Modifier.width(if (compact) 132.dp else 150.dp).fillMaxHeight().padding(top = 14.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        TireReadout("أمامي أيمن", "fr", snap, day, Modifier.height(if (compact) 88.dp else 105.dp), true)
                        TireReadout("خلفي أيمن", "rr", snap, day, Modifier.height(if (compact) 88.dp else 105.dp), true)
                    }
                    Image(painter = androidx.compose.ui.res.painterResource(R.drawable.t1_top_view_generated),
                        contentDescription = "جيتور T1 من الأعلى، قراءات الإطارات بجوار كل إطار",
                        modifier = Modifier.width(if (compact) 185.dp else 240.dp).fillMaxHeight(), contentScale = ContentScale.Fit)
                    Column(Modifier.width(if (compact) 132.dp else 150.dp).fillMaxHeight().padding(top = 14.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        TireReadout("أمامي أيسر", "fl", snap, day, Modifier.height(if (compact) 88.dp else 105.dp), false)
                        TireReadout("خلفي أيسر", "rl", snap, day, Modifier.height(if (compact) 88.dp else 105.dp), false)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleMetric("زاوية المقود", "${fmt(snap.reading("steering").current, 1)}°", day)
                VehicleMetric("زاوية الميل الطولي", "${fmt(pitch, 1)}°", day)
                VehicleMetric("زاوية الميل الجانبي", "${fmt(roll, 1)}°", day)
                VehicleMetric("الاتجاه", "${OffRoadPresentation.compassLabel(heading)}  ${fmt(heading)}°", day)
                VehicleMetric("الارتفاع", "${fmt(altitude)} m", day)
            }
        }
        CompactReadings(day, snap, true)
        Text("عبور الماء غير متاح؛ تظهر القيم المباشرة عند اتصال السيارة. باقي القراءات في تبويبات الصفحة.",
            color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TireReadout(label: String, suffix: String, snap: CarBridge.Snap, day: Boolean,
                        modifier: Modifier, nearCarOnLeft: Boolean) {
    val pressure = OffRoadPresentation.psi(snap.reading("pressure_$suffix").current)
    val temperature = snap.reading("temp_$suffix").current
    val gold = detailGold(day)
    val align = if (nearCarOnLeft) TextAlign.Left else TextAlign.Right
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Text(label, color = if (day) T1Colors.dayMuted else T1Colors.nightMuted,
            fontSize = 12.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text("${fmt(pressure, 1)} psi", color = tone(day), fontSize = 24.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, textAlign = align,
            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(1.dp).background(gold.copy(alpha = .45f)))
        Text("${fmt(temperature)} °C", color = gold, fontSize = 15.sp, textAlign = align,
            style = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CompassDial(day: Boolean, heading: Double?, compact: Boolean, modifier: Modifier = Modifier) {
    val gold = detailGold(day)
    val foreground = tone(day)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(if (compact) 215.dp else 265.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * .43f
            drawCircle(foreground.copy(alpha = .12f), radius, center, style = Stroke(15.dp.toPx()))
            drawCircle(gold.copy(alpha = .65f), radius * .82f, center, style = Stroke(1.dp.toPx()))
            repeat(72) { i ->
                val angle = (i * 5.0 - 90.0) * PI / 180.0
                val outer = radius * 1.07f
                val inner = radius * if (i % 9 == 0) .89f else .97f
                val start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner)
                val end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer)
                drawLine(if (i % 9 == 0) gold else foreground.copy(alpha = .45f), start, end,
                    if (i % 9 == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(gold, 4.dp.toPx(), Offset(center.x, center.y - radius * .97f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("N     E     S     W", color = gold, fontSize = 12.sp)
            Text(OffRoadPresentation.compassLabel(heading), color = foreground, fontSize = 22.sp)
            Text("${fmt(heading)}°", color = foreground, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text("اتجاه المركبة", color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VehicleMetric(label: String, value: String, day: Boolean) {
    Column(Modifier.widthIn(min = 140.dp).clip(RoundedCornerShape(10.dp))
        .background(if (day) Color(0xFFEEEAD9) else Color(0xFF0C291F))
        .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 11.sp)
        Text(value, color = tone(day), fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
