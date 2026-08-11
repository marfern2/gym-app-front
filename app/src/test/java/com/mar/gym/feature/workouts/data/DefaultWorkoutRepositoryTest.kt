package com.mar.gym.feature.workouts.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class DefaultWorkoutRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `maps active response with independent targets results and etag`() = runBlocking {
        server.enqueue(jsonResponse(detailJson(), etag = "\"7\""))

        val result = repository().getActiveWorkout() as WorkoutRepositoryResult.Success
        val document = result.value
        val set = document.detail.exercises.single().sets.single()

        assertEquals(7, document.etag.version)
        assertEquals("80.0", set.targets.targetWeight.toString())
        assertEquals(8, set.targets.targetRepsMin)
        assertNull(document.detail.exercises.single().supersetGroup)
        assertNull(set.weight)
        assertNull(set.reps)
        assertFalse(set.completed)
        assertEquals("/api/v1/workouts/active", server.takeRequest().path)
    }

    @Test
    fun `active absence preserves problem details`() = runBlocking {
        server.enqueue(problemResponse(404, "WORKOUT_NOT_FOUND"))
        val result = repository().getActiveWorkout() as WorkoutRepositoryResult.Failure
        assertEquals("WORKOUT_NOT_FOUND", (result.error as NetworkFailure.HttpProblem).problem.errorCode)
    }

    @Test
    fun `starts empty without body and starts from routine with exact body`() = runBlocking {
        server.enqueue(jsonResponse(detailJson(version = 0), etag = "\"0\"", code = 201))
        server.enqueue(jsonResponse(detailJson(version = 0), etag = "\"0\"", code = 201))

        assertTrue(repository().startWorkout() is WorkoutRepositoryResult.Success)
        val empty = server.takeRequest()
        assertEquals("POST", empty.method)
        assertEquals("/api/v1/workouts", empty.path)
        assertEquals(0, empty.bodySize)

        assertTrue(repository().startWorkout(ROUTINE_ID) is WorkoutRepositoryResult.Success)
        val routine = server.takeRequest()
        assertEquals("{\"routineId\":\"$ROUTINE_ID\"}", routine.body.readUtf8())
        assertEquals("no-retry", routine.getHeader("X-GYmApp-Requires-Authentication"))
    }

    @Test
    fun `start from routine trusts workout snapshot grouping`() = runBlocking {
        server.enqueue(jsonResponse(groupedDetailJson(version = 0), etag = "\"0\"", code = 201))

        val result = repository().startWorkout(ROUTINE_ID) as WorkoutRepositoryResult.Success

        assertEquals(listOf(1, 1), result.value.detail.exercises.map { it.supersetGroup })
        assertEquals(listOf(EXERCISE_ID, SECOND_EXERCISE_ID), result.value.detail.exercises.map { it.id })
    }

    @Test
    fun `update sends existing ids omits new ids and all targets then adopts canonical response etag`() = runBlocking {
        server.enqueue(jsonResponse(detailJson(version = 8, includeSecondSet = true), etag = "\"8\""))
        val draft = draft().copy(exercises = listOf(
            draft().exercises.single().copy(sets = listOf(
                draft().exercises.single().sets.single().copy(reps = "9", weight = "82.5", completed = true),
                WorkoutSetDraft("local-set", null, reps = "3", weight = "70"),
            )),
            WorkoutExerciseDraft(
                "local-exercise", null, OTHER_TEMPLATE_ID, "Client display only",
                ExerciseType.Duration, Equipment.None,
            ),
        ))

        val result = repository().updateWorkout(WORKOUT_ID, draft, WorkoutEtag.fromVersion(7)!!)
            as WorkoutRepositoryResult.Success
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("\"7\"", request.getHeader("If-Match"))
        assertEquals("no-retry", request.getHeader("X-GYmApp-Requires-Authentication"))
        assertTrue(body.contains("\"id\":\"$EXERCISE_ID\""))
        assertTrue(body.contains("\"id\":\"$SET_ID\""))
        assertTrue(body.contains("\"setType\":\"NORMAL\""))
        assertTrue(body.contains("\"exerciseTemplateId\":\"$OTHER_TEMPLATE_ID\""))
        assertFalse(body.contains("local-set"))
        assertFalse(body.contains("local-exercise"))
        assertFalse(body.contains("targetWeight"))
        assertEquals(8, result.value.etag.version)
        assertEquals(NEW_SET_ID, result.value.detail.exercises.single().sets.last().id)
    }

    @Test
    fun `update writes normalized groups and keeps stable exercise ids from canonical response`() = runBlocking {
        server.enqueue(jsonResponse(groupedDetailJson(version = 8), etag = "\"8\""))
        val draft = groupedDraft()

        val result = repository().updateWorkout(WORKOUT_ID, draft, WorkoutEtag.fromVersion(7)!!)
            as WorkoutRepositoryResult.Success
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals(2, "\"supersetGroup\":1".toRegex().findAll(body).count())
        assertTrue(!body.contains("temporary-group"))
        assertEquals(listOf(EXERCISE_ID, SECOND_EXERCISE_ID), result.value.detail.exercises.map { it.id })
        assertEquals(listOf(1, 1), result.value.detail.exercises.map { it.supersetGroup })
        assertEquals(8, result.value.etag.version)
    }

    @Test
    fun `complete discard and history follow status headers and pagination`() = runBlocking {
        server.enqueue(jsonResponse(detailJson(version = 8, status = "COMPLETED"), etag = "\"8\""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(jsonResponse(historyJson()))

        assertTrue(repository().completeWorkout(WORKOUT_ID, WorkoutEtag.fromVersion(7)!!) is WorkoutRepositoryResult.Success)
        val complete = server.takeRequest()
        assertEquals("/api/v1/workouts/$WORKOUT_ID/complete", complete.path)
        assertEquals("\"7\"", complete.getHeader("If-Match"))
        assertEquals("no-retry", complete.getHeader("X-GYmApp-Requires-Authentication"))

        assertTrue(repository().discardWorkout(WORKOUT_ID, WorkoutEtag.fromVersion(8)!!) is WorkoutRepositoryResult.Success)
        val discard = server.takeRequest()
        assertEquals("/api/v1/workouts/$WORKOUT_ID/discard", discard.path)
        assertEquals("no-retry", discard.getHeader("X-GYmApp-Requires-Authentication"))

        val history = repository().getWorkoutHistory(0, 20) as WorkoutRepositoryResult.Success
        assertEquals(1, history.value.content.size)
        assertEquals(2, history.value.content.single().exerciseCount)
        assertEquals(3, history.value.content.single().completedSetCount)
        assertEquals("/api/v1/workouts?page=0&size=20", server.takeRequest().path)
    }

    @Test
    fun `rejects mismatched or missing etag`() = runBlocking {
        server.enqueue(jsonResponse(detailJson(version = 7), etag = "\"6\""))
        val result = repository().getWorkout(WORKOUT_ID)
        assertTrue(result is WorkoutRepositoryResult.Failure)
        assertTrue((result as WorkoutRepositoryResult.Failure).error is NetworkFailure.InvalidResponse)
        assertEquals(null, WorkoutEtag.parse("W/\"1\""))
    }

    private fun repository(): DefaultWorkoutRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WorkoutApi::class.java)
        return DefaultWorkoutRepository(api)
    }

    private fun draft(): WorkoutDraft = WorkoutDraft(
        WORKOUT_ID, "Workout", exercises = listOf(WorkoutExerciseDraft(
            EXERCISE_ID, EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps,
            Equipment.Barbell, restSeconds = "90", sets = listOf(
                WorkoutSetDraft(SET_ID, SET_ID, SetType.Normal),
            ),
        )),
    )

    private fun groupedDraft(): WorkoutDraft = WorkoutDraft(
        WORKOUT_ID,
        "Workout",
        exercises = listOf(
            WorkoutExerciseDraft(
                EXERCISE_ID, EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps,
                Equipment.Barbell, supersetLocalId = "temporary-group",
            ),
            WorkoutExerciseDraft(
                SECOND_EXERCISE_ID, SECOND_EXERCISE_ID, OTHER_TEMPLATE_ID, "Remo",
                ExerciseType.WeightReps, Equipment.Barbell, supersetLocalId = "temporary-group",
            ),
        ),
    )

    private fun jsonResponse(body: String, etag: String? = null, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .apply { etag?.let { setHeader("ETag", it) } }
        .setBody(body)

    private fun problemResponse(code: Int, errorCode: String) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"status":$code,"errorCode":"$errorCode"}""")

    private fun detailJson(
        version: Int = 7,
        status: String = "ACTIVE",
        includeSecondSet: Boolean = false,
    ): String {
        val completedAt = if (status == "COMPLETED") "\"2026-08-08T11:00:00Z\"" else "null"
        val second = if (includeSecondSet) "," + setJson(NEW_SET_ID, 2, targets = false) else ""
        return """
            {"id":"$WORKOUT_ID","sourceRoutineId":null,"sourceRoutineName":null,
             "title":"Workout","notes":null,"status":"$status","startedAt":"2026-08-08T10:00:00Z",
             "completedAt":$completedAt,"durationSeconds":3600,"createdAt":"2026-08-08T10:00:00Z",
             "updatedAt":"2026-08-08T11:00:00Z","version":$version,"exercises":[{
               "id":"$EXERCISE_ID","sourceExerciseTemplateId":"$TEMPLATE_ID",
               "exerciseNameSnapshot":"Press","exerciseTypeSnapshot":"WEIGHT_REPS",
               "equipmentSnapshot":"BARBELL","position":1,"supersetGroup":null,"notes":null,"restSeconds":90,
               "sets":[${setJson(SET_ID, 1, targets = true)}$second]
             }]}
        """.trimIndent()
    }

    private fun groupedDetailJson(version: Int): String = """
        {"id":"$WORKOUT_ID","sourceRoutineId":"$ROUTINE_ID","sourceRoutineName":"Rutina",
         "title":"Workout","notes":null,"status":"ACTIVE","startedAt":"2026-08-08T10:00:00Z",
         "completedAt":null,"durationSeconds":0,"createdAt":"2026-08-08T10:00:00Z",
         "updatedAt":"2026-08-08T10:00:00Z","version":$version,"exercises":[
           {"id":"$EXERCISE_ID","sourceExerciseTemplateId":"$TEMPLATE_ID","exerciseNameSnapshot":"Press",
            "exerciseTypeSnapshot":"WEIGHT_REPS","equipmentSnapshot":"BARBELL","position":1,
            "supersetGroup":1,"notes":null,"restSeconds":90,"sets":[]},
           {"id":"$SECOND_EXERCISE_ID","sourceExerciseTemplateId":"$OTHER_TEMPLATE_ID","exerciseNameSnapshot":"Remo",
            "exerciseTypeSnapshot":"WEIGHT_REPS","equipmentSnapshot":"BARBELL","position":2,
            "supersetGroup":1,"notes":null,"restSeconds":90,"sets":[]}
         ]}
    """.trimIndent()

    private fun setJson(id: String, position: Int, targets: Boolean): String = if (targets) """
        {"id":"$id","position":$position,"setType":"NORMAL","targetRepsMin":8,"targetRepsMax":10,
         "targetWeight":80.0,"targetDurationSeconds":null,"targetDistanceMeters":null,"targetRpe":8.0,
         "completed":false,"reps":null,"weight":null,"durationSeconds":null,"distanceMeters":null,"rpe":null}
    """.trimIndent() else """
        {"id":"$id","position":$position,"setType":"NORMAL","targetRepsMin":null,"targetRepsMax":null,
         "targetWeight":null,"targetDurationSeconds":null,"targetDistanceMeters":null,"targetRpe":null,
         "completed":false,"reps":3,"weight":70.0,"durationSeconds":null,"distanceMeters":null,"rpe":null}
    """.trimIndent()

    private fun historyJson() = """
        {"content":[{"id":"$WORKOUT_ID","title":"Workout","startedAt":"2026-08-08T10:00:00Z",
        "completedAt":"2026-08-08T11:00:00Z","durationSeconds":3600,"exerciseCount":2,
        "completedSetCount":3}],"page":0,"size":20,"totalElements":1,"totalPages":1,
        "first":true,"last":true}
    """.trimIndent()

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000001"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000002"
        const val SET_ID = "00000000-0000-4000-8000-000000000003"
        const val NEW_SET_ID = "00000000-0000-4000-8000-000000000005"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000004"
        const val OTHER_TEMPLATE_ID = "00000000-0000-4000-8000-000000000006"
        const val ROUTINE_ID = "00000000-0000-4000-8000-000000000007"
        const val SECOND_EXERCISE_ID = "00000000-0000-4000-8000-000000000008"
    }
}
