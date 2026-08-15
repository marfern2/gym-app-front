package com.mar.gym.feature.routines.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutineExerciseDraft
import com.mar.gym.feature.routines.model.RoutineSetDraft
import com.mar.gym.feature.routines.model.RoutineSort
import kotlinx.coroutines.runBlocking
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

class DefaultRoutineRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test
    fun mapsDtosListAndPaginationWithExactQuery() = runBlocking {
        server.enqueue(json(pageJson()))
        val result = repository().list(false, "  fuerza   base ", 0, 20, RoutineSort.NameAscending)
            as RoutineRepositoryResult.Success

        assertEquals(1, result.value.content.size)
        assertEquals(3L, result.value.totalElements)
        val request = server.takeRequest()
        assertEquals("false", request.requestUrl?.queryParameter("archived"))
        assertEquals("fuerza base", request.requestUrl?.queryParameter("query"))
        assertEquals("name,asc", request.requestUrl?.queryParameter("sort"))
        assertEquals("retry-on-401", request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test
    fun detailCapturesQuotedEtagAndMapsNestedTypesUnitsAndOrder() = runBlocking {
        server.enqueue(json(detailJson(), etag = "\"7\""))
        val result = repository().detail(ROUTINE_ID) as RoutineRepositoryResult.Success

        assertEquals("\"7\"", result.value.etag.headerValue)
        assertEquals(7, result.value.etag.version)
        val exercise = result.value.detail.exercises.single()
        assertEquals(ExerciseType.WeightReps, exercise.exerciseType)
        assertEquals(null, exercise.supersetGroup)
        assertEquals("22.5", exercise.sets.single().targetWeight)
        assertEquals("8.5", exercise.sets.single().targetRpe)
    }

    @Test
    fun missingInvalidOrMismatchedEtagRejectsSuccessfulDetail() = runBlocking {
        listOf(null, "W/\"7\"", "\"6\"").forEach { etag ->
            server.enqueue(json(detailJson(), etag))
            val result = repository().detail(ROUTINE_ID) as RoutineRepositoryResult.Failure
            assertTrue(result.error is NetworkFailure.InvalidResponse)
        }
    }

    @Test
    fun createSerializesCompleteDraftAndDoesNotSendChildIds() = runBlocking {
        server.enqueue(json(detailJson(version = 0), "\"0\"", 201))
        val result = repository().create(draft(routineId = null))
        assertTrue(result is RoutineRepositoryResult.Success)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/routines", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"targetWeight\":22.5"))
        assertTrue(body.contains("\"position\":1"))
        assertTrue(body.contains("\"supersetGroup\":null"))
        assertTrue(!body.contains("local-exercise") && !body.contains("local-set"))
    }

    @Test
    fun mapsValidGroupsWritesNormalizedOrdinalsAndAdoptsCanonicalRenumbering() = runBlocking {
        server.enqueue(json(groupedDetailJson(version = 7), etag = "\"7\""))
        val loaded = repository().detail(ROUTINE_ID) as RoutineRepositoryResult.Success
        assertEquals(listOf(1, 1), loaded.value.detail.exercises.map { it.supersetGroup })
        server.takeRequest()

        server.enqueue(json(groupedDetailJson(version = 8), etag = "\"8\""))
        val draft = groupedDraft()
        val saved = repository().replace(draft, RoutineEtag.fromVersion(7)!!) as RoutineRepositoryResult.Success
        val body = server.takeRequest().body.readUtf8()
        assertEquals(2, "\"supersetGroup\":1".toRegex().findAll(body).count())
        assertTrue(!body.contains("temporary-group"))
        assertEquals(listOf(1, 1), saved.value.detail.exercises.map { it.supersetGroup })
        assertEquals(8, saved.value.etag.version)
    }

    @Test
    fun rejectsSingletonSupersetInCanonicalResponse() = runBlocking {
        server.enqueue(json(detailJson().replace("\"supersetGroup\":null", "\"supersetGroup\":1"), "\"7\""))
        val result = repository().detail(ROUTINE_ID)
        assertTrue(result is RoutineRepositoryResult.Failure)
        assertTrue((result as RoutineRepositoryResult.Failure).error is NetworkFailure.InvalidResponse)
    }

    @Test
    fun updateSendsCapturedIfMatchAndUsesPut() = runBlocking {
        server.enqueue(json(detailJson(version = 8), "\"8\""))
        repository().replace(draft(ROUTINE_ID), RoutineEtag.parse("\"7\"")!!)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("\"7\"", request.getHeader("If-Match"))
        assertEquals("/api/v1/routines/$ROUTINE_ID", request.path)
    }

    @Test
    fun archiveRestoreAndDuplicateUseIfMatchAndExactEndpoints() = runBlocking {
        val repository = repository()
        val etag = RoutineEtag.fromVersion(7)!!
        listOf("archive", "restore", "duplicate").forEach { server.enqueue(json(detailJson(), "\"7\"", if (it == "duplicate") 201 else 200)) }

        repository.archive(ROUTINE_ID, etag)
        repository.restore(ROUTINE_ID, etag)
        repository.duplicate(ROUTINE_ID, etag)

        listOf("archive", "restore", "duplicate").forEach { action ->
            val request = server.takeRequest()
            assertEquals("/api/v1/routines/$ROUTINE_ID/$action", request.path)
            assertEquals("\"7\"", request.getHeader("If-Match"))
        }
    }

    @Test
    fun deleteUsesCanonicalIfMatchAndAccepts204WithoutBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository().delete(ROUTINE_ID, RoutineEtag.parse("\"7\"")!!)

        assertTrue(result is RoutineRepositoryResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/routines/$ROUTINE_ID", request.path)
        assertEquals("\"7\"", request.getHeader("If-Match"))
        assertEquals(AUTHENTICATION_NO_RETRY, request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test
    fun preserves404ConflictAndNestedProblemDetails() = runBlocking {
        server.enqueue(problem(404, "ROUTINE_NOT_FOUND"))
        val missing = repository().detail(ROUTINE_ID) as RoutineRepositoryResult.Failure
        assertEquals(404, (missing.error as NetworkFailure.HttpProblem).statusCode)

        server.enqueue(problem(409, "ROUTINE_VERSION_CONFLICT"))
        val conflict = repository().replace(draft(ROUTINE_ID), RoutineEtag.fromVersion(1)!!)
            as RoutineRepositoryResult.Failure
        assertEquals("ROUTINE_VERSION_CONFLICT", (conflict.error as NetworkFailure.HttpProblem).problem.errorCode)

        server.enqueue(problem(409, "ROUTINE_VERSION_CONFLICT"))
        val deleteConflict = repository().delete(ROUTINE_ID, RoutineEtag.fromVersion(1)!!)
            as RoutineRepositoryResult.Failure
        assertEquals(409, (deleteConflict.error as NetworkFailure.HttpProblem).statusCode)

        server.enqueue(problem(404, "ROUTINE_NOT_FOUND"))
        val deleteMissing = repository().delete(ROUTINE_ID, RoutineEtag.fromVersion(1)!!)
            as RoutineRepositoryResult.Failure
        assertEquals(404, (deleteMissing.error as NetworkFailure.HttpProblem).statusCode)

        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/problem+json").setBody(
            """{"status":400,"errorCode":"INVALID_ROUTINE_SET","fieldErrors":[{"field":"exercises[0].sets[0].targetRepsMin","code":"INVALID_VALUE","message":"invalid"}]}"""
        ))
        val nested = repository().create(draft(null)) as RoutineRepositoryResult.Failure
        assertTrue((nested.error as NetworkFailure.HttpProblem).problem.fieldErrors.toString().contains("targetRepsMin"))
    }

    @Test
    fun etagParserAcceptsOnlyContractFormats() {
        assertEquals(12L, RoutineEtag.parse("12")?.version)
        assertEquals(12L, RoutineEtag.parse("\"12\"")?.version)
        listOf(null, "", "-1", "W/\"1\"", "*", "\"x\"").forEach { assertEquals(null, RoutineEtag.parse(it)) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun repository(): DefaultRoutineRepository {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(RoutineApi::class.java)
        return DefaultRoutineRepository(api)
    }

    private fun json(body: String, etag: String? = null, code: Int = 200) = MockResponse()
        .setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
        .apply { etag?.let { setHeader("ETag", it) } }

    private fun problem(code: Int, errorCode: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"status":$code,"errorCode":"$errorCode"}""")

    private fun pageJson() = """{
      "content":[{"id":"$ROUTINE_ID","name":"Fuerza","description":null,"exerciseCount":1,"archived":false,"createdAt":"2026-08-01T10:00:00Z","updatedAt":"2026-08-02T10:00:00Z","version":7}],
      "page":0,"size":20,"totalElements":3,"totalPages":1,"first":true,"last":true
    }"""

    private fun detailJson(version: Int = 7) = """{
      "id":"$ROUTINE_ID","name":"Fuerza","description":"Base","archived":false,"version":$version,
      "createdAt":"2026-08-01T10:00:00Z","updatedAt":"2026-08-02T10:00:00Z",
      "exercises":[{"id":"$EXERCISE_ID","exerciseTemplateId":"$TEMPLATE_ID","exerciseName":"Press","exerciseType":"WEIGHT_REPS","equipment":"BARBELL","position":1,"supersetGroup":null,"notes":null,"restSeconds":90,
        "sets":[{"id":"$SET_ID","position":1,"setType":"NORMAL","targetRepsMin":8,"targetRepsMax":10,"targetWeight":22.5,"targetDurationSeconds":null,"targetDistanceMeters":null,"targetRpe":8.5}]}]
    }"""

    private fun groupedDetailJson(version: Int) = """{
      "id":"$ROUTINE_ID","name":"Fuerza","description":"Base","archived":false,"version":$version,
      "createdAt":"2026-08-01T10:00:00Z","updatedAt":"2026-08-02T10:00:00Z",
      "exercises":[
        {"id":"$EXERCISE_ID","exerciseTemplateId":"$TEMPLATE_ID","exerciseName":"Press","exerciseType":"WEIGHT_REPS","equipment":"BARBELL","position":1,"supersetGroup":1,"notes":null,"restSeconds":90,"sets":[]},
        {"id":"$SECOND_EXERCISE_ID","exerciseTemplateId":"$SECOND_TEMPLATE_ID","exerciseName":"Remo","exerciseType":"WEIGHT_REPS","equipment":"BARBELL","position":2,"supersetGroup":1,"notes":null,"restSeconds":90,"sets":[]}
      ]
    }"""

    private fun draft(routineId: String?) = RoutineDraft(
        routineId = routineId, name = "Fuerza", description = "Base",
        exercises = listOf(RoutineExerciseDraft(
            "local-exercise", TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
            restSeconds = "90", sets = listOf(RoutineSetDraft(
                "local-set", targetRepsMin = "8", targetRepsMax = "10", targetWeight = "22.5", targetRpe = "8.5"
            )),
        )),
    )

    private fun groupedDraft() = RoutineDraft(
        routineId = ROUTINE_ID,
        name = "Fuerza",
        exercises = listOf(
            RoutineExerciseDraft(
                "local-first", TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
                supersetLocalId = "temporary-group",
            ),
            RoutineExerciseDraft(
                "local-second", SECOND_TEMPLATE_ID, "Remo", ExerciseType.WeightReps, Equipment.Barbell,
                supersetLocalId = "temporary-group",
            ),
        ),
    )

    private companion object {
        const val ROUTINE_ID = "81111111-1111-4111-8111-111111111111"
        const val EXERCISE_ID = "82222222-2222-4222-8222-222222222222"
        const val TEMPLATE_ID = "83333333-3333-4333-8333-333333333333"
        const val SET_ID = "84444444-4444-4444-8444-444444444444"
        const val SECOND_EXERCISE_ID = "85555555-5555-4555-8555-555555555555"
        const val SECOND_TEMPLATE_ID = "86666666-6666-4666-8666-666666666666"
    }
}
