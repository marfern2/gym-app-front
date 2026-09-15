package com.mar.gym.feature.social.data

import com.mar.gym.feature.social.model.SocialNotificationPage

interface SocialNotificationsRepository {
    suspend fun notifications(page: Int, size: Int): SocialResult<SocialNotificationPage>
    suspend fun unreadCount(): SocialResult<Long>
    suspend fun markRead(notificationId: String): SocialResult<Unit>
    suspend fun markAllRead(): SocialResult<Unit>
}
