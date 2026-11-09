package com.ghosted907.t1launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

/** Private copy of the compass photo. Nothing here is shipped in the APK. */
object CompassImage {
    private const val TAG = "T1Launcher"
    private val downloadPaths = listOf(
        "/sdcard/Download/t1_compass.png",
        "/storage/emulated/0/Download/t1_compass.png"
    )

    fun file(context: Context): File = File(context.filesDir, "compass.png")

    fun importDownload(context: Context): Boolean {
        val dest = file(context)
        val src = downloadPaths.map { File(it) }.firstOrNull { path ->
            runCatching { path.isFile && path.canRead() && path.length() > 0 }.getOrDefault(false)
        }
        if (src != null) {
            if (dest.isFile && dest.length() == src.length() && dest.lastModified() >= src.lastModified()) {
                return false
            }
            return try {
                src.copyTo(dest, overwrite = true)
                dest.setLastModified(src.lastModified())
                Log.i(TAG, "compassOverlay import ${src.path}")
                true
            } catch (t: Throwable) {
                Log.i(TAG, "compassOverlay import failed ${t.javaClass.simpleName}")
                false
            }
        }
        return importFromMediaStore(context, dest)
    }

    fun copyFromUri(context: Context, uri: Uri): Boolean {
        return try {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                file(context).outputStream().use { input.copyTo(it) }
            } ?: return false
            Log.i(TAG, "compassOverlay picked")
            true
        } catch (t: Throwable) {
            Log.i(TAG, "compassOverlay pick failed ${t.javaClass.simpleName}")
            false
        }
    }

    fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 && bounds.outHeight / sample > 512) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun importFromMediaStore(context: Context, dest: File): Boolean {
        return try {
            val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf("t1_compass.png"),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return false
                val item = android.content.ContentUris.withAppendedId(uri, cursor.getLong(0))
                context.contentResolver.openInputStream(item)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                } ?: return false
                Log.i(TAG, "compassOverlay import /sdcard/Download/t1_compass.png")
                true
            } ?: false
        } catch (t: Throwable) {
            Log.i(TAG, "compassOverlay import failed ${t.javaClass.simpleName}")
            false
        }
    }
}
