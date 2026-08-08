package com.mar.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.mar.gym.feature.auth.ui.AuthRoute
import com.mar.gym.feature.auth.ui.AuthViewModel
import com.mar.gym.feature.auth.ui.AuthViewModelFactory
import com.mar.gym.feature.exercises.model.ExercisePickerConfig
import com.mar.gym.feature.exercises.model.ExercisePickerOutcome
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.ui.ExerciseCatalogRoute
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModel
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModelFactory
import com.mar.gym.feature.exercises.ui.ExerciseDetailRoute
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModel
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModelFactory
import com.mar.gym.feature.exercises.ui.ExercisePickerRoute
import com.mar.gym.feature.exercises.ui.openHttpsUrl
import com.mar.gym.feature.routines.ui.RoutineEditorRoute
import com.mar.gym.feature.routines.ui.RoutineEditorViewModel
import com.mar.gym.feature.routines.ui.RoutineEditorViewModelFactory
import com.mar.gym.feature.routines.ui.RoutineListRoute
import com.mar.gym.feature.routines.ui.RoutineListViewModel
import com.mar.gym.feature.routines.ui.RoutineListViewModelFactory
import com.mar.gym.feature.system.SystemViewModel
import com.mar.gym.feature.system.SystemViewModelFactory
import com.mar.gym.feature.workouts.ui.ActiveWorkoutRoute
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModel
import com.mar.gym.feature.workouts.ui.ActiveWorkoutViewModelFactory
import com.mar.gym.feature.workouts.ui.WorkoutDetailRoute
import com.mar.gym.feature.workouts.ui.WorkoutDetailViewModel
import com.mar.gym.feature.workouts.ui.WorkoutDetailViewModelFactory
import com.mar.gym.feature.workouts.ui.WorkoutHistoryRoute
import com.mar.gym.feature.workouts.ui.WorkoutHistoryViewModel
import com.mar.gym.feature.workouts.ui.WorkoutHistoryViewModelFactory
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
                var destination by rememberSaveable { mutableStateOf(DESTINATION_HOME) }
                var detailId by rememberSaveable { mutableStateOf<String?>(null) }
                var routineId by rememberSaveable { mutableStateOf<String?>(null) }
                var routinePickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
                var workoutPickerInitialIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
                var workoutDetailId by rememberSaveable { mutableStateOf<String?>(null) }
                var pendingRoutineWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }

                BackHandler(enabled = destination != DESTINATION_HOME) {
                    destination = when (destination) {
                        DESTINATION_DETAIL, DESTINATION_PICKER -> DESTINATION_CATALOG
                        DESTINATION_ROUTINE_EDITOR -> DESTINATION_ROUTINES
                        DESTINATION_ROUTINE_PICKER -> DESTINATION_ROUTINE_EDITOR
                        DESTINATION_WORKOUT_PICKER -> DESTINATION_WORKOUT
                        DESTINATION_WORKOUT_HISTORY -> DESTINATION_WORKOUT
                        DESTINATION_WORKOUT_DETAIL -> DESTINATION_WORKOUT_HISTORY
                        else -> DESTINATION_HOME
                    }
                }
                when (destination) {
                    DESTINATION_CATALOG -> {
                        val catalogViewModel = remember { exerciseCatalogViewModel() }
                        ExerciseCatalogRoute(
                            viewModel = catalogViewModel,
                            onBack = { destination = DESTINATION_HOME },
                            onOpenDetail = { id ->
                                detailId = id
                                destination = DESTINATION_DETAIL
                            },
                            onOpenPicker = { destination = DESTINATION_PICKER },
                        )
                    }
                    DESTINATION_DETAIL -> detailId?.let { id ->
                        val detailViewModel = remember { exerciseDetailViewModel() }
                        ExerciseDetailRoute(
                            exerciseTemplateId = id,
                            viewModel = detailViewModel,
                            imageLoader = AppContainer.exerciseMediaImageLoader,
                            onOpenAttribution = ::openHttpsUrl,
                            onBack = { destination = DESTINATION_CATALOG },
                        )
                    } ?: ExerciseCatalogRoute(
                        viewModel = remember { exerciseCatalogViewModel() },
                        onBack = { destination = DESTINATION_HOME },
                        onOpenDetail = { id ->
                            detailId = id
                            destination = DESTINATION_DETAIL
                        },
                        onOpenPicker = { destination = DESTINATION_PICKER },
                    )
                    DESTINATION_PICKER -> {
                        val catalogViewModel = remember { exerciseCatalogViewModel() }
                        ExercisePickerRoute(
                            viewModel = catalogViewModel,
                            onResult = { destination = DESTINATION_CATALOG },
                        )
                    }
                    DESTINATION_ROUTINES -> {
                        val viewModel = remember { routineListViewModel() }
                        RoutineListRoute(
                            viewModel = viewModel,
                            onBack = { destination = DESTINATION_HOME },
                            onCreate = {
                                routineId = null
                                destination = DESTINATION_ROUTINE_EDITOR
                            },
                            onOpenRoutine = { id ->
                                routineId = id
                                destination = DESTINATION_ROUTINE_EDITOR
                            },
                        )
                    }
                    DESTINATION_ROUTINE_EDITOR -> {
                        val currentId = routineId
                        val viewModel = remember(currentId) { routineEditorViewModel(currentId) }
                        RoutineEditorRoute(
                            viewModel = viewModel,
                            onBack = {
                                routineListViewModel().refresh()
                                destination = DESTINATION_ROUTINES
                            },
                            onOpenPicker = { ids ->
                                routinePickerInitialIds = ids.toList()
                                destination = DESTINATION_ROUTINE_PICKER
                            },
                            onOpenRoutine = { id ->
                                routineId = id
                                destination = DESTINATION_ROUTINE_EDITOR
                            },
                            onStartRoutine = { id ->
                                pendingRoutineWorkoutId = id
                                destination = DESTINATION_WORKOUT
                            },
                        )
                    }
                    DESTINATION_ROUTINE_PICKER -> {
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
                                destination = DESTINATION_ROUTINE_EDITOR
                            },
                        )
                    }
                    DESTINATION_WORKOUT -> {
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
                            onBack = { destination = DESTINATION_HOME },
                            onOpenHistory = {
                                workoutHistoryViewModel().refresh()
                                destination = DESTINATION_WORKOUT_HISTORY
                            },
                            onOpenPicker = { ids ->
                                workoutPickerInitialIds = ids.toList()
                                destination = DESTINATION_WORKOUT_PICKER
                            },
                            onOpenCompletedWorkout = { id ->
                                workoutDetailId = id
                                destination = DESTINATION_WORKOUT_DETAIL
                            },
                        )
                    }
                    DESTINATION_WORKOUT_PICKER -> {
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
                                destination = DESTINATION_WORKOUT
                            },
                        )
                    }
                    DESTINATION_WORKOUT_HISTORY -> WorkoutHistoryRoute(
                        viewModel = remember { workoutHistoryViewModel() },
                        onBack = { destination = DESTINATION_WORKOUT },
                        onOpenWorkout = { id ->
                            workoutDetailId = id
                            destination = DESTINATION_WORKOUT_DETAIL
                        },
                    )
                    DESTINATION_WORKOUT_DETAIL -> workoutDetailId?.let { id ->
                        WorkoutDetailRoute(
                            viewModel = remember(id) { workoutDetailViewModel(id) },
                            onBack = { destination = DESTINATION_WORKOUT_HISTORY },
                        )
                    }
                    else -> AuthRoute(
                        authViewModel = authViewModel,
                        systemViewModel = systemViewModel,
                        onOpenExercises = { destination = DESTINATION_CATALOG },
                        onOpenRoutines = { destination = DESTINATION_ROUTINES },
                        onOpenWorkouts = { destination = DESTINATION_WORKOUT },
                    )
                }
            }
        }
    }

    private fun exerciseCatalogViewModel(): ExerciseCatalogViewModel = ViewModelProvider(
        this,
        ExerciseCatalogViewModelFactory(
            repository = AppContainer.exerciseTemplateRepository,
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple),
        ),
    )[ExerciseCatalogViewModel::class.java]

    private fun exerciseDetailViewModel(): ExerciseDetailViewModel = ViewModelProvider(
        this,
        ExerciseDetailViewModelFactory(AppContainer.exerciseTemplateRepository),
    )[ExerciseDetailViewModel::class.java]

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

    private companion object {
        const val DESTINATION_HOME = "home"
        const val DESTINATION_CATALOG = "exercise_catalog"
        const val DESTINATION_DETAIL = "exercise_detail"
        const val DESTINATION_PICKER = "exercise_picker"
        const val DESTINATION_ROUTINES = "routines"
        const val DESTINATION_ROUTINE_EDITOR = "routine_editor"
        const val DESTINATION_ROUTINE_PICKER = "routine_exercise_picker"
        const val DESTINATION_WORKOUT = "workout"
        const val DESTINATION_WORKOUT_PICKER = "workout_exercise_picker"
        const val DESTINATION_WORKOUT_HISTORY = "workout_history"
        const val DESTINATION_WORKOUT_DETAIL = "workout_detail"
    }
}
