package com.ghosted907.t1launcher

/**
 * Port of JCarTools / NaviStart 1.4.1 action model (`j0.d`, `j0.e`, `j0.f`).
 * One action string is stored per trigger key, exactly like NaviStart:
 *   "enum:<NAME>"                 built-in action (n0.d)
 *   "app:<package>"               launch app (getLaunchIntentForPackage + setLaunchDisplayId)
 *   "split:<a>,<b>,<orient>,<ratio>" split pair (parsed and stored only, see JctLaunch)
 *   "profile:<id>"                task group / profile
 * Unknown or empty strings fall back to "enum:Not_value".
 */
enum class JctType { ENUM, APP, SPLIT, CUSTOM_CMD }

/**
 * Subset of NaviStart `n0.d` built-in actions whose behaviour is confirmed in the
 * recovered Java (`e0.z.e`, `a7.g` case 14) and can run inside this launcher.
 * [code] is NaviStart's own action code.
 */
enum class JctEnum(val code: Int, val labelAr: String, val labelEn: String) {
    Not_value(0, "غير محدد", "Not specified"),
    // 1.11.0: NaviStart n0.d display-routing actions (codes 1, 6, 9) and black screen (2).
    GO_TO_PP(1, "عرض التطبيق على لوحة العدادات", "Show app on instrument cluster"),
    RUN_BLACK(2, "شاشة سوداء", "Black screen"),
    GO_TO_GU(6, "عرض التطبيق على وحدة الرأس", "Show app on head unit"),
    TOGGLE_GU_PP(9, "تبديل التطبيق بين وحدة الرأس/لوحة العدادات", "Switch app between head unit and cluster"),
    RUN_START_APP_MENU(7, "قائمة تشغيل التطبيقات المثبتة", "Launch installed apps menu"),
    GLOBAL_BACK(8, "رجوع", "Global back button"),
    GLOBAL_HOME(18, "الشاشة الرئيسية", "Global Home button"),
    MEDIA_PLAY(14, "تشغيل الوسائط", "Media play"),
    MEDIA_PAUSE(15, "إيقاف الوسائط مؤقتًا", "Media pause"),
    MEDIA_NEXT(16, "المسار التالي", "Media next track"),
    MEDIA_BLACK(17, "المسار السابق", "Media previous track"),
    // 1.11.0: NaviStart n0.d codes 74 and 75.
    RECENT_TASK_MANAGER(74, "مدير التطبيقات الأخيرة", "Recent apps manager"),
    MEDIA_PLAY_PAUSE(75, "تشغيل/إيقاف الوسائط مؤقتًا", "Media play/pause");

    fun label(english: Boolean) = if (english) labelEn else labelAr

