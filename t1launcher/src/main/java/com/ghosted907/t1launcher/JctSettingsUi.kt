package com.ghosted907.t1launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors

/** Action label as NaviStart's spinner shows it (j0.e.toString). */
fun jctLabel(context: Context, raw: String): String {
    val a = JctActions.parse(raw)
    return when (a.type) {
        JctType.ENUM -> (a.enumValue ?: JctEnum.Not_value).labelAr
        JctType.APP -> runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(a.pkg!!, 0)).toString() }.getOrDefault(a.pkg ?: "")
        JctType.SPLIT -> "${a.pkg}/${a.pkg2} ${a.ratio}/${100 - a.ratio}"
        JctType.CUSTOM_CMD -> if (a.profile == JctLaunch.PROFILE_TASKS) "تشغيل مجموعة المهام" else a.profile ?: ""
    }
}

/** j0.c.d: enum actions, profiles, then every launchable app. */
fun jctCatalog(context: Context): List<String> =
    JctEnum.values().map { JctActions.ENUM + it.name } +
        listOf(JctActions.PROFILE + JctLaunch.PROFILE_TASKS) +
        AppIndex.launchable(context).map { JctActions.APP + it.packageName }.distinct()

@Composable
private fun Card(title: String, day: Boolean, content: @Composable ColumnScope.() -> Unit) {
    // NaviStart: full-width cards, 15 dp corners, stacked vertically.
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(15.dp))
        .background(if (day) T1Colors.dayCard else T1Colors.nightCard).padding(16.dp)) {
        Text(title, color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, day: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Note(text: String, day: Boolean) =
    Text(text, color = if (day) T1Colors.dayMuted else T1Colors.nightMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))

/** Spinner bound to one action key (j0.c.j). */
@Composable
fun JctActionPicker(label: String, key: String, day: Boolean, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var value by remember(key) { mutableStateOf(JctStore.str(context, key, JctActions.NOT_VALUE)) }
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { open = true }) { Text(jctLabel(context, value), maxLines = 1) }
    }
    if (open) ActionDialog(onDismiss = { open = false }) { raw ->
        JctStore.putStr(context, key, raw); value = raw; open = false; onChanged()
    }
}

@Composable
private fun ActionDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val items = remember { jctCatalog(context) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("اختر الإجراء") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            items.forEach { raw -> TextButton(onClick = { onPick(raw) }) { Text(jctLabel(context, raw)) } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } })
}

@Composable
fun JctSettingsPage(day: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تشغيل التطبيقات والتحكم المريح", color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Button(onClick = onBack) { Text("رجوع") }
        }
        Note("منقول من JCarTools (NaviStart 1.4.1) بنفس الآلية.", day)
        JctClusterAppCard(day)
        FloatingCard(day)
        TaskGroupCard(day)
        ZonesCard(day)
        HideCard(day)
    }
}

/** main_startpropri.xml: package field + app list + launch buttons + auto-start switch. */
@Composable
fun JctClusterAppCard(day: Boolean) {
    val context = LocalContext.current
    var pkg by remember { mutableStateOf(JctStore.str(context, JctStore.PKG, JctStore.PKG_DEFAULT)) }
    var boot by remember { mutableStateOf(JctStore.bool(context, JctStore.BOOT, false)) }
    var choose by remember { mutableStateOf(false) }
    var gu by remember { mutableIntStateOf(JctStore.int(context, JctStore.GU_INDEX, -1)) }
    var pri by remember { mutableIntStateOf(JctStore.int(context, JctStore.PRI_INDEX, -1)) }
    Card("التطبيقات على لوحة العدادات", day) {
        OutlinedTextField(value = pkg, onValueChange = { pkg = it.trim() }, singleLine = true, label = { Text("الحزمة") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { choose = true }, modifier = Modifier.padding(top = 6.dp)) { Text("اختر تطبيقًا من القائمة") }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { JctLaunch.runPkgOn(context, pkg, cluster = true) }) { Text("تشغيل على لوحة العدادات") }
            Button(onClick = { JctLaunch.runPkgOn(context, pkg, cluster = false) }) { Text("تشغيل على الجهاز اللوحي") }
        }
        SwitchRow("تشغيل التطبيق تلقائيًا على لوحة العدادات عند تشغيل السيارة", boot, day) {
            boot = it; JctStore.putBool(context, JctStore.BOOT, it); JctStore.putStr(context, JctStore.PKG, pkg)
        }
        Note("الشاشات (فهرس getDisplays):\n" + JctLaunch.describe(context), day)
        Note("الجهاز اللوحي = [${JctLaunch.guIndex(context)}]${if (gu < 0) " تلقائي" else ""}   العدادات = [${JctLaunch.priIndex(context)}]${if (pri < 0) " تلقائي" else ""}", day)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { gu = cycle(context, gu); JctStore.putInt(context, JctStore.GU_INDEX, gu) }) { Text("تغيير فهرس اللوحي") }
            TextButton(onClick = { pri = cycle(context, pri); JctStore.putInt(context, JctStore.PRI_INDEX, pri) }) { Text("تغيير فهرس العدادات") }
        }
    }
    if (choose) {
        val apps = remember { AppIndex.launchable(context) }
        AlertDialog(onDismissRequest = { choose = false }, title = { Text("اختر تطبيقًا من القائمة") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                apps.forEach { a -> TextButton(onClick = { pkg = a.packageName; JctStore.putStr(context, JctStore.PKG, pkg); choose = false }) { Text(a.label) } }
            }
        }, confirmButton = { TextButton(onClick = { choose = false }) { Text("إغلاق") } })
    }
}

