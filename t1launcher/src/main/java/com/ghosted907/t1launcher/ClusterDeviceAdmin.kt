package com.ghosted907.t1launcher

import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class ClusterDeviceAdmin : DeviceAdminReceiver()

object ClusterForceClose {
    // Deliberately no UI switch: enable in a later approved build, after informed owner setup.
    const val ENABLED = false
    private val worker = Executors.newSingleThreadExecutor()
    private const val JOURNAL = "cluster_restore_journal"
    private fun journal(context: Context) = context.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
    private fun allowed(context: Context, pkg: String): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
        ClusterMovePolicy.mayHide(pkg, context.packageName,
            info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            info.flags and ApplicationInfo.FLAG_PERSISTENT != 0)
    }.getOrDefault(false)

    fun close(context: Context, pkg: String, result: (String) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        worker.execute {
            val message = runCatching {
                if (pkg !in ClusterLaunch.trackedPackages(app) || !allowed(app, pkg)) return@runCatching "الإغلاق محظور لهذه الحزمة لحماية النظام وتطبيقات السيارة"
                val dpm = app.getSystemService(DevicePolicyManager::class.java)
                if (dpm == null || !dpm.isDeviceOwnerApp(app.packageName)) {
                    runCatching { app.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(pkg) }
                    return@runCatching "الإغلاق القوي يحتاج إعداد مالك الجهاز عبر adb بموافقتك أولاً. لم تتم الموافقة أو التفعيل، وإيقاف الخلفية لا يغلق نافذة ظاهرة."
                }
                if (!ENABLED) return@runCatching "الإغلاق القوي معطل افتراضياً في هذا الإصدار، حتى مع وجود مالك الجهاز"
                val admin = ComponentName(app, ClusterDeviceAdmin::class.java)
                if (dpm.isApplicationHidden(admin, pkg)) return@runCatching "التطبيق مخفي مسبقاً؛ لن نغير حالته"
                // Commit recovery intent BEFORE any hide. No successful journal means no hide.
                if (!journal(app).edit().putBoolean(pkg, true).commit()) return@runCatching "تعذر حفظ ضمان الاستعادة؛ لم يتم الإخفاء"
                val ok = HideRestoreCycle.run(
                    { dpm.setApplicationHidden(admin, pkg, true) },
                    { Thread.sleep(700) },
                    {
                        val restored = dpm.setApplicationHidden(admin, pkg, false) && !dpm.isApplicationHidden(admin, pkg)
                        if (restored) journal(app).edit().remove(pkg).commit()
                        restored
                    })
                if (ok) "أُغلق التطبيق وأُعيد إظهاره" else "تعذر تأكيد الإغلاق أو الاستعادة؛ طلب الاستعادة محفوظ لإعادة المحاولة"
            }.getOrElse { "تعذر الإغلاق؛ تُحفظ أي استعادة معلقة للمحاولة عند بدء اللانشر" }
            main.post { result(message) }
        }
    }

    /** Recovery runs even when ENABLED is false. Only packages journaled by us are restored. */
    fun recover(context: Context) {
        val app = context.applicationContext
        worker.execute {
            val dpm = app.getSystemService(DevicePolicyManager::class.java) ?: return@execute
            if (!runCatching { dpm.isDeviceOwnerApp(app.packageName) }.getOrDefault(false)) return@execute
            val admin = ComponentName(app, ClusterDeviceAdmin::class.java)
            journal(app).all.keys.filter { allowed(app, it) }.forEach { pkg ->
                val restored = runCatching {
                    dpm.setApplicationHidden(admin, pkg, false) && !dpm.isApplicationHidden(admin, pkg)
                }.getOrDefault(false)
                if (restored) journal(app).edit().remove(pkg).commit()
            }
        }
    }
}