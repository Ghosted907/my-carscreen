package com.ghosted907.t1launcher

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private data class InkStroke(val color: Int, val points: List<Offset>)
private enum class ArtMode(val label: String) { Text("نص"), Draw("رسم"), Sticker("ملصقات") }

@Composable
fun SettingsArtBoard(day: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_art", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ArtMode.Text) }
    var caption by remember { mutableStateOf(prefs.getString("caption", "").orEmpty()) }
    var stickerVisible by remember { mutableStateOf(prefs.getBoolean("sticker", true)) }
    var stickerX by remember { mutableFloatStateOf(prefs.getFloat("sticker_x", .45f)) }
    var stickerY by remember { mutableFloatStateOf(prefs.getFloat("sticker_y", .44f)) }
    val strokes = remember {
        mutableStateListOf<InkStroke>().apply {
            runCatching {
                val saved = JSONArray(prefs.getString("strokes", "[]"))
                repeat(saved.length()) { i ->
                    val item = saved.getJSONObject(i)
                    val points = item.getJSONArray("points")
                    add(InkStroke(item.getInt("color"), List(points.length()) { j ->
                        val p = points.getJSONArray(j)
                        Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                    }))
                }
            }
        }
    }
    fun saveStrokes() {
        val array = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { points.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
            array.put(JSONObject().put("color", stroke.color).put("points", points))
        }
        prefs.edit().putString("strokes", array.toString()).apply()
    }
    var hue by remember { mutableFloatStateOf(45f) }
    var saturation by remember { mutableFloatStateOf(.8f) }
    var brightness by remember { mutableFloatStateOf(.9f) }
    val selected = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val textColor = if (day) T1Colors.dayText else Color.White
    val board = RoundedCornerShape(10.dp)
    BoxWithConstraints(modifier.clip(board)
        .background(if (day) T1Colors.dayCard else Color(0xFF101D18))
        .border(1.dp, T1Colors.gold.copy(alpha = .55f), board)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val stickerSizePx = with(density) { 100.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { stroke ->
                if (stroke.points.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                    stroke.points.drop(1).forEach { path.lineTo(it.x * size.width, it.y * size.height) }
                    drawPath(path, Color(stroke.color), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
        if (caption.isNotEmpty() || mode == ArtMode.Text) {
            BasicTextField(value = caption, onValueChange = {
                caption = it
                prefs.edit().putString("caption", it).apply()
            }, readOnly = mode != ArtMode.Text,
                textStyle = TextStyle(color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 108.dp, end = 30.dp)
                    .fillMaxWidth(.75f),
                decorationBox = { inner ->
                    Box {
                        if (caption.isEmpty()) Text("اكتب هنا لتظهر عبارتك مباشرة", color = textColor.copy(alpha = .48f), fontSize = 18.sp)
                        inner()
                    }
                })
        }
        if (mode == ArtMode.Draw) {
            Canvas(Modifier.fillMaxSize().pointerInput(mode, selected) {
                detectDragGestures(onDragStart = { p ->
                    strokes.add(InkStroke(selected.toArgb(), listOf(Offset((p.x / size.width).coerceIn(0f, 1f),
                        (p.y / size.height).coerceIn(0f, 1f)))))
                }, onDragEnd = { saveStrokes() }, onDrag = { change, _ ->
                    change.consume()
                    if (strokes.isNotEmpty()) {
                        val last = strokes.lastIndex
                        strokes[last] = strokes[last].copy(points = strokes[last].points +
                            Offset((change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f)))
                    }
                })
            }) {}
        }
        if (stickerVisible) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.saudi_emblem_gold), "ملصق النخلة والسيفين",
                modifier = Modifier.offset { IntOffset((stickerX * widthPx).roundToInt(), (stickerY * heightPx).roundToInt()) }
                    .size(100.dp)
                    .then(if (mode == ArtMode.Sticker) Modifier.pointerInput(widthPx, heightPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            stickerX = (stickerX - drag.x / widthPx).coerceIn(0f, (1f - stickerSizePx / widthPx).coerceAtLeast(0f))
                            stickerY = (stickerY + drag.y / heightPx).coerceIn(0f, (1f - stickerSizePx / heightPx).coerceAtLeast(0f))
                            prefs.edit().putFloat("sticker_x", stickerX).putFloat("sticker_y", stickerY).apply()
                        }
                    } else Modifier), contentScale = ContentScale.Fit)
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(if (day) Color(0xEDEEEDE7) else Color(0xE8081611))
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مساحة الإعدادات", color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
                ArtMode.entries.forEach { item ->
                    FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) })
                }
                if (mode == ArtMode.Draw) TextButton(onClick = { strokes.clear(); saveStrokes() }) { Text("مسح الرسم") }
                if (mode == ArtMode.Sticker) TextButton(onClick = {
                    stickerVisible = !stickerVisible
                    prefs.edit().putBoolean("sticker", stickerVisible).apply()
                }) { Text(if (stickerVisible) "إخفاء الملصق" else "إظهار الملصق") }
            }
            if (mode == ArtMode.Draw) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اللون", color = textColor)
                    Box(Modifier.padding(horizontal = 10.dp).size(30.dp).background(selected, RoundedCornerShape(5.dp)))
                    Text("درجة", color = textColor, fontSize = 12.sp)
                    Slider(hue, { hue = it }, valueRange = 0f..360f, modifier = Modifier.weight(2f))
                    Text("تشبع", color = textColor, fontSize = 12.sp)
                    Slider(saturation, { saturation = it }, modifier = Modifier.weight(1f))
                    Text("سطوع", color = textColor, fontSize = 12.sp)
                    Slider(brightness, { brightness = it }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
