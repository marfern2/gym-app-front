package com.mar.gym.ui.components

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class NiceScale(val yMax: Double, val tickStep: Double, val ticks: List<Double>)

/** Zero-based scale with up to five intervals and steps from 1, 2, 2.5, 5 × 10ⁿ. */
fun niceScale(dataMax: Double, integerTicks: Boolean = false): NiceScale {
    require(dataMax.isFinite() && dataMax >= 0.0)
    if (dataMax == 0.0) {
        val step = if (integerTicks) 1.0 else 0.25
        return NiceScale(1.0, step, (0..(1.0 / step).toInt()).map { it * step })
    }
    val exponent = floor(log10(dataMax)).toInt()
    val candidates = ((exponent - 3)..(exponent + 1)).flatMap { power ->
        val magnitude = 10.0.pow(power)
        listOf(1.0, 2.0, 2.5, 5.0).map { it * magnitude }
    }.filter { step -> step.isFinite() && step > 0.0 &&
        (!integerTicks || (step >= 1.0 && step == floor(step)))
    }.mapNotNull { step ->
        val intervals = ceil(dataMax / step - 1e-10).toInt()
        val minimum = if (integerTicks && dataMax < 3.0) 1 else 3
        if (intervals !in minimum..5) null else {
            val yMax = intervals * step
            Triple(step, intervals, yMax)
        }
    }
    val (step, intervals, yMax) = candidates.minWithOrNull(
        compareBy<Triple<Double, Int, Double>> { (it.third - dataMax) / dataMax + abs(it.second - 4) * 0.02 }
            .thenBy { it.third },
    ) ?: error("No readable scale for $dataMax")
    return NiceScale(yMax, step, (0..intervals).map { it * step })
}
