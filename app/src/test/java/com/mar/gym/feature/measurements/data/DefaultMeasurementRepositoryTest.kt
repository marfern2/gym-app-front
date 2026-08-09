package com.mar.gym.feature.measurements.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.measurements.model.BodyMeasurementUnit
import java.time.Instant
import kotlinx.coroutines.test.runTest
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

class DefaultMeasurementRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultMeasurementRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(MeasurementApi::class.java)
        repository = DefaultMeasurementRepository(api)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `create sends measuredAt without unit and maps canonical unit`() = runTest {
        enqueue(measurement("BODY_WEIGHT", "82.4", "KG", 0), 201, "\"0\"")
        val result = repository.create(
            BodyMeasurementDraft(BodyMeasurementType.BodyWeight, "82.4", MEASURED), NOW,
        ) as MeasurementResult.Success
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("2026-01-01T10:15:00Z"))
        assertFalse(body.contains("unit"))
        assertEquals(BodyMeasurementUnit.Kg, result.value.value.unit)
        assertEquals(MEASURED, result.value.value.measuredAt)
    }

    @Test fun `latest maps only real canonical values`() = runTest {
        enqueue("[${measurement("BODY_WEIGHT", "82.4", "KG", 0)},${measurement("WAIST", "81", "CM", 0, ID_TWO)}]")
        val values = (repository.latest() as MeasurementResult.Success).value
        assertEquals(listOf(BodyMeasurementType.BodyWeight, BodyMeasurementType.Waist), values.map { it.type })
        assertEquals(listOf(BodyMeasurementUnit.Kg, BodyMeasurementUnit.Cm), values.map { it.unit })
    }

    @Test fun `list sends type filter and preserves pagination`() = runTest {
        enqueue("""{"content":[${measurement("WAIST", "81", "CM", 0)}],"page":1,"size":20,"totalElements":21,"totalPages":2,"first":false,"last":true}""")
        val page = (repository.list(BodyMeasurementType.Waist, 1) as MeasurementResult.Success).value
        assertTrue(server.takeRequest().path!!.contains("type=WAIST"))
        assertEquals(1, page.page)
        assertTrue(page.last)
    }

    @Test fun `edit uses If-Match and delete follows backend contract`() = runTest {
        enqueue(measurement("BODY_WEIGHT", "82.4", "KG", 0), etag = "\"0\"")
        val current = (repository.detail(ID) as MeasurementResult.Success).value
        server.takeRequest()
        enqueue(measurement("WAIST", "81.25", "CM", 1), etag = "\"1\"")
        val updated = repository.update(
            current, BodyMeasurementDraft(BodyMeasurementType.Waist, "81.25", MEASURED), NOW,
        ) as MeasurementResult.Success
        val updateRequest = server.takeRequest()
        assertEquals("\"0\"", updateRequest.getHeader("If-Match"))
        assertEquals(BodyMeasurementUnit.Cm, updated.value.value.unit)

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(repository.delete(ID) is MeasurementResult.Success)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals(null, delete.getHeader("If-Match"))
    }

    @Test fun `conflict and hidden foreign measurement errors remain distinguishable`() = runTest {
        enqueueProblem(409, "BODY_MEASUREMENT_VERSION_CONFLICT")
        val conflict = repository.detail(ID) as MeasurementResult.Failure
        assertEquals("BODY_MEASUREMENT_VERSION_CONFLICT", (conflict.error as NetworkFailure.HttpProblem).problem.errorCode)
        enqueueProblem(404, "BODY_MEASUREMENT_NOT_FOUND")
        val missing = repository.delete(ID) as MeasurementResult.Failure
        assertEquals("BODY_MEASUREMENT_NOT_FOUND", (missing.error as NetworkFailure.HttpProblem).problem.errorCode)
    }

    @Test fun `future measuredAt and invalid percentage are rejected before HTTP`() = runTest {
        assertTrue(repository.create(BodyMeasurementDraft(BodyMeasurementType.BodyWeight, "80", NOW.plusSeconds(1)), NOW) is MeasurementResult.Failure)
        assertTrue(repository.create(BodyMeasurementDraft(BodyMeasurementType.BodyFatPercentage, "100.001", MEASURED), NOW) is MeasurementResult.Failure)
        assertEquals(0, server.requestCount)
    }

    private fun enqueue(body: String, status: Int = 200, etag: String? = null) {
        val response = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
        etag?.let { response.setHeader("ETag", it) }
        server.enqueue(response)
    }
    private fun enqueueProblem(status: Int, code: String) = server.enqueue(
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/problem+json")
            .setBody("""{"status":$status,"errorCode":"$code"}"""),
    )
    private fun measurement(type: String, value: String, unit: String, version: Int, id: String = ID) =
        """{"id":"$id","type":"$type","value":$value,"unit":"$unit","measuredAt":"$MEASURED","createdAt":"2026-01-01T10:16:00Z","updatedAt":"2026-01-01T10:16:00Z","version":$version}"""

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val ID_TWO = "00000000-0000-4000-8000-000000000002"
        val MEASURED: Instant = Instant.parse("2026-01-01T10:15:00Z")
        val NOW: Instant = Instant.parse("2026-01-02T10:15:00Z")
    }
}
