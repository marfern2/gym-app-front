package com.mar.gym.feature.progress.model

import java.time.LocalDate

enum class HistoryRange {
    ThreeMonths,
    OneYear,
    AllTime;

    fun startDate(today: LocalDate): LocalDate? = when (this) {
        ThreeMonths -> today.minusMonths(3)
        OneYear -> today.minusYears(1)
        AllTime -> null
    }
}
