package com.mar.gym.feature.social.data

import com.mar.gym.feature.social.model.BlockedUserPage
import com.mar.gym.feature.social.model.ReportRequest
import com.mar.gym.feature.social.model.ReportResponse

interface SocialModerationRepository {
    suspend fun block(username: String): SocialResult<Unit>
    suspend fun unblock(username: String): SocialResult<Unit>
    suspend fun blockedUsers(page: Int, size: Int): SocialResult<BlockedUserPage>
    suspend fun report(request: ReportRequest): SocialResult<ReportResponse>
}
