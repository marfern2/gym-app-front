package com.mar.gym.core.network

import com.mar.gym.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkClient {
    private val baseUrl = BuildConfig.API_BASE_URL

    private fun okHttpClient(interceptors: List<Interceptor>): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
        interceptors.forEach(builder::addInterceptor)
        return builder.build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun retrofit(interceptors: List<Interceptor>): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient(interceptors))
            .addConverterFactory(
                NetworkJson.instance.asConverterFactory("application/json".toMediaType())
            )
            .build()

    fun <T> create(
        service: Class<T>,
        interceptors: List<Interceptor> = emptyList(),
    ): T = retrofit(interceptors).create(service)
}
