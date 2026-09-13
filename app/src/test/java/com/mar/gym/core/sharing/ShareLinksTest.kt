package com.mar.gym.core.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinksTest {
    private val links = ShareLinks("https://links.example.test/app")

    @Test fun `profile workout and routine links use canonical paths`() {
        assertEquals("https://links.example.test/app/u/alice", links.profile("alice"))
        assertEquals("https://links.example.test/app/w/$WORKOUT_ID", links.workout(WORKOUT_ID))
        assertEquals("https://links.example.test/app/r/$SHARE_ID", links.routine(SHARE_ID))
        assertNull(links.profile(null))
        assertNull(links.profile("not valid"))
    }

    @Test fun `deep links route all supported resources`() {
        assertEquals(ShareDeepLink.Profile("alice"), links.parse("https://links.example.test/app/u/alice"))
        assertEquals(ShareDeepLink.Workout(WORKOUT_ID), links.parse("https://links.example.test/app/w/$WORKOUT_ID"))
        assertEquals(ShareDeepLink.Routine(SHARE_ID), links.parse("https://links.example.test/app/r/$SHARE_ID"))
    }

    @Test fun `invalid or foreign deep links are ignored safely`() {
        listOf(
            null,
            "not a uri",
            "https://evil.example/app/u/alice",
            "https://links.example.test/app/u/",
            "https://links.example.test/app/w/not-a-uuid",
            "https://links.example.test/app/r/$SHARE_ID/extra",
            "https://links.example.test/app/u/alice?token=secret",
        ).forEach { assertNull(links.parse(it)) }
    }

    private companion object {
        const val WORKOUT_ID = "11111111-1111-4111-8111-111111111111"
        const val SHARE_ID = "22222222-2222-4222-8222-222222222222"
    }
}
