package com.ghosted907.t1launcher

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SETUP_PREFS = "t1_permission_setup"
private const val SETUP_SHOWN = "shown"

fun permissionSetupShown(context: Context): Boolean =
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).getBoolean(SETUP_SHOWN, false)

fun markPermissionSetupShown(context: Context) {
    context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE).edit().putBoolean(SETUP_SHOWN, true).apply()
}

private data class PermissionItem(val title: String, val detail: String,
                                  val granted: Boolean, val action: () -> Unit)

private fun enabledService(context: Context, key: String, service: Class<*>): Boolean {
    val expected = ComponentName(context, service).flattenToString()
    return Settings.Secure.getString(context.contentResolver, key)
        ?.split(':')?.any { it.equals(expected, ignoreCase = true) } == true
}

private fun openSettings(context: Context, action: String, packageUri: Boolean = false) {
    val intent = Intent(action).apply {
        if (packageUri) data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "تعذر فتح إعدادات الأذونات", Toast.LENGTH_LONG).show() }
    }
}

/** First-run permission center; special access always uses the system consent screen. */
@Composable
fun PermissionSetupPage(day: Boolean, revision: Int, onContinue: () -> Unit) {
    val context = LocalContext.current
    var localRevision by remember { mutableIntStateOf(0) }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        localRevision++
    }
    val runtimeNames = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("runtime_requested", false)) {
            prefs.edit().putBoolean("runtime_requested", true).apply()
            val missing = runtimeNames.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) runtime.launch(missing.toTypedArray())
        }
    }
    val permissions = remember(revision, localRevision) {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val usage = ops?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        val power = context.getSystemService(PowerManager::class.java)
        listOf(
            PermissionItem("الظهور فوق التطبيقات", "للأزرار العائمة ومربع الاستعادة",
                Settings.canDrawOverlays(context)) {
                openSettings(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            },
            PermissionItem("إمكانية الوصول", "لإيماءات الحواف والتحكم باللمس",
                enabledService(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, T1TouchService::class.java)) {
                openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
            },
            PermissionItem("الوصول إلى الإشعارات", "لعرض الوسائط والتحكم بالتشغيل",
                enabledService(context, "enabled_notification_listeners", T1NotificationListener::class.java)) {
                openSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            },
            PermissionItem("الموقع الدقيق", "للبوصلة وبيانات الموقع",
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                runtime.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            },
            PermissionItem("الميكروفون", "للأوامر الصوتية",
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                runtime.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            },
            PermissionItem("الوصول إلى الاستخدام", "لعرض التطبيقات والمهام الحديثة",
                usage) { openSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            PermissionItem("العمل في الخلفية", "لخدمات اللانشر عند بدء تشغيل السيارة",
                power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                openSettings(context, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)
            }
        )
    }
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    val surface = if (day) T1Colors.dayCard else T1Colors.nightCard
    Column(Modifier.fillMaxSize().background(if (day) T1Colors.dayBg else T1Colors.nightBg)
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("أذونات لانشر جيتور T1", color = T1Colors.gold, fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold)
        Text("اختر تفعيل للأذونات التي يحتاجها اللانشر. بعض الأذونات تُمنح من إعدادات النظام، ويمكنك العودة لهذه الصفحة لاحقًا.",
            color = muted, fontSize = 15.sp)
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 290.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(permissions) { permission ->
                Column(Modifier.fillMaxWidth().heightIn(min = 132.dp)
                    .background(surface, RoundedCornerShape(12.dp))
                    .border(1.dp, T1Colors.gold.copy(alpha = .35f), RoundedCornerShape(12.dp))
                    .padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(permission.title, color = text, fontSize = 17.sp,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(if (permission.granted) "✓ مفعّل" else "مطلوب",
                            color = if (permission.granted) Color(0xFF80C99B) else T1Colors.gold,
                            fontSize = 13.sp)
                    }
                    Text(permission.detail, color = muted, fontSize = 12.sp)
                    if (!permission.granted) OutlinedButton(onClick = permission.action) { Text("تفعيل") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${permissions.count { it.granted }} من ${permissions.size} مفعّلة", color = muted, fontSize = 14.sp)
            Button(onClick = onContinue) { Text("متابعة إلى اللانشر") }
        }
    }
}
