package com.mar.gym.feature.exercises.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.ui.theme.GYmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExerciseScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogShowsLoadingListEmptyAndFilterIndicator() {
        var state by mutableStateOf<ExerciseCatalogUiState>(
            ExerciseCatalogUiState.Loading(ExerciseCatalogData())
        )
        setCatalog(state, stateProvider = { state })
        composeRule.onNodeWithText("Cargando ejercicios…").assertIsDisplayed()

        composeRule.runOnIdle {
            state = ExerciseCatalogUiState.Content(
                ExerciseCatalogData(
                    items = listOf(summary()),
                    filters = ExerciseFilters(
                        primaryMuscleGroup = MuscleGroup.Chest,
                        equipment = Equipment.Barbell,
                    ),
                    currentPage = 0,
                )
            )
        }
        composeRule.onNodeWithText("Press de banca").assertIsDisplayed()
        composeRule.onNodeWithText("Filtros (2)").assertIsDisplayed()

        composeRule.runOnIdle {
            state = ExerciseCatalogUiState.Empty(ExerciseCatalogData(currentPage = 0))
        }
        composeRule.onNodeWithText("No hay ejercicios").assertIsDisplayed()
    }

    @Test
    fun catalogErrorRetries() {
        var retries = 0
        setCatalog(
            state = ExerciseCatalogUiState.Error(
                ExerciseCatalogData(),
                ExerciseUiError(ExerciseUiErrorKind.Network, null),
            ),
            onRetry = { retries += 1 },
        )

        composeRule.onNodeWithText("No hay conexión con el servidor.").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun searchFieldEmitsUserText() {
        var query = ""
        setCatalog(
            ExerciseCatalogUiState.Content(
                ExerciseCatalogData(items = listOf(summary()), currentPage = 0)
            ),
            onSearch = { query = it },
        )

        composeRule.onNodeWithText("Buscar ejercicios").performTextInput("press")
        assertEquals("press", query)
    }

    @Test
    fun singlePickerShowsModeAndEmitsSelectedId() {
        var selectedId: String? = null
        setCatalog(
            state = ExerciseCatalogUiState.Content(
                ExerciseCatalogData(
                    items = listOf(summary()),
                    currentPage = 0,
                    selectionMode = ExerciseSelectionMode.Single,
                )
            ),
            pickerMode = true,
            onToggleSelection = { selectedId = it },
        )

        composeRule.onNodeWithText("Seleccionar ejercicio").assertIsDisplayed()
        composeRule.onNodeWithText("Press de banca").performClick()
        assertEquals(ID, selectedId)
    }

    @Test
    fun multiplePickerShowsCountAndConfirmationAvailability() {
        var state by mutableStateOf<ExerciseCatalogUiState>(
            ExerciseCatalogUiState.Content(
                ExerciseCatalogData(
                    items = listOf(summary()),
                    currentPage = 0,
                    selectionMode = ExerciseSelectionMode.Multiple,
                    selectedIds = setOf(ID),
                )
            )
        )
        setCatalog(
            state = state,
            pickerMode = true,
            stateProvider = { state },
        )
        composeRule.onNodeWithText("Seleccionar ejercicios").assertIsDisplayed()
        composeRule.onNodeWithText("Seleccionados: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()

        composeRule.runOnIdle {
            state = ExerciseCatalogUiState.Content(
                ExerciseCatalogData(
                    items = listOf(summary()),
                    currentPage = 0,
                    selectionMode = ExerciseSelectionMode.Multiple,
                )
            )
        }
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
    }

    @Test
    fun detailShowsFieldsAndOrderedInstructions() {
        composeRule.setContent {
            GYmAppTheme {
                ExerciseDetailScreen(
                    state = ExerciseDetailUiState.Content(detail()),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Press de banca").assertIsDisplayed()
        composeRule.onNodeWithText("1. Colócate").assertIsDisplayed()
        composeRule.onNodeWithText("2. Empuja").assertIsDisplayed()
    }

    private fun setCatalog(
        state: ExerciseCatalogUiState,
        pickerMode: Boolean = false,
        onRetry: () -> Unit = {},
        onSearch: (String) -> Unit = {},
        onToggleSelection: (String) -> Unit = {},
        stateProvider: () -> ExerciseCatalogUiState = { state },
    ) {
        composeRule.setContent {
            GYmAppTheme {
                ExerciseCatalogScreen(
                    state = stateProvider(),
                    pickerMode = pickerMode,
                    onBack = {},
                    onOpenDetail = {},
                    onOpenPicker = {},
                    onSearchTextChanged = onSearch,
                    onApplyFilters = {},
                    onChangeSort = {},
                    onRetry = onRetry,
                    onLoadMore = {},
                    onRetryLoadMore = {},
                    onToggleSelection = onToggleSelection,
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
    }

    private fun summary(): ExerciseTemplateSummary = ExerciseTemplateSummary(
        id = ID,
        slug = "press-banca",
        name = "Press de banca",
        primaryMuscleGroup = MuscleGroup.Chest,
        equipment = Equipment.Barbell,
        exerciseType = ExerciseType.WeightReps,
        movementPattern = MovementPattern.HorizontalPush,
    )

    private fun detail(): ExerciseTemplateDetail = ExerciseTemplateDetail(
        id = ID,
        slug = "press-banca",
        name = "Press de banca",
        description = "Descripción",
        primaryMuscleGroup = MuscleGroup.Chest,
        secondaryMuscleGroups = listOf(MuscleGroup.Triceps),
        equipment = Equipment.Barbell,
        exerciseType = ExerciseType.WeightReps,
        movementPattern = MovementPattern.HorizontalPush,
        instructions = listOf(
            ExerciseInstruction(1, "Colócate"),
            ExerciseInstruction(2, "Empuja"),
        ),
    )

    private companion object {
        const val ID = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11"
    }
}
