package com.mar.gym.feature.social.data

import com.mar.gym.core.network.AUTHENTICATION_REQUIRED_HEADER
import com.mar.gym.core.network.AUTHENTICATION_RETRY_ON_401
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SocialNotificationsApi {
    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/notifications")
    suspend fun notifications(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Response<SocialNotificationPageDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): Response<UnreadNotificationCountDto>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @POST("api/v1/notifications/{notificationId}/read")
    suspend fun markRead(@Path("notificationId") notificationId: String): Response<Unit>

    @Headers("$AUTHENTICATION_REQUIRED_HEADER: $AUTHENTICATION_RETRY_ON_401")
    @POST("api/v1/notifications/read-all")
    suspend fun markAllRead(): Response<Unit>
}
