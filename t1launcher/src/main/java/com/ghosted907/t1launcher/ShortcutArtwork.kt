package com.ghosted907.t1launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

internal enum class ShortcutArt(val tint: Color) {
    CarPlay(Color(0xFF96F289)),
    Video(Color(0xFFFF6378)),
    Map(Color(0xFF78EBDF)),
    Bluetooth(Color(0xFF91BDFF)),
    Wifi(Color(0xFF83EAC2)),
    Climate(Color(0xFFBBE7FF))
}

@Composable
internal fun ShortcutArtwork(art: ShortcutArt, modifier: Modifier) {
    val tint = art.tint
    val white = Color(0xFFF4FAFF)
    Canvas(modifier) {
        val unit = minOf(size.width / 152f, size.height / 128f)
        val origin = Offset(size.width * .5f - 76f * unit, size.height * .5f - 64f * unit)
        drawRect(Brush.radialGradient(
            listOf(tint.copy(alpha = .26f), tint.copy(alpha = .07f), Color.Transparent),
            center = Offset(size.width * .48f, size.height * .53f),
            radius = size.width.coerceAtLeast(size.height) * .62f
        ))
        for (index in 0..2) {
            val width = size.width * (.58f + index * .24f)
            val height = width * .35f
            drawOval(tint.copy(alpha = .12f - index * .025f),
                Offset((size.width - width) / 2f, size.height * .74f - height / 2f),
                Size(width, height), style = Stroke(unit))
        }
        translate(origin.x, origin.y) {
            scale(unit, unit, pivot = Offset.Zero) {
                val center = Offset(76f, 60f)
                drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = .66f), Color.Transparent),
                    center = Offset(76f, 105f), radius = 54f),
                    Offset(23f, 85f), Size(107f, 39f))
                rotate(-13f, center) {
                    val bounds = Size(94f, 76f)
                    val corners = CornerRadius(19f)
                    drawRoundRect(tint.copy(alpha = .23f), Offset(29f, 30f), bounds, corners)
                    drawRoundRect(Brush.linearGradient(listOf(tint.copy(alpha = .53f), Color(0xFF141F29)),
                        start = Offset(29f, 22f), end = Offset(118f, 100f)),
                        Offset(29f, 22f), bounds, corners)
                    drawRoundRect(Brush.linearGradient(listOf(white.copy(alpha = .62f), tint.copy(alpha = .12f)),
                        start = Offset(29f, 22f), end = Offset(123f, 98f)),
                        Offset(29f, 22f), bounds, corners, style = Stroke(1.25f))

                    fun line(x: Float, y: Float, endX: Float, endY: Float, color: Color = white) =
                        drawLine(color, Offset(x, y), Offset(endX, endY), 3f, StrokeCap.Round)
                    fun play(x: Float, y: Float, height: Float, width: Float) =
                        drawPath(Path().apply {
                            moveTo(x, y)
                            lineTo(x + width, y + height / 2f)
                            lineTo(x, y + height)
                            close()
                        }, white)
                    when (art) {
                        ShortcutArt.CarPlay -> {
                            drawArc(white, 39f, 282f, false, Offset(52f, 36f), Size(48f, 48f),
                                style = Stroke(3.5f, cap = StrokeCap.Round))
                            play(70f, 47f, 26f, 22f)
                        }
                        ShortcutArt.Video -> {
                            drawRoundRect(Brush.verticalGradient(listOf(tint, Color(0xFFB8193A)), 40f, 81f),
                                Offset(45f, 40f), Size(62f, 40f), CornerRadius(12f))
                            play(70f, 49f, 22f, 18f)
                        }
                        ShortcutArt.Map -> {
                            line(49f, 46f, 100f, 71f, white.copy(alpha = .38f))
                            line(53f, 73f, 95f, 43f, white.copy(alpha = .38f))
                            drawPath(Path().apply {
                                moveTo(49f, 76f)
                                lineTo(68f, 61f)
                                lineTo(79f, 67f)
                                lineTo(96f, 49f)
                            }, tint, style = Stroke(4f, cap = StrokeCap.Round))
                            drawCircle(white, 5f, Offset(96f, 49f))
                        }
                        ShortcutArt.Bluetooth -> drawPath(Path().apply {
                            moveTo(62f, 46f)
                            lineTo(90f, 72f)
                            lineTo(76f, 85f)
                            lineTo(76f, 35f)
                            lineTo(90f, 48f)
                            lineTo(62f, 75f)
                        }, white, style = Stroke(3.5f, cap = StrokeCap.Round))
                        ShortcutArt.Wifi -> {
                            for (r in listOf(36f, 25f, 14f)) {
                                drawArc(white, 228f, 84f, false, Offset(76f - r, 76f - r),
                                    Size(r * 2, r * 2), style = Stroke(3.8f, cap = StrokeCap.Round))
                            }
                            drawCircle(white, 4f, Offset(76f, 76f))
                        }
                        ShortcutArt.Climate -> {
                            for (angle in listOf(0f, 60f, 120f)) {
                                rotate(angle, center) {
                                    line(56f, 60f, 96f, 60f)
                                    line(57f, 60f, 63f, 54f)
                                    line(57f, 60f, 63f, 66f)
                                    line(95f, 60f, 89f, 54f)
                                    line(95f, 60f, 89f, 66f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
