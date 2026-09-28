package com.mar.gym.core.network

import com.mar.gym.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlTest {
    private val id = "00000000-0000-4000-8000-000000000001"

    @Test fun resolvesBackendMediaPath() {
        assertEquals(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/media/$id",
            mediaUrl("/api/v1/media/$id"))
    }

    @Test fun rejectsUnexpectedRelativePathsAndMalformedMediaIds() {
        assertNull(mediaUrl("/api/v1/users/me"))
        assertNull(mediaUrl("/api/v1/media/invalid"))
        assertNull(mediaUrl(null))
    }
}
