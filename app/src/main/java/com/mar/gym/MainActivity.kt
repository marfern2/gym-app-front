package com.mar.gym

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.feature.auth.ui.AuthRoute
import com.mar.gym.feature.auth.ui.AuthUiState
import com.mar.gym.feature.auth.ui.AuthViewModel
import com.mar.gym.feature.auth.ui.AuthViewModelFactory
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.exercises.model.ExercisePickerConfig
import com.mar.gym.feature.exercises.model.ExercisePickerOutcome
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.ui.ExerciseCatalogRoute
import com.mar.gym.feature.exercises.ui.CustomExerciseEditorRoute
import com.mar.gym.feature.exercises.ui.CustomExerciseEditorViewModel
import com.mar.gym.feature.exercises.ui.CustomExerciseEditorViewModelFactory
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModel
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModelFactory
import com.mar.gym.feature.exercises.ui.ExerciseDetailRoute
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModel
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModelFactory
import com.mar.gym.feature.exercises.ui.ExercisePickerRoute
import com.mar.gym.feature.exercises.ui.openHttpsUrl
import com.mar.gym.feature.home.ui.HomeRoute
import com.mar.gym.feature.home.ui.HomeViewModel
import com.mar.gym.feature.home.ui.HomeViewModelFactory
import com.mar.gym.feature.measurements.ui.MeasurementRoute
import com.mar.gym.feature.measurements.ui.MeasurementViewModel
import com.mar.gym.feature.measurements.ui.MeasurementViewModelFactory
import com.mar.gym.feature.profile.ui.ProfileRoute
import com.mar.gym.feature.profile.ui.ProfileCalendarRoute
import com.mar.gym.feature.profile.ui.ProfileCalendarViewModel
import com.mar.gym.feature.profile.ui.ProfileCalendarViewModelFactory
import com.mar.gym.feature.profile.ui.ProfileEditRoute
import com.mar.gym.feature.profile.ui.ProfileSettingsScreen
import com.mar.gym.feature.profile.ui.ProfileStatsRoute
import com.mar.gym.feature.profile.ui.ProfileViewModel
import com.mar.gym.feature.profile.ui.ProfileViewModelFactory
import com.mar.gym.feature.progress.data.DeviceTimeZoneProvider
import com.mar.gym.feature.progress.ui.ExerciseProgressRoute
import com.mar.gym.feature.progress.ui.ExerciseProgressViewModel
import com.mar.gym.feature.progress.ui.ExerciseProgressViewModelFactory
import com.mar.gym.feature.routines.ui.RoutineEditorRoute
import com.mar.gym.feature.routines.ui.RoutineEditorViewModel
import com.mar.gym.feature.routines.ui.RoutineEditorViewModelFactory
import com.mar.gym.feature.routines.ui.RoutineListEffect
import com.mar.gym.feature.routines.ui.RoutineListViewModel
import com.mar.gym.feature.routines.ui.RoutineListViewModelFactory
import com.mar.gym.feature.routines.ui.RoutineViewerRoute
import com.mar.gym.feature.routines.ui.RoutineViewerViewModel
import com.mar.gym.feature.routines.ui.RoutineViewerViewModelFactory
import com.mar.gym.feature.routines.ui.SharedRoutineRoute
import com.mar.gym.feature.routines.ui.SharedRoutineViewModel
import com.mar.gym.feature.routines.ui.SharedRoutineViewModelFactory
import com.mar.gym.feature.system.SystemViewModel
import com.mar.gym.feature.system.SystemViewModelFactory
import com.mar.gym.feature.social.ui.PublicProfileRoute
import com.mar.gym.feature.social.ui.PublicProfileViewModel
import com.mar.gym.feature.social.ui.PublicProfileViewModelFactory
import com.mar.gym.feature.social.ui.SocialListRoute
import com.mar.gym.feature.social.ui.SocialListType
import com.mar.gym.feature.social.ui.SocialListViewModel
import com.mar.gym.feature.social.ui.SocialListViewModelFactory
import com.mar.gym.feature.social.ui.SocialCommentsRoute
import com.mar.gym.feature.social.ui.SocialEngagementViewModel
import com.mar.gym.feature.social.ui.SocialEngagementViewModelFactory
import com.mar.gym.feature.social.ui.SocialWorkoutDetailRoute
import com.mar.gym.feature.social.ui.SocialWorkoutDetailViewModel
import com.mar.gym.feature.social.ui.SocialWorkoutDetailViewModelFactory
import com.mar.gym.feature.social.ui.UserSearchRoute
import com.mar.gym.feature.social.ui.UserSearchViewModel
import com.mar.gym.feature.social.ui.UserSearchViewModelFactory
import com.mar.gym.feature.training.ui.TrainingScreen
import com.mar.gym.feature.workouts.ui.ActiveWorkoutRoute
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModel
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModelFactory
import com.mar.gym.feature.workouts.ui.SaveWorkoutRoute
import com.mar.gym.feature.workouts.ui.WorkoutCongratsRoute
import com.mar.gym.ui.components.BarbellIcon
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.core.sharing.ShareDeepLink
import com.mar.gym.core.sharing.ShareLinks
import com.mar.gym.core.sharing.ShareMessages
import com.mar.gym.feature.social.model.SocialWorkoutDetail
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var userSessionViewModels: UserSessionViewModelScope
    private val incomingDeepLink = MutableStateFlow<String?>(null)
    private val shareLinks by lazy { ShareLinks(BuildConfig.SHARE_BASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppContainer.initialize(applicationContext)
        incomingDeepLink.value = intent?.dataString

        val authViewModel = ViewModelProvider(
            this,
            AuthViewModelFactory(
                AppContainer.authRepository,
                AppContainer.sessionStore,
                AppContainer.refreshCoordinator,
            ),
        )[AuthViewModel::class.java]
        val systemViewModel = ViewModelProvider(
            this,
            SystemViewModelFactory(AppContainer.systemRepository),
        )[SystemViewModel::class.java]
        userSessionViewModels = ViewModelProvider(this)[UserSessionViewModelScope::class.java]
        setContent {
            GYmAppTheme {
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                val authenticated = authState as? AuthUiState.Authenticated
                if (authenticated != null) {
                    if (userSessionViewModels.activate(authenticated.user.id)) {
                        AppContainer.clearUserScopedState()
                    }
                    key(authenticated.user.id) {
                        AuthenticatedApp(
                            user = authenticated.user,
                            authViewModel = authViewModel,
                        )
                    }
                } else {
                    userSessionViewModels.clearSession()
                    AppContainer.clearUserScopedState()
                    AuthRoute(
                        authViewModel = authViewModel,
                        systemViewModel = systemViewModel,
                    )
                }            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDeepLink.value = intent.dataString
    }

    @Composable
    private fun AuthenticatedApp(
        user: com.mar.gym.feature.auth.model.AuthenticatedUser,
        authViewModel: AuthViewModel,
    ) {
        var tab by rememberSaveable { mutableStateOf(TAB_HOME) }
        var deep by rememberSaveable { mutableStateOf<String?>(null) }
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var exerciseEditorId by rememberSaveable { mutableStateOf<String?>(null) }
        var routineId by rememberSaveable { mutableStateOf<String?>(null) }
        var routineEditorOrigin by rememberSaveable { mutableStateOf<String?>(null) }
        var routinePickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutPickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutExerciseToReplaceId by rememberSaveable { mutableStateOf<String?>(null) }
        var workoutReplacementPickerRequest by rememberSaveable { mutableStateOf(0) }
        var pendingRoutineWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
        var catalogOrigin by rememberSaveable { mutableStateOf(TAB_TRAINING) }
        var detailOrigin by rememberSaveable { mutableStateOf(DEEP_CATALOG) }
        var publicUsername by rememberSaveable { mutableStateOf<String?>(null) }
        var socialWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
        var socialWorkoutOrigin by rememberSaveable { mutableStateOf(TAB_HOME) }
        var socialCommentsOrigin by rememberSaveable { mutableStateOf(TAB_HOME) }
        var commentsParentUsername by rememberSaveable { mutableStateOf<String?>(null) }
        var userSearchOrigin by rememberSaveable { mutableStateOf(TAB_PROFILE) }
        var socialListUsername by rememberSaveable { mutableStateOf<String?>(null) }
        var publicProfileOrigin by rememberSaveable { mutableStateOf(DEEP_USER_SEARCH) }
        var socialListOrigin by rememberSaveable { mutableStateOf(TAB_PROFILE) }
        var socialListParentUsername by rememberSaveable { mutableStateOf<String?>(null) }
        var socialListType by rememberSaveable { mutableStateOf(SocialListType.Followers.name) }
        var sharedRoutineId by rememberSaveable { mutableStateOf<String?>(null) }

        val pendingDeepLink by incomingDeepLink.collectAsStateWithLifecycle()
        LaunchedEffect(pendingDeepLink) {
            when (val target = shareLinks.parse(pendingDeepLink)) {
                is ShareDeepLink.Profile -> {
                    publicUsername = target.username
                    publicProfileOrigin = TAB_HOME
                    deep = DEEP_PUBLIC_PROFILE
                }
                is ShareDeepLink.Workout -> {
                    socialWorkoutId = target.workoutId
                    socialWorkoutOrigin = TAB_HOME
                    deep = DEEP_SOCIAL_WORKOUT
                }
                is ShareDeepLink.Routine -> {
                    sharedRoutineId = target.shareId
                    deep = DEEP_SHARED_ROUTINE
                }
                null -> Unit
            }
            if (pendingDeepLink != null) incomingDeepLink.value = null
        }

        val activeWorkoutState by activeWorkoutViewModel().uiState.collectAsStateWithLifecycle()
        val routinesState by routineListViewModel().uiState.collectAsStateWithLifecycle()

        val finishCompletedWorkout: () -> Unit = {
            activeWorkoutViewModel().clearCompletedWorkout()
            profileViewModel().refresh()
            deep = null
            tab = TAB_TRAINING
        }

        LaunchedEffect(Unit) {
            routineListViewModel().effects.collect { effect ->
                if (effect is RoutineListEffect.OpenRoutine) {
                    routineId = effect.routineId
                    deep = DEEP_ROUTINE_VIEWER
                }
            }
        }

        BackHandler(enabled = deep != null || tab != TAB_HOME) {
            when {
                deep == DEEP_WORKOUT_CONGRATS -> finishCompletedWorkout()
                deep == DEEP_WORKOUT_SAVE && activeWorkoutState is com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState.Completed ->
                    deep = DEEP_WORKOUT_CONGRATS
                deep == DEEP_WORKOUT_SAVE && activeWorkoutState is com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState.Completing -> Unit
                deep != null -> deep = when (deep) {
                    DEEP_CATALOG -> {
                        tab = catalogOrigin
                        null
                    }
                    DEEP_DETAIL -> when (detailOrigin) {
                        DEEP_ROUTINE_VIEWER -> DEEP_ROUTINE_VIEWER
                        DEEP_ROUTINE_EDITOR -> DEEP_ROUTINE_EDITOR
                        DEEP_WORKOUT -> DEEP_WORKOUT
                        else -> {
                            exerciseCatalogViewModel().refresh()
                            DEEP_CATALOG
                        }
                    }
                    DEEP_PICKER -> DEEP_CATALOG
                    DEEP_EXERCISE_PROGRESS -> DEEP_DETAIL
                    DEEP_CUSTOM_EDITOR -> if (exerciseEditorId == null) DEEP_CATALOG else DEEP_DETAIL
                    DEEP_ROUTINE_VIEWER -> {
                        routineListViewModel().refresh()
                        tab = TAB_TRAINING
                        null
                    }
                    DEEP_SHARED_ROUTINE -> {
                        tab = TAB_HOME
                        null
                    }
                    DEEP_ROUTINE_EDITOR -> {
                        routineListViewModel().refresh()
                        when (routineEditorOrigin) {
                            ROUTINE_ORIGIN_VIEWER -> {
                                routineId?.let(::refreshRoutineViewer)
                                DEEP_ROUTINE_VIEWER
                            }
                            else -> {
                                tab = TAB_TRAINING
                                null
                            }
                        }
                    }
                    DEEP_ROUTINE_PICKER -> DEEP_ROUTINE_EDITOR
                    DEEP_WORKOUT_PICKER -> DEEP_WORKOUT
                    DEEP_WORKOUT_REPLACEMENT_PICKER -> {
                        workoutExerciseToReplaceId = null
                        DEEP_WORKOUT
                    }
                    DEEP_WORKOUT_SAVE -> DEEP_WORKOUT
                    DEEP_MEASUREMENTS -> {
                        profileViewModel().refresh()
                        tab = TAB_PROFILE
                        null
                    }
                    DEEP_PROFILE_EDIT -> {
                        profileViewModel().cancelEditing()
                        tab = TAB_PROFILE
                        null
                    }
                    DEEP_PROFILE_SETTINGS, DEEP_PROFILE_STATS, DEEP_PROFILE_CALENDAR -> {
                        tab = TAB_PROFILE
                        null
                    }
                    DEEP_USER_SEARCH -> {
                        tab = userSearchOrigin
                        null
                    }
                    DEEP_PUBLIC_PROFILE -> {
                        if (publicProfileOrigin == DEEP_USER_SEARCH) userSearchViewModel().refresh()
                        if (publicProfileOrigin == TAB_PROFILE || publicProfileOrigin == TAB_HOME) {
                            tab = publicProfileOrigin
                            if (tab == TAB_HOME) homeViewModel().refresh()
                            null
                        } else publicProfileOrigin
                    }
                    DEEP_SOCIAL_WORKOUT -> if (socialWorkoutOrigin == TAB_HOME || socialWorkoutOrigin == TAB_PROFILE) {
                        tab = socialWorkoutOrigin
                        null
                    } else socialWorkoutOrigin
                    DEEP_SOCIAL_COMMENTS -> {
                        if (socialCommentsOrigin == DEEP_PUBLIC_PROFILE) {
                            publicUsername = commentsParentUsername
                        }
                        if (socialCommentsOrigin == TAB_HOME || socialCommentsOrigin == TAB_PROFILE) {
                            tab = socialCommentsOrigin
                            null
                        } else socialCommentsOrigin
                    }
                    DEEP_SOCIAL_LIST -> if (socialListOrigin == TAB_PROFILE) {
                        tab = TAB_PROFILE
                        null
                    } else {
                        publicUsername = socialListParentUsername
                        socialListOrigin
                    }
                    else -> {
                        tab = TAB_TRAINING
                        null
                    }
                }
                tab != TAB_HOME -> tab = TAB_HOME
            }
        }

        if (deep == null) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == TAB_HOME,
                            onClick = { tab = TAB_HOME },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_home)) },
                        )
                        NavigationBarItem(
                            selected = tab == TAB_TRAINING,
                            onClick = { tab = TAB_TRAINING },
                            icon = { BarbellIcon(tint = LocalContentColor.current) },
                            label = { Text(stringResource(R.string.nav_training)) },
                        )
                        NavigationBarItem(
                            selected = tab == TAB_PROFILE,
                            onClick = { tab = TAB_PROFILE },
                            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_profile)) },
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(
                            PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding(),
                            ),
                        ),
                ) {
                    when (tab) {
                        TAB_HOME -> HomeRoute(
                            viewModel = homeViewModel(),
                            engagementViewModel = socialEngagementViewModel(user.id),
                            onSearchPeople = {
                                userSearchOrigin = TAB_HOME
                                deep = DEEP_USER_SEARCH
                            },
                            onOpenProfile = { username ->
                                publicUsername = username
                                publicProfileOrigin = TAB_HOME
                                deep = DEEP_PUBLIC_PROFILE
                            },
                            onOpenWorkout = { workoutId ->
                                socialWorkoutId = workoutId
                                socialWorkoutOrigin = TAB_HOME
                                deep = DEEP_SOCIAL_WORKOUT
                            },
                            onOpenComments = { workoutId ->
                                socialWorkoutId = workoutId
                                socialCommentsOrigin = TAB_HOME
                                commentsParentUsername = null
                                deep = DEEP_SOCIAL_COMMENTS
                            },
                            onShareWorkout = ::shareWorkout,
                        )
                        TAB_TRAINING -> TrainingScreen(
                            activeWorkout = activeWorkoutState,
                            routines = routinesState,
                            clock = AppContainer.applicationClock,
                            onContinueWorkout = { deep = DEEP_WORKOUT },
                            onStartEmpty = {
                                activeWorkoutViewModel().startEmpty()
                                deep = DEEP_WORKOUT
                            },
                            onRetryWorkout = { activeWorkoutViewModel().retry() },
                            onOpenRoutine = { id ->
                                routineId = id
                                deep = DEEP_ROUTINE_VIEWER
                            },
                            onStartRoutine = { id ->
                                pendingRoutineWorkoutId = id
                                deep = DEEP_WORKOUT
                            },
                            onEditRoutine = { id ->
                                routineId = id
                                routineEditorOrigin = ROUTINE_ORIGIN_TRAINING
                                deep = DEEP_ROUTINE_EDITOR
                            },
                            onDuplicateRoutine = { routineListViewModel().duplicate(it) },
                            onDeleteRoutine = { routineListViewModel().delete(it) },
                            onOpenCatalog = {
                                catalogOrigin = TAB_TRAINING
                                deep = DEEP_CATALOG
                            },
                            onCreateRoutine = {
                                routineId = null
                                routineEditorOrigin = ROUTINE_ORIGIN_TRAINING
                                deep = DEEP_ROUTINE_EDITOR
                            },
                            onRetryRoutines = { routineListViewModel().retry() },
                            onLoadMoreRoutines = { routineListViewModel().loadMore() },
                        )
                        TAB_PROFILE -> ProfileRoute(
                            viewModel = profileViewModel(),
                            onOpenEdit = { deep = DEEP_PROFILE_EDIT },
                            onShare = ::shareProfile,
                            onOpenSettings = { deep = DEEP_PROFILE_SETTINGS },
                            onOpenStatistics = { deep = DEEP_PROFILE_STATS },
                            onOpenMeasurements = { deep = DEEP_MEASUREMENTS },
                            onOpenExercises = {
                                catalogOrigin = TAB_PROFILE
                                deep = DEEP_CATALOG
                            },
                            onOpenCalendar = { deep = DEEP_PROFILE_CALENDAR },
                            onSearchPeople = {
                                userSearchOrigin = TAB_PROFILE
                                deep = DEEP_USER_SEARCH
                            },
                            onOpenFollowers = { username ->
                                socialListUsername = username
                                socialListType = SocialListType.Followers.name
                                socialListOrigin = TAB_PROFILE
                                socialListParentUsername = null
                                deep = DEEP_SOCIAL_LIST
                            },
                            onOpenFollowing = { username ->
                                socialListUsername = username
                                socialListType = SocialListType.Following.name
                                socialListOrigin = TAB_PROFILE
                                socialListParentUsername = null
                                deep = DEEP_SOCIAL_LIST
                            },
                        )
                    }
                }
            }
        } else {
            when (deep) {
                DEEP_CATALOG -> ExerciseCatalogRoute(
                    viewModel = remember { exerciseCatalogViewModel() },
                    onBack = {
                        deep = null
                        tab = catalogOrigin
                    },
                    onOpenDetail = { id ->
                        detailOrigin = DEEP_CATALOG
                        detailId = id
                        deep = DEEP_DETAIL
                    },
                    onOpenPicker = { deep = DEEP_PICKER },
                    onCreateCustom = {
                        exerciseEditorId = null
                        deep = DEEP_CUSTOM_EDITOR
                    },
                )
                DEEP_DETAIL -> detailId?.let { id ->
                    ExerciseDetailRoute(
                        exerciseTemplateId = id,
                        viewModel = remember { exerciseDetailViewModel() },
                        imageLoader = AppContainer.exerciseMediaImageLoader,
                        onOpenAttribution = ::openHttpsUrl,
                        onBack = {
                            if (detailOrigin == DEEP_CATALOG) exerciseCatalogViewModel().refresh()
                            deep = detailOrigin
                        },
                        onEdit = { id ->
                            exerciseEditorId = id
                            deep = DEEP_CUSTOM_EDITOR
                        },
                        onOpenProgress = { id ->
                            detailId = id
                            deep = DEEP_EXERCISE_PROGRESS
                        },
                    )
                } ?: ExerciseCatalogRoute(
                    viewModel = remember { exerciseCatalogViewModel() },
                    onBack = {
                        deep = null
                        tab = catalogOrigin
                    },
                    onOpenDetail = { id ->
                        detailOrigin = DEEP_CATALOG
                        detailId = id
                        deep = DEEP_DETAIL
                    },
                    onOpenPicker = { deep = DEEP_PICKER },
                    onCreateCustom = {
                        exerciseEditorId = null
                        deep = DEEP_CUSTOM_EDITOR
                    },
                )
                DEEP_PICKER -> {
                    val catalogViewModel = remember { generalExercisePickerViewModel() }
                    ExercisePickerRoute(
                        viewModel = catalogViewModel,
                        onResult = { deep = DEEP_CATALOG },
                    )
                }
                DEEP_CUSTOM_EDITOR -> {
                    val currentId = exerciseEditorId
                    CustomExerciseEditorRoute(
                        viewModel = remember(currentId) { customExerciseEditorViewModel(currentId) },
                        onBack = {
                            deep = if (currentId == null) DEEP_CATALOG else DEEP_DETAIL
                        },
                        onSaved = { id ->
                            exerciseCatalogViewModel().refresh()
                            exerciseDetailViewModel().load(id, force = true)
                            detailId = id
                            exerciseEditorId = id
                            deep = DEEP_DETAIL
                        },
                    )
                }
                DEEP_ROUTINE_VIEWER -> {
                    val currentId = routineId
                    if (currentId != null) {
                        RoutineViewerRoute(
                            viewModel = remember(currentId) { routineViewerViewModel(currentId) },
                            onBack = {
                                routineListViewModel().refresh()
                                deep = null
                                tab = TAB_TRAINING
                            },
                            onEdit = {
                                routineEditorOrigin = ROUTINE_ORIGIN_VIEWER
                                deep = DEEP_ROUTINE_EDITOR
                            },
                            onStartRoutine = {
                                pendingRoutineWorkoutId = currentId
                                deep = DEEP_WORKOUT
                            },
                            onOpenRoutine = { id ->
                                routineId = id
                                deep = DEEP_ROUTINE_VIEWER
                            },
                            onDeleted = {
                                routineId = null
                                routineListViewModel().refresh()
                                deep = null
                                tab = TAB_TRAINING
                            },
                            onShare = ::shareRoutine,
                            onOpenExercise = { id ->
                                detailOrigin = DEEP_ROUTINE_VIEWER
                                detailId = id
                                deep = DEEP_DETAIL
                            },
                        )
                    } else LaunchedEffect(Unit) {
                        routineListViewModel().refresh()
                        deep = null
                        tab = TAB_TRAINING
                    }
                }
                DEEP_ROUTINE_EDITOR -> {
                    val currentId = routineId
                    RoutineEditorRoute(
                        viewModel = remember(currentId) { routineEditorViewModel(currentId) },
                        onBack = {
                            routineListViewModel().refresh()
                            when (routineEditorOrigin) {
                                ROUTINE_ORIGIN_VIEWER -> {
                                    routineId?.let(::refreshRoutineViewer)
                                    deep = DEEP_ROUTINE_VIEWER
                                }
                                else -> {
                                    deep = null
                                    tab = TAB_TRAINING
                                }
                            }
                        },
                        onOpenPicker = { ids ->
                            routinePickerInitialIds = ids.toList()
                            deep = DEEP_ROUTINE_PICKER
                        },
                        onOpenRoutine = { id ->
                            routineId = id
                            deep = DEEP_ROUTINE_EDITOR
                        },
                        onStartRoutine = { id ->
                            pendingRoutineWorkoutId = id
                            deep = DEEP_WORKOUT
                        },
                        onOpenExercise = { id ->
                            detailOrigin = DEEP_ROUTINE_EDITOR
                            detailId = id
                            deep = DEEP_DETAIL
                        },
                    )
                }
                DEEP_ROUTINE_PICKER -> {
                    val currentId = routineId
                    val pickerViewModel = remember(currentId, routinePickerInitialIds) {
                        exercisePickerViewModel(routinePickerInitialIds.toSet(), currentId)
                    }
                    ExercisePickerRoute(
                        viewModel = pickerViewModel,
                        onResult = { outcome ->
                            if (outcome is ExercisePickerOutcome.Confirmed) {
                                routineEditorViewModel(currentId).addSelectedExercises(
                                    outcome.result.selectedExerciseTemplateIds
                                )
                            }
                            deep = DEEP_ROUTINE_EDITOR
                        },
                    )
                }
                DEEP_WORKOUT -> {
                    val viewModel = remember { activeWorkoutViewModel() }
                    val routineToStart = pendingRoutineWorkoutId
                    LaunchedEffect(routineToStart) {
                        if (routineToStart != null) {
                            pendingRoutineWorkoutId = null
                            viewModel.startFromRoutine(routineToStart)
                        }
                    }
                    ActiveWorkoutRoute(
                        viewModel = viewModel,
                        onBack = {
                            deep = null
                            tab = TAB_TRAINING
                        },
                        onOpenSaveWorkout = { deep = DEEP_WORKOUT_SAVE },
                        onOpenPicker = { ids ->
                            workoutPickerInitialIds = ids.toList()
                            deep = DEEP_WORKOUT_PICKER
                        },
                        onOpenReplacementPicker = { localId ->
                            workoutExerciseToReplaceId = localId
                            workoutReplacementPickerRequest += 1
                            deep = DEEP_WORKOUT_REPLACEMENT_PICKER
                        },
                        onOpenExercise = { id ->
                            detailOrigin = DEEP_WORKOUT
                            detailId = id
                            deep = DEEP_DETAIL
                        },
                    )
                }
                DEEP_WORKOUT_SAVE -> SaveWorkoutRoute(
                    viewModel = remember { activeWorkoutViewModel() },
                    onBack = { deep = DEEP_WORKOUT },
                    onCompleted = { deep = DEEP_WORKOUT_CONGRATS },
                )
                DEEP_WORKOUT_CONGRATS -> WorkoutCongratsRoute(
                    state = activeWorkoutState,
                    onDone = finishCompletedWorkout,
                )
                DEEP_WORKOUT_PICKER -> {
                    val pickerViewModel = remember(workoutPickerInitialIds) {
                        workoutExercisePickerViewModel(workoutPickerInitialIds.toSet())
                    }
                    ExercisePickerRoute(
                        viewModel = pickerViewModel,
                        onResult = { outcome ->
                            if (outcome is ExercisePickerOutcome.Confirmed) {
                                activeWorkoutViewModel().addSelectedExercises(
                                    outcome.result.selectedExerciseTemplateIds - workoutPickerInitialIds.toSet()
                                )
                            }
                            deep = DEEP_WORKOUT
                        },
                    )
                }
                DEEP_WORKOUT_REPLACEMENT_PICKER -> {
                    val localId = workoutExerciseToReplaceId
                    if (localId == null) {
                        LaunchedEffect(Unit) { deep = DEEP_WORKOUT }
                    } else {
                        val pickerViewModel = remember(localId, workoutReplacementPickerRequest) {
                            workoutReplacementPickerViewModel(localId, workoutReplacementPickerRequest)
                        }
                        ExercisePickerRoute(
                            viewModel = pickerViewModel,
                            onResult = { outcome ->
                                if (outcome is ExercisePickerOutcome.Confirmed) {
                                    outcome.result.selectedExerciseTemplateIds.singleOrNull()?.let { templateId ->
                                        activeWorkoutViewModel().replaceExercise(localId, templateId)
                                    }
                                }
                                workoutExerciseToReplaceId = null
                                deep = DEEP_WORKOUT
                            },
                        )
                    }
                }
                DEEP_EXERCISE_PROGRESS -> detailId?.let { id ->
                    ExerciseProgressRoute(
                        viewModel = remember(id) { exerciseProgressViewModel(id) },
                        onBack = { deep = DEEP_DETAIL },
                    )
                }
                DEEP_MEASUREMENTS -> MeasurementRoute(
                    viewModel = remember { measurementViewModel() },
                    onBack = {
                        profileViewModel().refresh()
                        deep = null
                        tab = TAB_PROFILE
                    },
                )
                DEEP_PROFILE_EDIT -> ProfileEditRoute(
                    viewModel = profileViewModel(),
                    onBack = {
                        deep = null
                        tab = TAB_PROFILE
                    },
                )
                DEEP_PROFILE_SETTINGS -> ProfileSettingsScreen(
                    onBack = {
                        deep = null
                        tab = TAB_PROFILE
                    },
                    onLogout = { authViewModel.logout() },
                )
                DEEP_PROFILE_STATS -> ProfileStatsRoute(
                    viewModel = profileViewModel(),
                    onBack = {
                        deep = null
                        tab = TAB_PROFILE
                    },
                )
                DEEP_PROFILE_CALENDAR -> ProfileCalendarRoute(
                    viewModel = remember { profileCalendarViewModel() },
                    onBack = {
                        deep = null
                        tab = TAB_PROFILE
                    },
                )
                DEEP_USER_SEARCH -> UserSearchRoute(
                    viewModel = remember { userSearchViewModel() },
                    onBack = {
                        if (userSearchOrigin == TAB_HOME) homeViewModel().refresh()
                        deep = null
                        tab = userSearchOrigin
                    },
                    onOpenProfile = { username ->
                        publicUsername = username
                        publicProfileOrigin = DEEP_USER_SEARCH
                        deep = DEEP_PUBLIC_PROFILE
                    },
                )
                DEEP_PUBLIC_PROFILE -> publicUsername?.let { username ->
                    PublicProfileRoute(
                        viewModel = remember(username) { publicProfileViewModel(username, user.id) },
                        engagementViewModel = socialEngagementViewModel(user.id),
                        onBack = {
                            if (publicProfileOrigin == DEEP_USER_SEARCH) userSearchViewModel().refresh()
                            if (publicProfileOrigin == TAB_HOME) homeViewModel().refresh()
                            deep = publicProfileOrigin.takeUnless { it == TAB_PROFILE || it == TAB_HOME }
                            if (deep == null) tab = publicProfileOrigin
                        },
                        onOpenOwnProfile = {
                            profileViewModel().refresh()
                            deep = null
                            tab = TAB_PROFILE
                        },
                        onOpenFollowers = {
                            socialListUsername = it
                            socialListType = SocialListType.Followers.name
                            socialListOrigin = DEEP_PUBLIC_PROFILE
                            socialListParentUsername = username
                            deep = DEEP_SOCIAL_LIST
                        },
                        onOpenFollowing = {
                            socialListUsername = it
                            socialListType = SocialListType.Following.name
                            socialListOrigin = DEEP_PUBLIC_PROFILE
                            socialListParentUsername = username
                            deep = DEEP_SOCIAL_LIST
                        },
                        onOpenWorkout = { workoutId ->
                            socialWorkoutId = workoutId
                            socialWorkoutOrigin = DEEP_PUBLIC_PROFILE
                            deep = DEEP_SOCIAL_WORKOUT
                        },
                        onOpenComments = { workoutId ->
                            socialWorkoutId = workoutId
                            socialCommentsOrigin = DEEP_PUBLIC_PROFILE
                            commentsParentUsername = username
                            deep = DEEP_SOCIAL_COMMENTS
                        },
                        onShareProfile = ::shareProfile,
                        onShareWorkout = ::shareWorkout,
                    )
                }
                DEEP_SOCIAL_WORKOUT -> socialWorkoutId?.let { workoutId ->
                    SocialWorkoutDetailRoute(
                        viewModel = remember(workoutId) { socialWorkoutDetailViewModel(workoutId) },
                        engagementViewModel = socialEngagementViewModel(user.id),
                        onBack = {
                            deep = socialWorkoutOrigin.takeUnless { it == TAB_HOME || it == TAB_PROFILE }
                            if (deep == null) tab = socialWorkoutOrigin
                        },
                        onOpenProfile = { username ->
                            publicUsername = username
                            publicProfileOrigin = DEEP_SOCIAL_WORKOUT
                            deep = DEEP_PUBLIC_PROFILE
                        },
                        onOpenComments = {
                            socialCommentsOrigin = DEEP_SOCIAL_WORKOUT
                            commentsParentUsername = null
                            deep = DEEP_SOCIAL_COMMENTS
                        },
                        onShareWorkout = ::shareWorkout,
                    )
                }
                DEEP_SHARED_ROUTINE -> sharedRoutineId?.let { shareId ->
                    SharedRoutineRoute(
                        viewModel = remember(shareId) { sharedRoutineViewModel(shareId) },
                        onBack = {
                            sharedRoutineId = null
                            deep = null
                            tab = TAB_HOME
                        },
                    )
                }
                DEEP_SOCIAL_COMMENTS -> socialWorkoutId?.let { workoutId ->
                    SocialCommentsRoute(
                        workoutId = workoutId,
                        viewModel = socialEngagementViewModel(user.id),
                        onBack = {
                            if (socialCommentsOrigin == DEEP_PUBLIC_PROFILE) {
                                publicUsername = commentsParentUsername
                            }
                            deep = socialCommentsOrigin.takeUnless { it == TAB_HOME || it == TAB_PROFILE }
                            if (deep == null) tab = socialCommentsOrigin
                        },
                        onOpenProfile = { username ->
                            publicUsername = username
                            publicProfileOrigin = DEEP_SOCIAL_COMMENTS
                            deep = DEEP_PUBLIC_PROFILE
                        },
                    )
                }
                DEEP_SOCIAL_LIST -> socialListUsername?.let { username ->
                    val type = SocialListType.valueOf(socialListType)
                    SocialListRoute(
                        viewModel = remember(username, type) { socialListViewModel(username, type) },
                        type = type,
                        onBack = {
                            if (socialListOrigin == DEEP_PUBLIC_PROFILE) {
                                publicUsername = socialListParentUsername
                            }
                            deep = socialListOrigin.takeUnless { it == TAB_PROFILE }
                        },
                        onOpenProfile = { selectedUsername ->
                            publicUsername = selectedUsername
                            publicProfileOrigin = DEEP_SOCIAL_LIST
                            deep = DEEP_PUBLIC_PROFILE
                        },
                    )
                }
            }
        }
    }

    private fun exerciseCatalogViewModel(): ExerciseCatalogViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
        ),
    )[ExerciseCatalogViewModel::class.java]

    private fun generalExercisePickerViewModel(): ExerciseCatalogViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple),
        ),
    )["catalog-exercise-picker", ExerciseCatalogViewModel::class.java]

    private fun exerciseDetailViewModel(): ExerciseDetailViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseDetailViewModelFactory(AppContainer.exerciseTemplateRepository),
    )[ExerciseDetailViewModel::class.java]

    private fun customExerciseEditorViewModel(
        exerciseTemplateId: String?,
    ): CustomExerciseEditorViewModel = ViewModelProvider(
        userSessionViewModels,
        CustomExerciseEditorViewModelFactory(
            exerciseTemplateId = exerciseTemplateId,
            repository = AppContainer.exerciseTemplateRepository,
        ),
    )["custom-exercise-editor-${exerciseTemplateId ?: "new"}", CustomExerciseEditorViewModel::class.java]

    private fun routineListViewModel(): RoutineListViewModel = ViewModelProvider(
        userSessionViewModels,
        RoutineListViewModelFactory(AppContainer.routineRepository),
    )[RoutineListViewModel::class.java]

    private fun routineEditorViewModel(routineId: String?): RoutineEditorViewModel = ViewModelProvider(
        userSessionViewModels,
        RoutineEditorViewModelFactory(
            routineId = routineId,
            repository = AppContainer.routineRepository,
            exerciseRepository = AppContainer.exerciseTemplateRepository,
        ),
    )["routine-editor-${routineId ?: "new"}", RoutineEditorViewModel::class.java]

    private fun routineViewerViewModel(routineId: String): RoutineViewerViewModel = ViewModelProvider(
        userSessionViewModels,
        RoutineViewerViewModelFactory(routineId, AppContainer.routineRepository),
    )["routine-viewer-$routineId", RoutineViewerViewModel::class.java]

    private fun refreshRoutineViewer(routineId: String) {
        routineViewerViewModel(routineId).refresh()
    }

    private fun exercisePickerViewModel(
        initialIds: Set<String>,
        routineId: String?,
    ): ExerciseCatalogViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(
                ExerciseSelectionMode.Multiple,
                initiallySelectedIds = initialIds,
            ),
        ),
    )["routine-picker-${routineId ?: "new"}-${initialIds.hashCode()}", ExerciseCatalogViewModel::class.java]

    private fun activeWorkoutViewModel(): ActiveWorkoutViewModel = ViewModelProvider(
        userSessionViewModels,
        ActiveWorkoutViewModelFactory(
            AppContainer.workoutRepository,
            AppContainer.exerciseTemplateRepository,
            AppContainer.analyticsRepository,
            AppContainer.applicationClock,
            AppContainer.restTimerController,
        ),
    )[ActiveWorkoutViewModel::class.java]

    private fun workoutExercisePickerViewModel(initialIds: Set<String>): ExerciseCatalogViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(
                ExerciseSelectionMode.Multiple,
                initiallySelectedIds = initialIds,
            ),
        ),
    )["workout-picker-${initialIds.hashCode()}", ExerciseCatalogViewModel::class.java]

    private fun workoutReplacementPickerViewModel(
        localId: String,
        request: Int,
    ): ExerciseCatalogViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Single),
        ),
    )["workout-replacement-picker-$localId-$request", ExerciseCatalogViewModel::class.java]

    private fun profileViewModel(): ProfileViewModel = ViewModelProvider(
        userSessionViewModels,
        ProfileViewModelFactory(
            AppContainer.profileRepository,
            AppContainer.analyticsRepository,
            AppContainer.workoutRepository,
            AppContainer.socialRepository,
            DeviceTimeZoneProvider,
            AppContainer.applicationClock,
        ),
    )[ProfileViewModel::class.java]

    private fun userSearchViewModel(): UserSearchViewModel = ViewModelProvider(
        userSessionViewModels,
        UserSearchViewModelFactory(AppContainer.socialRepository),
    )[UserSearchViewModel::class.java]

    private fun homeViewModel(): HomeViewModel = ViewModelProvider(
        userSessionViewModels,
        HomeViewModelFactory(AppContainer.socialFeedRepository, AppContainer.socialRepository),
    )[HomeViewModel::class.java]

    private fun publicProfileViewModel(username: String, currentUserId: String): PublicProfileViewModel =
        ViewModelProvider(
            userSessionViewModels,
            PublicProfileViewModelFactory(
                username,
                currentUserId,
                AppContainer.socialRepository,
                AppContainer.socialFeedRepository,
            ),
        )["public-profile-$username", PublicProfileViewModel::class.java]

    private fun socialWorkoutDetailViewModel(workoutId: String): SocialWorkoutDetailViewModel =
        ViewModelProvider(
            userSessionViewModels,
            SocialWorkoutDetailViewModelFactory(workoutId, AppContainer.socialFeedRepository),
        )["social-workout-$workoutId", SocialWorkoutDetailViewModel::class.java]

    private fun sharedRoutineViewModel(shareId: String): SharedRoutineViewModel = ViewModelProvider(
        userSessionViewModels,
        SharedRoutineViewModelFactory(shareId, AppContainer.routineRepository),
    )["shared-routine-$shareId", SharedRoutineViewModel::class.java]

    private fun socialEngagementViewModel(currentUserId: String): SocialEngagementViewModel = ViewModelProvider(
        userSessionViewModels,
        SocialEngagementViewModelFactory(currentUserId, AppContainer.socialFeedRepository),
    )[SocialEngagementViewModel::class.java]

    private fun socialListViewModel(username: String, type: SocialListType): SocialListViewModel =
        ViewModelProvider(
            userSessionViewModels,
            SocialListViewModelFactory(username, type, AppContainer.socialRepository),
        )["social-list-$username-${type.name}", SocialListViewModel::class.java]

    private fun profileCalendarViewModel(): ProfileCalendarViewModel = ViewModelProvider(
        userSessionViewModels,
        ProfileCalendarViewModelFactory(
            AppContainer.analyticsRepository,
            AppContainer.workoutRepository,
            DeviceTimeZoneProvider,
            AppContainer.applicationClock,
        ),
    )[ProfileCalendarViewModel::class.java]

    private fun shareProfile(displayName: String, username: String) {
        val url = shareLinks.profile(username) ?: return
        openShareSheet(ShareMessages.profile(displayName, url), "Compartir perfil")
    }

    private fun shareWorkout(workoutId: String) {
        val url = shareLinks.workout(workoutId) ?: return
        openShareSheet(ShareMessages.workout(url), "Compartir entrenamiento")
    }

    private fun shareWorkout(workout: SocialWorkoutDetail) {
        val url = workout.shareUrl ?: shareLinks.workout(workout.workoutId) ?: return
        openShareSheet(ShareMessages.workout(url), "Compartir entrenamiento")
    }

    private fun shareRoutine(url: String) {
        openShareSheet(ShareMessages.routine(url), "Compartir rutina")
    }

    private fun openShareSheet(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    private fun exerciseProgressViewModel(exerciseTemplateId: String): ExerciseProgressViewModel = ViewModelProvider(
        userSessionViewModels,
        ExerciseProgressViewModelFactory(exerciseTemplateId, AppContainer.analyticsRepository),
    )["exercise-progress-$exerciseTemplateId", ExerciseProgressViewModel::class.java]

    private fun measurementViewModel(): MeasurementViewModel = ViewModelProvider(
        userSessionViewModels,
        MeasurementViewModelFactory(AppContainer.measurementRepository, AppContainer.applicationClock),
    )[MeasurementViewModel::class.java]

    private companion object {
        const val TAB_HOME = "home"
        const val TAB_TRAINING = "training"
        const val TAB_PROFILE = "profile"

        const val ROUTINE_ORIGIN_TRAINING = "training"
        const val ROUTINE_ORIGIN_VIEWER = "viewer"

        const val DEEP_CATALOG = "exercise_catalog"
        const val DEEP_DETAIL = "exercise_detail"
        const val DEEP_PICKER = "exercise_picker"
        const val DEEP_CUSTOM_EDITOR = "custom_exercise_editor"
        const val DEEP_ROUTINE_VIEWER = "routine_viewer"
        const val DEEP_ROUTINE_EDITOR = "routine_editor"
        const val DEEP_ROUTINE_PICKER = "routine_exercise_picker"
        const val DEEP_WORKOUT = "workout"
        const val DEEP_WORKOUT_PICKER = "workout_exercise_picker"
        const val DEEP_WORKOUT_REPLACEMENT_PICKER = "workout_exercise_replacement_picker"
        const val DEEP_WORKOUT_SAVE = "workout_save"
        const val DEEP_WORKOUT_CONGRATS = "workout_congrats"
        const val DEEP_EXERCISE_PROGRESS = "exercise_progress"
        const val DEEP_MEASUREMENTS = "measurements"
        const val DEEP_PROFILE_EDIT = "profile_edit"
        const val DEEP_PROFILE_SETTINGS = "profile_settings"
        const val DEEP_PROFILE_STATS = "profile_stats"
        const val DEEP_PROFILE_CALENDAR = "profile_calendar"
        const val DEEP_USER_SEARCH = "user_search"
        const val DEEP_PUBLIC_PROFILE = "public_profile"
        const val DEEP_SOCIAL_LIST = "social_list"
        const val DEEP_SOCIAL_WORKOUT = "social_workout"
        const val DEEP_SOCIAL_COMMENTS = "social_comments"
        const val DEEP_SHARED_ROUTINE = "shared_routine"
    }
}
