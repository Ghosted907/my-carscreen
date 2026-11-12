package com.ghosted907.t1launcher

/** Saved launcher shortcuts. Prefixes remain readable for settings saved by earlier releases. */
enum class JctType { ENUM, APP, SPLIT, CUSTOM_CMD }

/** Numeric display codes are kept for interoperability; labels describe this launcher's actions. */
enum class JctEnum(val code: Int, val labelAr: String, val labelEn: String) {
    Not_value(0, "غير محدد", "Not specified"),
    GO_TO_PP(1, "افتح التطبيق المختار على العدادات", "Open chosen app on cluster"),
    RUN_BLACK(2, "وضع الشاشة الداكنة", "Dark screen mode"),
    GO_TO_GU(6, "افتح التطبيق المختار على الشاشة الرئيسية", "Open chosen app on main screen"),
    TOGGLE_GU_PP(9, "انقل التطبيق المختار للشاشة الأخرى", "Move chosen app to other screen"),
    RUN_START_APP_MENU(7, "قائمة تشغيل التطبيقات المثبتة", "Launch installed apps menu"),
    GLOBAL_BACK(8, "رجوع", "Global back button"),
    GLOBAL_HOME(18, "الشاشة الرئيسية", "Global Home button"),
    MEDIA_PLAY(14, "تشغيل الوسائط", "Media play"),
    MEDIA_PAUSE(15, "إيقاف الوسائط مؤقتًا", "Media pause"),
    MEDIA_NEXT(16, "المسار التالي", "Media next track"),
    MEDIA_BLACK(17, "المسار السابق", "Media previous track"),
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

    fun valid(action: JctAction?): Boolean = when (action?.type) {
        null -> false
        JctType.APP -> action.pkg != null
        JctType.SPLIT -> action.pkg != null && action.pkg2 != null
        JctType.CUSTOM_CMD -> action.profile != null
        JctType.ENUM -> action.enumValue != null && action.enumValue != JctEnum.Not_value
    }
}

/**
 * Split-pair geometry. Orientation 1 = side by side, 2 = stacked; ratio = first app's share.
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

/** Six launcher edge shortcuts, arranged around the usable display boundary. */
enum class JctZone(val labelAr: String, val labelEn: String) {
    LEFT_TOP("يسار أعلى", "Left top"), LEFT_BOTTOM("يسار أسفل", "Left bottom"), RIGHT_TOP("يمين أعلى", "Right top"),
    RIGHT_BOTTOM("يمين أسفل", "Right bottom"), BOTTOM_LEFT("أسفل يسار", "Bottom left"), BOTTOM_RIGHT("أسفل يمين", "Bottom right");

    fun label(english: Boolean) = if (english) labelEn else labelAr

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    companion object {
        const val THICKNESS_DP = 24f
        const val THRESHOLD_DP = 72f

        fun thickness(density: Float): Int = maxOf(1, Math.round(THICKNESS_DP * density))

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

        /** A deliberate inward stroke avoids accidental taps and outward system gestures. */
        fun fires(zone: JctZone, dx: Float, dy: Float, threshold: Float): Boolean = when (zone) {
            LEFT_TOP, LEFT_BOTTOM -> dx >= threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)
            RIGHT_TOP, RIGHT_BOTTOM -> -dx >= threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy)
            BOTTOM_LEFT, BOTTOM_RIGHT -> -dy >= threshold && kotlin.math.abs(dy) > kotlin.math.abs(dx)
        }
    }
}
