package com.ghosted907.t1remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    while (bounds.outWidth / options.inSampleSize > 800 ||
                        bounds.outHeight / options.inSampleSize > 400) options.inSampleSize *= 2
                    context.contentResolver.openInputStream(uri).use {
                        requireNotNull(BitmapFactory.decodeStream(it, null, options))
                    }
                }
            }
            result.onSuccess { board.addImage(it); it.recycle(); error = "" }
                .onFailure { error = "تعذر قراءة الصورة" }
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("لوحتك على العدادات")
        Text("ارسم بإصبعك، وأضف النصوص والصور ثم أرسل اللوحة.")
        AndroidView(factory = { board }, modifier = Modifier.fillMaxWidth().aspectRatio(2f))
        OutlinedTextField(caption, { caption = it.take(120) }, label = { Text("اكتب نصًا") },
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { board.addText(caption) }, enabled = caption.isNotBlank()) { Text("أضف النص") }
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !loading) { Text("رفع صورة") }
            OutlinedButton(onClick = { board.clear() }) { Text("مسح") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🇸🇦", "🌴", "⭐", "♥").forEach { sticker ->
                OutlinedButton(onClick = { board.addText(sticker) }) { Text(sticker) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("أخضر" to 0xFF16744A.toInt(), "ذهبي" to 0xFFD5B36A.toInt(),
                "أبيض" to android.graphics.Color.WHITE).forEach { (label, color) ->
                OutlinedButton(onClick = { board.ink = color }) { Text(label) }
            }
        }
        Text("الموضع الأفقي")
        Slider(x, { x = it })
        Text("الموضع الرأسي")
        Slider(y, { y = it })
        if (error.isNotEmpty()) Text(error)
        if (!enabled) Text("عرض اللوحة يحتاج لانشرًا محدثًا وشاشة عدادات متصلة")
        Button(enabled = enabled && !loading, onClick = {
            val image = board.encode()
            if (image.length > 900000) error = "الصورة كبيرة، جرّب صورة أبسط"
            else send(JSONObject().put("op", RemoteProtocol.OP_ART).put("image", image)
                .put("x", x.toDouble()).put("y", y.toDouble()))
        }) { Text("عرض على العدادات") }
        OutlinedButton(enabled = enabled, onClick = {
            send(JSONObject().put("op", RemoteProtocol.OP_ART_CLEAR))
        }) { Text("إخفاء من العدادات") }
    }
}

private class PhoneArtBoard(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    var ink = 0xFFD5B36A.toInt()
    private var lastX = 0f
    private var lastY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(target: Canvas) {
        target.drawColor(0xFF102B20.toInt())
        target.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x * 800 / width).coerceIn(0f, 800f)
        val y = (event.y * 400 / height).coerceIn(0f, 400f)
        paint.color = ink
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = x
                lastY = y
                canvas.drawCircle(x, y, 3f, paint)
            }
            MotionEvent.ACTION_MOVE -> {
                canvas.drawLine(lastX, lastY, x, y, paint)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    fun clear() { bitmap.eraseColor(android.graphics.Color.TRANSPARENT); invalidate() }
    fun addText(text: String) {
        paint.color = ink
        val fitted = text.take(120)
        paint.textSize = 48f
        val measured = paint.measureText(fitted)
        if (measured > 760f) paint.textSize *= 760f / measured
        canvas.drawText(fitted, 400f, 210f, paint)
        invalidate()
    }
    fun addImage(image: Bitmap) {
        val scale = minOf(800f / image.width, 400f / image.height)
        val w = image.width * scale
        val h = image.height * scale
        canvas.drawBitmap(image, null, RectF((800-w)/2, (400-h)/2, (800+w)/2, (400+h)/2), null)
        invalidate()
    }
    fun encode(): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
