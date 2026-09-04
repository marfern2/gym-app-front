package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkJson
import com.mar.gym.feature.exercises.model.ExerciseType
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

class DefaultSocialFeedRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultSocialFeedRepository

    @OptIn(ExperimentalSerializationApi::class)
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build().create(SocialFeedApi::class.java)
        repository = DefaultSocialFeedRepository(api)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `feed maps current summary contract without detail requests`() = runTest {
        enqueue(feedPage(nextCursor = "opaque", hasMore = true))
        val result = repository.feed(null, 20) as SocialResult.Success

        assertEquals(1, result.value.content.size)
        assertEquals("Push day", result.value.content.single().title)
        assertEquals("1250.5", result.value.content.single().totalVolumeKg.toPlainString())
        assertEquals(1, result.value.content.single().remainingExercisesCount)
        assertEquals(7, result.value.content.single().likesCount)
        assertTrue(result.value.content.single().isLikedByMe)
        assertEquals(3, result.value.content.single().commentsCount)
        assertEquals("/api/v1/feed?size=20", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `feed accepts author without username and legacy engagement omissions`() = runTest {
        enqueue(feedPage(nextCursor = null, hasMore = false, authorUsername = null, includeEngagement = false))

        val result = repository.feed(null, 20) as SocialResult.Success
        val workout = result.value.content.single()

        assertEquals(null, workout.author.username)
        assertEquals("Alice", workout.author.displayName)
        assertEquals(0, workout.likesCount)
        assertFalse(workout.isLikedByMe)
        assertEquals(0, workout.commentsCount)
    }

    @Test fun `user workouts passes opaque cursor and suggestions remove followed users`() = runTest {
        enqueue(feedPage(nextCursor = null, hasMore = false))
        repository.userWorkouts(" Alice ", "opaque", 20)
        assertEquals("/api/v1/users/alice/workouts?cursor=opaque&size=20", server.takeRequest().path)

        enqueue(
            """{"content":[${athlete("alice", false)},${athlete("bob", true)}],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true}""",
        )
        val suggestions = repository.suggestions(0, 20) as SocialResult.Success
        assertEquals(listOf("alice"), suggestions.value.content.map { it.username })
        assertEquals("/api/v1/users/suggestions?page=0&size=20", server.takeRequest().path)
    }

    @Test fun `detail maps only completed actual sets and exercise type`() = runTest {
        enqueue(detail())
        val result = repository.workoutDetail(WORKOUT_ID) as SocialResult.Success

        val exercise = result.value.exercises.single()
        assertEquals(ExerciseType.WeightReps, exercise.exerciseType)
        assertEquals(8, exercise.sets.single().reps)
        assertEquals("80.5", exercise.sets.single().weightKg?.toPlainString())
        assertEquals("/api/v1/feed/workouts/$WORKOUT_ID", server.takeRequest().path)
        assertEquals(7, result.value.likesCount)
        assertTrue(result.value.isLikedByMe)
        assertEquals(3, result.value.commentsCount)
    }

    @Test fun `like and unlike use workout scoped idempotent endpoints`() = runTest {
        enqueue("", status = 204)
        assertTrue(repository.like(WORKOUT_ID) is SocialResult.Success)
        server.takeRequest().let {
            assertEquals("PUT", it.method)
            assertEquals("/api/v1/feed/workouts/$WORKOUT_ID/like", it.path)
        }

        enqueue("", status = 204)
        assertTrue(repository.unlike(WORKOUT_ID) is SocialResult.Success)
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/feed/workouts/$WORKOUT_ID/like", it.path)
        }
    }

    @Test fun `comments map page create trims text and delete uses comment id`() = runTest {
        enqueue(commentPage())
        val page = repository.comments(WORKOUT_ID, 0, 30) as SocialResult.Success
        assertEquals("Hello", page.value.content.single().text)
        assertEquals(1, page.value.totalElements)
        assertEquals("/api/v1/feed/workouts/$WORKOUT_ID/comments?page=0&size=30", server.takeRequest().path)

        enqueue(comment())
        val created = repository.createComment(WORKOUT_ID, "  Hello  ") as SocialResult.Success
        assertEquals(COMMENT_ID, created.value.id)
        server.takeRequest().let {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/feed/workouts/$WORKOUT_ID/comments", it.path)
            assertEquals("{\"text\":\"Hello\"}", it.body.readUtf8())
        }

        enqueue("", status = 204)
        assertTrue(repository.deleteComment(WORKOUT_ID, COMMENT_ID) is SocialResult.Success)
        server.takeRequest().let {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/feed/workouts/$WORKOUT_ID/comments/$COMMENT_ID", it.path)
        }
    }

    @Test fun `invalid comments and negative social counts fail safely`() = runTest {
        assertTrue(repository.createComment(WORKOUT_ID, "   ") is SocialResult.Failure)
        assertTrue(repository.createComment(WORKOUT_ID, "x".repeat(1001)) is SocialResult.Failure)
        assertTrue(repository.comments(WORKOUT_ID, -1, 30) is SocialResult.Failure)
        assertEquals(0, server.requestCount)

        enqueue(feedPage(nextCursor = null, hasMore = false).replace("\"likesCount\":7", "\"likesCount\":-1"))
        assertTrue(repository.feed(null, 20) is SocialResult.Failure)
    }

    @Test fun `invalid cursor and non completed detail fail safely`() = runTest {
        assertTrue(repository.feed(" ", 20) is SocialResult.Failure)
        assertEquals(0, server.requestCount)

        enqueue(detail(status = "ACTIVE"))
        assertTrue(repository.workoutDetail(WORKOUT_ID) is SocialResult.Failure)
    }

    private fun enqueue(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun feedPage(
        nextCursor: String?,
        hasMore: Boolean,
        authorUsername: String? = "alice",
        includeEngagement: Boolean = true,
    ): String = """
        {"content":[{"workoutId":"$WORKOUT_ID","completedAt":"2026-08-25T10:00:00Z","title":"Push day",
        "notes":"Good session","author":${author(authorUsername)},"durationSeconds":3600,"totalVolumeKg":1250.5,
        "completedSetsCount":6,"exercisesCount":2,"exercises":[{"exerciseTemplateId":"$TEMPLATE_ID",
        "name":"Bench press","completedSetsCount":3,"thumbnailUrl":null}],"remainingExercisesCount":1
        ${if (includeEngagement) ",\"likesCount\":7,\"isLikedByMe\":true,\"commentsCount\":3" else ""}}],
        "nextCursor":${nextCursor?.let { "\"$it\"" } ?: "null"},"hasMore":$hasMore}
    """.trimIndent()

    private fun detail(status: String = "COMPLETED") = """
        {"workoutId":"$WORKOUT_ID","title":"Push day","notes":null,"status":"$status",
        "startedAt":"2026-08-25T09:00:00Z","completedAt":"2026-08-25T10:00:00Z","durationSeconds":3600,
        "author":${author()},"likesCount":7,"isLikedByMe":true,"commentsCount":3,
        "exercises":[{"id":"$EXERCISE_ID","exerciseTemplateId":"$TEMPLATE_ID",
        "name":"Bench press","exerciseType":"WEIGHT_REPS","equipment":"BARBELL","position":1,
        "supersetGroup":null,"thumbnailUrl":null,"sets":[{"id":"$SET_ID","position":1,"setType":"NORMAL",
        "reps":8,"weightKg":80.5,"durationSeconds":null,"distanceMeters":null,"rpe":8.0}]}]}
    """.trimIndent()

    private fun commentPage() = """
        {"content":[${comment()}],"page":0,"size":30,"totalElements":1,"totalPages":1,
        "first":true,"last":true}
    """.trimIndent()

    private fun comment() = """
        {"id":"$COMMENT_ID","workoutId":"$WORKOUT_ID","author":${author()},"text":"Hello",
        "createdAt":"2026-08-25T10:05:00Z","updatedAt":null}
    """.trimIndent()

    private fun author(username: String? = "alice") =
        """{"userId":"$USER_ID","username":${username?.let { "\"$it\"" } ?: "null"},"displayName":"Alice","avatarUrl":null}"""
    private fun athlete(username: String, following: Boolean) =
        """{"userId":"${if (username == "alice") USER_ID else OTHER_USER_ID}","username":"$username","displayName":null,"avatarUrl":null,"completedWorkoutsCount":4,"followersCount":2,"isFollowing":$following}"""

    private companion object {
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000010"
        const val USER_ID = "00000000-0000-4000-8000-000000000001"
        const val OTHER_USER_ID = "00000000-0000-4000-8000-000000000002"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000020"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000030"
        const val SET_ID = "00000000-0000-4000-8000-000000000040"
        const val COMMENT_ID = "00000000-0000-4000-8000-000000000050"
    }
}
