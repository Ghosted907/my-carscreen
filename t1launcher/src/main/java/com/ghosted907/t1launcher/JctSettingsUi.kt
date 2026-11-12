package com.ghosted907.t1launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.Executors

/** Human-readable shortcut name in the selected app language. */
fun jctLabel(context: Context, raw: String): String {
    val en = JctLang.english(context)
    val a = JctActions.parse(raw)
    return when (a.type) {
        JctType.ENUM -> (a.enumValue ?: JctEnum.Not_value).label(en)
        JctType.APP -> appLabel(context, a.pkg)
        JctType.SPLIT -> "${if (en) "Split" else "تقسيم"}: ${appLabel(context, a.pkg)} / ${appLabel(context, a.pkg2)} ${a.ratio}:${100 - a.ratio}"
        JctType.CUSTOM_CMD -> if (a.profile == JctLaunch.PROFILE_TASKS) (if (en) "Run task group" else "تشغيل مجموعة المهام") else a.profile ?: ""
    }
}

private fun appLabel(context: Context, pkg: String?): String =
    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg!!, 0)).toString() }.getOrDefault(pkg ?: "")

/** Actions available to launcher buttons and gestures. */
fun jctCatalog(context: Context): List<String> =
    JctEnum.values().map { JctActions.ENUM + it.name } +
        listOf(JctActions.PROFILE + JctLaunch.PROFILE_TASKS) +
        AppIndex.launchable(context).map { JctActions.APP + it.packageName }.distinct()

@Composable
private fun Card(title: String, day: Boolean, content: @Composable ColumnScope.() -> Unit) {
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

private data class PickerOption(val label: String, val select: () -> Unit)

/** A wide grid for app and action choices throughout launcher settings. */
@Composable
private fun GridPickerDialog(title: String, options: List<PickerOption>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.80f).fillMaxHeight(.78f),
            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontSize = 22.sp)
                    TextButton(onClick = onDismiss) { Text(tr("إغلاق", "Close")) }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 165.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(options) { option ->
                        OutlinedButton(onClick = option.select,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                            shape = RoundedCornerShape(10.dp)) {
                            Text(option.label, textAlign = TextAlign.Center, maxLines = 2,
                                fontSize = 14.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Assign one saved launcher shortcut. */
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
    var split by remember { mutableStateOf(false) }
    if (split) SplitDialog(onDismiss = { split = false }) { raw -> split = false; onPick(raw) }
    else GridPickerDialog(tr("اختر الإجراء", "Select action"),
        listOf(PickerOption(tr("تقسيم الشاشة بين تطبيقين…", "Split screen between two apps…")) { split = true }) +
            items.map { raw -> PickerOption(jctLabel(context, raw)) { onPick(raw) } }, onDismiss)
}

/** Compose a two-app freeform layout from installed applications. */
@Composable
private fun SplitDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppIndex.launchable(context).distinctBy { it.packageName } }
    var first by remember { mutableStateOf<String?>(null) }
    var second by remember { mutableStateOf<String?>(null) }
    var orientation by remember { mutableIntStateOf(1) }
    var ratio by remember { mutableIntStateOf(50) }
    var choosing by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("تقسيم الشاشة", "Split screen")) }, text = {
        Column {
            OutlinedButton(onClick = { choosing = 1 }) { Text(tr("التطبيق الأول: ", "First app: ") + (first?.let { appLabel(context, it) } ?: "—")) }
            OutlinedButton(onClick = { choosing = 2 }) { Text(tr("التطبيق الثاني: ", "Second app: ") + (second?.let { appLabel(context, it) } ?: "—")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = orientation == 1, onClick = { orientation = 1 }); Text(tr("جنبًا إلى جنب", "Side by side"))
                RadioButton(selected = orientation == 2, onClick = { orientation = 2 }); Text(tr("فوق بعض", "Stacked"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(30, 50, 70).forEach { r ->
                    RadioButton(selected = ratio == r, onClick = { ratio = r }); Text("$r:${100 - r}")
                }
            }
            Text(tr("يعمل فقط عند تفعيل النوافذ الحرة (freeform) على الشاشة الرئيسية.", "Works only when freeform windows are enabled on the main display."), fontSize = 12.sp)
        }
    }, confirmButton = {
        TextButton(enabled = first != null && second != null && first != second, onClick = {
            onPick(JctActions.encode(JctAction(JctType.SPLIT, pkg = first, pkg2 = second, orientation = orientation, ratio = ratio)))
        }) { Text(tr("حفظ", "Save")) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } })
    if (choosing != 0) GridPickerDialog(tr("اختر تطبيقًا من القائمة", "Choose an app from the list"),
        apps.map { a -> PickerOption(a.label) {
            if (choosing == 1) first = a.packageName else second = a.packageName
            choosing = 0
        } }, onDismiss = { choosing = 0 })
}

