package com.mar.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
import com.mar.gym.feature.home.ui.HomeScreen
import com.mar.gym.feature.measurements.ui.MeasurementRoute
import com.mar.gym.feature.measurements.ui.MeasurementViewModel
import com.mar.gym.feature.measurements.ui.MeasurementViewModelFactory
import com.mar.gym.feature.profile.ui.ProfileRoute
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
import com.mar.gym.feature.routines.ui.RoutineListRoute
import com.mar.gym.feature.routines.ui.RoutineListViewModel
import com.mar.gym.feature.routines.ui.RoutineListViewModelFactory
import com.mar.gym.feature.routines.ui.RoutineViewerRoute
import com.mar.gym.feature.routines.ui.RoutineViewerViewModel
import com.mar.gym.feature.routines.ui.RoutineViewerViewModelFactory
import com.mar.gym.feature.system.SystemViewModel
import com.mar.gym.feature.system.SystemViewModelFactory
import com.mar.gym.feature.training.ui.TrainingScreen
import com.mar.gym.feature.workouts.ui.ActiveWorkoutRoute
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModel
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModelFactory
import com.mar.gym.feature.workouts.ui.WorkoutDetailRoute
import com.mar.gym.feature.workouts.ui.WorkoutDetailViewModel
import com.mar.gym.feature.workouts.ui.WorkoutDetailViewModelFactory
import com.mar.gym.feature.workouts.ui.WorkoutHistoryRoute
import com.mar.gym.feature.workouts.ui.WorkoutHistoryViewModel
import com.mar.gym.feature.workouts.ui.WorkoutHistoryViewModelFactory
import com.mar.gym.ui.components.BarbellIcon
import com.mar.gym.ui.theme.GYmAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppContainer.initialize(applicationContext)

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
        setContent {
            GYmAppTheme {
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                val authenticated = authState as? AuthUiState.Authenticated
                if (authenticated != null) {
                    AuthenticatedApp(
                        user = authenticated.user,
                        authViewModel = authViewModel,
                    )
                } else {
                    AuthRoute(
                        authViewModel = authViewModel,
                        systemViewModel = systemViewModel,
                    )
                }            }
        }
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
        var routineViewerOrigin by rememberSaveable { mutableStateOf<String?>(null) }
        var routineEditorOrigin by rememberSaveable { mutableStateOf<String?>(null) }
        var routinePickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutPickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutDetailId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingRoutineWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
        var catalogOrigin by rememberSaveable { mutableStateOf(TAB_TRAINING) }

        val activeWorkoutState by activeWorkoutViewModel().uiState.collectAsStateWithLifecycle()
        val historyState by workoutHistoryViewModel().uiState.collectAsStateWithLifecycle()
        val routinesState by routineListViewModel().uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            routineListViewModel().effects.collect { effect ->
                if (effect is RoutineListEffect.OpenRoutine) {
                    routineId = effect.routineId
                    routineViewerOrigin = if (deep == DEEP_ROUTINES) ROUTINE_ORIGIN_ROUTINES else ROUTINE_ORIGIN_TRAINING
                    deep = DEEP_ROUTINE_VIEWER
                }
            }
        }

        BackHandler(enabled = deep != null || tab != TAB_HOME) {
            when {
                deep != null -> deep = when (deep) {
                    DEEP_CATALOG -> {
                        tab = catalogOrigin
                        null
                    }
                    DEEP_DETAIL, DEEP_PICKER -> DEEP_CATALOG
                    DEEP_EXERCISE_PROGRESS -> DEEP_DETAIL
                    DEEP_CUSTOM_EDITOR -> if (exerciseEditorId == null) DEEP_CATALOG else DEEP_DETAIL
                    DEEP_ROUTINE_VIEWER -> {
                        routineListViewModel().refresh()
                        when (routineViewerOrigin) {
                            ROUTINE_ORIGIN_ROUTINES -> DEEP_ROUTINES
                            else -> {
                                tab = TAB_TRAINING
                                null
                            }
                        }
                    }
                    DEEP_ROUTINE_EDITOR -> {
                        routineListViewModel().refresh()
                        when (routineEditorOrigin) {
                            ROUTINE_ORIGIN_VIEWER -> {
                                routineId?.let(::refreshRoutineViewer)
                                DEEP_ROUTINE_VIEWER
                            }
                            ROUTINE_ORIGIN_ROUTINES -> DEEP_ROUTINES
                            else -> {
                                tab = TAB_TRAINING
                                null
                            }
                        }
                    }
                    DEEP_ROUTINE_PICKER -> DEEP_ROUTINE_EDITOR
                    DEEP_WORKOUT_PICKER -> DEEP_WORKOUT
                    DEEP_WORKOUT_DETAIL -> DEEP_WORKOUT_HISTORY
                    DEEP_MEASUREMENTS -> {
                        profileViewModel().refresh()
                        tab = TAB_PROFILE
                        null
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
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (tab) {
                        TAB_HOME -> HomeScreen(
                            user = user,
                            activeWorkout = activeWorkoutState,
                            history = historyState,
                            clock = AppContainer.applicationClock,
                            onContinueWorkout = { deep = DEEP_WORKOUT },
                            onOpenHistoryItem = { id ->
                                workoutDetailId = id
                                deep = DEEP_WORKOUT_DETAIL
                            },
                            onOpenTraining = { tab = TAB_TRAINING },
                            onRetryHistory = { workoutHistoryViewModel().retry() },
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
                                routineViewerOrigin = ROUTINE_ORIGIN_TRAINING
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
                            onArchiveRoutine = { routineListViewModel().archive(it) },
                            onRestoreRoutine = { routineListViewModel().restore(it) },
                            onDuplicateRoutine = { routineListViewModel().duplicate(it) },
                            onOpenHistory = {
                                workoutHistoryViewModel().refresh()
                                deep = DEEP_WORKOUT_HISTORY
                            },
                            onOpenCatalog = {
                                catalogOrigin = TAB_TRAINING
                                deep = DEEP_CATALOG
                            },
                            onOpenAllRoutines = { deep = DEEP_ROUTINES },
                            onCreateRoutine = {
                                routineId = null
                                routineEditorOrigin = ROUTINE_ORIGIN_TRAINING
                                deep = DEEP_ROUTINE_EDITOR
                            },
                            onRetryRoutines = { routineListViewModel().retry() },
                        )
                        TAB_PROFILE -> ProfileRoute(
                            viewModel = profileViewModel(),
                            onOpenMeasurements = { deep = DEEP_MEASUREMENTS },
                            onOpenExercises = {
                                catalogOrigin = TAB_PROFILE
                                deep = DEEP_CATALOG
                            },
                            onLogout = { authViewModel.logout() },
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
                            exerciseCatalogViewModel().refresh()
                            deep = DEEP_CATALOG
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
                DEEP_ROUTINES -> RoutineListRoute(
                    viewModel = remember { routineListViewModel() },
                    onBack = {
                        deep = null
                        tab = TAB_TRAINING
                    },
                    onCreate = {
                        routineId = null
                        routineEditorOrigin = ROUTINE_ORIGIN_ROUTINES
                        deep = DEEP_ROUTINE_EDITOR
                    },
                    onOpenRoutine = { id ->
                        routineId = id
                        routineViewerOrigin = ROUTINE_ORIGIN_ROUTINES
                        deep = DEEP_ROUTINE_VIEWER
                    },
                    onEditRoutine = { id ->
                        routineId = id
                        routineEditorOrigin = ROUTINE_ORIGIN_ROUTINES
                        deep = DEEP_ROUTINE_EDITOR
                    },
                    onStartRoutine = { id ->
                        pendingRoutineWorkoutId = id
                        deep = DEEP_WORKOUT
                    },
                )
                DEEP_ROUTINE_VIEWER -> {
                    val currentId = routineId
                    if (currentId != null) {
                        RoutineViewerRoute(
                            viewModel = remember(currentId) { routineViewerViewModel(currentId) },
                            onBack = {
                                routineListViewModel().refresh()
                                when (routineViewerOrigin) {
                                    ROUTINE_ORIGIN_ROUTINES -> deep = DEEP_ROUTINES
                                    else -> {
                                        deep = null
                                        tab = TAB_TRAINING
                                    }
                                }
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
                        )
                    } else RoutineListRoute(
                        viewModel = remember { routineListViewModel() },
                        onBack = {
                            deep = null
                            tab = TAB_TRAINING
                        },
                        onCreate = {
                            routineId = null
                            routineEditorOrigin = ROUTINE_ORIGIN_ROUTINES
                            deep = DEEP_ROUTINE_EDITOR
                        },
                        onOpenRoutine = { id ->
                            routineId = id
                            routineViewerOrigin = ROUTINE_ORIGIN_ROUTINES
                            deep = DEEP_ROUTINE_VIEWER
                        },
                        onEditRoutine = { id ->
                            routineId = id
                            routineEditorOrigin = ROUTINE_ORIGIN_ROUTINES
                            deep = DEEP_ROUTINE_EDITOR
                        },
                        onStartRoutine = { id ->
                            pendingRoutineWorkoutId = id
                            deep = DEEP_WORKOUT
                        },
                    )
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
                                ROUTINE_ORIGIN_ROUTINES -> deep = DEEP_ROUTINES
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
                        onOpenHistory = {
                            workoutHistoryViewModel().refresh()
                            deep = DEEP_WORKOUT_HISTORY
                        },
                        onOpenPicker = { ids ->
                            workoutPickerInitialIds = ids.toList()
                            deep = DEEP_WORKOUT_PICKER
                        },
                        onOpenCompletedWorkout = { id ->
                            workoutDetailId = id
                            deep = DEEP_WORKOUT_DETAIL
                        },
                    )
                }
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
                DEEP_WORKOUT_HISTORY -> WorkoutHistoryRoute(
                    viewModel = remember { workoutHistoryViewModel() },
                    onBack = {
                        deep = null
                        tab = TAB_TRAINING
                    },
                    onOpenWorkout = { id ->
                        workoutDetailId = id
                        deep = DEEP_WORKOUT_DETAIL
                    },
                )
                DEEP_WORKOUT_DETAIL -> workoutDetailId?.let { id ->
                    WorkoutDetailRoute(
                        viewModel = remember(id) { workoutDetailViewModel(id) },
                        onBack = { deep = DEEP_WORKOUT_HISTORY },
                    )
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
            }
        }
    }

    private fun exerciseCatalogViewModel(): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
        ),
    )[ExerciseCatalogViewModel::class.java]

    private fun generalExercisePickerViewModel(): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple),
        ),
    )["catalog-exercise-picker", ExerciseCatalogViewModel::class.java]

    private fun exerciseDetailViewModel(): ExerciseDetailViewModel = ViewModelProvider(
        this,
        ExerciseDetailViewModelFactory(AppContainer.exerciseTemplateRepository),
    )[ExerciseDetailViewModel::class.java]

    private fun customExerciseEditorViewModel(
        exerciseTemplateId: String?,
    ): CustomExerciseEditorViewModel = ViewModelProvider(
        this,
        CustomExerciseEditorViewModelFactory(
            exerciseTemplateId = exerciseTemplateId,
            repository = AppContainer.exerciseTemplateRepository,
        ),
    )["custom-exercise-editor-${exerciseTemplateId ?: "new"}", CustomExerciseEditorViewModel::class.java]

    private fun routineListViewModel(): RoutineListViewModel = ViewModelProvider(
        this,
        RoutineListViewModelFactory(AppContainer.routineRepository),
    )[RoutineListViewModel::class.java]

    private fun routineEditorViewModel(routineId: String?): RoutineEditorViewModel = ViewModelProvider(
        this,
        RoutineEditorViewModelFactory(
            routineId = routineId,
            repository = AppContainer.routineRepository,
            exerciseRepository = AppContainer.exerciseTemplateRepository,
        ),
    )["routine-editor-${routineId ?: "new"}", RoutineEditorViewModel::class.java]

    private fun routineViewerViewModel(routineId: String): RoutineViewerViewModel = ViewModelProvider(
        this,
        RoutineViewerViewModelFactory(routineId, AppContainer.routineRepository),
    )["routine-viewer-$routineId", RoutineViewerViewModel::class.java]

    private fun refreshRoutineViewer(routineId: String) {
        routineViewerViewModel(routineId).refresh()
    }

    private fun exercisePickerViewModel(
        initialIds: Set<String>,
        routineId: String?,
    ): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(
                ExerciseSelectionMode.Multiple,
                initiallySelectedIds = initialIds,
            ),
        ),
    )["routine-picker-${routineId ?: "new"}-${initialIds.hashCode()}", ExerciseCatalogViewModel::class.java]

    private fun activeWorkoutViewModel(): ActiveWorkoutViewModel = ViewModelProvider(
        this,
        ActiveWorkoutViewModelFactory(
            AppContainer.workoutRepository,
            AppContainer.exerciseTemplateRepository,
            AppContainer.analyticsRepository,
            AppContainer.applicationClock,
        ),
    )[ActiveWorkoutViewModel::class.java]

    private fun workoutExercisePickerViewModel(initialIds: Set<String>): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(
                ExerciseSelectionMode.Multiple,
                initiallySelectedIds = initialIds,
            ),
        ),
    )["workout-picker-${initialIds.hashCode()}", ExerciseCatalogViewModel::class.java]

    private fun workoutHistoryViewModel(): WorkoutHistoryViewModel = ViewModelProvider(
        this,
        WorkoutHistoryViewModelFactory(AppContainer.workoutRepository),
    )[WorkoutHistoryViewModel::class.java]

    private fun workoutDetailViewModel(workoutId: String): WorkoutDetailViewModel = ViewModelProvider(
        this,
        WorkoutDetailViewModelFactory(workoutId, AppContainer.workoutRepository),
    )["workout-detail-$workoutId", WorkoutDetailViewModel::class.java]

    private fun profileViewModel(): ProfileViewModel = ViewModelProvider(
        this,
        ProfileViewModelFactory(
            AppContainer.profileRepository,
            AppContainer.analyticsRepository,
            AppContainer.measurementRepository,
            DeviceTimeZoneProvider,
            AppContainer.applicationClock,
        ),
    )[ProfileViewModel::class.java]

    private fun exerciseProgressViewModel(exerciseTemplateId: String): ExerciseProgressViewModel = ViewModelProvider(
        this,
        ExerciseProgressViewModelFactory(exerciseTemplateId, AppContainer.analyticsRepository),
    )["exercise-progress-$exerciseTemplateId", ExerciseProgressViewModel::class.java]

    private fun measurementViewModel(): MeasurementViewModel = ViewModelProvider(
        this,
        MeasurementViewModelFactory(AppContainer.measurementRepository, AppContainer.applicationClock),
    )[MeasurementViewModel::class.java]

    private companion object {
        const val TAB_HOME = "home"
        const val TAB_TRAINING = "training"
        const val TAB_PROFILE = "profile"

        const val ROUTINE_ORIGIN_TRAINING = "training"
        const val ROUTINE_ORIGIN_ROUTINES = "routines"
        const val ROUTINE_ORIGIN_VIEWER = "viewer"

        const val DEEP_CATALOG = "exercise_catalog"
        const val DEEP_DETAIL = "exercise_detail"
        const val DEEP_PICKER = "exercise_picker"
        const val DEEP_CUSTOM_EDITOR = "custom_exercise_editor"
        const val DEEP_ROUTINES = "routines"
        const val DEEP_ROUTINE_VIEWER = "routine_viewer"
        const val DEEP_ROUTINE_EDITOR = "routine_editor"
        const val DEEP_ROUTINE_PICKER = "routine_exercise_picker"
        const val DEEP_WORKOUT = "workout"
        const val DEEP_WORKOUT_PICKER = "workout_exercise_picker"
        const val DEEP_WORKOUT_HISTORY = "workout_history"
        const val DEEP_WORKOUT_DETAIL = "workout_detail"
        const val DEEP_EXERCISE_PROGRESS = "exercise_progress"
        const val DEEP_MEASUREMENTS = "measurements"
    }
}
