package com.ghosted907.t1remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt


@Composable
fun ClusterTab(session: Session) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Flow {
            listOf("أزرار العدادات", "لوحة اللانشر والملصقات", "لوحتي الحرة").forEachIndexed { i, t -> Pill(t, section == i) { section = i } }
        }
        when (section) {
            0 -> ClusterButtons(session)
            1 -> BoardEditor(session)
            else -> ArtTab(session.connected && session.info.optBoolean("art") && session.info.optBoolean("cluster")) { session.send(it, timeoutMs = RemoteClient.IMAGE_TIMEOUT_MS) }
        }
    }
}

@Composable
private fun ClusterButtons(session: Session) {
    val can = session.supports(RemoteProtocol.OP_CLUSTER)
    fun cluster(a: String, v: Int = 0) = session.send(op(RemoteProtocol.OP_CLUSTER).put("action", a).put("value", v))
    fun run(id: String) = session.send(op(RemoteProtocol.OP_RUN).put("id", id))
    NeedsUpdate(session)
    if (!session.info.optBoolean("cluster")) Note("شاشة العدادات غير متاحة الآن.", Warn)
    // Current theme / font come from the car's readings (rev 4 summary); "غير متاح" when unknown.
    val live = rememberLive(session, 3000)
    val theme = live.summary.optInt("theme", 0).takeIf { it > 0 && !live.stale() }
    val font = live.summary.optInt("font", 0).takeIf { it > 0 && !live.stale() }
    Section("مظهر العدادات", "نفس أزرار صفحة العدادات في اللانشر، كل زر وظيفة منفصلة.") {
        Note("الحالي: ${theme?.let { "قالب $it" } ?: "القالب غير متاح"} · ${font?.let { "خط $it" } ?: "الخط غير متاح"}")
        Flow {
            Pill("قالب كلاسيكي", enabled = can) { cluster("classic") }
            (1..3).forEach { t -> Pill("قالب $t", selected = theme == t, enabled = can) { cluster("theme", t) } }
            (1..2).forEach { f -> Pill("خط $f", selected = font == f, enabled = can) { cluster("font", f) } }
        }
    }
    Section("نوافذ T1 على العدادات") {
        Flow {
            Pill("إخفاء الإضافات", enabled = can) { cluster("hide_addons") }
            Pill("العدادات الأصلية", enabled = can) { cluster("stock") }
            Pill("إغلاق قوي", enabled = can && session.info.optBoolean("close")) { cluster("close_app") }
        }
        Note("«العدادات الأصلية» تخفي إضافات T1 وتغلق نافذتها، مثل زرّها في اللانشر.")
        if (!session.info.optBoolean("close")) Note("الإغلاق القوي معطل في اللانشر (ClusterForceClose.ENABLED = false)، ولا يُفعَّل من الجوال.")
    }
    Section("تطبيق العدادات", "التطبيق المختار في الإعدادات (التطبيقات على لوحة العدادات).") {
        Flow {
            Pill("افتح على العدادات", enabled = session.supports(RemoteProtocol.OP_RUN)) { run("cluster_open_cluster") }
            Pill("افتح على الرئيسية", enabled = session.supports(RemoteProtocol.OP_RUN)) { run("cluster_open_main") }
            Pill("انقل للشاشة الأخرى", enabled = session.supports(RemoteProtocol.OP_RUN)) { run("cluster_move") }
            Pill("صفحة العدادات على السيارة", enabled = session.supports(RemoteProtocol.OP_PAGE)) { session.send(op(RemoteProtocol.OP_PAGE).put("page", "cluster")) }
        }
        Note("اختيار التطبيق وتشغيله عند الإقلاع من تبويب الإعدادات ← العدادات.")
    }
}

// ---------------------------------------------------------------- Launcher art board (text, drawing, stickers)
private class Stroke2(val color: Int, val points: List<Offset>)
private enum class Layer(val label: String) { Caption("النص"), Emblem("ملصق النخلة والسيفين"), Custom("ملصقي"), Draw("رسم") }
private val palette = listOf(0xFFFFFFFF, 0xFFF4F0E5, 0xFFD5B36A, 0xFF16744A, 0xFF7FD3A0, 0xFFE0524A, 0xFF4A90E2, 0xFF000000).map { it.toInt() }
/** Launcher board width in dp used to scale text and stickers in the phone preview (approximate). */
private const val BOARD_DP = 1000f

