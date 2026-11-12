package com.ghosted907.t1launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private data class InkStroke(val color: Int, val points: List<Offset>)
private enum class ArtMode(val label: String) { Text("نص"), Draw("رسم"), Sticker("ملصقات") }
// Bundled OFL fonts (Google Fonts) so the same faces show on the car, plus the system families.
// Order matters: the saved choice is an index, keep existing entries first.
private val artFonts = listOf(
    "عربي" to FontFamily(Font(R.font.arabic)),
    "حديث / Modern" to FontFamily.SansSerif,
    "تقليدي / Serif" to FontFamily.Serif,
    "يدوي / Handwriting" to FontFamily.Cursive,
    "أحادي / Mono" to FontFamily.Monospace,
    "القاهرة Cairo" to FontFamily(Font(R.font.art_cairo)),
    "تجوال Tajawal" to FontFamily(Font(R.font.art_tajawal)),
    "أميري Amiri" to FontFamily(Font(R.font.art_amiri)),
    "ريم كوفي Reem Kufi" to FontFamily(Font(R.font.art_reemkufi)),
    "Lalezar عريض" to FontFamily(Font(R.font.art_lalezar)),
    "Orbitron" to FontFamily(Font(R.font.art_orbitron)),
    "Bebas Neue" to FontFamily(Font(R.font.art_bebas)),
    "Pacifico" to FontFamily(Font(R.font.art_pacifico))
)

