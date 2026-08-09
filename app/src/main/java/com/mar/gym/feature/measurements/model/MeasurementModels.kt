package com.mar.gym.feature.measurements.model

import com.mar.gym.core.network.VersionedDocument
import java.math.BigDecimal
import java.time.Instant

enum class BodyMeasurementType(val apiValue: String, val unit: BodyMeasurementUnit) {
    BodyWeight("BODY_WEIGHT", BodyMeasurementUnit.Kg),
    BodyFatPercentage("BODY_FAT_PERCENTAGE", BodyMeasurementUnit.Percent),
    Chest("CHEST", BodyMeasurementUnit.Cm),
    Waist("WAIST", BodyMeasurementUnit.Cm),
    Hips("HIPS", BodyMeasurementUnit.Cm),
    LeftBiceps("LEFT_BICEPS", BodyMeasurementUnit.Cm),
    RightBiceps("RIGHT_BICEPS", BodyMeasurementUnit.Cm),
    LeftThigh("LEFT_THIGH", BodyMeasurementUnit.Cm),
    RightThigh("RIGHT_THIGH", BodyMeasurementUnit.Cm),
    LeftCalf("LEFT_CALF", BodyMeasurementUnit.Cm),
    RightCalf("RIGHT_CALF", BodyMeasurementUnit.Cm),
    Neck("NECK", BodyMeasurementUnit.Cm);

    companion object { fun fromApiValue(value: String) = entries.find { it.apiValue == value } }
}

enum class BodyMeasurementUnit(val apiValue: String, val symbol: String) {
    Kg("KG", "kg"), Percent("PERCENT", "%"), Cm("CM", "cm");
    companion object { fun fromApiValue(value: String) = entries.find { it.apiValue == value } }
}

data class BodyMeasurement(
    val id: String,
    val type: BodyMeasurementType,
    val value: BigDecimal,
    val unit: BodyMeasurementUnit,
    val measuredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)

typealias BodyMeasurementDocument = VersionedDocument<BodyMeasurement>

data class BodyMeasurementPage(
    val content: List<BodyMeasurement>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class BodyMeasurementDraft(
    val type: BodyMeasurementType,
    val value: String,
    val measuredAt: Instant,
)

fun BodyMeasurementDraft.validate(now: Instant): Map<String, String> = buildMap {
    val decimal = value.toBigDecimalOrNull()
    if (decimal == null || decimal.scale().coerceAtLeast(0) > 3 || decimal <= BigDecimal.ZERO ||
        decimal > if (type == BodyMeasurementType.BodyFatPercentage) BigDecimal("100") else BigDecimal("500")) {
        put("value", "Introduce un valor válido, positivo y con hasta tres decimales.")
    }
    if (measuredAt > now) put("measuredAt", "La medición no puede estar en el futuro.")
}
