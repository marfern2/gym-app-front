package com.mar.gym.feature.profile.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft

sealed interface ProfileResult<out T> {
    data class Success<T>(val value: T) : ProfileResult<T>
    data class Failure(val error: NetworkFailure) : ProfileResult<Nothing>
}

interface ProfileRepository {
    suspend fun getProfile(): ProfileResult<PrivateProfileDocument>
    suspend fun updateProfile(
        draft: PrivateProfileDraft,
        current: PrivateProfileDocument,
    ): ProfileResult<PrivateProfileDocument>
}
