package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.social.model.SocialNotificationType
import kotlinx.coroutines.test.runTest
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

class DefaultSocialNotificationsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultSocialNotificationsRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SocialNotificationsApi::class.java)
        repository = DefaultSocialNotificationsRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `notification page maps all supported types and offset metadata`() = runTest {
        enqueue(pageBody())

        val result = repository.notifications(page = 0, size = 20) as SocialResult.Success

        assertEquals(
            listOf(SocialNotificationType.Follow, SocialNotificationType.WorkoutLike, SocialNotificationType.WorkoutComment),
            result.value.content.map { it.type },
        )
        assertEquals("alice", result.value.content.first().actor.username)
        assertEquals(WORKOUT_ID, result.value.content[1].workoutId)
        assertEquals(COMMENT_ID, result.value.content[2].commentId)
        assertEquals("/api/v1/notifications?page=0&size=20", server.takeRequest().path)
    }

    @Test fun `unread count accepts named contract and read endpoints are posts`() = runTest {
        enqueue("""{"unreadCount":3}""")
        assertEquals(3L, (repository.unreadCount() as SocialResult.Success).value)
        assertEquals("/api/v1/notifications/unread-count", server.takeRequest().path)

        enqueue("", 204)
        assertTrue(repository.markRead(NOTIFICATION_1) is SocialResult.Success)
        server.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/notifications/$NOTIFICATION_1/read", it.path)
        }

        enqueue("", 204)
        assertTrue(repository.markAllRead() is SocialResult.Success)
        server.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/notifications/read-all", it.path)
        }
    }

    @Test fun `legacy count key is tolerated but invalid payloads fail safely`() = runTest {
        enqueue("""{"count":2}""")
        assertEquals(2L, (repository.unreadCount() as SocialResult.Success).value)

        enqueue("""{"unreadCount":-1}""")
        assertTrue(repository.unreadCount() is SocialResult.Failure)

        enqueue(pageBody().replace("\"type\":\"FOLLOW\"", "\"type\":\"UNKNOWN\""))
        assertTrue(repository.notifications(0, 20) is SocialResult.Failure)

        assertTrue(repository.notifications(-1, 20) is SocialResult.Failure)
        assertTrue(repository.markRead("invalid") is SocialResult.Failure)
        assertEquals(3, server.requestCount)
    }

    private fun enqueue(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun pageBody() = """
        {"content":[
          {"id":"$NOTIFICATION_1","type":"FOLLOW","actor":${actor()},"createdAt":"2026-09-15T10:00:00Z",
           "read":false,"workoutId":null,"commentId":null,"targetAvailable":true},
          {"id":"$NOTIFICATION_2","type":"WORKOUT_LIKE","actor":${actor()},"createdAt":"2026-09-15T10:01:00Z",
           "read":true,"workoutId":"$WORKOUT_ID","commentId":null,"targetAvailable":true},
          {"id":"$NOTIFICATION_3","type":"WORKOUT_COMMENT","actor":${actor()},"createdAt":"2026-09-15T10:02:00Z",
           "read":false,"workoutId":"$WORKOUT_ID","commentId":"$COMMENT_ID","targetAvailable":false}],
         "page":0,"size":20,"totalElements":3,"totalPages":1,"first":true,"last":true}
    """.trimIndent()

    private fun actor() =
        """{"userId":"$USER_ID","username":"alice","displayName":"Alice","avatarUrl":"https://example.test/a.jpg"}"""

    private companion object {
        const val NOTIFICATION_1 = "00000000-0000-4000-8000-000000000001"
        const val NOTIFICATION_2 = "00000000-0000-4000-8000-000000000002"
        const val NOTIFICATION_3 = "00000000-0000-4000-8000-000000000003"
        const val USER_ID = "00000000-0000-4000-8000-000000000010"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000020"
        const val COMMENT_ID = "00000000-0000-4000-8000-000000000030"
    }
}
