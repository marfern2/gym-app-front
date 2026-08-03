package com.mar.gym.feature.exercises.ui

import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.HttpsUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseMediaSelectionTest {
    @Test
    fun selectsAnimatedGifDemonstrationBeforeImageRegardlessOfOrder() {
        val image = media(ExerciseMediaType.Image, ExerciseMediaRole.Demonstration, "image.jpg")
        val gif = media(ExerciseMediaType.AnimatedGif, ExerciseMediaRole.Demonstration, "demo.gif")

        assertEquals(gif, listOf(image, gif).selectDemonstrationMedia())
        assertEquals(gif, listOf(gif, image).selectDemonstrationMedia())
    }

    @Test
    fun fallsBackToImageDemonstration() {
        val image = media(ExerciseMediaType.Image, ExerciseMediaRole.Demonstration, "image.jpg")

        assertEquals(image, listOf(image).selectDemonstrationMedia())
    }

    @Test
    fun neverSelectsVideoOrNonDemonstrationMedia() {
        val video = media(ExerciseMediaType.Video, ExerciseMediaRole.Demonstration, "demo.mp4")
        val thumbnail = media(ExerciseMediaType.AnimatedGif, ExerciseMediaRole.Thumbnail, "thumb.gif")

        assertNull(listOf(video, thumbnail).selectDemonstrationMedia())
    }

    @Test
    fun usesBackendOrderAsDeterministicTieBreaker() {
        val first = media(ExerciseMediaType.AnimatedGif, ExerciseMediaRole.Demonstration, "first.gif")
        val second = media(ExerciseMediaType.AnimatedGif, ExerciseMediaRole.Demonstration, "second.gif")

        assertEquals(first, listOf(first, second).selectDemonstrationMedia())
    }

    private fun media(
        type: ExerciseMediaType,
        role: ExerciseMediaRole,
        fileName: String,
    ): ExerciseMedia = ExerciseMedia(
        type = type,
        role = role,
        url = requireNotNull(HttpsUrl.parse("https://example.test/$fileName")),
        width = null,
        height = null,
        attribution = null,
    )
}
