package com.ghosted907.t1launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import org.json.JSONObject

/** A bounded, non-interactive art overlay on the identified cluster display. */
class RemoteArt(private val context: Context) {
    private companion object {
        var manager: WindowManager? = null
        var view: ImageView? = null
    }

    fun show(body: JSONObject): RemoteActions.Out {
        val bytes = runCatching { Base64.decode(body.getString("image"), Base64.DEFAULT) }.getOrNull()
            ?: return RemoteActions.Out(false, "الصورة غير صالحة")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..800 || bounds.outHeight !in 1..400)
            return RemoteActions.Out(false, "أبعاد الصورة غير صالحة")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return RemoteActions.Out(false, "تعذر قراءة الصورة")
        return showBitmap(bitmap, body.getDouble("x"), body.getDouble("y"))
    }

    fun showBitmap(bitmap: android.graphics.Bitmap, left: Double? = null, top: Double? = null): RemoteActions.Out {
        if (!Settings.canDrawOverlays(context))
            return RemoteActions.Out(false, "فعّل صلاحية الظهور فوق التطبيقات للانشر")
        val display = JctLaunch.displays(context).getOrNull(JctLaunch.priIndex(context))
            ?.takeUnless { it.displayId == android.view.Display.DEFAULT_DISPLAY }
            ?: return RemoteActions.Out(false, "شاشة العدادات غير متاحة")
        val dc = context.createDisplayContext(display)
        val wm = dc.getSystemService(WindowManager::class.java)
        val size = android.graphics.Point()
        display.getRealSize(size)
        val matchPreview = left == null && top == null
        val width = if (matchPreview) size.x else (size.x * .34f).toInt()
        val height = if (matchPreview) size.y else (width / 2).coerceAtMost(size.y / 3)
        val params = WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = ((size.x - width) * (left ?: 0.0)).toInt()
            y = ((size.y - height) * (top ?: 0.0)).toInt()
        }
        val next = ImageView(dc).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return try {
            wm.addView(next, params)
            clear()
            manager = wm
            view = next
            RemoteActions.Out(true, "تم عرض اللوحة على العدادات")
        } catch (_: Exception) {
            bitmap.recycle()
            RemoteActions.Out(false, "نظام الشاشة لم يسمح بعرض اللوحة")
        }
    }

    /**
     * Phone remote (T1 Remote 1.3.0): draws the saved cluster art board (prefs "settings_art": drawing,
     * caption, emblem sticker, custom sticker) the same way the launcher board lays it out (RTL: x is
     * measured from the right edge), then shows it on the cluster like the board's apply button.
     */
    fun showBoard(): RemoteActions.Out {
        val display = JctLaunch.displays(context).getOrNull(JctLaunch.priIndex(context))
            ?.takeUnless { it.displayId == android.view.Display.DEFAULT_DISPLAY }
            ?: return RemoteActions.Out(false, "شاشة العدادات غير متاحة")
        val size = android.graphics.Point()
        display.getRealSize(size)
        val bitmap = runCatching { renderBoard(size.x.coerceIn(320, 2560), size.y.coerceIn(120, 1440)) }.getOrNull()
            ?: return RemoteActions.Out(false, "تعذر رسم اللوحة")
        return showBitmap(bitmap)
    }

    fun renderBoard(w: Int, h: Int): android.graphics.Bitmap {
        val prefs = context.getSharedPreferences("settings_art", Context.MODE_PRIVATE)
        val density = context.resources.displayMetrics.density
        val scaled = density * context.resources.configuration.fontScale
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        // Drawing strokes (normalized points).
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = 4 * density
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        runCatching {
            val strokes = org.json.JSONArray(prefs.getString("strokes", "[]"))
            for (i in 0 until strokes.length()) {
                val s = strokes.getJSONObject(i)
                val pts = s.getJSONArray("points")
                if (pts.length() == 0) continue
                ink.color = s.getInt("color")
                val path = android.graphics.Path()
                for (j in 0 until pts.length()) {
                    val p = pts.getJSONArray(j)
                    val x = (p.getDouble(0) * w).toFloat(); val y = (p.getDouble(1) * h).toFloat()
                    if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (pts.length() == 1) canvas.drawPoint((pts.getJSONArray(0).getDouble(0) * w).toFloat(), (pts.getJSONArray(0).getDouble(1) * h).toFloat(), ink)
                else canvas.drawPath(path, ink)
            }
        }
        // Caption: right-aligned block whose right edge sits captionX from the right (RTL TopStart offset).
        val caption = prefs.getString("caption", "").orEmpty()
        if (caption.isNotEmpty()) {
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = prefs.getInt("caption_color", android.graphics.Color.WHITE)
                textSize = prefs.getFloat("caption_size", 28f).coerceIn(16f, 120f) * scaled
                typeface = boardTypeface(prefs.getInt("caption_font", 0))
            }
            val boxW = (w * .42f).coerceIn(200 * density, 560 * density).toInt().coerceAtMost(w)
            val layout = android.text.StaticLayout.Builder.obtain(caption, 0, caption.length, paint, boxW)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL).build()
            val right = w * (1f - prefs.getFloat("caption_x", .04f).coerceIn(0f, .70f))
            val top = h * prefs.getFloat("caption_y", .36f).coerceIn(.32f, .85f)
            canvas.save(); canvas.translate(right - boxW, top); layout.draw(canvas); canvas.restore()
        }
        fun place(d: android.graphics.drawable.Drawable?, x: Float, y: Float, sizeDp: Float) {
            if (d == null) return
            val px = sizeDp.coerceIn(40f, 240f) * density
            val right = w * (1f - x)
            val top = h * y
            // ContentScale.Fit inside a square box.
            val iw = d.intrinsicWidth.takeIf { it > 0 } ?: px.toInt()
            val ih = d.intrinsicHeight.takeIf { it > 0 } ?: px.toInt()
            val k = minOf(px / iw, px / ih)
            val dw = iw * k; val dh = ih * k
            val left = right - px + (px - dw) / 2; val t = top + (px - dh) / 2
            d.setBounds(left.toInt(), t.toInt(), (left + dw).toInt(), (t + dh).toInt())
            d.draw(canvas)
        }
        if (prefs.getBoolean("sticker", true))
            place(context.getDrawable(R.drawable.saudi_emblem_gold), prefs.getFloat("sticker_x", .45f), prefs.getFloat("sticker_y", .44f), prefs.getFloat("sticker_size", 100f))
        val custom = prefs.getString("custom_sticker", "").orEmpty()
        if (custom.isNotBlank()) runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(custom))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.let {
            place(android.graphics.drawable.BitmapDrawable(context.resources, it), prefs.getFloat("custom_x", .72f), prefs.getFloat("custom_y", .44f), prefs.getFloat("custom_size", 100f))
        }
        return bitmap
    }

    private fun boardTypeface(index: Int): android.graphics.Typeface {
        val fonts = listOf(R.font.arabic, 0, 0, 0, 0, R.font.art_cairo, R.font.art_tajawal, R.font.art_amiri,
            R.font.art_reemkufi, R.font.art_lalezar, R.font.art_orbitron, R.font.art_bebas, R.font.art_pacifico)
        return when (index.coerceIn(0, fonts.lastIndex)) {
            1 -> android.graphics.Typeface.SANS_SERIF
            2 -> android.graphics.Typeface.SERIF
            3 -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
            4 -> android.graphics.Typeface.MONOSPACE
            else -> runCatching { context.resources.getFont(fonts[index.coerceIn(0, fonts.lastIndex)]) }.getOrNull()
                ?: android.graphics.Typeface.DEFAULT
        }
    }

    fun clear() {
        view?.let { runCatching { manager?.removeViewImmediate(it) } }
        view = null
        manager = null
    }
}
