package com.mar.gym.feature.exercises.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpsUrlTest {
    @Test
    fun acceptsOnlyAbsoluteHttpsUrlsAndRedactsTextRepresentation() {
        val valid = HttpsUrl.parse("https://static.example.test/demo.gif")

        assertEquals("https://static.example.test/demo.gif", valid?.value)
        assertEquals("HttpsUrl(redacted)", valid.toString())
        listOf(
            "http://example.test/demo.gif",
            "file:///tmp/demo.gif",
            "content://media/demo.gif",
            "javascript:alert(1)",
            "data:image/gif;base64,R0lGODlh",
            "https://",
        ).forEach { assertNull(HttpsUrl.parse(it)) }
    }
}
