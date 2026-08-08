package com.mar.gym.feature.exercises.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_NO_RETRY
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
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
        assertEquals(ExerciseTemplateSource.Global, result.value.content.single().source)
        assertFalse(result.value.content.single().archived)
        assertEquals(7L, result.value.content.single().version)
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
                source = ExerciseTemplateSource.Custom,
                archived = true,
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
        assertEquals("CUSTOM", request.requestUrl?.queryParameter("source"))
        assertEquals("true", request.requestUrl?.queryParameter("includeArchived"))
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
        assertEquals("false", url.queryParameter("includeArchived"))
        assertEquals("name,desc", url.queryParameter("sort"))
    }

    @Test
    fun mapsDetailAndSortsInstructions() = runBlocking {
        server.enqueue(jsonResponse(detailJson()))

        val result = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success

        assertEquals("Descripción", result.value.detail.description)
        assertEquals(listOf(MuscleGroup.Triceps), result.value.detail.secondaryMuscleGroups)
        assertEquals(listOf(1, 2), result.value.detail.instructions.map { it.position })
        assertTrue(result.value.detail.media.isEmpty())
        assertEquals(7L, result.value.etag.version)
        assertEquals("/api/v1/exercise-templates/$ID", server.takeRequest().path)
    }

    @Test
    fun mapsCustomArchivedAndVersion() = runBlocking {
        server.enqueue(jsonResponse(detailJson(source = "CUSTOM", archived = true, version = 9), etag = "\"9\""))

        val result = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success

        assertEquals(ExerciseTemplateSource.Custom, result.value.detail.source)
        assertTrue(result.value.detail.archived)
        assertEquals(9L, result.value.detail.version)
        assertEquals(9L, result.value.etag.version)
    }

    @Test
    fun createEditArchiveAndRestoreUseCanonicalDocumentsAndIfMatch() = runBlocking {
        val draft = CustomExerciseDraft(
            name = "  Press propio  ",
            exerciseType = ExerciseType.WeightReps,
            primaryMuscleGroup = MuscleGroup.Chest,
            secondaryMuscleGroups = setOf(MuscleGroup.Triceps),
            equipment = Equipment.Barbell,
            movementPattern = MovementPattern.HorizontalPush,
            instructions = listOf("Preparar", "Empujar"),
        )
        server.enqueue(jsonResponse(detailJson(source = "CUSTOM"), 201, "\"7\""))
        val created = repository().createCustomExercise(draft)
        assertTrue(created is ExerciseRepositoryResult.Success)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/exercise-templates/custom", create.path)
        assertEquals(AUTHENTICATION_NO_RETRY, create.getHeader(AUTHENTICATION_REQUIRED_HEADER))
        val createBody = create.body.readUtf8()
        assertTrue(createBody.contains("\"name\":\"Press propio\""))
        assertTrue(createBody.contains("\"instructions\":[\"Preparar\",\"Empujar\"]"))
        assertFalse(createBody.contains("owner"))
        assertFalse(createBody.contains("slug"))
        assertFalse(createBody.contains("version"))

        server.enqueue(jsonResponse(detailJson(source = "CUSTOM", version = 8), etag = "\"8\""))
        repository().replaceCustomExercise(
            draft.copy(exerciseTemplateId = ID),
            ExerciseTemplateEtag.fromVersion(7)!!,
        )
        val replace = server.takeRequest()
        assertEquals("PUT", replace.method)
        assertEquals("\"7\"", replace.getHeader("If-Match"))
        assertEquals(AUTHENTICATION_NO_RETRY, replace.getHeader(AUTHENTICATION_REQUIRED_HEADER))

        server.enqueue(jsonResponse(detailJson(source = "CUSTOM", archived = true, version = 9), etag = "\"9\""))
        repository().archiveCustomExercise(ID, ExerciseTemplateEtag.fromVersion(8)!!)
        val archive = server.takeRequest()
        assertEquals("/api/v1/exercise-templates/$ID/archive", archive.path)
        assertEquals("\"8\"", archive.getHeader("If-Match"))

        server.enqueue(jsonResponse(detailJson(source = "CUSTOM", version = 10), etag = "\"10\""))
        repository().restoreCustomExercise(ID, ExerciseTemplateEtag.fromVersion(9)!!)
        val restore = server.takeRequest()
        assertEquals("/api/v1/exercise-templates/$ID/restore", restore.path)
        assertEquals("\"9\"", restore.getHeader("If-Match"))
    }

    @Test
    fun rejectsMissingOrMismatchedEtagAndPreservesConflictProblem() = runBlocking {
        server.enqueue(jsonResponse(detailJson(), etag = null))
        assertTrue(
            (repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Failure).error
                is NetworkFailure.InvalidResponse
        )

        server.enqueue(jsonResponse(detailJson(version = 7), etag = "\"6\""))
        assertTrue(
            (repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Failure).error
                is NetworkFailure.InvalidResponse
        )

        server.enqueue(
            MockResponse().setResponseCode(409)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"status":409,"errorCode":"EXERCISE_TEMPLATE_VERSION_CONFLICT"}""")
        )
        val conflict = repository().archiveCustomExercise(ID, ExerciseTemplateEtag.fromVersion(7)!!)
            as ExerciseRepositoryResult.Failure
        assertEquals(
            "EXERCISE_TEMPLATE_VERSION_CONFLICT",
            (conflict.error as NetworkFailure.HttpProblem).problem.errorCode,
        )
        assertEquals(1, server.requestCount - 2)
    }

    @Test
    fun mapsGifAttributionAndNullableDimensions() = runBlocking {
        server.enqueue(jsonResponse(detailJson(media = validGifMedia())))

        val result = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success
        val media = result.value.detail.media.single()

        assertEquals(ExerciseMediaType.AnimatedGif, media.type)
        assertEquals(ExerciseMediaRole.Demonstration, media.role)
        assertEquals("https://static.exercisedb.dev/media/example.gif", media.url.value)
        assertEquals(null, media.width)
        assertEquals(null, media.height)
        assertEquals("Contenido visual: ExerciseDB / AscendAPI", media.attribution?.text)
        assertEquals("https://exercisedb.dev/", media.attribution?.url?.value)
    }

    @Test
    fun rejectsUnknownMediaEnumAsIncompatibleResponse() = runBlocking {
        server.enqueue(
            jsonResponse(detailJson(media = validGifMedia().replace("ANIMATED_GIF", "UNKNOWN")))
        )

        val result = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Failure

        assertTrue(result.error is NetworkFailure.InvalidResponse)
    }

    @Test
    fun treatsHttpMediaAsUnavailableAndDoesNotExposeHttpAttribution() = runBlocking {
        val httpMedia = validGifMedia()
            .replace("https://static.exercisedb.dev", "http://static.exercisedb.dev")
        server.enqueue(jsonResponse(detailJson(media = httpMedia)))
        val unavailable = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success
        assertTrue(unavailable.value.detail.media.isEmpty())

        val invalidAttribution = validGifMedia()
            .replace("https://exercisedb.dev/", "http://exercisedb.dev/")
        server.enqueue(jsonResponse(detailJson(media = invalidAttribution)))
        val mapped = repository().getExerciseTemplate(ID) as ExerciseRepositoryResult.Success
        assertEquals(null, mapped.value.detail.media.single().attribution?.url)
        assertEquals("Contenido visual: ExerciseDB / AscendAPI", mapped.value.detail.media.single().attribution?.text)
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

    private fun jsonResponse(
        body: String,
        status: Int = 200,
        etag: String? = "\"7\"",
    ): MockResponse = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .apply { etag?.let { setHeader("ETag", it) } }
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
              "movementPattern":"HORIZONTAL_PUSH",
              "source":"GLOBAL",
              "archived":false,
              "version":7
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

    private fun detailJson(
        media: String = "[]",
        source: String = "GLOBAL",
        archived: Boolean = false,
        version: Long = 7,
    ): String = """
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
          "source":"$source",
          "archived":$archived,
          "version":$version,
          "instructions":[
            {"position":2,"text":"Empuja"},
            {"position":1,"text":"Colócate"}
          ],
          "media":$media
        }
    """.trimIndent()

    private fun validGifMedia(): String = """
        [{
          "type":"ANIMATED_GIF",
          "role":"DEMONSTRATION",
          "url":"https://static.exercisedb.dev/media/example.gif",
          "width":null,
          "height":null,
          "attribution":{
            "text":"Contenido visual: ExerciseDB / AscendAPI",
            "url":"https://exercisedb.dev/"
          }
        }]
    """.trimIndent()

    private companion object {
        const val ID = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11"
    }
}
