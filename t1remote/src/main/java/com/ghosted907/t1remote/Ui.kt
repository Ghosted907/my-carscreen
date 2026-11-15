package com.ghosted907.t1remote

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFD5B36A)
val Bg = Color(0xFF0A1210)
val CardColor = Color(0xFF17221E)
val Ink = Color(0xFFF4F0E5)
val Muted = Color(0xFFA4B8A9)
val Bad = Color(0xFFE08A7A)
val Good = Color(0xFF7FD3A0)
val OnGold = Color(0xFF123827)

/** Decodes a base64 PNG; call off the main thread for lists. */
fun decodeImage(b64: String): ImageBitmap? = runCatching {
    if (b64.isEmpty()) return null
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
fun Section(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Gold, fontSize = 18.sp)
        if (note != null) Text(note, color = Muted, fontSize = 13.sp)
        content()
    }
}

@Composable
fun Pill(label: String, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, color = when { !enabled -> Muted; selected -> OnGold; else -> Ink }, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Gold else Color(0xFF22322B))
            .border(1.dp, if (selected) Gold else Color(0xFF2F4A3D), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable
fun Small(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) OnGold else Muted, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (enabled) Gold else Color(0xFF2A3A33))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Flow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun SwitchLine(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) Ink else Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = OnGold))
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, suffix: String = "", enabled: Boolean = true, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Pill("−", enabled = enabled && value > min) { onChange((value - step).coerceAtLeast(min)) }
        Text("$value$suffix", color = Gold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Pill("+", enabled = enabled && value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
fun Note(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 13.sp)

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { content() }
