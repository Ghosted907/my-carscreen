package com.ghosted907.t1launcher

import android.content.Context
import android.icu.util.Calendar as IcuCalendar
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val riyadh = TimeZone.getTimeZone("Asia/Riyadh")
private fun date(pattern: String, now: Date) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = riyadh }.format(now)
fun riyadhTime(now: Date): String = date("HH:mm", now)

@Composable
fun rememberRiyadhHour(): Int {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(30_000) } }
    return date("HH", now).toInt()
}

@Composable
fun HomeDateTime(compact: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val hijri = remember(now.time / 60_000) {
        val c = IcuCalendar.getInstance(IcuTimeZone.getTimeZone("Asia/Riyadh"), ULocale("ar_SA@calendar=islamic-umalqura"))
        c.timeInMillis = now.time
        "%02d/%02d/%04d".format(Locale.US, c.get(IcuCalendar.DAY_OF_MONTH), c.get(IcuCalendar.MONTH) + 1, c.get(IcuCalendar.YEAR))
            .map { if (it in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[it - '0'] else it }.joinToString("")
    }
    val clock = remember(now.time / 1000) { Calendar.getInstance(riyadh).apply { time = now } }
    val dialSize = if (compact) 230.dp else 300.dp
    val dateSize = if (compact) 15.sp else 18.sp
    Box(modifier.size(dialSize).background(Color(0xB5081712), CircleShape), contentAlignment = Alignment.Center) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.saudi_emblem_gold),
            contentDescription = "السيفان والنخلة داخل الساعة",
            modifier = Modifier.size(if (compact) 56.dp else 72.dp).alpha(.78f))
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 5.dp.toPx()
            val gold = T1Colors.goldSoft
            drawCircle(gold.copy(alpha = .78f), radius, center,
                style = Stroke(2.dp.toPx()))
            drawCircle(gold.copy(alpha = .25f), radius * .87f, center,
                style = Stroke(1.dp.toPx()))
            repeat(60) { i ->
                val angle = (i * 6.0 - 90.0) * PI / 180.0
                val inner = radius * if (i % 5 == 0) .78f else .87f
                val outer = radius * .95f
                drawLine(gold.copy(alpha = if (i % 5 == 0) .90f else .45f),
                    Offset(center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner),
                    Offset(center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer),
                    if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            }
            fun hand(degrees: Double, length: Float, width: Float, color: Color) {
                val angle = (degrees - 90.0) * PI / 180.0
                drawLine(color, center,
                    Offset(center.x + cos(angle).toFloat() * radius * length,
                        center.y + sin(angle).toFloat() * radius * length),
                    width.dp.toPx(), cap = StrokeCap.Round)
            }
            val seconds = clock.get(Calendar.SECOND).toDouble()
            val minutes = clock.get(Calendar.MINUTE) + seconds / 60.0
            val hours = clock.get(Calendar.HOUR) + minutes / 60.0
            hand(hours * 30.0, .42f, 4f, Color.White)
            hand(minutes * 6.0, .61f, 2.5f, gold)
            hand(seconds * 6.0, .67f, 1f, gold.copy(alpha = .75f))
            drawCircle(gold, 3.dp.toPx(), center)
        }
        Column(Modifier.align(Alignment.BottomCenter)
            .padding(bottom = if (compact) 15.dp else 23.dp)
            .background(Color(0xD4081712), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$hijri هـ", color = T1Colors.goldSoft, fontSize = dateSize, maxLines = 1)
            Text("${date("dd/MM/yyyy", now)} م", color = Color.White, fontSize = dateSize,
                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, maxLines = 1)
        }
    }
}

