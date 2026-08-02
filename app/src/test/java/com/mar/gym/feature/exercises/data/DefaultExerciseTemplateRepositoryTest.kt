package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class DefaultExerciseTemplateRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun mapsListAndPaginationExactly() = runBlocking {
        server.enqueue(jsonResponse(pageJson(page = 1, last = false)))

        val result = repository().getExerciseTemplates(
            query = null,
            filters = ExerciseFilters(),
            page = 1,
            size = 20,
            sort = ExerciseSort.NameAscending,
        ) as ExerciseRepositoryResult.Success

        assertEquals(1, result.value.page)
        assertEquals(42L, result.value.totalElements)
        assertFalse(result.value.last)
        assertEquals("Press de banca", result.value.content.single().name)
        assertEquals(MuscleGroup.Chest, result.value.content.single().primaryMuscleGroup)
    }

    @Test
    fun serializesNormalizedQueryFiltersAndControlledSort() = runBlocking {
        server.enqueue(jsonResponse(pageJson()))

        repository().getExerciseTemplates(
            query = "  press   banca ",
            filters = ExerciseFilters(
                primaryMuscleGroup = MuscleGroup.Chest,
                equipment = Equipment.Barbell,
                exerciseType = ExerciseType.WeightReps,
                movementPattern = MovementPattern.HorizontalPush,
            ),
            page = 0,
            size = 20,
            sort = ExerciseSort.EquipmentAscending,
        )

        val request = server.takeRequest()
        assertEquals("press banca", request.requestUrl?.queryParameter("query"))
        assertEquals("CHEST", request.requestUrl?.queryParameter("primaryMuscleGroup"))
        assertEquals("BARBELL", request.requestUrl?.queryParameter("equipment"))
        assertEquals("WEIGHT_REPS", request.requestUrl?.queryParameter("exerciseType"))
        assertEquals("HORIZONTAL_PUSH", request.requestUrl?.queryParameter("movementPattern"))
        assertEquals("equipment,asc", request.requestUrl?.queryParameter("sort"))
        assertEquals("20", request.requestUrl?.queryParameter("size"))
        assertEquals("retry-on-401", request.getHeader(AUTHENTICATION_REQUIRED_HEADER))
    }

    @Test
    fun omitsEmptyQueryAndNullableFilters() = runBlocking {
        server.enqueue(jsonResponse(pageJson()))

        repository().getExerciseTemplates(
            query = "   ",
            filters = ExerciseFilters(),
            page = 0,
            size = 20,
            sort = ExerciseSort.NameDescending,
        )

        val url = server.takeRequest().requestUrl!!
        assertEquals(null, url.queryParameter("query"))
        assertEquals(null, url.queryParameter("equipment"))
        assertEquals("name,desc", url.queryParameter("sort"))
    }

    @Test
    fun mapsDetailAndSortsInstructions() = runBlocking {
        server.enqueue(jsonResponse(detailJson()))

        val result = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success

        assertEquals("Descripción", result.value.description)
        assertEquals(listOf(MuscleGroup.Triceps), result.value.secondaryMuscleGroups)
        assertEquals(listOf(1, 2), result.value.instructions.map { it.position })
        assertEquals("/api/v1/exercise-templates/$ID", server.takeRequest().path)
    }

    @Test
    fun preservesProblemDetailsAndCorrelationId() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setHeader("X-Correlation-ID", "catalog-correlation")
                .setBody(
                    """{"title":"Invalid parameter","status":400,"errorCode":"INVALID_PARAMETER"}"""
                )
        )

        val result = listCall() as ExerciseRepositoryResult.Failure
        val error = result.error as NetworkFailure.HttpProblem

        assertEquals(400, error.statusCode)
        assertEquals("INVALID_PARAMETER", error.problem.errorCode)
        assertEquals("catalog-correlation", error.correlationId)
    }

    @Test
    fun returns401ForExistingSessionMechanismToHandle() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"title":"Unauthorized","status":401,"errorCode":"UNAUTHORIZED"}""")
        )

        val result = listCall() as ExerciseRepositoryResult.Failure

        assertEquals(401, (result.error as NetworkFailure.HttpProblem).statusCode)
    }

    @Test
    fun rejectsUnknownEnumAndMalformedSuccess() = runBlocking {
        server.enqueue(jsonResponse(pageJson().replace("CHEST", "UNKNOWN_MUSCLE")))
        val unknownEnum = listCall() as ExerciseRepositoryResult.Failure
        assertTrue(unknownEnum.error is NetworkFailure.InvalidResponse)

        server.enqueue(jsonResponse("""{"content":[]}"""))
        val malformed = listCall() as ExerciseRepositoryResult.Failure
        assertTrue(malformed.error is NetworkFailure.InvalidResponse)
    }

    @Test
    fun rejectsDuplicateIdsAndInvalidArgumentsWithoutNetworkCall() = runBlocking {
        server.enqueue(jsonResponse(pageJson(twoIdenticalItems = true)))
        val duplicates = listCall() as ExerciseRepositoryResult.Failure
        assertTrue(duplicates.error is NetworkFailure.InvalidResponse)

        val invalid = repository().getExerciseTemplates(
            query = null,
            filters = ExerciseFilters(),
            page = -1,
            size = 101,
            sort = ExerciseSort.NameAscending,
        ) as ExerciseRepositoryResult.Failure
        assertTrue(invalid.error is NetworkFailure.InvalidResponse)
        assertEquals(1, server.requestCount)
    }

    private suspend fun listCall(): ExerciseRepositoryResult<*> = repository().getExerciseTemplates(
        query = null,
        filters = ExerciseFilters(),
        page = 0,
        size = 20,
        sort = ExerciseSort.NameAscending,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun repository(): DefaultExerciseTemplateRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                NetworkJson.instance.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(ExerciseTemplateApi::class.java)
        return DefaultExerciseTemplateRepository(api)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun pageJson(
        page: Int = 0,
        last: Boolean = true,
        twoIdenticalItems: Boolean = false,
    ): String {
        val item = """
            {
              "id":"$ID",
              "slug":"press-banca",
              "name":"Press de banca",
              "primaryMuscleGroup":"CHEST",
              "equipment":"BARBELL",
              "exerciseType":"WEIGHT_REPS",
              "movementPattern":"HORIZONTAL_PUSH"
            }
        """.trimIndent()
        return """
            {
              "content":[$item${if (twoIdenticalItems) ",$item" else ""}],
              "page":$page,
              "size":20,
              "totalElements":42,
              "totalPages":3,
              "first":${page == 0},
              "last":$last
            }
        """.trimIndent()
    }

    private fun detailJson(): String = """
        {
          "id":"$ID",
          "slug":"press-banca",
          "name":"Press de banca",
          "description":"Descripción",
          "primaryMuscleGroup":"CHEST",
          "secondaryMuscleGroups":["TRICEPS"],
          "equipment":"BARBELL",
          "exerciseType":"WEIGHT_REPS",
          "movementPattern":"HORIZONTAL_PUSH",
          "instructions":[
            {"position":2,"text":"Empuja"},
            {"position":1,"text":"Colócate"}
          ]
        }
    """.trimIndent()

    private companion object {
        const val ID = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11"
    }
}
