package com.ghosted907.t1launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import org.json.JSONObject

/** A bounded, non-interactive art overlay on the identified cluster display. */
class RemoteArt(private val context: Context) {
    private var manager: WindowManager? = null
    private var view: ImageView? = null

    fun show(body: JSONObject): RemoteActions.Out {
        if (!Settings.canDrawOverlays(context))
            return RemoteActions.Out(false, "فعّل صلاحية الظهور فوق التطبيقات للانشر")
        val id = Displays.clusterId(context)
            ?: return RemoteActions.Out(false, "شاشة العدادات غير متاحة")
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(id)
            ?: return RemoteActions.Out(false, "انقطعت شاشة العدادات")
        val bytes = runCatching { Base64.decode(body.getString("image"), Base64.DEFAULT) }.getOrNull()
            ?: return RemoteActions.Out(false, "الصورة غير صالحة")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..800 || bounds.outHeight !in 1..400)
            return RemoteActions.Out(false, "أبعاد الصورة غير صالحة")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return RemoteActions.Out(false, "تعذر قراءة الصورة")
        val dc = context.createDisplayContext(display)
        val wm = dc.getSystemService(WindowManager::class.java)
        val size = android.graphics.Point()
        display.getRealSize(size)
        val width = (size.x * .34f).toInt()
        val height = (width / 2).coerceAtMost(size.y / 3)
        val params = WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = ((size.x - width) * body.getDouble("x")).toInt()
            y = ((size.y - height) * body.getDouble("y")).toInt()
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

    fun clear() {
        view?.let { runCatching { manager?.removeViewImmediate(it) } }
        view = null
        manager = null
    }
}
