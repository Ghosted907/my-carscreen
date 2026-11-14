package com.ghosted907.t1launcher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
        ClusterForceClose.recover(this)
        prefs = T1Prefs(this)
        car = CarBridge(this)
        car.start()
        // NaviStart-style startup: floating/multi buttons, hide recovery square.
        JctStartup.onProcess(this)
        // Phone remote: starts only if "التحكم من الجوال" was turned on.
        RemoteHost.onProcess(this)
    }
}

class MainActivity : ComponentActivity() {
    // NaviStart e0.b0 app language: the activity Configuration carries the chosen Locale.
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(JctLang.wrap(newBase))

    companion object { const val EXTRA_PAGE = "jct_page" }

    private var page by mutableStateOf(Page.Home)
    private var day by mutableStateOf(false)
    private var snap by mutableStateOf(CarBridge.Snap())
    private var toCluster by mutableStateOf(false)
    private var leavingForMainDisplay = false
    private var showWindows by mutableStateOf(false)
    private var overlayOk by mutableStateOf(false)
    private var freeform by mutableStateOf(false)
    private var showPermissionSetup by mutableStateOf(false)
    private var permissionRevision by mutableIntStateOf(0)
    private var availableApps by mutableStateOf<List<LaunchableApp>>(emptyList())
    private var pinnedApps by mutableStateOf<List<String>>(emptyList())
    private var recentApps by mutableStateOf<List<String>>(emptyList())
    private val homeBounds = mutableMapOf<String, Rect>()
    private val dockedBounds = mutableMapOf<String, Rect>()
    private val dockHandler = Handler(Looper.getMainLooper())
    private var dockScheduled = false
    private var lastDockAt = 0L
    private var homeScrolling = false
    private var expandedPackage: String? = null
    private val carListener: (CarBridge.Snap) -> Unit = { snap = it }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Toast.makeText(this, "جارٍ الاستماع…", Toast.LENGTH_SHORT).show()
            beginVoice(this) { handleVoice(it) }
        }
        else Toast.makeText(this, "الميكروفون مرفوض", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        val car = (application as T1App).car
        snap = car.snap
        car.onChange = carListener
        car.onSpeedClose = { Toast.makeText(this, "أغلق نظام السيارة كاميرا ٣٦٠", Toast.LENGTH_SHORT).show() }
        refreshEdge()
    }

    override fun onResume() {
        super.onResume()
        if (!ensureMainDisplay()) return
        permissionRevision++
        availableApps = AppIndex.launchable(this)
        pinnedApps = (application as T1App).prefs.pinnedApps
        recentApps = (application as T1App).prefs.recentApps
        refreshEdge()
        consumePageExtra(intent)
        if (page == Page.Home && freeform) {
            if (expandedPackage != null || SystemClock.elapsedRealtime() - lastDockAt > 1500L) {
                dockedBounds.clear()
                expandedPackage = null
                scheduleHomeDock()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePageExtra(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && page == Page.Home && freeform && !homeScrolling) {
            // Tapping the launcher brings its task in front of the two freeform tasks. Their
            // saved bounds are still correct, but the window order is not; request them again.
            dockedBounds.clear()
            scheduleHomeDock()
        }
    }

    /** RUN_START_APP_MENU action opens the app list. */
    private fun consumePageExtra(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_PAGE) == "back") {
            intent.removeExtra(EXTRA_PAGE)
            if (page != Page.Home || showWindows) goBack()
            return
        }
        val target = when (intent?.getStringExtra(EXTRA_PAGE)) {
            "home" -> Page.Home
            "apps" -> Page.Apps
            "cluster" -> Page.Cluster
            else -> return
        }
        navigate(target)
        intent.removeExtra(EXTRA_PAGE)
    }

    override fun onStop() {
        val car = (application as T1App).car
        if (car.onChange === carListener) {
            car.onChange = null
            car.onSpeedClose = null
        }
        super.onStop()
    }

    private fun refreshEdge() {
        overlayOk = Settings.canDrawOverlays(this)
        freeform = HomeAppWindows.freeformEnabled(this)
        JctLounchService.sync(this)
    }

    private fun openOverlaySettings() {
        try {
            LauncherDisplay.start(
                this,
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
        if (!ensureMainDisplay()) return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val app = application as T1App
        day = app.prefs.day
        toCluster = app.prefs.destinationCluster
        pinnedApps = app.prefs.pinnedApps
        recentApps = app.prefs.recentApps
        overlayOk = Settings.canDrawOverlays(this)
        freeform = HomeAppWindows.freeformEnabled(this)
        snap = app.car.snap
        showPermissionSetup = !permissionSetupShown(this)
        setContent {
            if (showPermissionSetup) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PermissionSetupPage(day, permissionRevision) {
                        markPermissionSetupShown(this@MainActivity)
                        showPermissionSetup = false
                    }
                }
            } else {
            BackHandler(enabled = page != Page.Home || showWindows) { goBack() }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                T1Shell(
                    page = page,
                    day = day,
                    snap = snap,
                    onPage = { if (it == Page.Camera) openCamera() else navigate(it) },
                    onMic = { voice() },
                    onOemAssistant = { OemAssistant.request(this@MainActivity) },
                    onDay = { day = !day; app.prefs.day = day },
                    onBack = { goBack() }
                ) {
                    androidx.compose.foundation.layout.Box {
                        when (page) {
                            Page.Home -> if (showWindows) {
                                WindowsPage(day) { showWindows = false }
                            } else {
                                HomePage(day, availableApps)
                            }
                            Page.Apps -> AppsPage(
                                day, toCluster, availableApps,
                                pinnedApps.toSet(), recentApps,
                                { setDest(it) },
                                { togglePin(it) }
                            ) { launch(it) }
                            Page.Cluster -> ClusterPage(
                                day, snap,
                                { classic() }, { hideAddons() }, { stock() }, { clearCluster() }
                            )
                            Page.Camera -> SimplePage("كاميرا ٣٦٠", "جارٍ فتح واجهة الكاميرات", day)
                            Page.Vehicle -> VehiclePage(day, snap, app.car)
                            Page.Controls -> ControlsPage(day, snap, { day = it; app.prefs.day = it }, { classic() }) {
                                quick("windows")
                            }
                            Page.Customize -> SimplePage(
                                "التخصيص",
                                "القالب الكلاسيكي هو الافتراضي. الإضافات S1–S10 مطفأة حتى تفعّلها، ولا تغطي مؤشرات الأمان.\nثيم مقروء: ${snap.theme ?: "--"}  الخط: ${snap.font ?: "--"}",
                                day,
                                "طبّق الكلاسيكي"
                            ) { classic() }
                            Page.Settings -> SettingsPage(
                                day = day,
                                overlayOk = overlayOk,
                                onDay = {
                                    day = !day
                                    app.prefs.day = day
                                },
                                onOverlaySettings = { openOverlaySettings() },
                                onRecheck = { refreshEdge() },
                                onPermissions = { showPermissionSetup = true }
                            )
                        }
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

    /** Display 0 only. A cluster instance relaunches there and closes. */
    private fun ensureMainDisplay(): Boolean {
        if (!LauncherDisplay.isOffMain(this)) {
            LauncherDisplay.noteOnMain()
            return true
        }
        if (leavingForMainDisplay) return false
        leavingForMainDisplay = true
        Log.i("T1Launcher", "launcher display ${display?.displayId} -> 0")
        try {
            LauncherDisplay.moveToMain(this)
        } catch (e: Exception) {
            Log.i("T1Launcher", "launcher display move failed ${e.javaClass.simpleName} ${e.message}")
        }
        finish()
        return false
    }

    private fun openNav(forceCluster: Boolean = false) {
        val apps = AppIndex.launchable(this)
        val target = AppIndex.find(apps, "com.google.android.apps.maps", "com.waze")
        if (target == null) {
            Toast.makeText(this, "Waze أو الخرائط غير مثبتة", Toast.LENGTH_SHORT).show()
            return
        }
        launch(target, forceCluster || toCluster)
    }

    /**
     * App tiles use JCarTools' launch path: getLaunchIntentForPackage + FLAG_ACTIVITY_NEW_TASK +
     * ActivityOptions.makeBasic().setLaunchDisplayId(displays[d]), d = head-unit or cluster index.
     */
    private fun launch(app: LaunchableApp, cluster: Boolean = toCluster) {
        val d = if (cluster) JctLaunch.priIndex(this) else JctLaunch.guIndex(this)
        if (JctLaunch.startOnDisplayIndex(this, app.packageName, d)) {
            if (cluster) (application as T1App).prefs.clusterPackage = app.packageName
            rememberLaunch(app.packageName)
        } else Toast.makeText(this, if (cluster) "تعذّر الفتح على لوحة العدادات" else "تعذّر الفتح", Toast.LENGTH_LONG).show()
    }

    private fun rememberLaunch(pkg: String) {
        val updated = (listOf(pkg) + recentApps.filterNot { it == pkg }).take(8)
        recentApps = updated
        (application as T1App).prefs.recentApps = updated
    }

    private fun onHomeDockMeasured(app: LaunchableApp, bounds: Rect) {
        if (page != Page.Home || !freeform) return
        if (bounds.width() < 220 || bounds.height() < 220) {
            // Slot mostly scrolled away: keep that window hidden behind T1, re-dock the others.
            homeBounds.remove(app.packageName)
            if (app.packageName in dockedBounds) hideHomeWindows()
            scheduleHomeDock()
            return
        }
        if (homeBounds[app.packageName] == bounds) return
        homeBounds[app.packageName] = Rect(bounds)
        scheduleHomeDock()
    }

    /**
     * Another app's freeform task cannot be moved every frame (each move is a new activity start and
     * some apps, e.g. YouTube, keep drawing at the old place). While the home page scrolls the windows
     * are hidden behind T1, and re-docked at the settled, clipped slot bounds when scrolling stops.
     */
    private fun onHomeScrolling(active: Boolean) {
        homeScrolling = active
        if (active) hideHomeWindows() else scheduleHomeDock()
    }

    private fun hideHomeWindows() {
        if (dockedBounds.isEmpty()) return
        dockedBounds.clear()
        revealLauncher()
    }

    private fun scheduleHomeDock() {
        if (dockScheduled || homeScrolling || page != Page.Home || !freeform) return
        dockScheduled = true
        dockHandler.postDelayed({
            dockScheduled = false
            if (homeScrolling || page != Page.Home || !freeform) return@postDelayed
            val targets = listOfNotNull(AppIndex.find(availableApps, "com.waze"))
            targets.forEach { app ->
                val bounds = homeBounds[app.packageName] ?: return@forEach
                if (dockedBounds[app.packageName] == bounds) return@forEach
                runCatching { HomeAppWindows.dock(this, app.component, bounds) }
                    .onSuccess { dockedBounds[app.packageName] = Rect(bounds); lastDockAt = SystemClock.elapsedRealtime() }
                    .onFailure { Log.i("T1Launcher", "home dock ${app.packageName}: ${it.javaClass.simpleName} ${it.message}") }
            }
        }, 300L)
    }

    private fun expandHomeApp(app: LaunchableApp) {
        expandedPackage = app.packageName
        runCatching { HomeAppWindows.fullscreen(this, app.component) }
            .onSuccess { rememberLaunch(app.packageName) }
            .onFailure {
                expandedPackage = null
                Toast.makeText(this, it.message ?: "تعذر تكبير التطبيق", Toast.LENGTH_LONG).show()
            }
    }

    private fun goBack() {
        if (showWindows) showWindows = false
        else if (page != Page.Home) navigate(Page.Home)
        else finish()
    }

    private fun navigate(target: Page) {
        val hadDock = dockedBounds.isNotEmpty()
        if (target != Page.Home) {
            dockedBounds.clear()
            homeBounds.clear()
        }
        showWindows = false
        page = target
        if (target != Page.Home && hadDock) revealLauncher()
    }

    private fun revealLauncher() {
        window.decorView.post {
            runCatching {
                LauncherDisplay.start(this, Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
    }

    private fun togglePin(app: LaunchableApp) {
        val updated = if (app.packageName in pinnedApps) pinnedApps.filterNot { it == app.packageName }
            else (pinnedApps + app.packageName).take(10)
        pinnedApps = updated
        (application as T1App).prefs.pinnedApps = updated
        Toast.makeText(this, if (app.packageName in updated) "ثُبّت ${app.label} في المفضلة" else "أُزيل ${app.label} من المفضلة", Toast.LENGTH_SHORT).show()
    }

    private fun quick(id: String) {
        val app = application as T1App
        when (id) {
            "avm" -> openCamera()
            "cluster" -> navigate(Page.Cluster)
            "vehicle" -> navigate(Page.Vehicle)
            "controls" -> navigate(Page.Controls)
            "apps" -> navigate(Page.Apps)
            "classic" -> classic()
            "hide" -> hideAddons()
            "stock" -> stock()
            "assistant" -> OemAssistant.request(this)
            "windows" -> { showWindows = true; page = Page.Home; if (dockedBounds.isNotEmpty()) revealLauncher() }
        }
    }

    private fun openCamera() {
        if (!(application as T1App).car.openAvm())
            Toast.makeText(this, "كاميرا ٣٦٠ غير متاحة على هذا الجهاز", Toast.LENGTH_LONG).show()
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

    private fun clearCluster() {
        android.app.AlertDialog.Builder(this)
            .setTitle("تطبيقات العدادات")
            .setMessage("الإغلاق القوي معطل افتراضياً ولا يفعّل مالك الجهاز.")
            .setNeutralButton("إغلاق قوي للتطبيق المحدد") { _, _ ->
                val pkg = T1Prefs(this).clusterPackage
                if (pkg !in JctLaunch.trackedPackages(this)) {
                    Toast.makeText(this, "لا يوجد تطبيق طرف ثالث متتبع محدد", Toast.LENGTH_LONG).show()
                } else ClusterForceClose.close(this, pkg) { message ->
                    android.app.AlertDialog.Builder(this).setMessage(message).setPositiveButton("حسناً", null).show()
                }
            }
            .setNegativeButton("إلغاء", null).show()
    }
    private fun voice() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "لا يوجد تعرّف صوت على هذا الجهاز", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            Toast.makeText(this, "جارٍ الاستماع…", Toast.LENGTH_SHORT).show()
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
