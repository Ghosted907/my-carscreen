package com.ghosted907.t1launcher

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun CompassEditor(day: Boolean, prefs: T1Prefs, preview: Boolean) {
    val context = LocalContext.current
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val center = remember { mutableIntStateOf(prefs.compassCx) to mutableIntStateOf(prefs.compassCy) }
    var cx by center.first
    var cy by center.second
    var diameter by remember { mutableIntStateOf(prefs.compassD) }
    var ring by remember { mutableIntStateOf(prefs.compassRingPx) }
    var ringColor by remember { mutableIntStateOf(prefs.compassRingColor) }
    var enabled by remember { mutableIntStateOf(if (prefs.compassEnabled) 1 else 0) }
    var generation by remember { mutableIntStateOf(0) }
    val photo = remember(generation) { CompassImage.decode(CompassImage.file(context)) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && CompassImage.copyFromUri(context, uri)) {
            generation++
            ClusterOverlayService.sync(context)
            Toast.makeText(context, "تم حفظ صورة البوصلة", Toast.LENGTH_SHORT).show()
        }
    }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && CompassImage.importDownload(context)) {
            generation++
            ClusterOverlayService.sync(context)
            Toast.makeText(context, "تم الاستيراد من Download", Toast.LENGTH_SHORT).show()
        } else if (!granted) {
            Toast.makeText(context, "لم تُمنح قراءة التنزيلات", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "لا يوجد /sdcard/Download/t1_compass.png", Toast.LENGTH_LONG).show()
        }
    }
    fun publish() {
        prefs.compassCx = cx
        prefs.compassCy = cy
        prefs.compassD = diameter
        prefs.compassRingPx = ring
        prefs.compassRingColor = ringColor
    }
    fun nudge(dx: Int, dy: Int) {
        cx = (cx + dx).coerceIn(0, 1920)
        cy = (cy + dy).coerceIn(0, 720)
        publish()
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("صورة بدل البوصلة", color = text, fontSize = 22.sp)
        Text("الدائرة تغطي بوصلة العداد عند 1556,384. اسحبها من هنا؛ شاشة العدادات بلا لمس.", color = muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompassChip(if (enabled == 1) "مفعّلة" else "متوقفة", enabled == 1) {
                enabled = if (enabled == 1) 0 else 1
                prefs.compassEnabled = enabled == 1
                ClusterOverlayService.sync(context)
            }
            CompassChip("اختر صورة", false) { open.launch(arrayOf("image/*")) }
            CompassChip("استيراد من Download", false) {
                if (CompassImage.importDownload(context)) {
                    generation++
                    ClusterOverlayService.sync(context)
                    Toast.makeText(context, "تم الاستيراد من Download", Toast.LENGTH_SHORT).show()
                } else if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    storage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    Toast.makeText(context, "لا يوجد /sdcard/Download/t1_compass.png", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (preview) {
            BoxWithConstraints(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .aspectRatio(1920f / 720f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF12181C))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            val lx = cx * size.width / 1920f
                            val ly = cy * size.height / 720f
                            val radius = (diameter / 2f) * size.width / 1920f
                            val resizing = hypot(down.position.x - lx, down.position.y - ly) > radius * 0.72f
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                if (resizing) {
                                    diameter = (diameter + delta.x * 1920f / size.width).roundToInt().coerceIn(80, 480)
                                } else {
                                    cx = (cx + delta.x * 1920f / size.width).roundToInt().coerceIn(0, 1920)
                                    cy = (cy + delta.y * 720f / size.height).roundToInt().coerceIn(0, 720)
                                }
                                publish()
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val markX = CompassGeometry.DEFAULT_CX * size.width / 1920f
                    val markY = CompassGeometry.DEFAULT_CY * size.height / 720f
                    drawCircle(Color(0x338BD3D5), radius = 6f, center = androidx.compose.ui.geometry.Offset(markX, markY))
                }
                val left = maxWidth * ((cx - diameter / 2f) / 1920f)
                val top = maxHeight * ((cy - diameter / 2f) / 720f)
                val side = maxWidth * (diameter / 1920f)
                Box(
                    Modifier
                        .offset(left, top)
                        .size(side)
                        .clip(CircleShape)
                        .background(Color(0x33202830))
                ) {
                    if (photo != null) {
                        Image(
                            photo.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (ring > 0) {
                    Box(
                        Modifier
                            .offset(left, top)
                            .size(side)
                            .border(ring.dp / 4, Color(ringColor), CircleShape)
                    )
                }
            }
            Text("المركز $cx,$cy    القطر $diameter", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompassChip("-2 س", false) { nudge(-2, 0) }
                CompassChip("+2 س", false) { nudge(2, 0) }
                CompassChip("-2 ص", false) { nudge(0, -2) }
                CompassChip("+2 ص", false) { nudge(0, 2) }
            }
            Text("القطر", color = text, fontSize = 13.sp)
            Slider(
                value = diameter.toFloat(),
                onValueChange = {
                    diameter = it.roundToInt()
                    publish()
                },
                valueRange = 80f..480f
            )
        }
        Text("سماكة الإطار", color = text, fontSize = 13.sp)
        Slider(
            value = ring.toFloat(),
            onValueChange = {
                ring = it.roundToInt()
                publish()
            },
            valueRange = 0f..12f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFFD4A84B.toInt(), 0xFFFFFFFF.toInt(), 0xFF8BD3D5.toInt(), 0xFF101419.toInt()).forEach { color ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(if (ringColor == color) 2.dp else 0.dp, T1Colors.cyan, CircleShape)
                        .clickable {
                            ringColor = color
                            publish()
                        }
                )
            }
        }
    }
}

@Composable
private fun CompassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color(0xFF173B47) else T1Colors.nightText,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) T1Colors.cyan else Color(0xFF1C393F))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
