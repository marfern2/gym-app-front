package com.mar.gym.feature.social.data

import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SuggestedAthletePage
import com.mar.gym.feature.social.model.SocialComment
import com.mar.gym.feature.social.model.SocialCommentPage

interface SocialFeedRepository {
    suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage>
    suspend fun discover(cursor: String?, size: Int): SocialResult<SocialWorkoutPage>
    suspend fun suggestions(page: Int, size: Int): SocialResult<SuggestedAthletePage>
    suspend fun userWorkouts(
        username: String,
        cursor: String?,
        size: Int,
    ): SocialResult<SocialWorkoutPage>
    suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail>
    suspend fun like(workoutId: String): SocialResult<Unit>
    suspend fun unlike(workoutId: String): SocialResult<Unit>
    suspend fun comments(workoutId: String, page: Int, size: Int): SocialResult<SocialCommentPage>
    suspend fun createComment(workoutId: String, text: String): SocialResult<SocialComment>
    suspend fun deleteComment(workoutId: String, commentId: String): SocialResult<Unit>
}
