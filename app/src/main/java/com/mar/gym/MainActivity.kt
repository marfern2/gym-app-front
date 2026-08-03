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
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.ui.ExerciseCatalogRoute
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModel
import com.mar.gym.feature.exercises.ui.ExerciseCatalogViewModelFactory
import com.mar.gym.feature.exercises.ui.ExerciseDetailRoute
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModel
import com.mar.gym.feature.exercises.ui.ExerciseDetailViewModelFactory
import com.mar.gym.feature.exercises.ui.ExercisePickerRoute
import com.mar.gym.feature.exercises.ui.openHttpsUrl
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

                BackHandler(enabled = destination != DESTINATION_HOME) {
                    destination = when (destination) {
                        DESTINATION_DETAIL, DESTINATION_PICKER -> DESTINATION_CATALOG
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
                    else -> AuthRoute(
                        authViewModel = authViewModel,
                        systemViewModel = systemViewModel,
                        onOpenExercises = { destination = DESTINATION_CATALOG },
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

    private companion object {
        const val DESTINATION_HOME = "home"
        const val DESTINATION_CATALOG = "exercise_catalog"
        const val DESTINATION_DETAIL = "exercise_detail"
        const val DESTINATION_PICKER = "exercise_picker"
    }
}