@Composable
fun SettingsArtBoard(day: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_art", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ArtMode.Text) }
    var caption by remember { mutableStateOf(prefs.getString("caption", "").orEmpty()) }
    var captionX by remember { mutableFloatStateOf(prefs.getFloat("caption_x", .04f)) }
    var captionY by remember { mutableFloatStateOf(prefs.getFloat("caption_y", .36f).coerceAtLeast(.32f)) }
    var captionSize by remember { mutableFloatStateOf(prefs.getFloat("caption_size", 28f)) }
    var fontIndex by remember { mutableIntStateOf(prefs.getInt("caption_font", 0).coerceIn(0, artFonts.lastIndex)) }
    var captionColor by remember { mutableIntStateOf(prefs.getInt("caption_color", if (day) AndroidColor.rgb(25, 55, 43) else AndroidColor.WHITE)) }
    var drawColor by remember { mutableIntStateOf(prefs.getInt("draw_color", AndroidColor.rgb(229, 185, 71))) }
    var stickerVisible by remember { mutableStateOf(prefs.getBoolean("sticker", true)) }
    var stickerX by remember { mutableFloatStateOf(prefs.getFloat("sticker_x", .45f)) }
    var stickerY by remember { mutableFloatStateOf(prefs.getFloat("sticker_y", .44f)) }
    var stickerSize by remember { mutableFloatStateOf(prefs.getFloat("sticker_size", 100f).coerceIn(40f, 240f)) }
    var customUri by remember { mutableStateOf(prefs.getString("custom_sticker", "").orEmpty()) }
    var customX by remember { mutableFloatStateOf(prefs.getFloat("custom_x", .72f)) }
    var customY by remember { mutableFloatStateOf(prefs.getFloat("custom_y", .44f)) }
    var customSize by remember { mutableFloatStateOf(prefs.getFloat("custom_size", 100f).coerceIn(40f, 240f)) }
    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            customUri = uri.toString()
            customX = .72f
            customY = .44f
            prefs.edit().putString("custom_sticker", customUri)
                .putFloat("custom_x", customX).putFloat("custom_y", customY).apply()
        }
    }
    val customBitmap = remember(customUri) {
        runCatching {
            if (customUri.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(customUri))?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }
        }.getOrNull()
    }
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
    val textColor = if (day) T1Colors.dayText else Color.White
    fun changePalette(x: Float, y: Float) {
        val h = (x * 360f).coerceIn(0f, 359.9f)
        val s = if (y < .5f) (y * 2f).coerceIn(0f, 1f) else 1f
        val v = if (y < .5f) 1f else ((1f - y) * 2f).coerceIn(0f, 1f)
        val argb = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        if (mode == ArtMode.Text) {
            captionColor = argb
            prefs.edit().putInt("caption_color", argb).apply()
        } else {
            drawColor = argb
            prefs.edit().putInt("draw_color", argb).apply()
        }
    }
    val board = RoundedCornerShape(10.dp)
    BoxWithConstraints(modifier.clip(board)
        .background(if (day) T1Colors.dayCard else Color(0xFF101D18))
        .border(1.dp, T1Colors.gold.copy(alpha = .55f), board)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val stickerSizePx = with(density) { stickerSize.dp.toPx() }
        val customSizePx = with(density) { customSize.dp.toPx() }
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
                textStyle = TextStyle(color = Color(captionColor), fontSize = captionSize.sp,
                    fontWeight = FontWeight.Medium, fontFamily = artFonts[fontIndex].second),
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((captionX * widthPx).roundToInt(), (captionY * heightPx).roundToInt()) }
                    .width((maxWidth * .42f).coerceIn(200.dp, 560.dp))
                    .then(if (mode == ArtMode.Text) Modifier.pointerInput(widthPx, heightPx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            captionX = (captionX - pan.x / widthPx).coerceIn(0f, .70f)
                            captionY = (captionY + pan.y / heightPx).coerceIn(.32f, .85f)
                            captionSize = (captionSize * zoom).coerceIn(16f, 120f)
                            prefs.edit().putFloat("caption_x", captionX).putFloat("caption_y", captionY)
                                .putFloat("caption_size", captionSize).apply()
                        }
                    } else Modifier),
                decorationBox = { inner ->
                    Box {
                        if (caption.isEmpty()) Text("اكتب هنا لتظهر عبارتك مباشرة", color = textColor.copy(alpha = .48f), fontSize = 18.sp)
                        inner()
                    }
                })
        }
        if (mode == ArtMode.Draw) {
            Canvas(Modifier.fillMaxSize().pointerInput(mode, drawColor) {
                detectDragGestures(onDragStart = { p ->
                    strokes.add(InkStroke(drawColor, listOf(Offset((p.x / size.width).coerceIn(0f, 1f),
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
                    .size(stickerSize.dp)
                    .then(if (mode == ArtMode.Sticker) Modifier.pointerInput(widthPx, heightPx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            stickerSize = (stickerSize * zoom).coerceIn(40f, 320f)
                            stickerX = (stickerX - pan.x / widthPx).coerceIn(0f, (1f - stickerSizePx / widthPx).coerceAtLeast(0f))
                            stickerY = (stickerY + pan.y / heightPx).coerceIn(0f, (1f - stickerSizePx / heightPx).coerceAtLeast(0f))
                            prefs.edit().putFloat("sticker_x", stickerX).putFloat("sticker_y", stickerY)
                                .putFloat("sticker_size", stickerSize).apply()
                        }
                    } else Modifier), contentScale = ContentScale.Fit)
        }
        if (customBitmap != null) {
            Image(customBitmap, "ملصق من الملفات", modifier = Modifier
                .offset { IntOffset((customX * widthPx).roundToInt(), (customY * heightPx).roundToInt()) }
                .size(customSize.dp)
                .then(if (mode == ArtMode.Sticker) Modifier.pointerInput(widthPx, heightPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        customSize = (customSize * zoom).coerceIn(40f, 320f)
                        customX = (customX - pan.x / widthPx).coerceIn(0f, (1f - customSizePx / widthPx).coerceAtLeast(0f))
                        customY = (customY + pan.y / heightPx).coerceIn(0f, (1f - customSizePx / heightPx).coerceAtLeast(0f))
                        prefs.edit().putFloat("custom_x", customX).putFloat("custom_y", customY)
                            .putFloat("custom_size", customSize).apply()
                    }
                } else Modifier), contentScale = ContentScale.Fit)
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(if (day) Color(0xEDEEEDE7) else Color(0xE8081611))
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مساحة العدادات", color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
                ArtMode.entries.forEach { item ->
                    FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) })
                }
                if (mode == ArtMode.Draw) TextButton(onClick = { strokes.clear(); saveStrokes() }) { Text("مسح الرسم") }
                if (mode == ArtMode.Sticker) TextButton(onClick = { pickSticker.launch(arrayOf("image/*")) }) {
                    Text("إضافة من الملفات")
                }
                if (mode == ArtMode.Sticker && customUri.isNotBlank()) TextButton(onClick = {
                    customUri = ""; prefs.edit().remove("custom_sticker").apply()
                }) { Text("إزالة المضاف") }
                if (mode == ArtMode.Sticker) TextButton(onClick = {
                    stickerVisible = !stickerVisible
                    prefs.edit().putBoolean("sticker", stickerVisible).apply()
                }) { Text(if (stickerVisible) "إخفاء الملصق" else "إظهار الملصق") }
            }
            if (mode == ArtMode.Text) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اسحب النص لتحريكه، وبإصبعين لتغيير حجمه", color = textColor, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    artFonts.forEachIndexed { index, (label, family) ->
                        FilterChip(selected = fontIndex == index, onClick = {
                            fontIndex = index; prefs.edit().putInt("caption_font", index).apply()
                        }, label = { Text(label, fontFamily = family, fontSize = 11.sp) })
                    }
                }
            }
            if (mode == ArtMode.Sticker) {
                Text("اسحب الملصق لتحريكه، وبإصبعين لتغيير حجمه", color = textColor, fontSize = 12.sp)
            }
            if (mode == ArtMode.Draw || mode == ArtMode.Text) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == ArtMode.Text) "لون النص" else "لون الرسم", color = textColor)
                    Canvas(Modifier.padding(horizontal = 8.dp).width(320.dp).height(72.dp)
                        .pointerInput(mode) {
                            detectTapGestures { point -> changePalette(point.x / size.width, point.y / size.height) }
                        }
                        .pointerInput(mode) {
                            // Slide a finger across the board to pick continuously.
                            detectDragGestures(onDragStart = { p -> changePalette((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f)) }) { change, _ ->
                                change.consume()
                                changePalette((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                            }
                        }) {
                        val cellW = size.width / 60f; val cellH = size.height / 24f
                        repeat(60) { x -> repeat(24) { y ->
                            val py = y / 23f
                            val sat = if (py < .5f) py * 2f else 1f
                            val value = if (py < .5f) 1f else (1f - py) * 2f
                            drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(x * 6f, sat, value))),
                                topLeft = Offset(x * cellW, y * cellH),
                                size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
                        } }
                    }
                    Box(Modifier.padding(horizontal = 8.dp).size(36.dp)
                        .border(1.dp, textColor, RoundedCornerShape(5.dp))
                        .background(Color(if (mode == ArtMode.Text) captionColor else drawColor), RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
