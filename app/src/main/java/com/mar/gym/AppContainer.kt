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
import com.mar.gym.feature.measurements.data.DefaultMeasurementRepository
import com.mar.gym.feature.measurements.data.MeasurementApi
import com.mar.gym.feature.measurements.data.MeasurementRepository
import com.mar.gym.feature.profile.data.DefaultProfileRepository
import com.mar.gym.feature.profile.data.ProfileApi
import com.mar.gym.feature.profile.data.ProfileRepository
import com.mar.gym.feature.progress.data.AnalyticsApi
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.DefaultAnalyticsRepository
import com.mar.gym.feature.routines.data.DefaultRoutineRepository
import com.mar.gym.feature.routines.data.RoutineApi
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.system.DefaultSystemRepository
import com.mar.gym.feature.system.SystemApi
import com.mar.gym.feature.system.SystemRepository
import com.mar.gym.feature.workouts.data.DefaultWorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutApi
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutMutationSession
import com.mar.gym.feature.workouts.rest.AndroidRestTimerNotifier
import com.mar.gym.feature.workouts.rest.HandlerRestTimerScheduler
import com.mar.gym.feature.workouts.rest.RestTimerAction
import com.mar.gym.feature.workouts.rest.RestTimerController
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

    private val workoutApi: WorkoutApi by lazy {
        NetworkClient.create(
            service = WorkoutApi::class.java,
            interceptors = listOf(AuthorizationInterceptor(sessionStore)),
            authenticator = SessionAuthenticator(sessionStore, refreshCoordinator),
        )
    }

    val workoutRepository: WorkoutRepository by lazy {
        DefaultWorkoutRepository(
            workoutApi,
            WorkoutMutationSession(sessionStore, refreshCoordinator, clock),
        )
    }

    val restTimerController: RestTimerController by lazy {
        check(::applicationContext.isInitialized) { "AppContainer must be initialized first" }
        RestTimerController(
            clock = clock,
            scheduler = HandlerRestTimerScheduler(),
            notifier = AndroidRestTimerNotifier(applicationContext, exerciseMediaImageLoader),
        )
    }

    /** Returns false when a receiver was recreated after the process-local timer was lost. */
    fun handleRestTimerAction(action: RestTimerAction): Boolean {
        if (restTimerController.active.value == null) return false
        restTimerController.handle(action)
        return true
    }

    private val analyticsApi: AnalyticsApi by lazy { protectedApi(AnalyticsApi::class.java) }
    val analyticsRepository: AnalyticsRepository by lazy { DefaultAnalyticsRepository(analyticsApi) }

    private val profileApi: ProfileApi by lazy { protectedApi(ProfileApi::class.java) }
    val profileRepository: ProfileRepository by lazy { DefaultProfileRepository(profileApi) }

    private val measurementApi: MeasurementApi by lazy { protectedApi(MeasurementApi::class.java) }
    val measurementRepository: MeasurementRepository by lazy { DefaultMeasurementRepository(measurementApi) }

    private fun <T> protectedApi(service: Class<T>): T = NetworkClient.create(
        service = service,
        interceptors = listOf(AuthorizationInterceptor(sessionStore)),
        authenticator = SessionAuthenticator(sessionStore, refreshCoordinator),
    )

    val applicationClock: Clock get() = clock

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