@Composable
fun JctSettingsPage(day: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val dir = if (JctLang.english(context)) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("تشغيل التطبيقات والتحكم المريح", "App launch and quick controls"), color = if (day) T1Colors.dayText else T1Colors.nightText, fontSize = 24.sp, modifier = Modifier.weight(1f))
                Button(onClick = onBack) { Text(tr("رجوع", "Back")) }
            }
            Note(tr("اختصارات التطبيقات والشاشات والإيماءات في T1 Launcher.", "App, display, and gesture shortcuts for T1 Launcher."), day)
            JctSettingsContent(day)
        }
    }
}

@Composable
fun JctSettingsContent(day: Boolean) {
    val context = LocalContext.current
    val dir = if (JctLang.english(context)) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        LanguageCard(day)
        JctClusterAppCard(day)
        SplitPresetsCard(day)
        FloatingCard(day)
        TaskGroupCard(day)
        ZonesCard(day)
        BlackScreenCard(day)
        HideCard(day)
    }
}

@Composable
private fun LanguageCard(day: Boolean) {
    val context = LocalContext.current
    val en = JctLang.english(context)
    val current = JctLang.saved(context)
    Card(tr("لغة التطبيق", "Application language"), day) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JctLang.choices.forEach { v ->
                if (v == current) Button(onClick = {}) { Text(JctLang.label(v, en)) }
                else OutlinedButton(onClick = { (context as? Activity)?.let { JctLang.set(it, v) } }) { Text(JctLang.label(v, en)) }
            }
        }
        Note(tr("تتغير لغة هذه الصفحة ورسائل اختصارات التطبيقات. بقيّة الواجهة عربية حاليًا.",
            "Changes this page and shortcut messages. The rest of the launcher is still Arabic."), day)
    }
}

