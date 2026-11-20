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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1.4.0: colours follow the launcher's current T1Colors (dark green identity, light day mode). Gold is
 * gone from the UI (only the emblem image keeps it). Secondary surfaces use the launcher sidebar colours
 * with 80% opaque element fills. Day/night here is the PHONE's own choice (PhoneStore.theme), independent
 * of the launcher's day/night switch on the car.
 */
object PhoneTheme { var day by mutableStateOf(false) }
private val day get() = PhoneTheme.day
/** Page background = launcher sidebar colour (T1Colors.railDay / railNight, opaque on the phone). */
val Bg: Color get() = if (day) Color(0xFFE9E6DA) else Color(0xFF07110F)
/** Cards = T1Colors.dayCard / nightCard at the launcher's 80% fill. */
val CardColor: Color get() = if (day) Color(0xCCFCFBF4) else Color(0xCC17221E)
val Card2: Color get() = if (day) Color(0xCCF3F1E7) else Color(0xCC20362B)
val Ink: Color get() = if (day) Color(0xFF123827) else Color(0xFFF4F0E5)
val Muted: Color get() = if (day) Color(0xFF5E7465) else Color(0xFFA4B8A9)
val Line: Color get() = if (day) Color(0xFFCFD8D0) else Color(0xFF425646)
/** Accent for titles and selection marks (railSelText). Replaces the old gold. */
val Accent: Color get() = if (day) Color(0xFF123827) else Color(0xFFF4F0E5)
/** Button fill (T1Colors.chipSelected in the night, dark green in the day) and its text. */
val Btn: Color get() = if (day) Color(0xFF123827) else Color(0xFF2F6B4C)
val OnBtn: Color get() = Color(0xFFF4F0E5)
val SelFill: Color get() = Accent.copy(alpha = .12f)
val Warn: Color get() = if (day) Color(0xFF9A4B1C) else Color(0xFFE8B18A)
val Bad: Color get() = if (day) Color(0xFFA33A2C) else Color(0xFFE08A7A)
val Good: Color get() = if (day) Color(0xFF276E53) else Color(0xFF7FD3A0)

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
        Text(title, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) Text(note, color = Muted, fontSize = 13.sp)
        content()
    }
}

@Composable
fun Pill(label: String, selected: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, color = when { !enabled -> Muted.copy(alpha = .6f); selected -> OnBtn; else -> Ink }, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Btn else Card2)
            .border(1.dp, if (selected) Btn else Line, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable
fun Small(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) OnBtn else Muted.copy(alpha = .7f), fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) Btn else Card2)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp))
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
            colors = SwitchDefaults.colors(checkedTrackColor = Btn, checkedThumbColor = OnBtn, uncheckedTrackColor = Card2,
                uncheckedThumbColor = Muted, uncheckedBorderColor = Line))
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, suffix: String = "", enabled: Boolean = true, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Pill("−", enabled = enabled && value > min) { onChange((value - step).coerceAtLeast(min)) }
        Text("$value$suffix", color = Accent, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Pill("+", enabled = enabled && value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
fun Note(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 13.sp)

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { content() }

/** A value that is not live is never shown as if it were: "غير متاح" or the stale age is written next to it. */
@Composable
fun ValueLine(label: String, value: String, live: Boolean, sub: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 15.sp)
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 12.sp)
        }
        Text(value, color = if (live) Good else Muted, fontSize = 16.sp)
    }
}

/** Small badge used for "يُفتح على شاشة السيارة" and similar destinations. */
@Composable
fun Badge(text: String, color: Color = Muted) = Text(text, color = color, fontSize = 11.sp,
    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SelFill).padding(horizontal = 6.dp, vertical = 2.dp))
