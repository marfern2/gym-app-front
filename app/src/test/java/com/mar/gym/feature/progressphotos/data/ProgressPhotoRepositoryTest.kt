package com.mar.gym.feature.progressphotos.data

import android.net.Uri
import com.mar.gym.core.network.ImagePartSource
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.core.network.SelectedImageResult
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

class ProgressPhotoRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ProgressPhotoApi
    private lateinit var repository: DefaultProgressPhotoRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(ProgressPhotoApi::class.java)
        repository = DefaultProgressPhotoRepository(api, object : ImagePartSource {
            override fun from(uri: Uri, maxBytes: Long): SelectedImageResult {
                assertEquals(10_000_000, maxBytes)
                return SelectedImageResult.Ready(MultipartBody.Part.createFormData("file", "image",
                    byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType())))
            }
        })
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `list and detail parse private photos and page fields`() = runTest {
        enqueue("""{"content":[${photo()}],"page":0,"size":20,"totalElements":1,"totalPages":1,"first":true,"last":true}""")
        val page = (repository.list(0) as ProgressPhotoResult.Success).value
        assertEquals("/api/v1/progress-photos?page=0&size=20", server.takeRequest().path)
        assertTrue(page.last)
        assertEquals(BigDecimal("72.5"), page.content.single().weightKg)
        assertTrue(page.content.single().media.url.contains("/api/v1/media/$MEDIA_ID"))

        enqueue(photo())
        val detail = (repository.detail(ID) as ProgressPhotoResult.Success).value
        assertEquals("/api/v1/progress-photos/$ID", server.takeRequest().path)
        assertEquals(ID, detail.id)
    }

    @Test fun `delete uses owner endpoint and invalid ids never reach network`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.delete(ID) is ProgressPhotoResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/progress-photos/$ID", request.path)
        assertTrue(repository.detail("bad") is ProgressPhotoResult.Error)
        assertEquals(1, server.requestCount)
    }

    @Test fun `malformed media and dates fail safely`() = runTest {
        enqueue(photo().replace("\"width\":100", "\"width\":0"))
        assertTrue(repository.detail(ID) is ProgressPhotoResult.Error)
        server.takeRequest()
        enqueue(photo().replace("2026-01-01T10:15:00Z", "not-a-date"))
        assertTrue(repository.detail(ID) is ProgressPhotoResult.Error)
    }

    @Test fun `upload API sends multipart fields and omits absent optionals`() = runTest {
        val file = MultipartBody.Part.createFormData("file", "image",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()))
        enqueue(photo(), 201)
        assertTrue(api.upload(file, "2026-01-01T10:15:00Z".toRequestBody("text/plain".toMediaType()),
            "72.5".toRequestBody("text/plain".toMediaType()),
            "nota".toRequestBody("text/plain".toMediaType())).isSuccessful)
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/progress-photos", request.path)
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("name=\"takenAt\""))
        assertTrue(body.contains("name=\"weightKg\""))
        assertTrue(body.contains("name=\"note\""))

        enqueue(photo(), 201)
        assertTrue(api.upload(file, "2026-01-01T10:15:00Z".toRequestBody("text/plain".toMediaType()),
            null, null).isSuccessful)
        val emptyBody = server.takeRequest().body.readUtf8()
        assertFalse(emptyBody.contains("name=\"weightKg\""))
        assertFalse(emptyBody.contains("name=\"note\""))
    }

    @Test fun `repository upload sends canonical fields and maps response`() = runTest {
        val file = MultipartBody.Part.createFormData("file", "image",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()))
        enqueue(photo(), 201)
        val result = repository.uploadPrepared(file, Instant.parse("2026-01-01T10:15:00Z"),
            BigDecimal("45.359"), normalizedProgressPhotoNote("  nota  "))
        assertTrue(result is ProgressPhotoResult.Success)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("45.359"))
        assertTrue(body.contains("2026-01-01T10:15:00Z"))
        assertTrue(body.contains("\r\nnota\r\n"))
    }

    private fun enqueue(body: String, status: Int = 200) = server.enqueue(MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))

    private fun photo() = """{"id":"$ID","takenAt":"2026-01-01T10:15:00Z","weightKg":72.5,"note":"nota","createdAt":"2026-01-01T10:16:00Z","media":{"id":"$MEDIA_ID","url":"/api/v1/media/$MEDIA_ID","width":100,"height":200}}"""

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val MEDIA_ID = "00000000-0000-4000-8000-000000000002"
    }
}
