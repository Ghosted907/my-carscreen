package com.ghosted907.t1launcher

/**
 * Where an app sits on the 1920×720 cluster.
 * A new app has no saved id and opens on the right.
 */
object ClusterPlacement {
    enum class Spot(val id: String, val labelAr: String) {
        RIGHT("right", "يمين"),
        LEFT("left", "يسار"),
        CENTER("center", "وسط"),
        FULL("full", "ملء الشاشة"),
        CUSTOM("custom", "مخصص")
    }

    /** Choices shown on the main screen. Custom stays in the cluster editor. */
    val choices = listOf(Spot.RIGHT, Spot.LEFT, Spot.CENTER, Spot.FULL)

    fun fromId(id: String?): Spot = when (id) {
        Spot.LEFT.id -> Spot.LEFT
        Spot.CENTER.id -> Spot.CENTER
        Spot.FULL.id -> Spot.FULL
        Spot.CUSTOM.id -> Spot.CUSTOM
        else -> Spot.RIGHT
    }

    /**
     * Armed chip wins for the next launch. Otherwise the app's saved id is used.
     * Missing and unknown ids are Right.
     */
    fun resolve(savedId: String?, armed: Spot?, armedActive: Boolean): Spot {
        if (armedActive && armed != null) return armed
        return fromId(savedId)
    }

    fun bounds(
        spot: Spot,
        custom: ClusterPresets.Box? = null,
        width: Int = ClusterPresets.DESIGN_W,
        height: Int = ClusterPresets.DESIGN_H
    ): ClusterPresets.Box = when (spot) {
        Spot.RIGHT -> ClusterPresets.rightSide(width, height)
        Spot.LEFT -> ClusterPresets.leftSide(width, height)
        Spot.CENTER -> ClusterPresets.center(width, height)
        Spot.FULL -> ClusterPresets.full(width, height)
        Spot.CUSTOM -> custom ?: ClusterPresets.rightSide(width, height)
    }
}