    companion object {
        fun byName(name: String?): JctEnum? = values().firstOrNull { it.name == name }
            ?: values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

data class JctAction(
    val type: JctType,
    val enumValue: JctEnum? = null,
    val pkg: String? = null,
    val pkg2: String? = null,
    val orientation: Int = 1,
    val ratio: Int = 50,
    val profile: String? = null
) {
    companion object {
        val NOT_VALUE = JctAction(JctType.ENUM, JctEnum.Not_value)
    }
}

object JctActions {
    const val ENUM = "enum:"
    const val APP = "app:"
    const val SPLIT = "split:"
    const val PROFILE = "profile:"
    const val NOT_VALUE = "enum:Not_value"

    /** j0.f.b */
    fun parse(raw: String?): JctAction {
        if (raw == null) return JctAction.NOT_VALUE
        val s = raw.trim()
        return when {
            s.startsWith(ENUM) -> enumOf(s.substring(ENUM.length))
            s.startsWith(APP) -> s.substring(APP.length).trim().let {
                if (it.isEmpty()) JctAction.NOT_VALUE else JctAction(JctType.APP, pkg = it)
            }
            s.startsWith(SPLIT) -> split(s.substring(SPLIT.length))
            s.startsWith(PROFILE) -> s.substring(PROFILE.length).trim().let {
                if (it.isEmpty()) JctAction.NOT_VALUE else JctAction(JctType.CUSTOM_CMD, profile = it)
            }
            else -> enumOf(s)
        }
    }

    private fun enumOf(name: String): JctAction =
        JctAction(JctType.ENUM, JctEnum.byName(name) ?: JctEnum.Not_value)

    /** j0.f.c / j0.e.e: orientation 2 or 1, ratio 30/70 else 50. */
    private fun split(body: String): JctAction {
        val parts = body.split(",")
        if (parts.size < 3) return JctAction.NOT_VALUE
        val a = parts[0].trim(); val b = parts[1].trim()
        if (a.isEmpty() || b.isEmpty()) return JctAction.NOT_VALUE
        val orient = parts[2].trim().toIntOrNull() ?: return JctAction.NOT_VALUE
        val ratio = if (parts.size >= 4) parts[3].trim().toIntOrNull() ?: return JctAction.NOT_VALUE else 50
        return JctAction(JctType.SPLIT, pkg = a, pkg2 = b,
            orientation = if (orient != 2) 1 else 2, ratio = if (ratio != 30 && ratio != 70) 50 else ratio)
    }

    /** j0.f.f */
    fun encode(action: JctAction?): String {
        if (action == null) return NOT_VALUE
        return when (action.type) {
            JctType.ENUM -> action.enumValue?.let { ENUM + it.name } ?: NOT_VALUE
            JctType.APP -> action.pkg?.let { APP + it } ?: NOT_VALUE
            JctType.SPLIT -> if (action.pkg != null && action.pkg2 != null) {
                val o = if (action.orientation != 2) 1 else 2
                val r = if (action.ratio != 30 && action.ratio != 70) 50 else action.ratio
                "$SPLIT${action.pkg},${action.pkg2},$o,$r"
            } else NOT_VALUE
            JctType.CUSTOM_CMD -> action.profile?.let { PROFILE + it } ?: NOT_VALUE
        }
    }

    /** j0.f.d: is the action runnable/assigned. */
    fun valid(action: JctAction?): Boolean = when (action?.type) {
        null -> false
        JctType.APP -> action.pkg != null
        JctType.SPLIT -> action.pkg != null && action.pkg2 != null
        JctType.CUSTOM_CMD -> action.profile != null
        JctType.ENUM -> action.enumValue != null && action.enumValue != JctEnum.Not_value
    }
}

/**
 * Split-pair geometry for "split:<a>,<b>,<orient>,<ratio>" (fragment_apps2.xml: horizontal/vertical,
 * 30:70 / 50:50 / 70:30). orientation 1 = side by side, 2 = stacked; ratio = first app's share.
 * Returns the two window rects as [x, y, w, h].
 */
object JctSplit {
    fun bounds(width: Int, height: Int, orientation: Int, ratio: Int): Pair<JctZone.Rect, JctZone.Rect> {
        val r = if (ratio != 30 && ratio != 70) 50 else ratio
        return if (orientation == 2) {
            val h1 = height * r / 100
            JctZone.Rect(0, 0, width, h1) to JctZone.Rect(0, h1, width, height - h1)
        } else {
            val w1 = width * r / 100
            JctZone.Rect(0, 0, w1, height) to JctZone.Rect(w1, 0, width - w1, height)
        }
    }
}

/** NaviStart six edge zones (o0.f) and their geometry (a7.g case 17). */
enum class JctZone(val labelAr: String, val labelEn: String) {
    LEFT_TOP("يسار أعلى", "Left top"), LEFT_BOTTOM("يسار أسفل", "Left bottom"), RIGHT_TOP("يمين أعلى", "Right top"),
    RIGHT_BOTTOM("يمين أسفل", "Right bottom"), BOTTOM_LEFT("أسفل يسار", "Bottom left"), BOTTOM_RIGHT("أسفل يمين", "Bottom right");

    fun label(english: Boolean) = if (english) labelEn else labelAr

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    companion object {
        const val THICKNESS_DP = 32f
        const val THRESHOLD_PX = 90f
        const val MAX_PULL_PX = 500f

        fun thickness(density: Float): Int = maxOf(1, Math.round(THICKNESS_DP * density))

        /** Exactly the arithmetic of a7.g case 17; null when NaviStart would skip the zone. */
        fun geometry(zone: JctZone, width: Int, height: Int, t: Int): Rect? {
            val half = height / 2
            val bottomY = height - t
            val inner = width - t * 2
            val leftW = inner / 2
            val rightW = inner - leftW
            return when (zone) {
                LEFT_TOP -> Rect(0, 0, t, half)
                LEFT_BOTTOM -> Rect(0, half, t, half)
                RIGHT_TOP -> Rect(width - t, 0, t, half)
                RIGHT_BOTTOM -> Rect(width - t, half, t, half)
                BOTTOM_LEFT -> if (inner > 0) Rect(t, bottomY, leftW, t) else null
                BOTTOM_RIGHT -> if (inner > 0) Rect(t + leftW, bottomY, rightW, t) else null
            }?.takeIf { it.w > 0 && it.h > 0 }
        }

        /** o0.b touch listener: distance capped at 500, fires above 90 px. */
        fun fires(dx: Float, dy: Float): Boolean =
            minOf(Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat(), MAX_PULL_PX) > THRESHOLD_PX
    }
}
