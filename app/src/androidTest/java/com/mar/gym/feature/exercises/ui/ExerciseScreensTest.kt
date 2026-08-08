package com.mar.gym.feature.exercises.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaAttribution
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseTemplateSummary
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.exercises.model.HttpsUrl
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
        setDetail(detail())

        composeRule.onNodeWithText("Press de banca").assertIsDisplayed()
        composeRule.onNodeWithText("1. Colócate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. Empuja").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailWithoutMediaKeepsInstructionsVisible() {
        setDetail(detail())

        composeRule.onNodeWithText("Demostración").assertIsDisplayed()
        composeRule.onNodeWithText("Demostración no disponible").assertIsDisplayed()
        composeRule.onNodeWithText("1. Colócate").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun globalDetailHasNoModificationActions() {
        setDetail(detail(source = ExerciseTemplateSource.Global))
        composeRule.onNodeWithTag("exercise-edit").assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-archive").assertDoesNotExist()
    }

    @Test
    fun activeCustomDetailHasEditAndArchiveActions() {
        setDetail(detail(source = ExerciseTemplateSource.Custom))
        composeRule.onNodeWithTag("exercise-edit").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-archive").assertIsDisplayed()
    }

    @Test
    fun archivedCustomShowsRestoreOnly() {
        setDetail(detail(source = ExerciseTemplateSource.Custom, archived = true))

        composeRule.onNodeWithTag("exercise-edit").assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-archive").assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-restore").assertIsDisplayed()
    }

    @Test
    fun customEditorUsesStableSemanticsAndEmitsNameAndSave() {
        var editorState by mutableStateOf(
            CustomExerciseEditorUiState.Editing(CustomExerciseEditorData())
        )
        var saves = 0
        composeRule.setContent {
            GYmAppTheme {
                CustomExerciseEditorScreen(
                    state = editorState,
                    onBack = {},
                    onNameChanged = { name ->
                        editorState = CustomExerciseEditorUiState.Editing(
                            editorState.data.copy(draft = editorState.data.draft.copy(name = name))
                        )
                    },
                    onExerciseTypeChanged = {},
                    onPrimaryMuscleChanged = {},
                    onSecondaryMuscleToggled = {},
                    onEquipmentChanged = {},
                    onMovementPatternChanged = {},
                    onInstructionsChanged = {},
                    onSave = { saves += 1 },
                    onReload = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("exercise-editor-name").performTextReplacement("Ejercicio propio")
        composeRule.onNodeWithTag("exercise-editor-save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("Ejercicio propio", editorState.data.draft.name)
            assertEquals(1, saves)
        }
    }

    @Test
    fun detailMediaShowsLoadingPlaceholderWithoutInternet() {
        setDetail(
            detail(media = listOf(media())),
            mediaRenderer = { _, _, modifier ->
                ExerciseMediaLoadingPlaceholder(modifier)
            },
        )

        composeRule.onNodeWithText("Demostración").assertIsDisplayed()
        composeRule.onNodeWithText("Cargando demostración…").assertIsDisplayed()
    }

    @Test
    fun visualMediaErrorDoesNotHideInstructions() {
        setDetail(
            detail(media = listOf(media())),
            mediaRenderer = { _, _, modifier ->
                ExerciseMediaErrorPlaceholder(onRetry = {}, modifier = modifier)
            },
        )

        composeRule.onNodeWithText("No se pudo cargar la demostración.").assertIsDisplayed()
        composeRule.onNodeWithText("1. Colócate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. Empuja").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun attributionIsVisibleAndOpensValidatedUrl() {
        var openedUrl: HttpsUrl? = null
        setDetail(
            detail(media = listOf(media())),
            mediaRenderer = { _, _, modifier -> Box(modifier) },
            onOpenAttribution = {
                openedUrl = it
                true
            },
        )

        composeRule.onNodeWithText("Contenido visual: proveedor de prueba")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Abrir fuente").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("https://provider.example.test/", openedUrl?.value)
        }
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
                    onCreateCustom = {},
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

    private fun setDetail(
        detail: ExerciseTemplateDetail,
        mediaRenderer: ExerciseMediaRenderer = { _, _, modifier -> Box(modifier) },
        onOpenAttribution: (HttpsUrl) -> Boolean = { false },
    ) {
        composeRule.setContent {
            GYmAppTheme {
                ExerciseDetailScreen(
                    state = ExerciseDetailUiState.Content(
                        ExerciseTemplateDocument(
                            detail,
                            requireNotNull(ExerciseTemplateEtag.fromVersion(detail.version)),
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    mediaRenderer = mediaRenderer,
                    onOpenAttribution = onOpenAttribution,
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

    private fun detail(
        media: List<ExerciseMedia> = emptyList(),
        source: ExerciseTemplateSource = ExerciseTemplateSource.Global,
        archived: Boolean = false,
    ): ExerciseTemplateDetail = ExerciseTemplateDetail(
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
        media = media,
        source = source,
        archived = archived,
    )

    private fun media(): ExerciseMedia = ExerciseMedia(
        type = ExerciseMediaType.AnimatedGif,
        role = ExerciseMediaRole.Demonstration,
        url = requireNotNull(HttpsUrl.parse("https://media.example.test/demo.gif")),
        width = null,
        height = null,
        attribution = ExerciseMediaAttribution(
            text = "Contenido visual: proveedor de prueba",
            url = HttpsUrl.parse("https://provider.example.test/"),
        ),
    )

    private companion object {
        const val ID = "77d6fc7b-4c59-46aa-b7e4-e58dc7301b11"
    }
}
