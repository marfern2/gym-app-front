package com.mar.gym.feature.exercises.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.progress.ui.ExerciseProgressUiState
import com.mar.gym.core.units.WeightUnit
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

        composeRule.onNodeWithTag("exercise-search-field").performTextInput("press")
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

        composeRule.onNodeWithTag("exercise-title").assertIsDisplayed()
        composeRule.onNodeWithText("Indicaciones").performClick()
        composeRule.onNodeWithText("1. Colócate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2. Empuja").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailHeaderTabsAndShare() {
        var shared: String? = null
        setDetail(detail(), onShare = { shared = it })
        composeRule.onNodeWithTag("exercise-title").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-share").performClick()
        assertEquals("Press de banca", shared)
        composeRule.onNodeWithText("Historial").performClick()
        composeRule.onNodeWithTag("exercise-history").assertIsDisplayed()
        composeRule.onNodeWithText("Indicaciones").performClick()
        composeRule.onNodeWithTag("exercise-instructions").assertIsDisplayed()
        composeRule.onNodeWithText("Resumen").performClick()
        composeRule.onNodeWithTag("exercise-summary").assertIsDisplayed()
    }

    @Test
    fun detailBackAndProgressSelectors() {
        var backs = 0
        setDetail(detail(), onBack = { backs += 1 })
        composeRule.onNodeWithTag("exercise-metric-TotalReps").performClick()
        composeRule.onNodeWithTag("exercise-range").performClick()
        composeRule.onNodeWithText("Todo").performClick()
        composeRule.onNodeWithContentDescription("Volver").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun customWithoutMediaOrInstructionsKeepsNoteAction() {
        setDetail(detail(source = ExerciseTemplateSource.Custom).copy(
            description = null, instructions = emptyList(),
        ))
        composeRule.onNodeWithText("Indicaciones").performClick()
        composeRule.onNodeWithText("Demostración no disponible").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-note-action").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyProgressKeepsInstructionsAndNotes() {
        setDetail(detail())
        composeRule.onNodeWithTag("exercise-progress-empty").assertIsDisplayed()
        composeRule.onNodeWithText("Historial").performClick()
        composeRule.onNodeWithTag("exercise-history-empty").assertIsDisplayed()
        composeRule.onNodeWithText("Indicaciones").performClick()
        composeRule.onNodeWithTag("exercise-note-action").performScrollTo().performClick()
        composeRule.onNodeWithTag("exercise-note-input").performTextInput("Mi nota")
        composeRule.onNodeWithText("Guardar").performClick()
        composeRule.onNodeWithText("Mi nota").assertIsDisplayed()
    }

    @Test
    fun unitsSheetChangesOverride() {
        setDetail(detail())
        composeRule.onNodeWithTag("exercise-menu").performClick()
        composeRule.onNodeWithTag("exercise-unit-action").performClick()
        composeRule.onNodeWithTag("exercise-unit-LB").performClick()
        composeRule.onNodeWithTag("exercise-menu").performClick()
        composeRule.onNodeWithTag("exercise-unit-action").performClick()
        composeRule.onNodeWithTag("exercise-unit-LB").assertIsSelected()
        composeRule.onNodeWithTag("exercise-unit-default").performClick()
    }

    @Test
    fun historyEntryOpensExistingWorkout() {
        var opened: String? = null
        val session = ExercisePerformanceSession(
            "workout-1", Instant.parse("2026-10-27T12:00:00Z"), "Press de banca",
            ExerciseType.WeightReps,
            listOf(ActualPerformanceSet(1, 8, 45.toBigDecimal(), null, null, null)),
        )
        setDetail(detail(), progress = ExerciseProgressUiState(
            loading = false, recordsLoading = false, sessions = listOf(session), hasMore = false,
        ), onOpenWorkout = { opened = it })
        composeRule.onNodeWithText("Historial").performClick()
        composeRule.onNodeWithTag("exercise-history-workout-1").performClick()
        assertEquals("workout-1", opened)
    }

    @Test
    fun chartTapAndHorizontalDragSelectRealSessionsAndOpenTheirWorkout() {
        var opened: String? = null
        val sessions = listOf(
            chartSession("first", "2026-08-01T12:00:00Z", 70),
            chartSession("middle", "2026-09-15T12:00:00Z", 85),
            chartSession("latest", "2026-10-27T12:00:00Z", 95),
        )
        setDetail(detail(), progress = ExerciseProgressUiState(
            loading = false, recordsLoading = false, sessions = sessions, hasMore = false,
        ), initialOptions = ExerciseDetailOptions(range = HistoryRange.AllTime),
            onOpenWorkout = { opened = it })

        val selected = composeRule.onNodeWithTag("exercise-progress-selected")
        selected.assertContentDescriptionContains("95 kg")
        selected.assertContentDescriptionContains(LocalDate.parse("2026-10-27")
            .format(DateTimeFormatter.ofPattern("d MMM yyyy")))
        val plot = composeRule.onNodeWithTag("temporal_chart_plot")
        plot.performScrollTo().performTouchInput { click(percentOffset(0.02f, 0.5f)) }
        selected.assertContentDescriptionContains("70 kg")
        selected.assertContentDescriptionContains(LocalDate.parse("2026-08-01")
            .format(DateTimeFormatter.ofPattern("d MMM yyyy")))
        selected.performScrollTo().performClick()
        assertEquals("first", opened)

        plot.performScrollTo().performTouchInput {
            swipe(percentOffset(0.05f, 0.5f), percentOffset(0.95f, 0.5f), durationMillis = 500)
        }
        selected.assertContentDescriptionContains("95 kg")
        selected.performScrollTo().performClick()
        assertEquals("latest", opened)

        plot.performScrollTo().performTouchInput {
            swipe(percentOffset(0.95f, 0.5f), percentOffset(0.5f, 0.5f), durationMillis = 100)
        }
        selected.assertContentDescriptionContains("85 kg")
        selected.assertContentDescriptionContains(LocalDate.parse("2026-09-15")
            .format(DateTimeFormatter.ofPattern("d MMM yyyy")))
        selected.performScrollTo().performClick()
        assertEquals("middle", opened)
    }

    @Test
    fun ambiguousDailyPointCanBeExploredButCannotOpenAnArbitraryWorkout() {
        val sessions = listOf(
            chartSession("morning", "2026-10-27T08:00:00Z", 45),
            chartSession("evening", "2026-10-27T18:00:00Z", 45),
        )
        setDetail(detail(), progress = ExerciseProgressUiState(
            loading = false, recordsLoading = false, sessions = sessions, hasMore = false,
        ), initialOptions = ExerciseDetailOptions(range = HistoryRange.AllTime))
        composeRule.onNodeWithTag("exercise-progress-selected")
            .assertContentDescriptionContains("45 kg").assertHasNoClickAction()
        composeRule.onNodeWithTag("temporal_chart_plot").performScrollTo()
            .performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        composeRule.onNodeWithTag("exercise-progress-selected").assertHasNoClickAction()
    }

    @Test
    fun detailWithoutMediaKeepsInstructionsVisible() {
        setDetail(detail())

        composeRule.onNodeWithText("Demostración").assertIsDisplayed()
        composeRule.onNodeWithText("Demostración no disponible").assertIsDisplayed()
        composeRule.onNodeWithText("Indicaciones").performClick()
        composeRule.onNodeWithText("1. Colócate").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun globalDetailHasNoModificationActions() {
        setDetail(detail(source = ExerciseTemplateSource.Global))
        composeRule.onNodeWithTag("exercise-menu").performClick()
        composeRule.onNodeWithTag("exercise-edit").assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-archive").assertDoesNotExist()
    }

    @Test
    fun activeCustomDetailHasEditAndArchiveActions() {
        setDetail(detail(source = ExerciseTemplateSource.Custom))
        composeRule.onNodeWithTag("exercise-menu").performClick()
        composeRule.onNodeWithTag("exercise-edit").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-archive").assertIsDisplayed()
    }

    @Test
    fun archivedCustomShowsRestoreOnly() {
        setDetail(detail(source = ExerciseTemplateSource.Custom, archived = true))
        composeRule.onNodeWithTag("exercise-menu").performClick()

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
        composeRule.onNodeWithText("Indicaciones").performClick()
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

        composeRule.onNodeWithText("Indicaciones").performClick()
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
        onShare: (String) -> Unit = {},
        progress: ExerciseProgressUiState = ExerciseProgressUiState(loading = false, recordsLoading = false),
        onOpenWorkout: (String) -> Unit = {},
        onBack: () -> Unit = {},
        initialOptions: ExerciseDetailOptions = ExerciseDetailOptions(),
    ) {
        composeRule.setContent {
            var options by remember { mutableStateOf(initialOptions) }
            GYmAppTheme {
                ExerciseDetailScreen(
                    state = ExerciseDetailUiState.Content(
                        ExerciseTemplateDocument(
                            detail,
                            requireNotNull(ExerciseTemplateEtag.fromVersion(detail.version)),
                        )
                    ),
                    onBack = onBack,
                    onRetry = {},
                    options = options,
                    progress = progress,
                    onSelectTab = { options = options.copy(tab = it) },
                    onSelectRange = { options = options.copy(range = it, selectedChartDate = null) },
                    onSelectMetric = { options = options.copy(metric = it, selectedChartDate = null) },
                    onSelectChartPoint = { options = options.copy(selectedChartDate = it) },
                    onUnitOverride = { options = options.copy(weightUnitOverride = it) },
                    onSaveNote = { options = options.copy(note = it) },
                    onShare = onShare,
                    onOpenWorkout = onOpenWorkout,
                    mediaRenderer = mediaRenderer,
                    onOpenAttribution = onOpenAttribution,
                )
            }
        }
    }

    private fun chartSession(id: String, time: String, weight: Int) = ExercisePerformanceSession(
        id, Instant.parse(time), "Press de banca", ExerciseType.WeightReps,
        listOf(ActualPerformanceSet(1, 8, weight.toBigDecimal(), null, null, null)),
    )

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
