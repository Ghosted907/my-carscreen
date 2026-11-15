package com.ghosted907.t1remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * "لوحتي الحرة": a free 800×400 picture drawn on the phone and shown as a small window on the cluster.
 * Text, emoji stickers and photos are separate layers: tap to select, drag to move, pinch to resize,
 * twist with two fingers to rotate. Encoding runs off the main thread.
 */
@Composable
fun ArtTab(enabled: Boolean, send: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val board = remember { PhoneArtBoard(context) }
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var x by remember { mutableFloatStateOf(.5f) }
    var y by remember { mutableFloatStateOf(.7f) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    DisposableEffect(board) {
        board.onSelect = { selected = it }
        onDispose { board.onSelect = null }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    while (bounds.outWidth / options.inSampleSize > 800 || bounds.outHeight / options.inSampleSize > 400) options.inSampleSize *= 2
                    context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
                }
            }
            result.onSuccess { board.addImage(it); error = "" }.onFailure { error = "تعذر قراءة الصورة" }
            loading = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("لوحتي الحرة على العدادات", "ارسم، أضف نصاً أو ملصقاً أو صورة. اضغط على العنصر لاختياره ثم حرّكه، كبّره بإصبعين، ولفّ الإصبعين لتدويره.") {
            AndroidView(factory = { board }, modifier = Modifier.fillMaxWidth().aspectRatio(2f))
            Flow {
                Pill("تحريك العناصر", !drawMode) { drawMode = false; board.drawMode = false }
                Pill("رسم", drawMode) { drawMode = true; board.drawMode = true }
                Pill("حذف المختار", enabled = selected >= 0) { board.removeSelected() }
                Pill("مسح الكل") { board.clear() }
            }
            OutlinedTextField(caption, { caption = it.take(120) }, label = { Text("اكتب نصاً") }, modifier = Modifier.fillMaxWidth())
            Flow {
                Pill("أضف النص", enabled = caption.isNotBlank()) { board.addText(caption); caption = "" }
                Pill(if (loading) "…" else "أضف صورة", enabled = !loading) { picker.launch("image/*") }
            }
            Text("ملصقات", color = Ink)
            Flow { listOf("🇸🇦", "🌴", "⚔️", "⭐", "♥", "🚙", "🔥", "👍").forEach { s -> Pill(s) { board.addText(s, sticker = true) } } }
            Text("اللون", color = Ink)
            Flow {
                listOf("ذهبي" to 0xFFD5B36A.toInt(), "أخضر" to 0xFF16744A.toInt(), "أبيض" to android.graphics.Color.WHITE,
                    "أحمر" to 0xFFE0524A.toInt(), "أزرق" to 0xFF4A90E2.toInt()).forEach { (label, color) ->
                    Pill(label) { board.ink = color; board.recolorSelected(color) }
                }
            }
            Text("مكان اللوحة على العدادات: أفقي", color = Ink)
            Slider(x, { x = it })
            Text("رأسي", color = Ink)
            Slider(y, { y = it })
            if (error.isNotEmpty()) Note(error, Bad)
            if (!enabled) Note("عرض اللوحة يحتاج اتصالاً وشاشة عدادات متاحة.")
            Flow {
                Pill("عرض على العدادات", selected = true, enabled = enabled && !loading) {
                    scope.launch {
                        loading = true
                        val snapshot = board.snapshot()
                        val image = withContext(Dispatchers.Default) { PhoneArtBoard.encode(snapshot) }
                        loading = false
                        if (image.length > RemoteProtocol.MAX_IMAGE) error = "الصورة كبيرة، جرّب صورة أبسط"
                        else send(JSONObject().put("op", RemoteProtocol.OP_ART).put("image", image).put("x", x.toDouble()).put("y", y.toDouble()))
                    }
                }
                Pill("إخفاء من العدادات", enabled = enabled) { send(JSONObject().put("op", RemoteProtocol.OP_ART_CLEAR)) }
            }
        }
    }
}

