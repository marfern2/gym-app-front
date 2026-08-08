package com.mar.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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

                BackHandler(enabled = destination != DESTINATION_HOME) {
                    destination = when (destination) {
                        DESTINATION_DETAIL, DESTINATION_PICKER -> DESTINATION_CATALOG
                        DESTINATION_ROUTINE_EDITOR -> DESTINATION_ROUTINES
                        DESTINATION_ROUTINE_PICKER -> DESTINATION_ROUTINE_EDITOR
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
                    else -> AuthRoute(
                        authViewModel = authViewModel,
                        systemViewModel = systemViewModel,
                        onOpenExercises = { destination = DESTINATION_CATALOG },
                        onOpenRoutines = { destination = DESTINATION_ROUTINES },
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

    private companion object {
        const val DESTINATION_HOME = "home"
        const val DESTINATION_CATALOG = "exercise_catalog"
        const val DESTINATION_DETAIL = "exercise_detail"
        const val DESTINATION_PICKER = "exercise_picker"
        const val DESTINATION_ROUTINES = "routines"
        const val DESTINATION_ROUTINE_EDITOR = "routine_editor"
        const val DESTINATION_ROUTINE_PICKER = "routine_exercise_picker"
    }
}