@Composable
fun JctClusterAppCard(day: Boolean) {
    val context = LocalContext.current
    var pkg by remember { mutableStateOf(JctStore.str(context, JctStore.PKG, JctStore.PKG_DEFAULT)) }
    var boot by remember { mutableStateOf(JctStore.bool(context, JctStore.BOOT, false)) }
    var choose by remember { mutableStateOf(false) }
    var gu by remember { mutableIntStateOf(JctStore.int(context, JctStore.GU_INDEX, -1)) }
    var pri by remember { mutableIntStateOf(JctStore.int(context, JctStore.PRI_INDEX, -1)) }
    var area by remember { mutableIntStateOf(JctStore.int(context, JctStore.PKG_AREA, 0)) }
    Card(tr("التطبيقات على لوحة العدادات", "Apps on the instrument cluster"), day) {
        OutlinedTextField(value = pkg, onValueChange = { pkg = it.trim() }, singleLine = true, label = { Text(tr("الحزمة", "Package")) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { choose = true }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("اختر تطبيقًا من القائمة", "Choose an app from the list")) }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = pkg.isNotBlank(), onClick = { if (JctLaunch.runPkgOn(context, pkg, cluster = true)) area = JctStore.AREA_PRI else launchError(context) }) { Text(tr("افتح على العدادات", "Open on cluster")) }
            Button(enabled = pkg.isNotBlank(), onClick = { if (JctLaunch.runPkgOn(context, pkg, cluster = false)) area = JctStore.AREA_GU else launchError(context) }) { Text(tr("افتح على الشاشة الرئيسية", "Open on main screen")) }
            OutlinedButton(onClick = {
                val toCluster = JctStore.int(context, JctStore.PKG_AREA, JctStore.AREA_GU) != JctStore.AREA_PRI
                if (JctLaunch.runPkgOn(context, pkg, cluster = toCluster)) area = if (toCluster) JctStore.AREA_PRI else JctStore.AREA_GU else launchError(context)
            }, enabled = pkg.isNotBlank()) { Text(tr("انقل للشاشة الأخرى", "Move to other screen")) }
        }
        Note(when (area) {
            JctStore.AREA_PRI -> tr("آخر وجهة: لوحة العدادات", "Last destination: cluster")
            JctStore.AREA_GU -> tr("آخر وجهة: الجهاز اللوحي", "Last destination: head unit")
            else -> tr("لم يُرسل التطبيق بعد", "Not sent yet")
        }, day)
        SwitchRow(tr("تشغيل التطبيق تلقائيًا على العدادات عند تشغيل السيارة", "Open on cluster at startup"), boot, day) {
            boot = it; JctStore.putBool(context, JctStore.BOOT, it); JctStore.putStr(context, JctStore.PKG, pkg)
        }
        Note(tr("الشاشات (فهرس getDisplays):\n", "Displays (getDisplays index):\n") + JctLaunch.describe(context), day)
        val auto = tr(" تلقائي", " auto")
        Note(tr("الجهاز اللوحي", "Head unit") + " = [${JctLaunch.guIndex(context)}]${if (gu < 0) auto else ""}   " +
            tr("العدادات", "Cluster") + " = [${JctLaunch.priIndex(context)}]${if (pri < 0) auto else ""}", day)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { gu = cycle(context, gu); JctStore.putInt(context, JctStore.GU_INDEX, gu) }) { Text(tr("تغيير فهرس اللوحي", "Change head-unit index")) }
            TextButton(onClick = { pri = cycle(context, pri); JctStore.putInt(context, JctStore.PRI_INDEX, pri) }) { Text(tr("تغيير فهرس العدادات", "Change cluster index")) }
        }
    }
    if (choose) {
        val apps = remember { AppIndex.launchable(context) }
        GridPickerDialog(tr("اختر تطبيقًا من القائمة", "Choose an app from the list"),
            apps.map { a -> PickerOption(a.label) {
                pkg = a.packageName; JctStore.putStr(context, JctStore.PKG, pkg); choose = false
            } }, onDismiss = { choose = false })
    }
}

private fun cycle(context: Context, cur: Int): Int {
    val n = JctLaunch.displays(context).size
    return if (cur + 1 >= n) -1 else cur + 1
}

private fun launchError(context: Context) {
    android.widget.Toast.makeText(context, tr(context,
        "تعذّر فتح التطبيق على الشاشة المحددة. راجع التطبيق وفهرس الشاشة.",
        "Could not open the app on that display. Check the app and display index."), android.widget.Toast.LENGTH_LONG).show()
}

/** Three reusable layouts keep common app pairs one tap away. */
@Composable
private fun SplitPresetsCard(day: Boolean) {
    val context = LocalContext.current
    val keys = remember { (1..3).map { "split_preset_$it" } }
    val presets = remember { mutableStateListOf<String>().apply { addAll(keys.map { JctStore.str(context, it, "") }) } }
    var editing by remember { mutableIntStateOf(-1) }
    Card(tr("تخطيطات تطبيقين", "Two-app layouts"), day) {
        Note(tr("احفظ أزواج التطبيقات التي تستخدمها كثيرًا. تتطلب النوافذ الحرة في النظام.",
            "Save frequent app pairs. System freeform windows must be enabled."), day)
        keys.forEachIndexed { index, key ->
            val action = JctActions.parse(presets[index])
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Note(tr("تخطيط ", "Layout ") + (index + 1) + ": " +
                    if (action.type == JctType.SPLIT) jctLabel(context, presets[index]) else tr("غير محفوظ", "Empty"), day)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = index }) { Text(tr("إعداد", "Set up")) }
                    Button(enabled = action.type == JctType.SPLIT, onClick = { JctLaunch.runSplit(context, action) }) { Text(tr("افتح", "Open")) }
                    TextButton(enabled = action.type == JctType.SPLIT, onClick = {
                        presets[index] = ""; JctStore.putStr(context, key, "")
                    }) { Text(tr("مسح", "Clear")) }
                }
            }
        }
    }
    if (editing >= 0) SplitDialog(onDismiss = { editing = -1 }) { raw ->
        val index = editing
        presets[index] = raw
        JctStore.putStr(context, keys[index], raw)
        editing = -1
    }
}