/** Layered board in 800×400 board units. */
class PhoneArtBoard(context: Context) : View(context) {
    private sealed class Item {
        var cx = 400f; var cy = 200f; var scale = 1f; var angle = 0f
        abstract val w: Float; abstract val h: Float
        abstract fun drawBody(c: Canvas)
        class Text(val text: String, var color: Int, val size: Float) : Item() {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; textAlign = Paint.Align.CENTER; color = this@Text.color }
            override val w get() = paint.measureText(text).coerceAtLeast(20f)
            override val h get() = paint.fontSpacing
            override fun drawBody(c: Canvas) { paint.color = color; c.drawText(text, 0f, -(paint.ascent() + paint.descent()) / 2, paint) }
        }
        class Image(val bitmap: Bitmap) : Item() {
            override val w get() = bitmap.width.toFloat()
            override val h get() = bitmap.height.toFloat()
            override fun drawBody(c: Canvas) { c.drawBitmap(bitmap, null, RectF(-w / 2, -h / 2, w / 2, h / 2), null) }
        }
        fun draw(c: Canvas) { c.save(); c.translate(cx, cy); c.rotate(angle); c.scale(scale, scale); drawBody(c); c.restore() }
        fun hit(x: Float, y: Float): Boolean {
            val m = Matrix().apply { postTranslate(-cx, -cy); postRotate(-angle); postScale(1 / scale, 1 / scale) }
            val p = floatArrayOf(x, y); m.mapPoints(p)
            val pad = 16f / scale
            return p[0] in (-w / 2 - pad)..(w / 2 + pad) && p[1] in (-h / 2 - pad)..(h / 2 + pad)
        }
    }

    private val ink2 = mutableListOf<Pair<Int, Path>>()
    private val items = mutableListOf<Item>()
    private var selected: Item? = null
    var onSelect: ((Int) -> Unit)? = null
    var ink = 0xFFD5B36A.toInt()
    var drawMode = false
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFD5B36A.toInt() }

    private fun toBoard(ex: Float, ey: Float) = floatArrayOf((ex * 800 / width.coerceAtLeast(1)).coerceIn(0f, 800f), (ey * 400 / height.coerceAtLeast(1)).coerceIn(0f, 400f))

    private fun render(c: Canvas, withFrame: Boolean) {
        ink2.forEach { (color, path) -> strokePaint.color = color; c.drawPath(path, strokePaint) }
        items.forEach { it.draw(c) }
        if (withFrame) selected?.let { s ->
            c.save(); c.translate(s.cx, s.cy); c.rotate(s.angle)
            c.drawRect(-s.w * s.scale / 2 - 6, -s.h * s.scale / 2 - 6, s.w * s.scale / 2 + 6, s.h * s.scale / 2 + 6, frame)
            c.restore()
        }
    }

    override fun onDraw(target: Canvas) {
        target.drawColor(0xFF102B20.toInt())
        target.save(); target.scale(width / 800f, height / 400f); render(target, true); target.restore()
    }

    // Gesture state
    private var lastX = 0f; private var lastY = 0f
    private var startDist = 0f; private var startAngle = 0f; private var startScale = 1f; private var startRot = 0f
    private var path: Path? = null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p = toBoard(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = p[0]; lastY = p[1]
                if (drawMode) { path = Path().apply { moveTo(p[0], p[1]); lineTo(p[0] + .1f, p[1]) }.also { ink2 += ink to it } }
                else {
                    selected = items.lastOrNull { it.hit(p[0], p[1]) }
                    onSelect?.invoke(selected?.let { items.indexOf(it) } ?: -1)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (!drawMode && e.pointerCount >= 2) selected?.let { s ->
                startDist = spacing(e); startAngle = angle(e); startScale = s.scale; startRot = s.angle
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawMode) path?.lineTo(p[0], p[1])
                else selected?.let { s ->
                    if (e.pointerCount >= 2 && startDist > 0f) {
                        s.scale = (startScale * spacing(e) / startDist).coerceIn(.2f, 6f)
                        s.angle = startRot + (angle(e) - startAngle)
                    } else if (e.pointerCount == 1) {
                        s.cx = (s.cx + p[0] - lastX).coerceIn(0f, 800f); s.cy = (s.cy + p[1] - lastY).coerceIn(0f, 400f)
                    }
                }
                lastX = p[0]; lastY = p[1]
            }
            MotionEvent.ACTION_POINTER_UP -> {
                startDist = 0f
                // Continue a one-finger drag from the remaining finger without a jump.
                val keep = if (e.actionIndex == 0) 1 else 0
                val q = toBoard(e.getX(keep), e.getY(keep)); lastX = q[0]; lastY = q[1]
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { path = null; startDist = 0f; parent?.requestDisallowInterceptTouchEvent(false); performClick() }
        }
        invalidate()
        return true
    }

    private fun spacing(e: MotionEvent) = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    private fun angle(e: MotionEvent) = Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    override fun performClick(): Boolean { super.performClick(); return true }

    fun clear() { ink2.clear(); items.forEach { (it as? Item.Image)?.bitmap?.recycle() }; items.clear(); selected = null; onSelect?.invoke(-1); invalidate() }
    fun removeSelected() {
        selected?.let { items.remove(it); (it as? Item.Image)?.bitmap?.recycle() }
        selected = null; onSelect?.invoke(-1); invalidate()
    }
    fun recolorSelected(color: Int) { (selected as? Item.Text)?.let { it.color = color; invalidate() } }

    fun addText(text: String, sticker: Boolean = false) {
        val t = text.take(120)
        val item = Item.Text(t, ink, if (sticker) 72f else 48f)
        val wide = item.w
        if (wide > 760f) item.scale = 760f / wide
        add(item)
    }

    fun addImage(image: Bitmap) {
        val item = Item.Image(image)
        item.scale = minOf(600f / image.width, 300f / image.height, 1f)
        add(item)
    }

    private fun add(item: Item) { items += item; selected = item; onSelect?.invoke(items.lastIndex); invalidate() }

    /** Flattens all layers into an 800×400 bitmap (main thread, fast); [encode] runs off the main thread. */
    fun snapshot(): Bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).also { render(Canvas(it), false) }

    companion object {
        fun encode(bitmap: Bitmap): String {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }
}
