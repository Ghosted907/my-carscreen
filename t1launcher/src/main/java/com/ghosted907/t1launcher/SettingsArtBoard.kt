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
import androidx.compose.foundation.gestures.detectDragGestures
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

@Composable
fun SettingsArtBoard(day: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings_art", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ArtMode.Text) }
    var caption by remember { mutableStateOf(prefs.getString("caption", "").orEmpty()) }
    var captionX by remember { mutableFloatStateOf(prefs.getFloat("caption_x", .04f)) }
    var captionY by remember { mutableFloatStateOf(prefs.getFloat("caption_y", .36f).coerceAtLeast(.32f)) }
    var captionSize by remember { mutableFloatStateOf(prefs.getFloat("caption_size", 28f)) }
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
    var hue by remember { mutableFloatStateOf(45f) }
    var saturation by remember { mutableFloatStateOf(.8f) }
    var brightness by remember { mutableFloatStateOf(.9f) }
    val selected = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val textColor = if (day) T1Colors.dayText else Color.White
    fun changePalette(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        hue = h; saturation = s; brightness = v
        val argb = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        if (mode == ArtMode.Text) {
            captionColor = argb
            prefs.edit().putInt("caption_color", argb).apply()
        } else {
            drawColor = argb
            prefs.edit().putInt("draw_color", argb).apply()
        }
    }
    LaunchedEffect(mode) {
        if (mode != ArtMode.Sticker) {
            val values = FloatArray(3)
            AndroidColor.colorToHSV(if (mode == ArtMode.Text) captionColor else drawColor, values)
            hue = values[0]; saturation = values[1]; brightness = values[2]
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
                    fontWeight = FontWeight.Medium, fontFamily = FontFamily(Font(R.font.arabic))),
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((captionX * widthPx).roundToInt(), (captionY * heightPx).roundToInt()) }
                    .width((maxWidth * .42f).coerceIn(200.dp, 560.dp))
                    .then(if (mode == ArtMode.Text) Modifier.pointerInput(widthPx, heightPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            captionX = (captionX - drag.x / widthPx).coerceIn(0f, .70f)
                            captionY = (captionY + drag.y / heightPx).coerceIn(.32f, .85f)
                            prefs.edit().putFloat("caption_x", captionX).putFloat("caption_y", captionY).apply()
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
                        detectDragGestures { change, drag ->
                            change.consume()
                            stickerX = (stickerX - drag.x / widthPx).coerceIn(0f, (1f - stickerSizePx / widthPx).coerceAtLeast(0f))
                            stickerY = (stickerY + drag.y / heightPx).coerceIn(0f, (1f - stickerSizePx / heightPx).coerceAtLeast(0f))
                            prefs.edit().putFloat("sticker_x", stickerX).putFloat("sticker_y", stickerY).apply()
                        }
                    } else Modifier), contentScale = ContentScale.Fit)
        }
        if (customBitmap != null) {
            Image(customBitmap, "ملصق من الملفات", modifier = Modifier
                .offset { IntOffset((customX * widthPx).roundToInt(), (customY * heightPx).roundToInt()) }
                .size(customSize.dp)
                .then(if (mode == ArtMode.Sticker) Modifier.pointerInput(widthPx, heightPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        customX = (customX - drag.x / widthPx).coerceIn(0f, (1f - customSizePx / widthPx).coerceAtLeast(0f))
                        customY = (customY + drag.y / heightPx).coerceIn(0f, (1f - customSizePx / heightPx).coerceAtLeast(0f))
                        prefs.edit().putFloat("custom_x", customX).putFloat("custom_y", customY).apply()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("خط عربي · حجم النص", color = textColor, fontSize = 12.sp)
                    TextButton(onClick = { captionSize = (captionSize - 2f).coerceAtLeast(16f); prefs.edit().putFloat("caption_size", captionSize).apply() }) { Text("−") }
                    Text("${captionSize.toInt()}", color = textColor)
                    TextButton(onClick = { captionSize = (captionSize + 2f).coerceAtMost(72f); prefs.edit().putFloat("caption_size", captionSize).apply() }) { Text("+") }
                    Text("اسحب النص لتغيير موقعه", color = textColor, fontSize = 11.sp)
                }
            }
            if (mode == ArtMode.Sticker) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("حجم النخلة والسيفين", color = textColor, fontSize = 12.sp)
                    TextButton(onClick = { stickerSize = (stickerSize - 10f).coerceAtLeast(40f); prefs.edit().putFloat("sticker_size", stickerSize).apply() }) { Text("−") }
                    Text("${stickerSize.toInt()}", color = textColor)
                    TextButton(onClick = { stickerSize = (stickerSize + 10f).coerceAtMost(240f); prefs.edit().putFloat("sticker_size", stickerSize).apply() }) { Text("+") }
                    if (customBitmap != null) {
                        Spacer(Modifier.width(24.dp))
                        Text("حجم الملصق المضاف", color = textColor, fontSize = 12.sp)
                        TextButton(onClick = { customSize = (customSize - 10f).coerceAtLeast(40f); prefs.edit().putFloat("custom_size", customSize).apply() }) { Text("−") }
                        Text("${customSize.toInt()}", color = textColor)
                        TextButton(onClick = { customSize = (customSize + 10f).coerceAtMost(240f); prefs.edit().putFloat("custom_size", customSize).apply() }) { Text("+") }
                    }
                }
            }
            if (mode == ArtMode.Draw || mode == ArtMode.Text) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == ArtMode.Text) "لون النص" else "لون الرسم", color = textColor)
                    Canvas(Modifier.padding(horizontal = 8.dp).width(150.dp).height(28.dp)
                        .pointerInput(mode, saturation, brightness) {
                            detectTapGestures { point -> changePalette(h = (point.x / size.width * 360f).coerceIn(0f, 360f)) }
                        }) {
                        drawRect(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    }
                    Box(Modifier.padding(horizontal = 8.dp).size(30.dp).background(selected, RoundedCornerShape(5.dp)))
                    Text("درجة", color = textColor, fontSize = 12.sp)
                    Slider(hue, { changePalette(h = it) }, valueRange = 0f..360f, modifier = Modifier.weight(2f))
                    Text("تشبع", color = textColor, fontSize = 12.sp)
                    Slider(saturation, { changePalette(s = it) }, modifier = Modifier.weight(1f))
                    Text("سطوع", color = textColor, fontSize = 12.sp)
                    Slider(brightness, { changePalette(v = it) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
