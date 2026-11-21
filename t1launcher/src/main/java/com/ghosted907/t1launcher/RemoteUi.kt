package com.ghosted907.t1launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.delay

/** Settings card for "التحكم من الجوال". 1.15.8: always on, no on/off switch. */
@Composable
fun RemoteSettingsCard(day: Boolean) {
    val context = LocalContext.current
    val text = t1CardText(day)   // 1.15.11: card text, cream in day mode on the dark fill
    val muted = t1CardMuted(day)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val ips = remember(tick / 5) { RemoteHost.addresses() }
    var internet by remember { mutableStateOf(CloudStore.enabled(context)) }
    var showCode by remember { mutableStateOf(false) }
    var resetCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf(if (internet) CloudStore.keys(context).code else "") }

    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .t1Bg(T1Colors.fill(if (day) T1Colors.dayCard else T1Colors.nightCard)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("التحكم من الجوال", color = text, fontSize = 20.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الربط عبر الإنترنت", color = text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Switch(checked = internet, onCheckedChange = {
                internet = it; CloudStore.enable(context, it)
                if (it) code = CloudStore.keys(context).code
                else showCode = false
                RemoteHost.onProcess(context)
            })
        }
        Text(CloudStatus.message, color = muted, fontSize = 14.sp)
        Text("يربط جوالك بالسيارة حتى لو كل جهاز على شبكة مختلفة. لازم شاشة السيارة تكون شغالة ومتصلة بالإنترنت.", color = muted, fontSize = 13.sp)
        if (internet) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("T1 Remote", code))
                }) { Text("نسخ رمز الربط", color = text) }
                TextButton(onClick = { showCode = !showCode }) { Text(if (showCode) "إخفاء الرمز" else "إظهار الرمز", color = text) }
                TextButton(onClick = { resetCode = true }) { Text("تغيير الرمز", color = text) }
            }
            if (showCode) SelectionContainer { Text(code, color = text, fontSize = 16.sp) }
            Text("أدخل الرمز في T1 Remote ← اتصال عبر الإنترنت. الرمز يمنح التحكم بسيارتك؛ احتفظ به لنفسك.", color = muted, fontSize = 13.sp)
        }
        Text("الاتصال المحلي", color = text, fontSize = 17.sp)
        Text("العنوان: ${ips.joinToString("  ·  ").ifEmpty { "غير متصل بشبكة" }}  منفذ ${RemoteProtocol.PORT}", color = text, fontSize = 15.sp)
        Text("يظهر في تطبيق T1 Remote باسم «${RemoteProtocol.SERVICE_NAME}». أي جوال على نفس الشبكة يقدر يتحكم، فاستخدم هوتسبوت جوالك.", color = muted, fontSize = 13.sp)
        Text("النوافذ والسقف والستارة مسموحة من الجوال دائماً (ضغط مطوّل في التطبيق).", color = text, fontSize = 15.sp)
        Spacer(Modifier.padding(2.dp))
    }
    if (resetCode) AlertDialog(onDismissRequest = { resetCode = false }, title = { Text("تغيير رمز الربط؟") },
        text = { Text("ينقطع الربط السابق. أضف الرمز الجديد في جوالك لإعادة الاتصال.") },
        confirmButton = { TextButton(onClick = {
            code = CloudStore.reset(context).code; resetCode = false; showCode = false; RemoteHost.onProcess(context)
        }) { Text("تغيير الرمز") } },
        dismissButton = { TextButton(onClick = { resetCode = false }) { Text("إلغاء") } })
}