private fun cycle(context: Context, cur: Int): Int {
    val n = JctLaunch.displays(context).size
    return if (cur + 1 >= n) -1 else cur + 1
}

@Composable
private fun FloatingCard(day: Boolean) {
    val context = LocalContext.current
    var single by remember { mutableStateOf(JctStore.bool(context, JctStore.LOUNCHB_TRUE, false)) }
    var multi by remember { mutableStateOf(JctStore.bool(context, JctStore.MULTI_ON, false)) }
    var eight by remember { mutableStateOf(JctStore.int(context, JctStore.MULTI_COUNT, 8) == 8) }
    var over by remember { mutableStateOf(JctStore.bool(context, JctStore.OVER_SYSTEM, true)) }
    val sync = { JctLounchService.sync(context) }
    Card("الأزرار العائمة", day) {
        if (!Settings.canDrawOverlays(context)) {
            Note("يحتاج صلاحية الظهور فوق التطبيقات: adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow", day)
            TextButton(onClick = { runCatching { LauncherDisplay.start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) } }) { Text("فتح إعدادات الظهور") }
        }
        SwitchRow("عرض الزر العائم", single, day) { single = it; JctStore.putBool(context, JctStore.LOUNCHB_TRUE, it); sync() }
        JctActionPicker("زر عائم:", JctStore.LOUNCHB_ACTION, day)
        Note("اضغط مطولاً على الزر ثم اسحبه لمكان لا يغطي عناصر المصنع.", day)
        SwitchRow("عرض الزر المتعدد", multi, day) { multi = it; JctStore.putBool(context, JctStore.MULTI_ON, it); sync() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = eight, onCheckedChange = { eight = it; JctStore.putInt(context, JctStore.MULTI_COUNT, if (it) 8 else 6); sync() })
            Text("استخدام 8 أزرار", color = if (day) T1Colors.dayText else T1Colors.nightText)
        }
        val names = listOf("أعلى", "أسفل", "يسار", "يمين", "أعلى يسار", "أعلى يمين", "أسفل يسار", "أسفل يمين")
        names.forEachIndexed { i, n -> if (eight || i < 4) JctActionPicker(n, JctStore.MULTI_ACTION + i, day) { sync() } }
        SwitchRow("الأدوات فوق عناصر النظام", over, day) { over = it; JctStore.putBool(context, JctStore.OVER_SYSTEM, it); sync() }
    }
}

@Composable
private fun TaskGroupCard(day: Boolean) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(JctStore.bool(context, JctStore.TASKS_BOOT, false)) }
    var delay by remember { mutableIntStateOf(JctStore.int(context, JctStore.TASKS_DELAY, 1)) }
    var tasks by remember { mutableStateOf(JctLaunch.loadTasks(context)) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    fun save(n: List<JctLaunch.Task>) { tasks = n; JctLaunch.saveTasks(context, n) }
    Card("تشغيل مجموعة المهام تلقائيًا", day) {
        SwitchRow("تشغيل مجموعة المهام تلقائيًا", on, day) { on = it; JctStore.putBool(context, JctStore.TASKS_BOOT, it) }
        Note("اختر الأوامر التي سيتم تنفيذها عند تشغيل السيارة", day)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Note("تعيين الفاصل الزمني بين الأوامر بالثواني: $delay", day)
            TextButton(onClick = { delay = (delay - 1).coerceIn(1, 20); JctStore.putInt(context, JctStore.TASKS_DELAY, delay) }) { Text("−") }
            TextButton(onClick = { delay = (delay + 1).coerceIn(1, 20); JctStore.putInt(context, JctStore.TASKS_DELAY, delay) }) { Text("+") }
        }
        tasks.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", color = if (day) T1Colors.dayText else T1Colors.nightText)
                OutlinedButton(onClick = { pickFor = i }, modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) { Text(jctLabel(context, t.action), maxLines = 1) }
                TextButton(enabled = i > 0, onClick = { save(tasks.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }) { Text("▲") }
                TextButton(onClick = { save(tasks.filterIndexed { j, _ -> j != i }) }) { Text("حذف") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save(tasks + JctLaunch.Task("", true, false, JctActions.NOT_VALUE)); pickFor = tasks.size - 1 }) { Text("إضافة أمر") }
            OutlinedButton(onClick = { JctLaunch.runTaskGroup(context, JctStore.TASKS) }) { Text("تجربة الآن") }
        }
    }
    pickFor?.let { idx -> ActionDialog(onDismiss = { pickFor = null }) { raw ->
        save(tasks.mapIndexed { j, t -> if (j == idx) t.copy(action = raw) else t }); pickFor = null
    } }
}

