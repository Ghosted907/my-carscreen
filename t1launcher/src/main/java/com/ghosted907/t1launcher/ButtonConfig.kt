package com.ghosted907.t1launcher

/** Normalized physical coordinates; independent of Android RTL and screen density. */
data class ButtonConfig(val id: String, val target: String, val label: String,
                        val x: Float, val y: Float, val enabled: Boolean = true) {
    val fromRight: Boolean get() = x >= .5f
    fun valid(): Boolean = id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) &&
        (target == LAUNCHER || target.matches(Regex("[a-zA-Z][a-zA-Z0-9_.]+(/[a-zA-Z0-9_.$]+)?"))) &&
        label.length in 1..80 && x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f
    companion object {
        const val LAUNCHER = "__launcher__"
        const val MAX_BUTTONS = 16
        fun defaults() = listOf(
            ButtonConfig("launcher", LAUNCHER, "فتح T1", 1f, .64f),
            ButtonConfig("youtube", "app.revanced.android.youtube", "يوتيوب", 0f, .64f))
        fun validate(all: List<ButtonConfig>): Boolean = all.size <= MAX_BUTTONS &&
            all.all { it.valid() } && all.map { it.id }.distinct().size == all.size
        fun preset(name: String): Pair<Float, Float> = when (name) {
            "يمين فوق" -> 1f to .2f
            "يمين تحت" -> 1f to .8f
            "يسار فوق" -> 0f to .2f
            "يسار تحت" -> 0f to .8f
            else -> 0f to .64f
        }
    }
}
