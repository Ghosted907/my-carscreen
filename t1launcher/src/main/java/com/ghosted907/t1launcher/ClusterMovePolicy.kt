package com.ghosted907.t1launcher

/** Pure decisions, deliberately independent of Android for local JVM tests. */
object ClusterMovePolicy {
    const val REUSE_FLAGS = 0x10000000 or 0x00020000 or 0x00200000
    data class Panel(val id: Int, val width: Int, val height: Int)
    fun main(panels: List<Panel>): Int? = panels.firstOrNull { it.id == 0 }?.id
    // Never send apps to an arbitrary secondary panel or assume array index == display ID.
    fun cluster(panels: List<Panel>): Int? = panels.filter {
        it.id > 0 && it.width >= 1600 && it.height in 640..800
    }.singleOrNull()?.id
    fun mayMoveToMain(pkg: String, own: String): Boolean =
        pkg.isNotBlank() && pkg != own && !pkg.startsWith("com.ghosted907.cluster")
    fun mayHide(pkg: String, own: String, system: Boolean, updatedSystem: Boolean, persistent: Boolean): Boolean =
        mayMoveToMain(pkg, own) && !system && !updatedSystem && !persistent &&
            deniedPrefixes.none { pkg == it || pkg.startsWith("$it.") }
    private val deniedPrefixes = listOf("android", "com.android", "com.google.android.car",
        "com.autolink", "com.desaysv", "com.jetour", "com.chery", "com.mega", "mega",
        "com.iflytek", "com.flyaudio", "com.harman", "com.bosch", "com.example.navistart")
}

/** Restore is attempted even if hide or the delay throws; false is not success. */
object HideRestoreCycle {
    fun run(hide: () -> Boolean, pause: () -> Unit, restore: () -> Boolean): Boolean {
        var hidden = false
        var restored = false
        try {
            hidden = hide()
            if (hidden) pause()
        } finally {
            for (attempt in 1..3) {
                restored = runCatching { restore() }.getOrDefault(false)
                if (restored) break
            }
        }
        return hidden && restored
    }
}