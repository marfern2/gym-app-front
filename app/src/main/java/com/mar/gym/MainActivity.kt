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
import com.mar.gym.feature.workouts.ui.SaveWorkoutRoute
import com.mar.gym.feature.workouts.ui.WorkoutCongratsRoute
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
        var routineEditorOrigin by rememberSaveable { mutableStateOf<String?>(null) }
        var routinePickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutPickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var workoutExerciseToReplaceId by rememberSaveable { mutableStateOf<String?>(null) }
        var workoutReplacementPickerRequest by rememberSaveable { mutableStateOf(0) }
        var pendingRoutineWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
        var catalogOrigin by rememberSaveable { mutableStateOf(TAB_TRAINING) }
        var detailOrigin by rememberSaveable { mutableStateOf(DEEP_CATALOG) }

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
                            clock = AppContainer.applicationClock,
                            onContinueWorkout = { deep = DEEP_WORKOUT },
                            onOpenTraining = { tab = TAB_TRAINING },
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

    private fun workoutReplacementPickerViewModel(
        localId: String,
        request: Int,
    ): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Single),
        ),
    )["workout-replacement-picker-$localId-$request", ExerciseCatalogViewModel::class.java]

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
    }
}
