package com.mar.gym.core.network

import com.mar.gym.feature.system.PingResponse
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkJsonTest {
    @Test
    fun decodesPingResponse() {
        val response = NetworkJson.instance.decodeFromString<PingResponse>(
            """{"status":"ok","timestamp":"2026-08-01T10:15:30Z","future":"ignored"}"""
        )

        assertEquals("ok", response.status)
        assertEquals("2026-08-01T10:15:30Z", response.timestamp)
    }

    @Test
    fun decodesProblemDetailsWithOptionalFields() {
        val problem = NetworkJson.instance.decodeFromString<ProblemDetails>(
            """
            {
              "type":"https://example.test/problems/validation",
              "title":"Validation failed",
              "status":400,
              "detail":"Request validation failed",
              "instance":"/api/v1/system/ping",
              "timestamp":"2026-08-01T10:15:30Z",
              "correlationId":"correlation-body",
              "errorCode":"VALIDATION_ERROR",
              "fieldErrors":{"name":["must not be blank"]}
            }
            """.trimIndent()
        )

        assertEquals(400, problem.status)
        assertEquals("VALIDATION_ERROR", problem.errorCode)
        assertEquals("correlation-body", problem.correlationId)
        val fieldErrors = requireNotNull(problem.fieldErrors).jsonObject
        assertEquals("must not be blank", fieldErrors.getValue("name").jsonArray.first().toString().trim('"'))
    }
}
