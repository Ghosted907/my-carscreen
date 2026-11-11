package com.ghosted907.t1launcher

object OffRoadPresentation {
    // VehicleService pressure keys return bar (captured 2501 callback values ~2.4).
    fun psi(bar: Double?): Double? = bar?.takeIf { it.isFinite() && it in 0.1..6.0 }?.times(14.5037738)
    fun compassLabel(heading: Double?): String {
        if (heading == null || !heading.isFinite()) return "البوصلة"
        return listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((heading % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
    }
}