@Composable
fun HomeVehicleTelemetry(day: Boolean, snap: CarBridge.Snap, height: Dp) {
    val context = LocalContext.current
    val sensors = remember { OffRoadSensors(context) }
    var tick by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    DisposableEffect(sensors) { sensors.start(); onDispose { sensors.close() } }
    LaunchedEffect(Unit) { while (true) { delay(500); tick = android.os.SystemClock.elapsedRealtime() } }
    val heading = sensors.heading?.live(tick)
    val fg = if (day) T1Colors.dayText else T1Colors.nightText
    val sub = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val compact = height < 320.dp
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().height(height).clip(shape)
        .background(Brush.verticalGradient(if (day)
            listOf(Color(0xFFE8E9DF), Color(0xFFCED8CA))
            else listOf(Color(0xFF1B2A26), Color(0xFF09120F))))
        .border(1.dp, T1Colors.gold.copy(alpha = .28f), shape)
        .padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("المركبة  ·  JETOUR T1", color = fg, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("ضغط الإطارات وحرارتها  /  البوصلة", color = sub, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompassDial(day, heading, compact, Modifier.weight(.23f).fillMaxHeight(),
                dialSize = if (compact) 190.dp else 235.dp)
            Box(Modifier.weight(.40f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(Color(0xFF456B3F).copy(alpha = if (day) .32f else .43f),
                        topLeft = Offset(size.width * .08f, size.height * .62f),
                        size = Size(size.width * .84f, size.height * .30f))
                    drawOval(T1Colors.gold.copy(alpha = .46f),
                        topLeft = Offset(size.width * .08f, size.height * .62f),
                        size = Size(size.width * .84f, size.height * .30f),
                        style = Stroke(1.dp.toPx()))
                }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("الشنطة الخلفية", color = fg, fontSize = 13.sp)
                    Image(androidx.compose.ui.res.painterResource(R.drawable.t1_tailgate_mid_open),
                        "جيتور T1 والشنطة في طور الفتح", Modifier.weight(1f).fillMaxWidth(.80f),
                        contentScale = ContentScale.Fit)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {}, enabled = false,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)) { Text("فتح الشنطة", fontSize = 11.sp) }
                        OutlinedButton(onClick = {}, enabled = false,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)) { Text("إغلاق الشنطة", fontSize = 11.sp) }
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.weight(.37f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        TireReadout("أمامي أيسر", "fl", snap, day,
                            Modifier.height(if (compact) 90.dp else 115.dp), false, compact)
                        TireReadout("خلفي أيسر", "rl", snap, day,
                            Modifier.height(if (compact) 90.dp else 115.dp), false, compact)
                    }
                    Image(androidx.compose.ui.res.painterResource(R.drawable.t1_top_view_generated),
                        "جيتور T1 من الأعلى، بيانات الإطارات بجانب كل إطار",
                        Modifier.width(if (compact) 160.dp else 205.dp).fillMaxHeight(),
                        contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        TireReadout("أمامي أيمن", "fr", snap, day,
                            Modifier.height(if (compact) 90.dp else 115.dp), true, compact)
                        TireReadout("خلفي أيمن", "rr", snap, day,
                            Modifier.height(if (compact) 90.dp else 115.dp), true, compact)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeNoteTile(day: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home_note", Context.MODE_PRIVATE) }
    var note by rememberSaveable { mutableStateOf(prefs.getString("text", "").orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    Column(modifier.heightIn(min = 90.dp).clip(RoundedCornerShape(8.dp))
        .background(if (day) T1Colors.dayCard else T1Colors.nightCard)
        .border(1.dp, T1Colors.gold.copy(alpha=.25f), RoundedCornerShape(8.dp))
        .clickable { editing = true }.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text("ملاحظاتي", color = T1Colors.goldSoft, fontSize = 14.sp)
        Text(note.ifBlank { "اضغط لكتابة ملاحظة" }, color = if (day) T1Colors.dayText else T1Colors.nightText,
            fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Start)
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("ملاحظاتي") },
        text = { TextField(note, { note = it; prefs.edit().putString("text", it).apply() },
            label = { Text("اكتب ملاحظتك") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { editing = false }) { Text("تم") } })
}