@Composable
private fun FloatingCard(day: Boolean) {
    val context = LocalContext.current
    var single by remember { mutableStateOf(JctStore.bool(context, JctStore.LOUNCHB_TRUE, false)) }
    var multi by remember { mutableStateOf(JctStore.bool(context, JctStore.MULTI_ON, false)) }
    var eight by remember { mutableStateOf(JctStore.int(context, JctStore.MULTI_COUNT, 8) == 8) }
    var over by remember { mutableStateOf(JctStore.bool(context, JctStore.OVER_SYSTEM, true)) }
    val sync = { JctLounchService.sync(context) }
    Card(tr("الأزرار العائمة", "Floating buttons"), day) {
        if (!Settings.canDrawOverlays(context)) {
            Note(tr("يحتاج صلاحية الظهور فوق التطبيقات", "Needs the display-over-apps permission") + ": adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow", day)
            TextButton(onClick = { runCatching { LauncherDisplay.start(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) } }) { Text(tr("فتح إعدادات الظهور", "Open overlay settings")) }
        }
        SwitchRow(tr("عرض الزر العائم", "Show floating button"), single, day) { single = it; JctStore.putBool(context, JctStore.LOUNCHB_TRUE, it); sync() }
        JctActionPicker(tr("زر عائم:", "Floating button:"), JctStore.LOUNCHB_ACTION, day)
        Note(tr("اضغط مطولاً على الزر ثم اسحبه لمكان لا يغطي عناصر المصنع.", "Long-press the button, then drag it where it does not cover OEM controls."), day)
        SwitchRow(tr("عرض الزر المتعدد", "Show multi-button"), multi, day) { multi = it; JctStore.putBool(context, JctStore.MULTI_ON, it); sync() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = eight, onCheckedChange = { eight = it; JctStore.putInt(context, JctStore.MULTI_COUNT, if (it) 8 else 6); sync() })
            Text(tr("استخدام 8 أزرار", "Use 8 buttons"), color = if (day) T1Colors.dayText else T1Colors.nightText)
        }
        val names = if (JctLang.english(context)) listOf("Top", "Bottom", "Left", "Right", "Top left", "Top right", "Bottom left", "Bottom right")
            else listOf("أعلى", "أسفل", "يسار", "يمين", "أعلى يسار", "أعلى يمين", "أسفل يسار", "أسفل يمين")
        names.forEachIndexed { i, n -> if (eight || i < 4) JctActionPicker(n, JctStore.MULTI_ACTION + i, day) { sync() } }
        SwitchRow(tr("الأدوات فوق عناصر النظام", "Widgets over system elements"), over, day) { over = it; JctStore.putBool(context, JctStore.OVER_SYSTEM, it); sync() }
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
    Card(tr("تشغيل مجموعة المهام تلقائيًا", "Auto-run task group"), day) {
        SwitchRow(tr("تشغيل مجموعة المهام تلقائيًا", "Auto-run task group"), on, day) { on = it; JctStore.putBool(context, JctStore.TASKS_BOOT, it) }
        Note(tr("اختر الأوامر التي سيتم تنفيذها عند تشغيل السيارة", "Choose the commands to run when the car starts"), day)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Note(tr("تعيين الفاصل الزمني بين الأوامر بالثواني: ", "Delay between commands, seconds: ") + delay, day)
            TextButton(onClick = { delay = (delay - 1).coerceIn(1, 20); JctStore.putInt(context, JctStore.TASKS_DELAY, delay) }) { Text("−") }
            TextButton(onClick = { delay = (delay + 1).coerceIn(1, 20); JctStore.putInt(context, JctStore.TASKS_DELAY, delay) }) { Text("+") }
        }
        tasks.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", color = if (day) T1Colors.dayText else T1Colors.nightText)
                Checkbox(checked = t.enabled, onCheckedChange = { enabled ->
                    save(tasks.mapIndexed { j, item -> if (j == i) item.copy(enabled = enabled) else item })
                })
                OutlinedButton(onClick = { pickFor = i }, modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) { Text(jctLabel(context, t.action), maxLines = 1) }
                TextButton(enabled = i > 0, onClick = { save(tasks.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }) }) { Text("▲") }
                TextButton(enabled = i < tasks.lastIndex, onClick = { save(tasks.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }) }) { Text("▼") }
                TextButton(onClick = { save(tasks.filterIndexed { j, _ -> j != i }) }) { Text(tr("حذف", "Delete")) }
            }
        }
        Note(tr("علامة الاختيار تحدد الأوامر التي ستعمل. الترتيب من أعلى إلى أسفل.",
            "Checked commands run from top to bottom."), day)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save(tasks + JctLaunch.Task("", true, false, JctActions.NOT_VALUE)); pickFor = tasks.size - 1 }) { Text(tr("إضافة أمر", "Add command")) }
            OutlinedButton(onClick = { JctLaunch.runTaskGroup(context, JctStore.TASKS) }) { Text(tr("تجربة الآن", "Test now")) }
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
    val en = JctLang.english(context)
    var width by remember { mutableIntStateOf(JctGestureZones.widthPercent(context)) }
    var sensitivity by remember { mutableIntStateOf(JctGestureZones.sensitivityPercent(context)) }
    Card(tr("إعداد السحب من حافة الشاشة", "Configure edge swipe"), day) {
        if (!a11y) {
            Note(tr("يحتاج تفعيل خدمة إمكانية الوصول «T1 remote touch».", "Needs the \"T1 remote touch\" accessibility service."), day)
            TextButton(onClick = { runCatching { LauncherDisplay.start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }) { Text(tr("فتح إعدادات إمكانية الوصول", "Open accessibility settings")) }
        }
        Note(tr("اسحب نحو داخل الشاشة. فعّل المناطق التي تحتاجها فقط لتقليل التعارض مع إيماءات النظام.",
            "Swipe into the screen. Enable only the edges you need to avoid system gesture conflicts."), day)
        Note(tr("عرض منطقة السحب: ", "Edge width: ") + "$width%", day)
        Slider(value = width.toFloat(), onValueChange = { width = it.toInt() }, valueRange = 75f..150f,
            onValueChangeFinished = { JctGestureZones.setWidthPercent(context, width) })
        Note(tr("مسافة التفعيل: ", "Swipe distance: ") + "$sensitivity%", day)
        Slider(value = sensitivity.toFloat(), onValueChange = { sensitivity = it.toInt() }, valueRange = 75f..150f,
            onValueChangeFinished = { JctGestureZones.setSensitivityPercent(context, sensitivity) })
        JctZone.values().forEach { z ->
            var on by remember(z) { mutableStateOf(JctGestureZones.enabled(context, z)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = { on = it; JctGestureZones.setEnabled(context, z, it) })
                Box(Modifier.weight(1f)) { JctActionPicker(z.label(en), JctStore.ZONE_ACTION + z.name, day) }
            }
        }
    }
}

