package com.ghosted907.t1launcher

/** Audited allow-list only: VehicleService keys are NOT raw alcar_service addresses.
 * OEM MapperVehicleSkyLight/SunShade -> ALCarManager -> HAL property.
 * A command is sent only by an explicit press, never by startup, polling, or a test.
 */
object VehicleCommands {
    data class Command(val id: String, val label: String, val transaction: Int, val args: List<Int>)
    val all = listOf(
        Command("windows_open", "النوافذ · فتح", 12, listOf(2, 2, 2, 2)),
        Command("windows_close", "النوافذ · إغلاق", 12, listOf(1, 1, 1, 1)),
        Command("windows_vent", "النوافذ · تهوية", 12, listOf(3, 0, 0, 3)),
        Command("roof_open", "السقف · فتح", 1, listOf(557843525, 0, 11)),
        Command("roof_close", "السقف · إغلاق", 1, listOf(557843525, 0, 1)),
        Command("roof_tilt", "السقف · رفع", 1, listOf(557843525, 0, 12)),
        Command("shade_open", "الستارة · فتح", 1, listOf(557843527, 0, 11)),
        Command("shade_close", "الستارة · إغلاق", 1, listOf(557843527, 0, 1)),
        Command("shade_half", "الستارة · نصف", 1, listOf(557843527, 0, 6))
    )
    fun stationary(speed: Double?, gear: Double?): Boolean =
        speed != null && speed.isFinite() && speed == 0.0 && (gear == null || gear == 1.0)
    fun tailgateAllowed(speed: Double?, gear: Double?, heldMs: Long, commandVerified: Boolean) =
        commandVerified && heldMs >= 2000L && stationary(speed, gear)
}
