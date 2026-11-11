package com.ghosted907.t1launcher

import java.util.Locale

enum class SignalSource { CAR_INT, CAR_FLOAT, CLUSTER_INT, CLUSTER_FLOAT, VEHICLE_INT, VEHICLE_FLOAT }
enum class ReadingStatus(val label: String) {
    WAITING("بانتظار القراءة"), LIVE("مباشر"), STALE("توقف التحديث"),
    UNAVAILABLE("غير متاح"), INVALID("قيمة غير صالحة"), ERROR("تعذرت القراءة")
}
enum class SignalGroup(val label: String) {
    DRIVE("القيادة"), TIRES("الإطارات"), CLIMATE("التكييف"), BODY("الأبواب والنوافذ"),
    LIGHTS("الأنوار"), ENERGY("الوقود والمحرك"), SYSTEM("النظام"), RAW("فهرس النظام")
}

data class SignalDefinition(
    val key: String,
    val label: String,
    val group: SignalGroup,
    val source: SignalSource,
    val address: Int,
    val unit: String = "",
    val min: Double = 0.0,
    val max: Double = Int.MAX_VALUE.toDouble(),
    val decimals: Int = 0,
    val labels: Map<Int, String> = emptyMap(),
    val confirmed: Boolean = false,
    val periodMs: Long = 5000,
    val note: String = "معرّف من النظام الأصلي؛ المعنى والوحدة يحتاجان مطابقة على السيارة."
) {
    val staleAfterMs: Long get() = if (periodMs <= 500) 2000 else 15000
    val reference: String get() = when (source) {
        SignalSource.CAR_INT -> "IALCar · int · $address · area 0"
        SignalSource.CAR_FLOAT -> "IALCar · float · $address · area 0"
        SignalSource.CLUSTER_INT -> "CiService · int · $address"
        SignalSource.CLUSTER_FLOAT -> "CiService · float · $address"
        SignalSource.VEHICLE_INT, SignalSource.VEHICLE_FLOAT -> "VehicleService · key $address"
    }

    fun accepts(value: Double): Boolean = value.isFinite() && value != -1.0 && value != -127.0 &&
        value in min..max &&
        (source == SignalSource.CAR_FLOAT || source == SignalSource.CLUSTER_FLOAT || source == SignalSource.VEHICLE_FLOAT || value % 1.0 == 0.0) &&
        (labels.isEmpty() || labels.containsKey(value.toInt()))

    fun format(value: Double?): String {
        if (value == null) return "—"
        labels[value.toInt()]?.let { return it }
        return if (decimals == 0) value.toLong().toString() else String.format(Locale.US, "%.${decimals}f", value)
    }
}

data class SignalReading(
    val value: Double? = null,
    val sampledAtMs: Long? = null,
    val attemptedAtMs: Long? = null,
    val status: ReadingStatus = ReadingStatus.WAITING,
    val detail: String = ""
) {
    fun at(nowMs: Long, definition: SignalDefinition): SignalReading =
        if (status == ReadingStatus.LIVE && sampledAtMs != null && nowMs - sampledAtMs > definition.staleAfterMs)
            copy(status = ReadingStatus.STALE, detail = "لم تصل قراءة حديثة") else this

    // A retained last sample is never presented as a current reading.
    val current: Double? get() = value.takeIf { status == ReadingStatus.LIVE }
}

/** Pure state model. All times are monotonic, never wall clock or persisted values. */
class TelemetryStore {
    private val values = linkedMapOf<String, SignalReading>()

    @Synchronized fun record(definition: SignalDefinition, raw: Double?, nowMs: Long, error: String = "") {
        val previous = values[definition.key] ?: SignalReading()
        values[definition.key] = when {
            raw != null && definition.accepts(raw) -> SignalReading(raw, nowMs, nowMs, ReadingStatus.LIVE)
            else -> {
                val problem = when {
                    error.isNotEmpty() -> ReadingStatus.ERROR
                    raw == null || raw == -1.0 || raw == -127.0 -> ReadingStatus.UNAVAILABLE
                    else -> ReadingStatus.INVALID
                }
                previous.copy(attemptedAtMs = nowMs,
                    status = if (previous.value != null) ReadingStatus.STALE else problem,
                    detail = error.ifEmpty { problem.label })
            }
        }
    }

    @Synchronized fun disconnect(definitions: List<SignalDefinition>) {
        definitions.forEach { definition ->
            val old = values[definition.key] ?: SignalReading()
            values[definition.key] = old.copy(status = if (old.value == null) ReadingStatus.UNAVAILABLE else ReadingStatus.STALE,
                detail = "الخدمة غير متصلة")
        }
    }

    @Synchronized fun snapshot(definitions: List<SignalDefinition>, nowMs: Long): Map<String, SignalReading> =
        definitions.associate { it.key to (values[it.key] ?: SignalReading()).at(nowMs, it) }
}

data class DisplaySpeed(val key: String, val value: Double?, val unit: String)

object TelemetryPresentation {
    fun speed(readings: Map<String, SignalReading>): DisplaySpeed {
        readings["speed"]?.current?.let { return DisplaySpeed("speed", it, "كم/س") }
        readings["speed_cluster"]?.current?.let { return DisplaySpeed("speed_cluster", it, "وحدة العدادات") }
        return DisplaySpeed("speed", null, "كم/س")
    }
}

/** SDK catalogue metadata. Zoned/vector/string properties are listed but never read as scalar globals. */
data class OemProperty(val address: Int, val name: String, val type: String, val area: String, val readable: Boolean) {
    fun definition(): SignalDefinition? = if (!readable || area != "GLOBAL" || type !in setOf("INT32", "FLOAT")) null else SignalDefinition(
        "raw_$address", name, SignalGroup.RAW,
        if (type == "FLOAT") SignalSource.CAR_FLOAT else SignalSource.CAR_INT,
        address, "خام", min = -1.0e12, max = 1.0e12, decimals = if (type == "FLOAT") 2 else 0
    )
}

object OemCatalogParser {
    fun parse(lines: Sequence<String>): List<OemProperty> = lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val columns = line.split('\t')
        require(columns.size == 5) { "Invalid OEM catalog row" }
        OemProperty(columns[0].toInt(), columns[1], columns[2], columns[3], columns[4] == "read")
    }.toList()
}
