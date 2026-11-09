package com.ghosted907.t1launcher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

class T1App : android.app.Application() {
    lateinit var car: CarBridge
    lateinit var prefs: T1Prefs
    override fun onCreate() {
        super.onCreate()
        prefs = T1Prefs(this)
        car = CarBridge(this)
        car.start()
    }
}

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf(Page.Home)
    private var day by mutableStateOf(false)
    private var snap by mutableStateOf(CarBridge.Snap())
    private var toCluster by mutableStateOf(false)
    private var showWindows by mutableStateOf(false)
    private var edgeOn by mutableStateOf(true)
    private var edgeRight by mutableStateOf(true)
    private var overlayOk by mutableStateOf(false)
    private var freeform by mutableStateOf(false)
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoice(this) { handleVoice(it) }
        else Toast.makeText(this, "الميكروفون مرفوض", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        EdgeSwipeService.watch = false
        refreshEdge()
    }

    override fun onResume() {
        super.onResume()
        EdgeSwipeService.watch = false
        refreshEdge()
    }

    override fun onPause() {
        EdgeSwipeService.watch = true
        EdgeSwipeService.sync(this)
        super.onPause()
    }

    private fun refreshEdge() {
        overlayOk = Settings.canDrawOverlays(this)
        freeform = ClusterLaunch.freeformEnabled(this)
        EdgeSwipeService.sync(this)
        ClusterOverlayService.sync(this)
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "adb shell appops set com.ghosted907.t1launcher SYSTEM_ALERT_WINDOW allow",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val app = application as T1App
        day = app.prefs.day
        toCluster = app.prefs.destinationCluster
        edgeOn = app.prefs.edgeEnabled
        edgeRight = app.prefs.edgeRight
        overlayOk = Settings.canDrawOverlays(this)
        freeform = ClusterLaunch.freeformEnabled(this)
        EdgeSwipeService.watch = false
        app.car.onChange = { snap = it }
        app.car.onSpeedClose = {
            Toast.makeText(this, "أُغلقت كاميرا ٣٦٠ بسبب السرعة", Toast.LENGTH_LONG).show()
        }
        snap = app.car.snap
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                T1Shell(
                    page = page,
                    day = day,
                    snap = snap,
                    onPage = { page = it },
                    onMic = { voice() },
                    onOemAssistant = { OemAssistant.request(this@MainActivity) }
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        when (page) {
                            Page.Home -> if (showWindows) {
                                WindowsPage(day) { showWindows = false }
                            } else {
                                HomePage(day, snap, AppIndex.favorites(AppIndex.launchable(this@MainActivity)), toCluster, { setDest(it) }, { openNav() }, { quick(it) }, { launch(it) })
                            }
                            Page.Apps -> AppsPage(day, toCluster, { setDest(it) }) { launch(it) }
                            Page.Cluster -> ClusterPage(
                                day, snap, app.prefs, freeform,
                                { classic() }, { hideAddons() }, { stock() }, { openNav(true) },
                                { applyCluster(false) }, { clearCluster() }
                            )
                            Page.Camera -> SimplePage(
                                "كاميرا ٣٦٠",
                                "الزر يرسل نفس أمر الشريط الأصلي: startService AvmService مع key_type=1. لا نعيد فتحها إذا أغلقتها السيارة بسبب السرعة.\nالحالة: ${if (snap.avmOpen == true) "مفتوحة" else if (snap.avmOpen == false) "مقفلة" else "غير معروفة"}",
                                day,
                                "كاميرا ٣٦٠"
                            ) { app.car.openAvm() }
                            Page.Vehicle -> SimplePage(
                                "بيانات السيارة",
                                "الترس ${app.car.gearLetter(snap.gear).ifBlank { "--" }}   السرعة ${snap.speed?.toInt() ?: "--"}   الدوران ${snap.rpm?.toInt() ?: "--"}\nالمدى ${snap.rangeKm ?: "--"} كم   الوقود ${snap.fuel ?: "--"}%   الوضع ${app.car.modeLabel(snap.driveMode)}   الحرارة ${snap.temp?.toInt() ?: "--"}°\nالقراءة فقط. القيم الناقصة تظهر --.",
                                day
                            )
                            Page.Customize -> SimplePage(
                                "التخصيص",
                                "القالب الكلاسيكي هو الافتراضي. الإضافات S1–S10 مطفأة حتى تفعّلها، ولا تغطي مؤشرات الأمان.\nثيم مقروء: ${snap.theme ?: "--"}  الخط: ${snap.font ?: "--"}",
                                day,
                                "طبّق الكلاسيكي"
                            ) { classic() }
                            Page.Settings -> SettingsPage(
                                day = day,
                                edgeOn = edgeOn,
                                edgeRight = edgeRight,
                                overlayOk = overlayOk,
                                onDay = {
                                    day = !day
                                    app.prefs.day = day
                                },
                                onEdge = { on ->
                                    edgeOn = on
                                    app.prefs.edgeEnabled = on
                                    EdgeSwipeService.sync(this@MainActivity)
                                },
                                onRight = { right ->
                                    edgeRight = right
                                    app.prefs.edgeRight = right
                                    EdgeSwipeService.sync(this@MainActivity)
                                },
                                onOverlaySettings = { openOverlaySettings() },
                                onRecheck = { refreshEdge() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setDest(cluster: Boolean) {
        toCluster = cluster
        (application as T1App).prefs.destinationCluster = cluster
    }

    private fun openNav(forceCluster: Boolean = false) {
        val apps = AppIndex.launchable(this)
        val target = AppIndex.find(apps, "com.waze", "com.google.android.apps.maps")
        if (target == null) {
            Toast.makeText(this, "Waze أو الخرائط غير مثبتة", Toast.LENGTH_SHORT).show()
            return
        }
        launch(target, forceCluster || toCluster)
    }

    private fun launch(app: LaunchableApp, cluster: Boolean = toCluster) {
        try {
            AppIndex.launch(this, app, cluster, if (cluster) (application as T1App).prefs.bounds() else null)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "تعذّر الفتح", Toast.LENGTH_LONG).show()
        }
    }

    private fun quick(id: String) {
        val app = application as T1App
        when (id) {
            "avm" -> app.car.openAvm()
            "cluster" -> page = Page.Cluster
            "classic" -> classic()
            "hide" -> hideAddons()
            "stock" -> stock()
            "assistant" -> OemAssistant.request(this)
            "windows" -> showWindows = true
        }
    }

    private fun classic() {
        val app = application as T1App
        app.prefs.savedTheme = app.car.snap.theme ?: app.prefs.savedTheme
        app.prefs.savedFont = app.car.snap.font ?: app.prefs.savedFont
        app.car.applyClassic()
        app.car.applyFont(1)
        Toast.makeText(this, "طُلب قالب كلاسيكي", Toast.LENGTH_SHORT).show()
    }

    private fun hideAddons() {
        val app = application as T1App
        app.prefs.hideAllSlots()
        ClusterAddonActivity.finishIfRunning()
        Toast.makeText(this, "أُخفيت الإضافات", Toast.LENGTH_SHORT).show()
    }

    private fun stock() {
        hideAddons()
        Toast.makeText(this, "أُغلقت نافذة T1 على العدادات", Toast.LENGTH_SHORT).show()
    }

    private fun applyCluster(quiet: Boolean) {
        val prefs = (application as T1App).prefs
        val component = prefs.clusterComponent()
        if (component == null) {
            if (!quiet) Toast.makeText(this, "افتح تطبيقاً على العدادات أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            ClusterLaunch.launch(this, component, prefs.bounds())
            if (!quiet) Toast.makeText(this, "طُبّق القياس على العدادات", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "تعذّر تغيير القياس", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCluster() {
        try {
            ClusterLaunch.clear(this)
            Toast.makeText(this, "أُزيل التطبيق عن العدادات", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "تعذّرت الإزالة", Toast.LENGTH_LONG).show()
        }
    }

    private fun voice() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "لا يوجد تعرّف صوت على هذا الجهاز", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            beginVoice(this) { handleVoice(it) }
        }
    }

    private fun handleVoice(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        when {
            text.contains("٣٦٠") || text.contains("360") || text.contains("كاميرا") -> (application as T1App).car.openAvm()
            text.contains("كلاسيك") -> classic()
            text.contains("إخفاء") -> hideAddons()
            text.contains("يوتيوب") || text.contains("YouTube") -> {
                val yt = AppIndex.find(AppIndex.launchable(this), "com.google.android.youtube")
                if (yt != null) launch(yt, text.contains("عداد"))
            }
            text.contains("ويز") || text.contains("Waze") -> openNav(text.contains("عداد"))
        }
    }

    fun enableHome(on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "com.ghosted907.t1launcher.HomeAlias"),
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}
