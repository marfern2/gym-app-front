package com.mar.gym.feature.social.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialProfilePage

sealed interface SocialResult<out T> {
    data class Success<T>(val value: T) : SocialResult<T>
    data class Failure(val error: NetworkFailure) : SocialResult<Nothing>
}

interface SocialRepository {
    suspend fun profile(username: String): SocialResult<PublicProfile>
    suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage>
    suspend fun follow(username: String): SocialResult<Unit>
    suspend fun unfollow(username: String): SocialResult<Unit>
    suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage>
    suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage>
}
