package com.mar.gym.core.units

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class WeightUnit(val apiValue: String, val symbol: String) {
    KG("KG", "kg"),
    LB("LB", "lb");

    companion object {
        fun fromApiValue(value: String?): WeightUnit = entries.find { it.apiValue == value } ?: KG
    }
}

enum class DistanceUnit(val apiValue: String, val symbol: String) {
    KM("KM", "km"),
    MI("MI", "mi");

    companion object {
        fun fromApiValue(value: String?): DistanceUnit = entries.find { it.apiValue == value } ?: KM
    }
}

data class UnitPreferences(
    val weight: WeightUnit = WeightUnit.KG,
    val distance: DistanceUnit = DistanceUnit.KM,
)

object UnitConverter {
    val kilogramsPerPoundFactor = BigDecimal("2.2046226218")
    val metersPerKilometer = BigDecimal("1000")
    val metersPerMile = BigDecimal("1609.344")

    fun weightToDisplay(kilograms: BigDecimal, unit: WeightUnit): BigDecimal = when (unit) {
        WeightUnit.KG -> kilograms
        WeightUnit.LB -> kilograms.multiply(kilogramsPerPoundFactor)
    }

    fun weightToCanonical(value: BigDecimal, unit: WeightUnit): BigDecimal = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value.divide(kilogramsPerPoundFactor, MathContext.DECIMAL128)
    }

    fun distanceToDisplay(meters: BigDecimal, unit: DistanceUnit): BigDecimal = when (unit) {
        DistanceUnit.KM -> meters.divide(metersPerKilometer, MathContext.DECIMAL128)
        DistanceUnit.MI -> meters.divide(metersPerMile, MathContext.DECIMAL128)
    }

    fun distanceToCanonical(value: BigDecimal, unit: DistanceUnit): BigDecimal = when (unit) {
        DistanceUnit.KM -> value.multiply(metersPerKilometer)
        DistanceUnit.MI -> value.multiply(metersPerMile)
    }

    fun weightInput(kilograms: BigDecimal, unit: WeightUnit): String =
        decimal(weightToDisplay(kilograms, unit), WEIGHT_SCALE)

    fun distanceInput(meters: BigDecimal, unit: DistanceUnit): String =
        decimal(distanceToDisplay(meters, unit), DISTANCE_SCALE)

    fun formatWeight(kilograms: BigDecimal, unit: WeightUnit): String =
        "${weightInput(kilograms, unit)} ${unit.symbol}"

    fun formatDistance(meters: BigDecimal, unit: DistanceUnit): String =
        "${distanceInput(meters, unit)} ${unit.symbol}"

    fun formatVolume(totalVolumeKg: BigDecimal, unit: WeightUnit): String =
        formatWeight(totalVolumeKg, unit)

    private fun decimal(value: BigDecimal, scale: Int): String = value
        .setScale(scale, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

    private const val WEIGHT_SCALE = 1
    private const val DISTANCE_SCALE = 2
}

data class EditableWeightState(
    val originalKilograms: BigDecimal?,
    val displayUnit: WeightUnit,
    val dirty: Boolean,
) {
    fun canonicalOrNull(text: String): BigDecimal? = if (!dirty) originalKilograms else {
        text.toBigDecimalOrNull()?.let { UnitConverter.weightToCanonical(it, displayUnit) }
    }

    fun edited() = copy(dirty = true)

    fun rebase(text: String, newUnit: WeightUnit): Pair<EditableWeightState, String> {
        if (newUnit == displayUnit) return this to text
        val canonical = canonicalOrNull(text)
        return EditableWeightState(canonical, newUnit, dirty = false) to
            canonical?.let { UnitConverter.weightInput(it, newUnit) }.orEmpty()
    }

    companion object {
        fun fromCanonical(value: BigDecimal?, unit: WeightUnit) = EditableWeightState(value, unit, dirty = false)
    }
}

data class EditableDistanceState(
    val originalMeters: BigDecimal?,
    val displayUnit: DistanceUnit,
    val dirty: Boolean,
) {
    fun canonicalOrNull(text: String): BigDecimal? = if (!dirty) originalMeters else {
        text.toBigDecimalOrNull()?.let { UnitConverter.distanceToCanonical(it, displayUnit) }
    }

    fun edited() = copy(dirty = true)

    fun rebase(text: String, newUnit: DistanceUnit): Pair<EditableDistanceState, String> {
        if (newUnit == displayUnit) return this to text
        val canonical = canonicalOrNull(text)
        return EditableDistanceState(canonical, newUnit, dirty = false) to
            canonical?.let { UnitConverter.distanceInput(it, newUnit) }.orEmpty()
    }

    companion object {
        fun fromCanonical(value: BigDecimal?, unit: DistanceUnit) = EditableDistanceState(value, unit, dirty = false)
    }
}
