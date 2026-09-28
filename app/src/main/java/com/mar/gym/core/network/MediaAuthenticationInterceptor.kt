package com.mar.gym.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Marks only this API's media GETs for the existing session interceptor. */
class MediaAuthenticationInterceptor(private val apiBase: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val isMedia = original.method == "GET" && url.scheme == apiBase.scheme &&
            url.host == apiBase.host && url.port == apiBase.port &&
            url.encodedPath.startsWith("/api/v1/media/")
        val request = if (isMedia) original.newBuilder()
            .header(AUTHENTICATION_REQUIRED_HEADER, AUTHENTICATION_RETRY_ON_401).build() else original
        return chain.proceed(request)
    }
}
