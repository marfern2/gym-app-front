package com.mar.gym.feature.exercises.ui

import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExercisePickerConfig
import com.mar.gym.feature.exercises.model.ExercisePickerOutcome
import com.mar.gym.feature.exercises.model.ExerciseSelectionMode
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCatalogViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadRepresentsSuccessEmptyAndError() = runTest {
        val success = fixture()
        runCurrent()
        assertTrue(success.viewModel.uiState.value is ExerciseCatalogUiState.Content)

        val empty = fixture { ExerciseRepositoryResult.Success(page(content = emptyList())) }
        runCurrent()
        assertTrue(empty.viewModel.uiState.value is ExerciseCatalogUiState.Empty)

        val error = fixture { networkFailure() }
        runCurrent()
        assertTrue(error.viewModel.uiState.value is ExerciseCatalogUiState.Error)
    }

    @Test
    fun retryAfterInitialErrorLoadsAgain() = runTest {
        var fail = true
        val fixture = fixture {
            if (fail) networkFailure() else ExerciseRepositoryResult.Success(page())
        }
        runCurrent()
        fail = false

        fixture.viewModel.retry()
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value is ExerciseCatalogUiState.Content)
        assertEquals(2, fixture.repository.listRequests.size)
    }

    @Test
    fun searchIsTrimmedDebouncedAndEmptyRemovesQuery() = runTest {
        val fixture = fixture()
        runCurrent()

        fixture.viewModel.onSearchTextChanged("  press   banca ")
        advanceTimeBy(399)
        runCurrent()
        assertEquals(1, fixture.repository.listRequests.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("press banca", fixture.repository.listRequests.last().query)

        fixture.viewModel.onSearchTextChanged("")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(null, fixture.repository.listRequests.last().query)
    }

    @Test
    fun latestSearchCancelsAndCannotMixOlderResponse() = runTest {
        val oldResponse = CompletableDeferred<ExerciseRepositoryResult<*>>()
        val fixture = fixture { request ->
            when (request.query) {
                "old" -> @Suppress("UNCHECKED_CAST")
                (oldResponse.await() as ExerciseRepositoryResult<com.mar.gym.feature.exercises.model.ExerciseTemplatePage>)
                "new" -> ExerciseRepositoryResult.Success(
                    page(content = listOf(summary(name = "Resultado nuevo")))
                )
                else -> ExerciseRepositoryResult.Success(page())
            }
        }
        runCurrent()
        fixture.viewModel.onSearchTextChanged("old")
        advanceTimeBy(400)
        runCurrent()
        fixture.viewModel.onSearchTextChanged("new")
        advanceTimeBy(400)
        runCurrent()
        oldResponse.complete(
            ExerciseRepositoryResult.Success(page(content = listOf(summary(name = "Antiguo"))))
        )
        runCurrent()

        assertEquals("Resultado nuevo", fixture.viewModel.uiState.value.data.items.single().name)
    }

    @Test
    fun applyingClearingFiltersAndSortRestartAtPageZero() = runTest {
        val fixture = fixture { request ->
            ExerciseRepositoryResult.Success(page(page = request.page, last = request.page > 0))
        }
        runCurrent()
        fixture.viewModel.applyFilters(
            ExerciseFilters(primaryMuscleGroup = MuscleGroup.Chest, equipment = Equipment.Barbell)
        )
        runCurrent()
        assertEquals(0, fixture.repository.listRequests.last().page)
        assertEquals(2, fixture.repository.listRequests.last().filters.activeCount)

        fixture.viewModel.clearFilters()
        runCurrent()
        assertEquals(0, fixture.repository.listRequests.last().filters.activeCount)
        assertEquals(0, fixture.repository.listRequests.last().page)

        fixture.viewModel.changeSort(ExerciseSort.NameDescending)
        runCurrent()
        assertEquals(ExerciseSort.NameDescending, fixture.repository.listRequests.last().sort)
        assertEquals(0, fixture.repository.listRequests.last().page)
    }

    @Test
    fun filtersCombinedGlobalCustomAndArchivedCatalogs() = runTest {
        val fixture = fixture()
        runCurrent()

        fixture.viewModel.applyFilters(ExerciseFilters(source = ExerciseTemplateSource.Global))
        runCurrent()
        assertEquals(ExerciseTemplateSource.Global, fixture.repository.listRequests.last().filters.source)
        assertFalse(fixture.repository.listRequests.last().filters.archived)

        fixture.viewModel.applyFilters(
            ExerciseFilters(source = ExerciseTemplateSource.Custom, archived = true)
        )
        runCurrent()
        assertEquals(ExerciseTemplateSource.Custom, fixture.repository.listRequests.last().filters.source)
        assertTrue(fixture.repository.listRequests.last().filters.archived)

        fixture.viewModel.applyFilters(ExerciseFilters())
        runCurrent()
        assertEquals(null, fixture.repository.listRequests.last().filters.source)
    }

    @Test
    fun pickerIncludesActiveCustomAndForcesArchivedFalse() = runTest {
        val custom = summary(SECOND_EXERCISE_ID, "Press propio")
            .copy(source = ExerciseTemplateSource.Custom, archived = false)
        val fixture = fixture(
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple),
        ) { request ->
            assertFalse(request.filters.archived)
            ExerciseRepositoryResult.Success(page(content = listOf(summary(), custom)))
        }
        runCurrent()

        fixture.viewModel.applyFilters(
            ExerciseFilters(source = ExerciseTemplateSource.Custom, archived = true)
        )
        runCurrent()
        assertFalse(fixture.repository.listRequests.last().filters.archived)
        assertTrue(fixture.viewModel.uiState.value.data.items.any { it.source == ExerciseTemplateSource.Custom })
    }

    @Test
    fun pickerCannotSelectArchivedItemEvenIfBackendReturnsOne() = runTest {
        val archived = summary().copy(source = ExerciseTemplateSource.Custom, archived = true)
        val fixture = fixture(
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple),
        ) { ExerciseRepositoryResult.Success(page(content = listOf(archived))) }
        runCurrent()

        assertTrue(fixture.viewModel.uiState.value.data.items.isEmpty())
    }

    @Test
    fun paginationLoadsOnceDeduplicatesAndStopsAfterLast() = runTest {
        val fixture = fixture { request ->
            if (request.page == 0) {
                ExerciseRepositoryResult.Success(page(last = false))
            } else {
                ExerciseRepositoryResult.Success(
                    page(
                        page = 1,
                        last = true,
                        content = listOf(
                            summary(),
                            summary(SECOND_EXERCISE_ID, "Sentadilla"),
                        ),
                    )
                )
            }
        }
        runCurrent()
        fixture.viewModel.loadMore()
        fixture.viewModel.loadMore()
        runCurrent()

        val data = fixture.viewModel.uiState.value.data
        assertEquals(listOf(EXERCISE_ID, SECOND_EXERCISE_ID), data.items.map { it.id })
        assertFalse(data.hasNextPage)
        assertEquals(1, fixture.repository.listRequests.count { it.page == 1 })
        fixture.viewModel.loadMore()
        runCurrent()
        assertEquals(2, fixture.repository.listRequests.size)
    }

    @Test
    fun laterPageErrorPreservesContentAndRetriesOnlyFailedPage() = runTest {
        var pageOneFails = true
        val fixture = fixture { request ->
            when {
                request.page == 0 -> ExerciseRepositoryResult.Success(page(last = false))
                pageOneFails -> networkFailure()
                else -> ExerciseRepositoryResult.Success(
                    page(
                        content = listOf(summary(SECOND_EXERCISE_ID, "Sentadilla")),
                        page = 1,
                    )
                )
            }
        }
        runCurrent()
        fixture.viewModel.loadMore()
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value is ExerciseCatalogUiState.ErrorLoadingMore)
        assertEquals(listOf(EXERCISE_ID), fixture.viewModel.uiState.value.data.items.map { it.id })

        pageOneFails = false
        fixture.viewModel.retryLoadMore()
        runCurrent()
        assertEquals(listOf(0, 1, 1), fixture.repository.listRequests.map { it.page })
        assertEquals(2, fixture.viewModel.uiState.value.data.items.size)
    }

    @Test
    fun changingSearchDiscardsPendingPagination() = runTest {
        val pendingPage = CompletableDeferred<ExerciseRepositoryResult<com.mar.gym.feature.exercises.model.ExerciseTemplatePage>>()
        val fixture = fixture { request ->
            when {
                request.page == 1 -> pendingPage.await()
                request.query == "fresh" -> ExerciseRepositoryResult.Success(
                    page(content = listOf(summary(name = "Nuevo filtro")))
                )
                else -> ExerciseRepositoryResult.Success(page(last = false))
            }
        }
        runCurrent()
        fixture.viewModel.loadMore()
        runCurrent()
        fixture.viewModel.onSearchTextChanged("fresh")
        advanceTimeBy(400)
        runCurrent()
        pendingPage.complete(
            ExerciseRepositoryResult.Success(
                page(content = listOf(summary(SECOND_EXERCISE_ID, "Antiguo")), page = 1)
            )
        )
        runCurrent()

        assertEquals("Nuevo filtro", fixture.viewModel.uiState.value.data.items.single().name)
    }

    @Test
    fun pickerSupportsSingleMultipleInitialSelectionAndNoDuplicates() = runTest {
        val single = fixture(
            pickerConfig = ExercisePickerConfig(
                ExerciseSelectionMode.Single,
                setOf(EXERCISE_ID),
            )
        )
        runCurrent()
        single.viewModel.toggleSelection(SECOND_EXERCISE_ID)
        assertEquals(setOf(SECOND_EXERCISE_ID), single.viewModel.uiState.value.data.selectedIds)
        single.viewModel.toggleSelection(SECOND_EXERCISE_ID)
        assertTrue(single.viewModel.uiState.value.data.selectedIds.isEmpty())

        val multiple = fixture(
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple)
        )
        runCurrent()
        multiple.viewModel.toggleSelection(EXERCISE_ID)
        multiple.viewModel.toggleSelection(EXERCISE_ID)
        assertTrue(multiple.viewModel.uiState.value.data.selectedIds.isEmpty())
        multiple.viewModel.toggleSelection(EXERCISE_ID)
        multiple.viewModel.toggleSelection(SECOND_EXERCISE_ID)
        assertEquals(2, multiple.viewModel.uiState.value.data.selectedIds.size)
    }

    @Test
    fun pickerSelectionSurvivesSearchAndReturnsConfirmedOrCancelledOutcome() = runTest {
        val fixture = fixture(
            pickerConfig = ExercisePickerConfig(ExerciseSelectionMode.Multiple)
        )
        runCurrent()
        assertNull(fixture.viewModel.confirmSelection())
        fixture.viewModel.toggleSelection(EXERCISE_ID)
        fixture.viewModel.onSearchTextChanged("sentadilla")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(setOf(EXERCISE_ID), fixture.viewModel.uiState.value.data.selectedIds)
        val confirmed = fixture.viewModel.confirmSelection() as ExercisePickerOutcome.Confirmed
        assertEquals(setOf(EXERCISE_ID), confirmed.result.selectedExerciseTemplateIds)
        assertEquals(ExercisePickerOutcome.Cancelled, fixture.viewModel.cancelSelection())
    }

    private fun fixture(
        pickerConfig: ExercisePickerConfig? = null,
        handler: suspend (ListRequest) -> ExerciseRepositoryResult<com.mar.gym.feature.exercises.model.ExerciseTemplatePage> = {
            ExerciseRepositoryResult.Success(page())
        },
    ): Fixture {
        val repository = FakeExerciseTemplateRepository(listHandler = handler)
        return Fixture(
            repository,
            ExerciseCatalogViewModel(
                repository = repository,
                pickerConfig = pickerConfig,
                searchDebounceMillis = 400,
            ),
        )
    }

    private data class Fixture(
        val repository: FakeExerciseTemplateRepository,
        val viewModel: ExerciseCatalogViewModel,
    )
}
