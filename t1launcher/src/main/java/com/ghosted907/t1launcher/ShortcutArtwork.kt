package com.ghosted907.t1launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
internal fun ShortcutArtwork(slot: Int) {
    val accent = listOf(0xFFFF394F, 0xFF77E4EF, 0xFF83B9FF, 0xFF9BF1D5, 0xFFB3E6FF)[slot]
    val tint = Color(accent)
    val white = Color(0xFFF4FAFF)
    Canvas(Modifier.size(104.dp, 62.dp)) {
        scale(size.width / 104f, size.height / 62f, pivot = Offset.Zero) {
            fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, r: Float = 7f) =
                drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
            fun line(x: Float, y: Float, ex: Float, ey: Float, color: Color, width: Float) =
                drawLine(color, Offset(x, y), Offset(ex, ey), width, StrokeCap.Round)
            when (slot) {
                0 -> {
                    rect(33f, 12f, 59f, 41f, tint.copy(alpha = .23f), 12f)
                    rect(12f, 7f, 69f, 46f, tint, 12f)
                    drawPath(Path().apply { moveTo(40f, 18f); lineTo(40f, 42f); lineTo(61f, 30f); close() }, white)
                }
                1 -> {
                    rect(7f, 6f, 90f, 50f, tint.copy(alpha = .16f))
                    line(14f, 40f, 83f, 15f, white.copy(alpha = .43f), 2f)
                    line(22f, 13f, 88f, 47f, white.copy(alpha = .32f), 2f)
                    line(18f, 47f, 46f, 34f, tint, 4f)
                    line(46f, 34f, 58f, 44f, tint, 4f)
                    line(58f, 44f, 77f, 24f, tint, 4f)
                    drawCircle(white, 6f, Offset(77f, 24f))
                    drawCircle(Color(0xFF167695), 3f, Offset(77f, 24f))
                }
                2 -> {
                    rect(52f, 5f, 31f, 53f, white)
                    rect(55f, 9f, 25f, 43f, Color(0xFF273D5C), 5f)
                    drawCircle(tint.copy(alpha = .25f), 21f, Offset(34f, 31f))
                    drawPath(Path().apply {
                        moveTo(24f, 18f); lineTo(44f, 39f); lineTo(34f, 49f); lineTo(34f, 13f)
                        lineTo(44f, 23f); lineTo(24f, 44f)
                    }, tint, style = Stroke(3f, cap = StrokeCap.Round))
                }
                3 -> {
                    drawCircle(tint.copy(alpha = .13f), 28f, Offset(52f, 31f))
                    for (r in listOf(34f, 24f, 14f)) {
                        drawArc(tint, 224f, 92f, false, Offset(52f - r, 51f - r),
                            Size(r * 2, r * 2), style = Stroke(3.5f, cap = StrokeCap.Round))
                    }
                    drawCircle(white, 3.7f, Offset(52f, 51f))
                }
                else -> {
                    rect(8f, 8f, 88f, 46f, tint.copy(alpha = .22f), 9f)
                    rect(12f, 13f, 80f, 35f, Color(0xFF182A38))
                    for (y in listOf(22f, 30f, 38f)) line(23f, y, 61f, y, tint, 2.5f)
                    line(68f, 30f, 84f, 30f, white, 2f)
                    line(76f, 22f, 76f, 38f, white, 2f)
                    line(70f, 24f, 82f, 36f, white, 2f)
                    line(70f, 36f, 82f, 24f, white, 2f)
                }
            }
        }
    }
}
