package com.mar.gym.feature.progress.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.BestWeightForReps
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.MuscleDistributionItem
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousExercisePerformance
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.PreviousPerformanceSet
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.feature.progress.model.TrainingCalendarDay
import com.mar.gym.feature.routines.model.SetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class DefaultAnalyticsRepository(private val api: AnalyticsApi) : AnalyticsRepository {
    override suspend fun calendar(month: YearMonth, timezone: String) = request(
        validateZone(timezone)?.let { { api.calendar(month.toString(), it) } },
    ) { dto -> dto.toDomain() }

    override suspend fun calendar(from: LocalDate, to: LocalDate, timezone: String) = request(
        validateZone(timezone)?.takeIf { !to.isBefore(from) && java.time.temporal.ChronoUnit.DAYS.between(from, to) < 366 }
            ?.let { { api.calendarRange(from.toString(), to.toString(), it) } },
    ) { dto -> dto.toDomain() }

    override suspend fun summary(period: AnalyticsPeriod, timezone: String) = request(
        validateZone(timezone)?.let { { api.summary(period.apiValue, it) } },
    ) { it.toDomain() }

    override suspend fun muscleDistribution(period: AnalyticsPeriod, timezone: String) = request(
        validateZone(timezone)?.let { { api.muscleDistribution(period.apiValue, it) } },
    ) { it.toDomain() }

    override suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int) =
        if (!exerciseTemplateId.isUuid() || page < 0 || size !in 1..100) invalid()
        else request({ api.exerciseHistory(exerciseTemplateId, page, size) }) { it.toDomain() }

    override suspend fun previousPerformance(exerciseTemplateIds: List<String>) =
        if (exerciseTemplateIds.size !in 1..100 || exerciseTemplateIds.distinct().size != exerciseTemplateIds.size ||
            exerciseTemplateIds.any { !it.isUuid() }
        ) invalid() else request({ api.previousPerformance(PreviousPerformanceRequestDto(exerciseTemplateIds)) }) { dto ->
            dto.items.map { it.toDomain() ?: return@request null }.takeIf { mapped ->
                mapped.map { it.exerciseTemplateId } == exerciseTemplateIds
            }
        }

    override suspend fun personalRecords(exerciseTemplateId: String) =
        if (!exerciseTemplateId.isUuid()) invalid()
        else request({ api.personalRecords(exerciseTemplateId) }) { it.toDomain() }

    private suspend fun <D : Any, T> request(
        call: (suspend () -> retrofit2.Response<D>)?,
        mapper: (D) -> T?,
    ): AnalyticsResult<T> {
        if (call == null) return invalid()
        return when (val response = executeNetworkRequest(call)) {
            is NetworkResponse.Failure -> AnalyticsResult.Failure(response.error)
            is NetworkResponse.Success -> mapper(response.value)?.let { AnalyticsResult.Success(it) }
                ?: invalid(response.correlationId)
        }
    }

    private fun CalendarDto.toDomain(): TrainingCalendar? {
        val start = from.date() ?: return null
        val end = to.date() ?: return null
        if (end < start || validateZone(timezone) == null) return null
        val mapped = days.map { day ->
            val date = day.date.date() ?: return null
            if (date !in start..end || day.workoutCount < 0 || day.completedSetCount < 0 || day.durationSeconds < 0) return null
            TrainingCalendarDay(date, day.workoutCount, day.completedSetCount, day.durationSeconds)
        }
        if (mapped.zipWithNext().any { (a, b) -> a.date >= b.date }) return null
        return TrainingCalendar(start, end, timezone, mapped)
    }

    private fun SummaryDto.toDomain(): ProgressSummary? {
        val start = from.date() ?: return null
        val end = to.date() ?: return null
        if (end < start || validateZone(timezone) == null || listOf(
                workoutCount, completedSetCount, totalDurationSeconds, activeDays, averageWorkoutDurationSeconds,
            ).any { it < 0 } || !totalVolumeKg.isFinite() || totalVolumeKg < 0
        ) return null
        return ProgressSummary(start, end, timezone, workoutCount, completedSetCount, totalDurationSeconds,
            totalVolumeKg.decimal(), activeDays, averageWorkoutDurationSeconds)
    }

    private fun MuscleDistributionDto.toDomain(): MuscleDistribution? {
        val start = from.date() ?: return null
        val end = to.date() ?: return null
        val mapped = items.map { item ->
            val group = MuscleGroup.fromApiValue(item.muscleGroup) ?: return null
            if (item.completedSetCount < 0) return null
            MuscleDistributionItem(group, item.completedSetCount)
        }
        if (end < start || validateZone(timezone) == null || totalCompletedSetCount < 0 ||
            mapped.sumOf { it.completedSetCount } != totalCompletedSetCount || mapped.map { it.muscleGroup }.distinct().size != mapped.size
        ) return null
        return MuscleDistribution(start, end, timezone, totalCompletedSetCount, mapped)
    }

    private fun ExerciseHistoryDto.toDomain(): ExerciseHistoryPage? {
        if (!exerciseTemplateId.isUuid() || page < 0 || size !in 1..100 || totalElements < 0 || totalPages < 0) return null
        val sessions = content.map { it.toDomain() ?: return null }
        return ExerciseHistoryPage(exerciseTemplateId, sessions, page, size, totalElements, totalPages, first, last)
    }

    private fun ExerciseSessionDto.toDomain(): ExercisePerformanceSession? {
        if (!workoutId.isUuid() || exerciseNameSnapshot.isBlank()) return null
        val type = ExerciseType.fromApiValue(exerciseTypeSnapshot) ?: return null
        val completion = completedAt.instant() ?: return null
        val mapped = sets.map { set ->
            if (set.position <= 0 || set.reps?.let { it < 0 } == true || set.durationSeconds?.let { it < 0 } == true) return null
            ActualPerformanceSet(set.position, set.reps, set.weightKg?.decimal(), set.durationSeconds,
                set.distanceMeters?.decimal(), set.rpe?.decimal())
        }
        return ExercisePerformanceSession(workoutId, completion, exerciseNameSnapshot, type, mapped)
    }

    private fun PreviousPerformanceItemDto.toDomain(): PreviousPerformanceItem? {
        if (!exerciseTemplateId.isUuid()) return null
        return PreviousPerformanceItem(exerciseTemplateId, previousPerformance?.toDomain())
    }

    private fun PreviousExerciseSessionDto.toDomain(): PreviousExercisePerformance? {
        if (!workoutId.isUuid() || exerciseNameSnapshot.isBlank()) return null
        val type = ExerciseType.fromApiValue(exerciseTypeSnapshot) ?: return null
        val completion = completedAt.instant() ?: return null
        val mapped = sets.map { set ->
            if (set.workoutExercisePosition <= 0 || set.setPosition <= 0) return null
            PreviousPerformanceSet(
                set.workoutExercisePosition, set.setPosition,
                SetType.fromApiValue(set.setType) ?: return null,
                set.reps, set.weightKg?.decimal(), set.durationSeconds,
                set.distanceMeters?.decimal(), set.rpe?.decimal(),
            )
        }
        if (mapped.zipWithNext().any { (a, b) ->
                a.workoutExercisePosition > b.workoutExercisePosition ||
                    (a.workoutExercisePosition == b.workoutExercisePosition && a.setPosition >= b.setPosition)
            }) return null
        return PreviousExercisePerformance(workoutId, completion, exerciseNameSnapshot, type, mapped)
    }

    private fun PersonalRecordsDto.toDomain(): PersonalRecords? {
        if (!exerciseTemplateId.isUuid()) return null
        val weights = bestWeightsForReps.map {
            if (it.reps <= 0 || !it.weightKg.isFinite() || it.weightKg < 0) return null
            BestWeightForReps(it.reps, it.weightKg.decimal())
        }
        return PersonalRecords(exerciseTemplateId, maximumWeightKg?.decimal(), maximumReps,
            maximumDurationSeconds, maximumDistanceMeters?.decimal(), minimumAssistanceKg?.decimal(), weights)
    }

    private fun validateZone(value: String): String? = runCatching { ZoneId.of(value) }.getOrNull()
        ?.takeIf { it !is java.time.ZoneOffset }?.id
    private fun String.date() = runCatching { LocalDate.parse(this) }.getOrNull()
    private fun String.instant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun Double.decimal(): BigDecimal = BigDecimal.valueOf(this)
    private fun <T> invalid(correlationId: String? = null): AnalyticsResult<T> =
        AnalyticsResult.Failure(NetworkFailure.InvalidResponse(correlationId))
}