@Composable
private fun ZonesCard(day: Boolean) {
    val context = LocalContext.current
    val a11y = T1TouchService.instance != null
    Card("إعداد السحب من حافة الشاشة", day) {
        if (!a11y) {
            Note("يحتاج تفعيل خدمة إمكانية الوصول «T1 remote touch».", day)
            TextButton(onClick = { runCatching { LauncherDisplay.start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text("فتح إعدادات إمكانية الوصول") }
        }
        Note("قد تتعارض المناطق مع إيماءات النظام. اسحب أكثر من 90 بكسل نحو الداخل.", day)
        JctZone.values().forEach { z ->
            var on by remember(z) { mutableStateOf(JctGestureZones.enabled(context, z)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = { on = it; JctGestureZones.setEnabled(context, z, it) })
                Box(Modifier.weight(1f)) { JctActionPicker(z.labelAr, JctStore.ZONE_ACTION + z.name, day) }
            }
        }
    }
}

@Composable
private fun HideCard(day: Boolean) {
    val context = LocalContext.current
    val hider = remember { JctUserAppsHide(context) }
    var hidden by remember { mutableStateOf(JctUserAppsHide.enabled(context)) }
    var status by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val owner = hider.isOwner()
    val worker = remember { Executors.newSingleThreadExecutor() }
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    Card("إخفاء التطبيقات", day) {
        Note(if (owner) "مالك الجهاز مفعّل: سيُخفى كل تطبيق مستخدم غير نظامي، مع اختصارات T1."
            else "بدون مالك الجهاز يُخفى اختصار T1 فقط. للإخفاء الكامل (مرة واحدة من الكمبيوتر، بدون حسابات على الجهاز):\nadb shell dpm set-device-owner com.ghosted907.t1launcher/.ClusterDeviceAdmin", day)
        hider.shortcuts.forEach { s ->
            var on by remember(s.id) { mutableStateOf(hider.shortcutEnabled(s)) }
            SwitchRow("اختصار ${s.labelAr}${if (s.locked) " (ثابت)" else ""}", on, day) { if (!s.locked) { on = it; hider.setShortcut(s, it) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = !hidden, onClick = { confirm = true }) { Text("إخفاء") }
            OutlinedButton(enabled = hidden, onClick = {
                worker.execute { val r = hider.restore(); main.post { hidden = JctUserAppsHide.enabled(context); status = "أُظهر ${r.success}، فشل ${r.fail}${r.error?.let { " — $it" } ?: ""}"; JctHideZone.dismiss() } }
            }) { Text("إظهار") }
        }
        Note("للاستعادة: اضغط المربع الشفاف 14 مرة، أو من الكمبيوتر:\nadb shell am broadcast -a ${JctUserAppsHide.ACTION_OPEN} -n com.ghosted907.t1launcher/.UserAppsHideCommandReceiver", day)
        if (status.isNotBlank()) Note(status, day)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("إخفاء التطبيقات؟") },
        text = { Text(if (owner) "سيُخفى كل تطبيق مستخدم (${hider.userApps().size}) واختصار T1. يظهر مربع استعادة شبه شفاف؛ اضغطه 14 مرة للإظهار." else "سيُخفى اختصار T1 من قائمة التطبيقات. يظهر مربع استعادة؛ اضغطه 14 مرة للإظهار.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            worker.execute { val r = hider.hide(); main.post { hidden = JctUserAppsHide.enabled(context); status = "أُخفي ${r.success}، فشل ${r.fail}${r.error?.let { " — $it" } ?: ""}"; if (hidden) JctHideZone.show(context) } }
        }) { Text("إخفاء") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } })
}
