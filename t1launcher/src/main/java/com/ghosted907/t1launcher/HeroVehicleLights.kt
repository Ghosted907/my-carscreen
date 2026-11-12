package com.ghosted907.t1launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private data class LampMask(val image: ImageBitmap, val bounds: Rect)
private data class LampMasks(val near: LampMask, val far: LampMask)

enum class LampDemoMode { NONE, LEFT, RIGHT, HAZARD }

/** Recolors only the bright pixels already present in the real headlamp photograph. */
private fun createLampMasks(context: Context): LampMasks {
    val source = BitmapFactory.decodeResource(context.resources, R.drawable.oem_t1_cutout_saudi,
        BitmapFactory.Options().apply { inScaled = false })
    fun make(bounds: Rect): LampMask {
        val width = bounds.width()
        val height = bounds.height()
        val original = IntArray(width * height)
        val pixels = IntArray(width * height)
        source.getPixels(original, 0, width, bounds.left, bounds.top, width, height)
        for (i in original.indices) {
            val p = original[i]
            val minChannel = minOf(AndroidColor.red(p), AndroidColor.green(p), AndroidColor.blue(p))
            val maxChannel = maxOf(AndroidColor.red(p), AndroidColor.green(p), AndroidColor.blue(p))
            val alpha = if (AndroidColor.alpha(p) > 0 && minChannel > 150 && maxChannel - minChannel < 48)
                ((minChannel - 150) * 2.5f).roundToInt().coerceIn(0, 235) else 0
            pixels[i] = AndroidColor.argb(alpha, 255, 157, 27)
        }
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mask.setPixels(pixels, 0, width, 0, 0, width, height)
        return LampMask(mask.asImageBitmap(), bounds)
    }
    val masks = LampMasks(make(Rect(662, 372, 791, 454)), make(Rect(235, 385, 284, 467)))
    source.recycle()
    return masks
}

/** Lamps are drawn in the original 1672 x 941 cutout coordinates, then fitted with the car image. */
@Composable
fun HeroVehicleLights(snap: CarBridge.Snap, modifier: Modifier = Modifier,
                      demoMode: LampDemoMode = LampDemoMode.NONE) {
    val context = LocalContext.current
    val masks = remember(context) { createLampMasks(context) }
    val leftRaw = snap.reading("turn_left").current
    val rightRaw = snap.reading("turn_right").current
    val hazardRaw = snap.reading("hazard").current
    val low = snap.reading("low_beam").current == 1.0
    val high = snap.reading("high_beam").current == 1.0
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var leftOnAt by remember { mutableLongStateOf(0L) }
    var rightOnAt by remember { mutableLongStateOf(0L) }
    var hazardOnAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(leftRaw, rightRaw, hazardRaw, demoMode) {
        val now = SystemClock.elapsedRealtime()
        if (leftRaw == 1.0) leftOnAt = now
        if (rightRaw == 1.0) rightOnAt = now
        if (hazardRaw == 1.0) hazardOnAt = now
        while (demoMode != LampDemoMode.NONE || leftRaw == 1.0 || rightRaw == 1.0 || hazardRaw == 1.0 ||
            (leftRaw == 0.0 && SystemClock.elapsedRealtime() - leftOnAt < 700L) ||
            (rightRaw == 0.0 && SystemClock.elapsedRealtime() - rightOnAt < 700L) ||
            (hazardRaw == 0.0 && SystemClock.elapsedRealtime() - hazardOnAt < 700L)) {
            tick = SystemClock.elapsedRealtime()
            delay(80)
        }
    }
    val left = if (demoMode == LampDemoMode.NONE)
        leftRaw == 1.0 || (leftRaw == 0.0 && tick - leftOnAt < 700L)
        else demoMode == LampDemoMode.LEFT
    val right = if (demoMode == LampDemoMode.NONE)
        rightRaw == 1.0 || (rightRaw == 0.0 && tick - rightOnAt < 700L)
        else demoMode == LampDemoMode.RIGHT
    val hazard = if (demoMode == LampDemoMode.NONE)
        hazardRaw == 1.0 || (hazardRaw == 0.0 && tick - hazardOnAt < 700L)
        else demoMode == LampDemoMode.HAZARD
    val blink = (tick / 500L) % 2L == 0L
    val beam by animateFloatAsState(if (high) 1f else if (low) .55f else 0f,
        animationSpec = tween(140), label = "headlight brightness")
    Canvas(modifier.fillMaxSize()) {
        val scale = min(size.width / 1672f, size.height / 941f)
        val ox = (size.width - 1672f * scale) / 2f
        val oy = (size.height - 941f * scale) / 2f
        fun at(x: Float, y: Float) = Offset(ox + x * scale, oy + y * scale)
        fun glow(x: Float, y: Float, radius: Float, color: Color, opacity: Float) {
            val center = at(x, y)
            val r = radius * scale
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = opacity),
                color.copy(alpha = opacity * .36f), Color.Transparent), center, r), r, center)
        }
        fun drawLamp(mask: LampMask) {
            val b = mask.bounds
            drawImage(mask.image,
                dstOffset = IntOffset((ox + b.left * scale).roundToInt(), (oy + b.top * scale).roundToInt()),
                dstSize = IntSize((b.width() * scale).roundToInt(), (b.height() * scale).roundToInt()),
                filterQuality = FilterQuality.High)
        }
        fun projector(x: Float, y: Float, w: Float, h: Float, intensity: Float) {
            val center = at(x, y)
            val radius = w * scale * .65f
            drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = .98f * intensity),
                Color.White.copy(alpha = .78f * intensity), Color.Transparent), center, radius),
                topLeft = at(x - w / 2f, y - h / 2f), size = Size(w * scale, h * scale))
        }
        val amber = Color(0xFFFFA51C)
        val white = Color(0xFFF6F9FF)
        if (beam > 0f) {
            glow(738f, 411f, if (high) 105f else 35f, white, if (high) .62f else .30f)
            glow(260f, 425f, if (high) 55f else 21f, white, if (high) .55f else .26f)
        }
        // The visible side of this three-quarter cutout is the vehicle's left side.
        if (blink && (left || hazard)) {
            glow(738f, 413f, 53f, amber, .17f)
            drawLamp(masks.near)
        }
        if (blink && (right || hazard)) {
            glow(259f, 426f, 29f, amber, .20f)
            drawLamp(masks.far)
        }
        if (beam > 0f) {
            // Draw white projectors last so they remain white while an amber signal flashes around them.
            val projectorBrightness = .75f + beam * .25f
            projector(738f, 411f, 43f, 28f, projectorBrightness)
            projector(260f, 425f, 19f, 21f, projectorBrightness)
            if (high) {
                glow(738f, 411f, 31f, white, .86f * beam)
                glow(260f, 425f, 18f, white, .82f * beam)
            }
        }
    }
}
