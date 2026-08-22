package com.mar.gym.feature.progress.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import java.time.YearMonth
import java.time.LocalDate

sealed interface AnalyticsResult<out T> {
    data class Success<T>(val value: T) : AnalyticsResult<T>
    data class Failure(val error: NetworkFailure) : AnalyticsResult<Nothing>
}

interface AnalyticsRepository {
    suspend fun calendar(month: YearMonth, timezone: String): AnalyticsResult<TrainingCalendar>
    suspend fun calendar(from: LocalDate, to: LocalDate, timezone: String): AnalyticsResult<TrainingCalendar> {
        if (to < from) return AnalyticsResult.Failure(NetworkFailure.InvalidResponse())
        val months = generateSequence(YearMonth.from(from)) { current ->
            current.plusMonths(1).takeIf { it <= YearMonth.from(to) }
        }.toList()
        val days = mutableListOf<com.mar.gym.feature.progress.model.TrainingCalendarDay>()
        for (month in months) {
            when (val result = calendar(month, timezone)) {
                is AnalyticsResult.Failure -> return result
                is AnalyticsResult.Success -> days += result.value.days.filter { it.date in from..to }
            }
        }
        return AnalyticsResult.Success(TrainingCalendar(from, to, timezone, days.sortedBy { it.date }))
    }
    suspend fun summary(period: AnalyticsPeriod, timezone: String): AnalyticsResult<ProgressSummary>
    suspend fun muscleDistribution(period: AnalyticsPeriod, timezone: String): AnalyticsResult<MuscleDistribution>
    suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int = 20): AnalyticsResult<ExerciseHistoryPage>
    suspend fun previousPerformance(exerciseTemplateIds: List<String>): AnalyticsResult<List<PreviousPerformanceItem>>
    suspend fun personalRecords(exerciseTemplateId: String): AnalyticsResult<PersonalRecords>
}

fun interface TimeZoneProvider { fun zoneId(): String }

object DeviceTimeZoneProvider : TimeZoneProvider {
    override fun zoneId(): String = java.time.ZoneId.systemDefault().id
}
