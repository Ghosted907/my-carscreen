package com.ghosted907.t1launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol

/**
 * 1.15.10+ (source only): A/C page. Before this the launcher only had the Home card that opens the
 * OEM A/C app and read-only climate readings on the vehicle-data page.
 */
@Composable
fun ClimatePage(day: Boolean, snap: CarBridge.Snap) {
    val context = LocalContext.current
    val ink = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val card = T1Colors.fill(if (day) T1Colors.dayCard else T1Colors.nightCard) // secondary page: drawn with t1Bg
    var status by remember { mutableStateOf("التحكم بالمكيف من اللانشر. القيم تُقرأ من السيارة عند توفرها.") }
    // Last value sent from this page, shown until the car reports a live value.
    val sent = remember { mutableStateMapOf<String, Double>() }
    val live = Climate.state(snap)
    fun value(item: String): Double? = live[item] ?: sent[item]
    fun send(item: String, v: Double) {
        val clamped = RemoteProtocol.climateValue(item, v) ?: return
        val (ok, msg) = Climate.send(context, item, clamped)
        if (ok) sent[item] = clamped
        status = msg
    }
    fun toggle(item: String) = send(item, if ((value(item) ?: 0.0) >= 1.0) 0.0 else 1.0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("التكييف", color = ink, fontSize = 26.sp, modifier = Modifier.weight(1f))
            T1OutlinedButton(onClick = {
                runCatching { context.packageManager.getLaunchIntentForPackage("com.autolink.aircontroller")?.let { LauncherDisplay.start(context, it) } }
            }) { Text("شاشة المكيف الأصلية") }
        }
        Text(status, color = muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("power", "ac", "auto", "sync", "rear_defrost").forEach { item ->
                val on = (value(item) ?: 0.0) >= 1.0
                ClimateChip(Climate.labels.getValue(item), on, day, Modifier.weight(1f)) { toggle(item) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TempCard("temp_l", value("temp_l"), day, card, Modifier.weight(1f)) { send("temp_l", it) }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).t1Bg(card).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Climate.labels.getValue("fan"), color = t1CardMuted(day), fontSize = 14.sp)
                val fan = value("fan")?.toInt()
                Text(fan?.toString() ?: "—", color = t1CardText(day), fontSize = 40.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { level ->
                        ClimateChip("$level", fan == level, day, Modifier.widthIn(min = 40.dp)) { send("fan", level.toDouble()) }
                    }
                }
            }
            TempCard("temp_r", value("temp_r"), day, card, Modifier.weight(1f)) { send("temp_r", it) }
        }
        ChoiceRow(Climate.labels.getValue("blow"), Climate.blowLabels, value("blow")?.toInt(), day, ink) { send("blow", it.toDouble()) }
        ChoiceRow(Climate.labels.getValue("recirc"), Climate.recircLabels, value("recirc")?.toInt(), day, ink) { send("recirc", it.toDouble()) }
        ChoiceRow(Climate.labels.getValue("mode"), Climate.modeLabels, value("mode")?.toInt(), day, ink) { send("mode", it.toDouble()) }
        Text("الحرارة من ١٦ إلى ٣٠ بخطوة نصف درجة، والمروحة من ١ إلى ٧. ترسل عبر خدمة السيارة نفسها التي يستخدمها تطبيق المكيف الأصلي.",
            color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun TempCard(item: String, value: Double?, day: Boolean, card: Color, modifier: Modifier, onSet: (Double) -> Unit) {
    val ink = t1CardText(day)
    val muted = t1CardMuted(day)
    val spec = RemoteProtocol.CLIMATE.getValue(item)
    val current = value ?: 22.0
    Column(modifier.clip(RoundedCornerShape(16.dp)).t1Bg(card).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Climate.labels.getValue(item), color = muted, fontSize = 14.sp)
        Text(value?.let { Climate.describe(item, it) } ?: "—", color = ink, fontSize = 40.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClimateChip("−", false, day, Modifier.widthIn(min = 64.dp)) { onSet((current - spec.step).coerceAtLeast(spec.min)) }
            ClimateChip("+", false, day, Modifier.widthIn(min = 64.dp)) { onSet((current + spec.step).coerceAtMost(spec.max)) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<Int, String>>, selected: Int?, day: Boolean, ink: Color, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ink, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) -> ClimateChip(label, selected == v, day, Modifier.weight(1f)) { onPick(v) } }
        }
    }
}

@Composable
private fun ClimateChip(label: String, selected: Boolean, day: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = t1CardText(day)
    Text(label, color = if (selected) (if (LocalHomePage.current) T1Colors.nightText else t1SelText(day)) else ink, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .t1Bg(T1Colors.fill(if (selected) T1Colors.chipSelected else if (day) T1Colors.dayCard else Color(0xFF173B2B)))
            .t1Selected(selected, RoundedCornerShape(12.dp))
            .border(1.dp, if (day) T1Colors.dayLine else T1Colors.nightLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 16.dp))
}
