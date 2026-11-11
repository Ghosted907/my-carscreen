package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun ButtonSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { T1Prefs(context) }
    var buttons by remember { mutableStateOf(prefs.buttons()) }
    var selected by remember { mutableStateOf(buttons.firstOrNull()?.id) }
    var appsFor by remember { mutableStateOf<String?>(null) }
    var free by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var importCandidate by remember { mutableStateOf<List<ButtonConfig>?>(null) }
    fun save(next: List<ButtonConfig>) {
        prefs.saveButtons(next); buttons = next; EdgeSwipeService.sync(context)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) message = runCatching {
            context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(ButtonJson.encode(buttons)) }
            "تم تصدير الإعدادات"
        }.getOrElse { "تعذر التصدير" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readTextLimited() }
            ButtonJson.decode(text)
        }.onSuccess { importCandidate = it }.onFailure { message = it.message ?: "ملف غير صالح؛ لم تتغير الإعدادات" }
    }
    val active = buttons.firstOrNull { it.id == selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("الأزرار · ${buttons.size} / ${ButtonConfig.MAX_BUTTONS}", modifier = Modifier.weight(1f), color = Color(0xFFCDD8D9))
            Button(onClick = onBack) { Text("رجوع") }
        }
        Text("طرف مخفي 10px؛ اسحب نحو الوسط أو اضغط لفتح التطبيق على الشاشة الرئيسية. مواضع المعاينة فعلية من اليسار لليمين.", color = Color(0xFFCDD8D9))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            buttons.forEach { b -> FilterChip(selected = selected == b.id, onClick = { selected = b.id; free = false }, label = { Text(b.label) }) }
            Button(enabled = buttons.size < ButtonConfig.MAX_BUTTONS, onClick = {
                val b = ButtonConfig(UUID.randomUUID().toString(), ButtonConfig.LAUNCHER, "زر جديد", 1f, .35f)
                save(buttons + b); selected = b.id; appsFor = b.id
            }) { Text("إضافة زر") }
        }
        if (active != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { appsFor = active.id }) { Text("التطبيق: ${active.label}") }
                Button(onClick = { save(buttons.map { if (it.id == active.id) it.copy(enabled = !it.enabled) else it }) }) { Text(if (active.enabled) "مفعّل" else "معطّل") }
                OutlinedButton(onClick = { save(buttons.filterNot { it.id == active.id }); selected = buttons.firstOrNull()?.id }) { Text("حذف الزر") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("يمين فوق", "يمين تحت", "يسار", "يسار فوق", "يسار تحت").forEach { name ->
                    OutlinedButton(onClick = {
                        val (x, y) = ButtonConfig.preset(name); free = false
                        save(buttons.map { if (it.id == active.id) it.copy(x = x, y = y) else it })
                    }) { Text(name) }
                }
                FilterChip(selected = free, onClick = { free = !free }, label = { Text("موضع حر") })
            }
            Text(if (free) "المس أي مكان في المعاينة لنقل الزر المحدد" else "اختر موضعاً جاهزاً أو فعّل الموضع الحر", color = Color(0xFFCDD8D9))
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF101C22))
            .pointerInput(active?.id, free, buttons) {
                detectTapGestures { pos ->
                    if (free && active != null) {
                        val x = (pos.x / size.width).coerceIn(0f, 1f)
                        val y = (pos.y / size.height).coerceIn(0f, 1f)
                        save(buttons.map { if (it.id == active.id) it.copy(x = x, y = y) else it })
                    } else {
                        selected = buttons.minByOrNull { b ->
                            val dx = b.x * size.width - pos.x; val dy = b.y * size.height - pos.y; dx * dx + dy * dy
                        }?.id
                    }
                }
            }) {
            buttons.forEach { b -> drawCircle(if (selected == b.id) Color(0xFFD8BE8A) else Color(0xFF70C5C9),
                9.dp.toPx(), Offset((b.x * size.width).coerceIn(10f, size.width - 10f), (b.y * size.height).coerceIn(10f, size.height - 10f))) }
        }
        active?.let { Text("${it.label} · x=${(it.x * 100).toInt()}%  y=${(it.y * 100).toInt()}% · السحب ${if (it.fromRight) "إلى اليسار" else "إلى اليمين"}", color = Color(0xFFCDD8D9)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { export.launch("ufuq-buttons.json") }) { Text("تصدير JSON") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("استيراد JSON") }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFCDD8D9))
    }
    if (appsFor != null) AlertDialog(onDismissRequest = { appsFor = null }, title = { Text("اختر التطبيق") }, text = {
        val apps = remember { AppIndex.launchable(context) }
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = {
                save(buttons.map { if (it.id == appsFor) it.copy(target = ButtonConfig.LAUNCHER, label = "فتح T1") else it }); appsFor = null
            }) { Text("T1 اللانشر") }
            apps.forEach { app -> TextButton(onClick = {
                save(buttons.map { if (it.id == appsFor) it.copy(target = app.component.flattenToString(), label = app.label.take(80).ifBlank { app.packageName }) else it }); appsFor = null
            }) { Text(app.label) } }
        }
    }, confirmButton = { TextButton(onClick = { appsFor = null }) { Text("إغلاق") } })
    importCandidate?.let { candidate -> AlertDialog(onDismissRequest = { importCandidate = null },
        title = { Text("استبدال الأزرار؟") }, text = { Text("سيستبدل الملف الأزرار الحالية بـ ${candidate.size} زر. لا يثبت أي تطبيق.") },
        confirmButton = { TextButton(onClick = { save(candidate); selected = candidate.firstOrNull()?.id; importCandidate = null; message = "تم الاستيراد" }) { Text("استبدال") } },
        dismissButton = { TextButton(onClick = { importCandidate = null }) { Text("إلغاء") } }) }
}

private fun java.io.Reader.readTextLimited(): String {
    val chars = CharArray(65537)
    var total = 0
    while (total < chars.size) { val n = read(chars, total, chars.size - total); if (n < 0) break; total += n }
    require(total <= 65536) { "الملف كبير جداً؛ لم تتغير الإعدادات" }
    return String(chars, 0, total)
}
