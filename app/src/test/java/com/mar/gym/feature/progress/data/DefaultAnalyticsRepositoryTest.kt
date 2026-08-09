package com.mar.gym.feature.progress.data

import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.routines.model.SetType
import java.time.YearMonth
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DefaultAnalyticsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultAnalyticsRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(AnalyticsApi::class.java)
        repository = DefaultAnalyticsRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `calendar sends IANA timezone and maps compact days`() = kotlinx.coroutines.test.runTest {
        enqueue("""{"from":"2026-08-01","to":"2026-08-31","timezone":"Europe/Madrid","days":[{"date":"2026-08-02","workoutCount":2,"completedSetCount":7,"durationSeconds":3600}]}""")
        val result = repository.calendar(YearMonth.of(2026, 8), "Europe/Madrid") as AnalyticsResult.Success
        assertEquals("/api/v1/analytics/calendar?month=2026-08&timezone=Europe%2FMadrid", server.takeRequest().path)
        assertEquals(2, result.value.days.single().workoutCount)
        assertEquals("Europe/Madrid", result.value.timezone)
    }

    @Test fun `summary and distribution preserve backend values`() = kotlinx.coroutines.test.runTest {
        enqueue("""{"from":"2026-08-03","to":"2026-08-09","timezone":"Europe/Madrid","workoutCount":3,"completedSetCount":20,"totalDurationSeconds":7200,"totalVolumeKg":1234.5,"activeDays":2,"averageWorkoutDurationSeconds":2400}""")
        enqueue("""{"from":"2026-08-03","to":"2026-08-09","timezone":"Europe/Madrid","totalCompletedSetCount":20,"items":[{"muscleGroup":"CHEST","completedSetCount":12},{"muscleGroup":"BACK","completedSetCount":8}]}""")
        val summary = repository.summary(AnalyticsPeriod.Week, "Europe/Madrid") as AnalyticsResult.Success
        val distribution = repository.muscleDistribution(AnalyticsPeriod.Week, "Europe/Madrid") as AnalyticsResult.Success
        assertEquals("1234.5", summary.value.totalVolumeKg.toPlainString())
        assertEquals(2400, summary.value.averageWorkoutDurationSeconds)
        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Back), distribution.value.items.map { it.muscleGroup })
        assertEquals(20, distribution.value.totalCompletedSetCount)
    }

    @Test fun `history maps actual sessions and pagination without targets`() = kotlinx.coroutines.test.runTest {
        enqueue("""{"exerciseTemplateId":"$ID","content":[{"workoutId":"$WORKOUT","completedAt":"2026-08-09T09:30:00Z","exerciseNameSnapshot":"Press","exerciseTypeSnapshot":"WEIGHT_REPS","sets":[{"position":1,"reps":8,"weightKg":85.0,"durationSeconds":null,"distanceMeters":null,"rpe":8.0}]}],"page":1,"size":20,"totalElements":21,"totalPages":2,"first":false,"last":true}""")
        val result = repository.exerciseHistory(ID, 1) as AnalyticsResult.Success
        assertEquals(1, result.value.page)
        assertTrue(result.value.last)
        assertEquals(ExerciseType.WeightReps, result.value.content.single().exerciseTypeSnapshot)
        assertEquals(8, result.value.content.single().sets.single().reps)
    }

    @Test fun `previous performance is one batch and preserves position gaps and set type`() = kotlinx.coroutines.test.runTest {
        enqueue("""{"items":[{"exerciseTemplateId":"$ID","previousPerformance":{"workoutId":"$WORKOUT","completedAt":"2026-08-09T09:30:00Z","exerciseNameSnapshot":"Press","exerciseTypeSnapshot":"WEIGHT_REPS","sets":[{"workoutExercisePosition":2,"setPosition":2,"setType":"WARMUP","reps":8,"weightKg":80.0,"durationSeconds":null,"distanceMeters":null,"rpe":null},{"workoutExercisePosition":2,"setPosition":4,"setType":"FAILURE","reps":6,"weightKg":90.0,"durationSeconds":null,"distanceMeters":null,"rpe":null}]}},{"exerciseTemplateId":"$ID_TWO","previousPerformance":null}]}""")
        val result = repository.previousPerformance(listOf(ID, ID_TWO)) as AnalyticsResult.Success
        val request = server.takeRequest()
        assertEquals("/api/v1/analytics/exercises/previous-performance", request.path)
        assertTrue(request.body.readUtf8().contains(ID_TWO))
        val sets = result.value.first().previousPerformance!!.sets
        assertEquals(listOf(2, 4), sets.map { it.setPosition })
        assertEquals(listOf(SetType.Warmup, SetType.Failure), sets.map { it.setType })
        assertEquals(null, result.value.last().previousPerformance)
    }

    @Test fun `personal records maps every explicit metric and no generic PR`() = kotlinx.coroutines.test.runTest {
        enqueue("""{"exerciseTemplateId":"$ID","maximumWeightKg":100.0,"maximumReps":12,"maximumDurationSeconds":90,"maximumDistanceMeters":5000.0,"minimumAssistanceKg":20.0,"bestWeightsForReps":[{"reps":8,"weightKg":100.0}]}""")
        val records = (repository.personalRecords(ID) as AnalyticsResult.Success).value
        assertEquals("100.0", records.maximumWeightKg.toString())
        assertEquals(12, records.maximumReps)
        assertEquals(90, records.maximumDurationSeconds)
        assertEquals("5000.0", records.maximumDistanceMeters.toString())
        assertEquals("20.0", records.minimumAssistanceKg.toString())
        assertEquals(8, records.bestWeightsForReps.single().reps)
    }

    @Test fun `fixed offset timezone is rejected before HTTP`() = kotlinx.coroutines.test.runTest {
        assertTrue(repository.summary(AnalyticsPeriod.Month, "+02:00") is AnalyticsResult.Failure)
        assertEquals(0, server.requestCount)
    }

    private fun enqueue(body: String) { server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)) }

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val ID_TWO = "00000000-0000-4000-8000-000000000002"
        const val WORKOUT = "00000000-0000-4000-8000-000000000003"
    }
}
