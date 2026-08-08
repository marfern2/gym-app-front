package com.mar.gym

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.mar.gym.core.network.AuthorizationInterceptor
import com.mar.gym.core.network.NetworkClient
import com.mar.gym.core.network.SessionAuthenticator
import com.mar.gym.feature.auth.data.AndroidKeystoreSessionCipher
import com.mar.gym.feature.auth.data.AuthApi
import com.mar.gym.feature.auth.data.AuthRepository
import com.mar.gym.feature.auth.data.AtomicSessionFileStorage
import com.mar.gym.feature.auth.data.DefaultAuthRepository
import com.mar.gym.feature.auth.data.DefaultTokenRefreshRemote
import com.mar.gym.feature.auth.data.PersistentSessionStore
import com.mar.gym.feature.auth.data.SessionRefreshCoordinator
import com.mar.gym.feature.auth.data.SessionStore
import com.mar.gym.feature.exercises.data.DefaultExerciseTemplateRepository
import com.mar.gym.feature.exercises.data.ExerciseTemplateApi
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.routines.data.DefaultRoutineRepository
import com.mar.gym.feature.routines.data.RoutineApi
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.system.DefaultSystemRepository
import com.mar.gym.feature.system.SystemApi
import com.mar.gym.feature.system.SystemRepository
import java.time.Clock

object AppContainer {
    private lateinit var applicationContext: Context
    private val clock: Clock = Clock.systemUTC()

    fun initialize(context: Context) {
        if (!::applicationContext.isInitialized) {
            applicationContext = context.applicationContext
        }
    }

    val sessionStore: SessionStore by lazy {
        check(::applicationContext.isInitialized) { "AppContainer must be initialized first" }
        PersistentSessionStore(
            storage = AtomicSessionFileStorage(applicationContext),
            cipher = AndroidKeystoreSessionCipher(),
        )
    }

    private val publicAuthApi: AuthApi by lazy { NetworkClient.create(AuthApi::class.java) }

    val refreshCoordinator: SessionRefreshCoordinator by lazy {
        SessionRefreshCoordinator(
            remote = DefaultTokenRefreshRemote(publicAuthApi, clock),
            sessionStore = sessionStore,
            clock = clock,
        )
    }

    private val protectedAuthApi: AuthApi by lazy {
        NetworkClient.create(
            service = AuthApi::class.java,
            interceptors = listOf(AuthorizationInterceptor(sessionStore)),
            authenticator = SessionAuthenticator(sessionStore, refreshCoordinator),
        )
    }

    val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(
            publicApi = publicAuthApi,
            protectedApi = protectedAuthApi,
            clock = clock,
        )
    }

    val systemRepository: SystemRepository by lazy {
        DefaultSystemRepository(NetworkClient.create(SystemApi::class.java))
    }

    private val exerciseTemplateApi: ExerciseTemplateApi by lazy {
        NetworkClient.create(
            service = ExerciseTemplateApi::class.java,
            interceptors = listOf(AuthorizationInterceptor(sessionStore)),
            authenticator = SessionAuthenticator(sessionStore, refreshCoordinator),
        )
    }

    val exerciseTemplateRepository: ExerciseTemplateRepository by lazy {
        DefaultExerciseTemplateRepository(exerciseTemplateApi)
    }

    private val routineApi: RoutineApi by lazy {
        NetworkClient.create(
            service = RoutineApi::class.java,
            interceptors = listOf(AuthorizationInterceptor(sessionStore)),
            authenticator = SessionAuthenticator(sessionStore, refreshCoordinator),
        )
    }

    val routineRepository: RoutineRepository by lazy { DefaultRoutineRepository(routineApi) }

    val exerciseMediaImageLoader: ImageLoader by lazy {
        check(::applicationContext.isInitialized) { "AppContainer must be initialized first" }
        ImageLoader.Builder(applicationContext)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
