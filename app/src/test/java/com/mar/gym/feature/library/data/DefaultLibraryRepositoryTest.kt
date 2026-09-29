package com.mar.gym.feature.library.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.library.model.ProgramDayInput
import com.mar.gym.feature.library.model.ProgramInput
import com.mar.gym.feature.routines.model.RoutineEtag
import kotlinx.coroutines.runBlocking
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

class DefaultLibraryRepositoryTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun libraryMapsFoldersRoutinesAndProgramsAndRejectsInvalidBody() = runBlocking {
        server.enqueue(json(libraryJson()))
        val result = repository().library() as LibraryResult.Success
        assertEquals(FOLDER, result.value.routines.single().folderId)
        assertEquals(1, result.value.programs.single().daysCount)
        assertEquals("/api/v1/library", server.takeRequest().path)

        server.enqueue(json("""{"folders":[],"routines":[],"programs":[]}"""))
        assertEquals(0, (repository().library() as LibraryResult.Success).value.routines.size)
        server.takeRequest()

        server.enqueue(json(libraryJson().replace("\"folderId\":\"$FOLDER\"", "\"folderId\":\"$OTHER\"")))
        assertTrue((repository().library() as LibraryResult.Failure).error is NetworkFailure.InvalidResponse)
        server.takeRequest()
        server.enqueue(problem(500, "SERVER_ERROR"))
        assertTrue(repository().library() is LibraryResult.Failure)
    }

    @Test fun folderLifecycleAndRoutineMoveReorderUseRealContracts() = runBlocking {
        val repo = repository()
        server.enqueue(json(folderJson(), 201))
        assertEquals(FOLDER, (repo.createFolder(" Fuerza ") as LibraryResult.Success).value.id)
        assertEquals("{\"name\":\"Fuerza\"}", server.takeRequest().body.readUtf8())

        server.enqueue(json(folderJson(name = "Casa")))
        assertEquals("Casa", (repo.renameFolder(FOLDER, "Casa") as LibraryResult.Success).value.name)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"name\":\"Casa\""))

        server.enqueue(json(folderJson(position = 2)))
        assertEquals(2, (repo.reorderFolder(FOLDER, 2) as LibraryResult.Success).value.position)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"position\":2"))

        val routine = (run { server.enqueue(json(libraryJson())); repo.library() } as LibraryResult.Success).value.routines.single()
        server.takeRequest()
        server.enqueue(json(routineJson(folderId = null, version = 8), etag = "\"8\""))
        val moved = (repo.moveRoutine(routine, null) as LibraryResult.Success).value
        val moveRequest = server.takeRequest()
        assertEquals("/api/v1/routines/$ROUTINE/folder", moveRequest.path)
        assertEquals("\"7\"", moveRequest.getHeader("If-Match"))
        assertTrue(moveRequest.body.readUtf8().contains("\"folderId\":null"))
        assertEquals(null, moved.folderId)
        assertEquals("\"8\"", moved.etag.headerValue)

        server.enqueue(json(routineJson(folderId = FOLDER, version = 9), etag = "\"9\""))
        assertEquals(FOLDER, (repo.moveRoutine(moved, FOLDER) as LibraryResult.Success).value.folderId)
        assertEquals("\"8\"", server.takeRequest().getHeader("If-Match"))

        server.enqueue(json(routineJson(folderId = FOLDER, position = 0, version = 10), etag = "\"10\""))
        val reordered = repo.reorderRoutine(routine.copy(etag = RoutineEtag.fromVersion(9)!!), 0) as LibraryResult.Success
        assertEquals(0, reordered.value.position)
        val orderRequest = server.takeRequest()
        assertEquals("/api/v1/routines/$ROUTINE/position", orderRequest.path)
        assertEquals("\"9\"", orderRequest.getHeader("If-Match"))
        assertTrue(orderRequest.body.readUtf8().contains("\"position\":0"))

        server.enqueue(problem(409, "LIBRARY_VERSION_CONFLICT"))
        val conflict = repo.moveRoutine(routine, null) as LibraryResult.Failure
        assertEquals(409, (conflict.error as NetworkFailure.HttpProblem).statusCode)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.deleteFolder(FOLDER) is LibraryResult.Success)
        assertEquals("DELETE", server.takeRequest().method)
        server.enqueue(json(libraryJson(folderId = null, folder = false)))
        val afterDelete = (repo.library() as LibraryResult.Success).value
        assertTrue(afterDelete.folders.isEmpty())
        assertEquals(ROUTINE, afterDelete.routines.single().id)
    }

    @Test fun programCreateDetailEditDeleteAndRepeatedRoutineUseEtag() = runBlocking {
        val repo = repository()
        val input = ProgramInput("Fuerza", null, listOf(ProgramDayInput(ROUTINE, null), ProgramDayInput(ROUTINE, "Día B")))
        server.enqueue(json(programJson(version = 0), 201, "\"0\""))
        val created = repo.createProgram(input) as LibraryResult.Success
        assertEquals(2, created.value.days.size)
        assertEquals(ROUTINE, created.value.days[1].routineId)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/programs", create.path)
        assertEquals(2, Regex("routineId").findAll(create.body.readUtf8()).count())

        server.enqueue(json(programJson(version = 0), etag = "\"0\""))
        assertEquals("\"0\"", (repo.program(PROGRAM) as LibraryResult.Success).value.etag.headerValue)
        assertEquals("/api/v1/programs/$PROGRAM", server.takeRequest().path)

        server.enqueue(json(programJson(version = 1), etag = "\"1\""))
        val updated = repo.updateProgram(PROGRAM, created.value.etag,
            input.copy(days = listOf(input.days[1], input.days[0]))) as LibraryResult.Success
        assertEquals(1L, updated.value.etag.version)
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("\"0\"", update.getHeader("If-Match"))

        server.enqueue(problem(409, "LIBRARY_VERSION_CONFLICT"))
        assertTrue(repo.updateProgram(PROGRAM, created.value.etag, input) is LibraryResult.Failure)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repo.deleteProgram(PROGRAM, updated.value.etag) is LibraryResult.Success)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("\"1\"", delete.getHeader("If-Match"))
    }

    private fun repository(): DefaultLibraryRepository {
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(LibraryApi::class.java)
        return DefaultLibraryRepository(api)
    }
    private fun json(body: String, status: Int = 200, etag: String? = null) = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json")
        .setBody(body).apply { if (etag != null) setHeader("ETag", etag) }
    private fun problem(status: Int, code: String) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/problem+json")
        .setBody("""{"status":$status,"errorCode":"$code","title":"Conflict"}""")
    private fun folderJson(name: String = "Fuerza", position: Int = 1) =
        """{"id":"$FOLDER","name":"$name","position":$position,"routineCount":1,"createdAt":"$DATE","updatedAt":"$DATE"}"""
    private fun routineJson(folderId: String? = FOLDER, position: Int = 0, version: Int = 7) =
        """{"id":"$ROUTINE","folderId":${folderId?.let { "\"$it\"" } ?: "null"},"position":$position,"name":"Fuerza A","description":null,"archived":false,"exerciseCount":2,"version":$version,"updatedAt":"$DATE"}"""
    private fun libraryJson(folderId: String? = FOLDER, folder: Boolean = true) =
        """{"folders":[${if (folder) folderJson() else ""}],"routines":[${routineJson(folderId)}],"programs":[{"id":"$PROGRAM","name":"Plan","description":null,"daysCount":1,"updatedAt":"$DATE","version":0}]}"""
    private fun programJson(version: Int) = """{"id":"$PROGRAM","name":"Fuerza","description":null,"version":$version,"createdAt":"$DATE","updatedAt":"$DATE","days":[{"id":"$DAY1","position":1,"label":null,"routineId":"$ROUTINE","routineName":"Fuerza A","routineArchived":false},{"id":"$DAY2","position":2,"label":"Día B","routineId":"$ROUTINE","routineName":"Fuerza A","routineArchived":false}]}"""
    private companion object {
        const val FOLDER = "00000000-0000-0000-0000-000000000001"
        const val ROUTINE = "00000000-0000-0000-0000-000000000002"
        const val PROGRAM = "00000000-0000-0000-0000-000000000003"
        const val DAY1 = "00000000-0000-0000-0000-000000000004"
        const val DAY2 = "00000000-0000-0000-0000-000000000005"
        const val OTHER = "00000000-0000-0000-0000-000000000006"
        const val DATE = "2026-09-29T10:00:00Z"
    }
}
