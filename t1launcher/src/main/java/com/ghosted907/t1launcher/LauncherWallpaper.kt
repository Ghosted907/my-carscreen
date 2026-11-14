package com.ghosted907.t1launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val Key = "wallpaper_uri"
private fun preferences(context: Context) = context.getSharedPreferences("launcher_wallpaper", Context.MODE_PRIVATE)

@Composable
internal fun rememberLauncherWallpaper(): ImageBitmap? {
    val context = LocalContext.current
    val prefs = remember(context) { preferences(context) }
    var uri by remember(prefs) { mutableStateOf(prefs.getString(Key, null)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Key) uri = prefs.getString(Key, null)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val image by produceState<ImageBitmap?>(null, uri) {
        val selected = uri
        value = if (selected == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val input = Uri.parse(selected)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(input)?.use { BitmapFactory.decodeStream(it, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0)
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2560) {
                    options.inSampleSize *= 2
                }
                resolver.openInputStream(input)?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return image
}

@Composable
internal fun LauncherWallpaperSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { preferences(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString(Key, uri.toString()).apply()
            }.onFailure {
                Toast.makeText(context, "تعذّر حفظ الوصول للصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Row {
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("تغيير خلفية اللانشر") }
        TextButton(onClick = { prefs.edit().remove(Key).apply() }) { Text("استعادة الخلفية الافتراضية") }
    }
}
