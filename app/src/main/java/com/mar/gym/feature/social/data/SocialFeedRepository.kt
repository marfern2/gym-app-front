package com.mar.gym.feature.social.data

import com.mar.gym.feature.social.model.SocialWorkoutDetail
import com.mar.gym.feature.social.model.SocialWorkoutPage
import com.mar.gym.feature.social.model.SuggestedAthletePage

interface SocialFeedRepository {
    suspend fun feed(cursor: String?, size: Int): SocialResult<SocialWorkoutPage>
    suspend fun suggestions(page: Int, size: Int): SocialResult<SuggestedAthletePage>
    suspend fun userWorkouts(
        username: String,
        cursor: String?,
        size: Int,
    ): SocialResult<SocialWorkoutPage>
    suspend fun workoutDetail(workoutId: String): SocialResult<SocialWorkoutDetail>
}