@Composable
private fun BoardEditor(session: Session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var loaded by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var captionX by remember { mutableFloatStateOf(.04f) }
    var captionY by remember { mutableFloatStateOf(.36f) }
    var captionSize by remember { mutableFloatStateOf(28f) }
    var font by remember { mutableIntStateOf(0) }
    var fonts by remember { mutableStateOf(listOf<String>()) }
    var captionColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var drawColor by remember { mutableIntStateOf(0xFFF4F0E5.toInt()) }
    var sticker by remember { mutableStateOf(true) }
    var stickerX by remember { mutableFloatStateOf(.45f) }
    var stickerY by remember { mutableFloatStateOf(.44f) }
    var stickerSize by remember { mutableFloatStateOf(100f) }
    var custom by remember { mutableStateOf(false) }
    var customImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var customX by remember { mutableFloatStateOf(.72f) }
    var customY by remember { mutableFloatStateOf(.44f) }
    var customSize by remember { mutableFloatStateOf(100f) }
    var emblem by remember { mutableStateOf<ImageBitmap?>(null) }
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var layer by remember { mutableStateOf(Layer.Emblem) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        session.send(op(RemoteProtocol.OP_BOARD), quiet = true) { d ->
            caption = d.optString("caption"); captionX = d.optDouble("caption_x", .04).toFloat(); captionY = d.optDouble("caption_y", .36).toFloat()
            captionSize = d.optDouble("caption_size", 28.0).toFloat(); font = d.optInt("caption_font", 0)
            captionColor = d.optInt("caption_color", android.graphics.Color.WHITE); drawColor = d.optInt("draw_color", drawColor)
            sticker = d.optBoolean("sticker", true); stickerX = d.optDouble("sticker_x", .45).toFloat(); stickerY = d.optDouble("sticker_y", .44).toFloat()
            stickerSize = d.optDouble("sticker_size", 100.0).toFloat()
            custom = d.optBoolean("custom"); customX = d.optDouble("custom_x", .72).toFloat(); customY = d.optDouble("custom_y", .44).toFloat()
            customSize = d.optDouble("custom_size", 100.0).toFloat()
            fonts = d.optJSONArray("fonts")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            strokes.clear()
            d.optJSONArray("strokes")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { s ->
                val p = s.optJSONArray("points") ?: return@let
                strokes += Stroke2(s.optInt("color"), (0 until p.length()).mapNotNull { j -> p.optJSONArray(j)?.let { Offset(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) } })
            } }
            val thumb = d.optString("custom_image")
            scope.launch { customImage = withContext(Dispatchers.Default) { decodeImage(thumb) } }
            loaded = true
        }
        if (emblem == null) session.send(op(RemoteProtocol.OP_ASSET).put("name", "emblem"), quiet = true) { d ->
            val b = d.optString("image"); scope.launch { emblem = withContext(Dispatchers.Default) { decodeImage(b) } }
        }
    }
    LaunchedEffect(session.epoch) { if (session.supports(RemoteProtocol.OP_BOARD)) load() }

    fun save(fields: JSONObject, msg: Boolean = false) = session.send(fields.put("op", RemoteProtocol.OP_BOARD_SET), quiet = !msg)
    // Gestures change positions many times a second: send only the final position.
    var pending by remember { mutableStateOf<Job?>(null) }
    fun saveLater(fields: () -> JSONObject) {
        pending?.cancel()
        pending = scope.launch { delay(350); save(fields()) }
    }
    fun strokesJson(): JSONArray = JSONArray().also { a -> strokes.forEach { s ->
        val p = JSONArray(); s.points.forEach { p.put(JSONArray().put(it.x.toDouble().coerceIn(0.0, 1.0)).put(it.y.toDouble().coerceIn(0.0, 1.0))) }
        a.put(JSONObject().put("color", s.color).put("points", p))
    } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val encoded = withContext(Dispatchers.IO) { encodeImage(context, uri, 512, Bitmap.CompressFormat.PNG) }
            if (encoded == null) session.say("تعذر قراءة الصورة أو أنها كبيرة جداً", false)
            else session.send(op(RemoteProtocol.OP_BOARD_SET).put("custom_image", encoded), timeoutMs = RemoteClient.IMAGE_TIMEOUT_MS,
                onError = { busy = false }) { busy = false; load() }
            if (encoded == null) busy = false
        }
    }

    if (!session.supports(RemoteProtocol.OP_BOARD)) { NeedsUpdate(session); return }
    Section("لوحة العدادات في اللانشر", "نفس لوحة صفحة العدادات: نص، رسم، ملصق النخلة والسيفين وملصق من صورك. حرّك العنصر المختار بإصبعك، وكبّره أو صغّره بإصبعين.") {
        Flow { Layer.values().forEach { l -> Pill(l.label, layer == l) { layer = l } } }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101D18))
            .border(1.dp, Color(0xFF2F4A3D), RoundedCornerShape(12.dp))) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val k = w / (BOARD_DP * density)   // board dp -> preview px
            Canvas(Modifier.fillMaxSize().pointerInput(layer, w, h) {
                if (layer == Layer.Draw) detectDragGestures(onDragStart = { p ->
                    // RemoteProtocol.MAX_STROKES: the launcher refuses more, so the phone stops adding instead of failing later.
                    if (strokes.size < RemoteProtocol.MAX_STROKES) strokes += Stroke2(drawColor, listOf(Offset((p.x / w).coerceIn(0f, 1f), (p.y / h).coerceIn(0f, 1f))))
                }, onDragEnd = { save(JSONObject().put("strokes", strokesJson())) }) { change, _ ->
                    change.consume()
                    if (strokes.isNotEmpty() && strokes.last().points.size < RemoteProtocol.MAX_POINTS) {
                        val last = strokes.removeAt(strokes.lastIndex)
                        strokes += Stroke2(last.color, last.points + Offset((change.position.x / w).coerceIn(0f, 1f), (change.position.y / h).coerceIn(0f, 1f)))
                    }
                } else detectTransformGestures { _, pan, zoom, _ ->
                    // RTL like the launcher: x is the distance of the item's right edge from the board's right edge.
                    when (layer) {
                        Layer.Caption -> {
                            captionX = (captionX - pan.x / w).coerceIn(0f, .70f); captionY = (captionY + pan.y / h).coerceIn(.32f, .85f)
                            captionSize = (captionSize * zoom).coerceIn(16f, 120f)
                            saveLater { JSONObject().put("caption_x", captionX.toDouble()).put("caption_y", captionY.toDouble()).put("caption_size", captionSize.toDouble()) }
                        }
                        Layer.Emblem -> {
                            stickerSize = (stickerSize * zoom).coerceIn(40f, 240f)
                            stickerX = (stickerX - pan.x / w).coerceIn(0f, 1f); stickerY = (stickerY + pan.y / h).coerceIn(0f, 1f)
                            saveLater { JSONObject().put("sticker_x", stickerX.toDouble()).put("sticker_y", stickerY.toDouble()).put("sticker_size", stickerSize.toDouble()) }
                        }
                        Layer.Custom -> if (custom) {
                            customSize = (customSize * zoom).coerceIn(40f, 240f)
                            customX = (customX - pan.x / w).coerceIn(0f, 1f); customY = (customY + pan.y / h).coerceIn(0f, 1f)
                            saveLater { JSONObject().put("custom_x", customX.toDouble()).put("custom_y", customY.toDouble()).put("custom_size", customSize.toDouble()) }
                        }
                        Layer.Draw -> {}
                    }
                }
            }) {
                strokes.forEach { s ->
                    if (s.points.isEmpty()) return@forEach
                    val path = Path().apply { moveTo(s.points[0].x * w, s.points[0].y * h); s.points.drop(1).forEach { lineTo(it.x * w, it.y * h) } }
                    drawPath(path, Color(s.color), style = Stroke((4 * density * k).coerceAtLeast(1.5f), cap = StrokeCap.Round))
                }
                fun image(img: ImageBitmap?, x: Float, y: Float, sizeDp: Float, selected: Boolean) {
                    val px = sizeDp * density * k
                    val right = w * (1f - x); val left = right - px; val top = h * y
                    if (img != null) {
                        val s = minOf(px / img.width, px / img.height)
                        val dw = img.width * s; val dh = img.height * s
                        drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset((left + (px - dw) / 2).roundToInt(), (top + (px - dh) / 2).roundToInt()),
                            dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)))
                    } else drawRect(Color(0xFF7FD3A0).copy(alpha = .4f), Offset(left, top), Size(px, px))
                    if (selected) drawRect(Color(0xFF7FD3A0), Offset(left, top), Size(px, px), style = Stroke(2f))
                }
                if (sticker) image(emblem, stickerX, stickerY, stickerSize, layer == Layer.Emblem)
                if (custom) image(customImage, customX, customY, customSize, layer == Layer.Custom)
                if (caption.isNotEmpty()) drawIntoCanvas { c ->
                    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = captionColor; textSize = captionSize * density * k * 1.0f; textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val right = w * (1f - captionX); val top = h * captionY
                    c.nativeCanvas.drawText(caption, right, top - paint.ascent(), paint)
                    if (layer == Layer.Caption) {
                        val tw = paint.measureText(caption)
                        drawRect(Color(0xFF7FD3A0), Offset(right - tw, top), Size(tw, paint.descent() - paint.ascent()), style = Stroke(2f))
                    }
                }
            }
        }
        when (layer) {
            Layer.Caption -> {
                OutlinedTextField(caption, { caption = it.take(120) }, label = { Text("النص") }, modifier = Modifier.fillMaxWidth())
                Small("احفظ النص") { save(JSONObject().put("caption", caption), msg = true) }
                Text("الخط", color = Ink)
                Flow { fonts.forEachIndexed { i, f -> Pill(f, font == i) { font = i; save(JSONObject().put("caption_font", i)) } } }
                Text("لون النص", color = Ink)
                ColorRow(captionColor) { captionColor = it; save(JSONObject().put("caption_color", it)) }
                Text("الحجم ${captionSize.roundToInt()}", color = Ink)
                Slider(captionSize, { captionSize = it }, valueRange = 16f..120f, onValueChangeFinished = { save(JSONObject().put("caption_size", captionSize.toDouble())) })
            }
            Layer.Emblem -> {
                SwitchLine("إظهار ملصق النخلة والسيفين", sticker) { sticker = it; save(JSONObject().put("sticker", it)) }
                Text("الحجم ${stickerSize.roundToInt()}", color = Ink)
                Slider(stickerSize, { stickerSize = it }, valueRange = 40f..240f, onValueChangeFinished = { save(JSONObject().put("sticker_size", stickerSize.toDouble())) })
                Small("إعادة لموضعه") { stickerX = .45f; stickerY = .44f; stickerSize = 100f
                    save(JSONObject().put("sticker_x", .45).put("sticker_y", .44).put("sticker_size", 100.0)) }
            }
            Layer.Custom -> {
                Flow {
                    Pill(if (custom) "تغيير الصورة" else "اختر صورة من الجوال", enabled = !busy) { picker.launch("image/*") }
                    if (custom) Pill("إزالة ملصقي") { save(JSONObject().put("custom_remove", true), msg = true); custom = false; customImage = null }
                }
                if (custom) {
                    Text("الحجم ${customSize.roundToInt()}", color = Ink)
                    Slider(customSize, { customSize = it }, valueRange = 40f..240f, onValueChangeFinished = { save(JSONObject().put("custom_size", customSize.toDouble())) })
                }
                Note("الدوران غير مدعوم في لوحة اللانشر؛ للتدوير استخدم «لوحتي الحرة». صور GIF المتحركة تُرسل كصورة ثابتة (لا حركة).")
            }
            Layer.Draw -> {
                Text("لون الرسم", color = Ink)
                ColorRow(drawColor) { drawColor = it; save(JSONObject().put("draw_color", it)) }
                Note("حتى ${RemoteProtocol.MAX_STROKES} خط و${RemoteProtocol.MAX_POINTS} نقطة لكل خط (${strokes.size} الآن).",
                    if (strokes.size >= RemoteProtocol.MAX_STROKES) Warn else Muted)
                Flow {
                    Pill("تراجع", enabled = strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); save(JSONObject().put("strokes", strokesJson())) }
                    Pill("مسح الرسم", enabled = strokes.isNotEmpty()) { strokes.clear(); save(JSONObject().put("strokes_clear", true), msg = true) }
                }
            }
        }
        Flow {
            Pill("اعرض على العدادات", selected = true, enabled = session.connected) {
                session.send(JSONObject().put("op", RemoteProtocol.OP_BOARD_SET).put("caption", caption), quiet = true) {
                    session.send(op(RemoteProtocol.OP_BOARD_APPLY), timeoutMs = RemoteClient.IMAGE_TIMEOUT_MS)
                }
            }
            Pill("أخفِ من العدادات") { session.send(op(RemoteProtocol.OP_ART_CLEAR)) }
            Pill("إعادة التحميل") { load() }
        }
        if (!loaded) Note("جاري تحميل اللوحة…")
        Note("التعديلات تُحفظ في اللانشر وتظهر في صفحة العدادات عند فتحها من جديد.")
    }
}

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    Flow {
        palette.forEach { c ->
            androidx.compose.foundation.layout.Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(c))
                .border(if (c == selected) 3.dp else 1.dp, if (c == selected) Accent else Line, RoundedCornerShape(50))
                .clickable { onPick(c) })
        }
    }
}

/** Reads, downsizes and encodes a phone image on the caller's (IO) thread. Null when unreadable or too large. */
fun encodeImage(context: android.content.Context, uri: Uri, maxSide: Int, format: Bitmap.CompressFormat): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
    while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxSide * 2) options.inSampleSize *= 2
    val decoded = context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
    val m = maxOf(decoded.width, decoded.height)
    val bitmap = if (m > maxSide) Bitmap.createScaledBitmap(decoded, decoded.width * maxSide / m, decoded.height * maxSide / m, true) else decoded
    val out = ByteArrayOutputStream()
    bitmap.compress(format, if (format == Bitmap.CompressFormat.PNG) 100 else 85, out)
    val s = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    if (s.length > RemoteProtocol.MAX_IMAGE) null else s
}.getOrNull()