@Composable
private fun BlackScreenCard(day: Boolean) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(JctStore.bool(context, JctBlackActivity.KEY_CLOCK, true)) }
    var clock24 by remember { mutableStateOf(JctStore.bool(context, JctBlackActivity.KEY_CLOCK_24, true)) }
    var bright by remember { mutableIntStateOf(JctStore.int(context, JctBlackActivity.KEY_BRIGHTNESS, 10)) }
    Card(tr("شاشة سوداء", "Black screen"), day) {
        Note(tr("تُفتح من أي زر أو منطقة سحب بالإجراء «شاشة سوداء». المس الشاشة للخروج.", "Opened by any button or edge zone set to \"Black screen\". Tap to exit."), day)
        SwitchRow(tr("عرض الساعة", "Show clock"), clock, day) { clock = it; JctStore.putBool(context, JctBlackActivity.KEY_CLOCK, it) }
        if (clock) SwitchRow(tr("الوقت بنظام 24 ساعة", "24-hour time"), clock24, day) {
            clock24 = it; JctStore.putBool(context, JctBlackActivity.KEY_CLOCK_24, it)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Note(tr("السطوع: ", "Brightness: ") + "$bright%", day)
            TextButton(onClick = { bright = (bright - 5).coerceIn(1, 100); JctStore.putInt(context, JctBlackActivity.KEY_BRIGHTNESS, bright) }) { Text("−") }
            TextButton(onClick = { bright = (bright + 5).coerceIn(1, 100); JctStore.putInt(context, JctBlackActivity.KEY_BRIGHTNESS, bright) }) { Text("+") }
        }
        OutlinedButton(onClick = { JctLaunch.run(context, JctAction(JctType.ENUM, JctEnum.RUN_BLACK)) }) { Text(tr("تجربة الآن", "Test now")) }
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
    fun result(ar: String, en: String, r: JctUserAppsHide.Result) =
        tr(context, ar, en) + " ${r.success}، " + tr(context, "فشل", "failed") + " ${r.fail}${r.error?.let { " — $it" } ?: ""}"
    Card(tr("إخفاء التطبيقات", "Hide apps"), day) {
        Note(if (owner) tr("مالك الجهاز مفعّل: سيُخفى كل تطبيق مستخدم غير نظامي، مع اختصارات T1.", "Device owner is active: every non-system user app is hidden, with the T1 shortcuts.")
            else tr("بدون مالك الجهاز يُخفى اختصار T1 فقط. للإخفاء الكامل (مرة واحدة من الكمبيوتر، بدون حسابات على الجهاز):", "Without device owner only the T1 shortcut is hidden. For full hiding (once, from a computer, no accounts on the device):") +
                "\nadb shell dpm set-device-owner com.ghosted907.t1launcher/.ClusterDeviceAdmin", day)
        hider.shortcuts.forEach { s ->
            var on by remember(s.id) { mutableStateOf(hider.shortcutEnabled(s)) }
            SwitchRow(tr("اختصار ", "Shortcut ") + s.labelAr + if (s.locked) tr(" (ثابت)", " (fixed)") else "", on, day) { if (!s.locked) { on = it; hider.setShortcut(s, it) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(enabled = !hidden, onClick = { confirm = true }) { Text(tr("إخفاء", "Hide")) }
            OutlinedButton(enabled = hidden, onClick = {
                worker.execute { val r = hider.restore(); main.post { hidden = JctUserAppsHide.enabled(context); status = result("أُظهر", "shown", r); JctHideZone.dismiss() } }
            }) { Text(tr("إظهار", "Show")) }
        }
        Note(tr("للاستعادة: اضغط المربع الشفاف 14 مرة، أو من الكمبيوتر:", "To restore: tap the transparent square 14 times, or from a computer:") +
            "\nadb shell am broadcast -a ${JctUserAppsHide.ACTION_OPEN} -n com.ghosted907.t1launcher/.UserAppsHideCommandReceiver", day)
        if (status.isNotBlank()) Note(status, day)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(tr("إخفاء التطبيقات؟", "Hide apps?")) },
        text = { Text(if (owner) tr("سيُخفى كل تطبيق مستخدم (${hider.userApps().size}) واختصار T1. يظهر مربع استعادة شبه شفاف؛ اضغطه 14 مرة للإظهار.",
                "Every user app (${hider.userApps().size}) and the T1 shortcut will be hidden. A semi-transparent restore square appears; tap it 14 times to show them.")
            else tr("سيُخفى اختصار T1 من قائمة التطبيقات. يظهر مربع استعادة؛ اضغطه 14 مرة للإظهار.", "The T1 shortcut will be hidden from the app list. A restore square appears; tap it 14 times to show it.")) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            worker.execute { val r = hider.hide(); main.post { hidden = JctUserAppsHide.enabled(context); status = result("أُخفي", "hidden", r); if (hidden) JctHideZone.show(context) } }
        }) { Text(tr("إخفاء", "Hide")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("إلغاء", "Cancel")) } })
}
