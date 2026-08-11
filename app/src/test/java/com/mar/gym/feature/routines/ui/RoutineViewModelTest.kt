package com.mar.gym.feature.routines.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.data.ExerciseTemplateRepository
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseFilters
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseSort as ExerciseCatalogSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.CustomExerciseDraft
import com.mar.gym.feature.exercises.model.ExerciseTemplatePage
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.LocalIdSource
import com.mar.gym.feature.routines.model.RoutineDetail
import com.mar.gym.feature.routines.model.RoutineDocument
import com.mar.gym.feature.routines.model.RoutineDraft
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.routines.model.RoutinePage
import com.mar.gym.feature.routines.model.RoutineSort
import com.mar.gym.feature.routines.model.RoutineSummary
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun listRepresentsContentEmptyErrorDebounceAndPagination() = runTest {
        val repository = FakeRoutineRepository()
        val viewModel = RoutineListViewModel(repository, searchDebounceMillis = 400)
        runCurrent()
        assertTrue(viewModel.uiState.value is RoutineListUiState.Content)

        viewModel.onSearchChanged("  fuerza   base ")
        advanceTimeBy(399); runCurrent()
        assertEquals(1, repository.listRequests.size)
        advanceTimeBy(1); runCurrent()
        assertEquals("fuerza base", repository.listRequests.last().query)

        repository.listHandler = { request ->
            RoutineRepositoryResult.Success(page(request.page, last = request.page == 1))
        }
        viewModel.onSearchChanged("")
        advanceTimeBy(400); runCurrent()
        viewModel.loadMore(); viewModel.loadMore(); runCurrent()
        assertEquals(listOf(0, 1), repository.listRequests.takeLast(2).map { it.page })

        repository.listHandler = { RoutineRepositoryResult.Success(page(content = emptyList())) }
        viewModel.showArchived(true); runCurrent()
        assertTrue(viewModel.uiState.value is RoutineListUiState.Empty)

        repository.listHandler = { RoutineRepositoryResult.Failure(NetworkFailure.Network()) }
        viewModel.showArchived(false); runCurrent()
        assertTrue(viewModel.uiState.value is RoutineListUiState.Error)
    }

    @Test
    fun listArchiveRestoreAndDuplicateUpdateListAndEmitNewId() = runTest {
        val repository = FakeRoutineRepository()
        val viewModel = RoutineListViewModel(repository, 0)
        runCurrent()
        viewModel.archive(ROUTINE_ID); runCurrent()
        assertTrue(viewModel.uiState.value is RoutineListUiState.Empty)
        assertEquals("\"2\"", repository.lastEtag?.headerValue)

        viewModel.showArchived(true); runCurrent()
        viewModel.restore(ROUTINE_ID); runCurrent()
        assertTrue(viewModel.uiState.value is RoutineListUiState.Empty)

        viewModel.showArchived(false); runCurrent()
        val effect = async { viewModel.effects.first() }
        viewModel.duplicate(ROUTINE_ID); runCurrent()
        assertEquals(COPY_ID, (effect.await() as RoutineListEffect.OpenRoutine).routineId)
    }

    @Test
    fun editorCreationValidationSavingAndUnsavedChanges() = runTest {
        val repository = FakeRoutineRepository()
        val viewModel = RoutineEditorViewModel(null, repository, FakeExerciseRepository(), SequentialIds())
        assertFalse(viewModel.uiState.value.data.hasUnsavedChanges)

        viewModel.updateName("R")
        assertTrue(viewModel.uiState.value.data.hasUnsavedChanges)
        viewModel.save()
        assertTrue(viewModel.uiState.value is RoutineEditorUiState.ValidationError)

        viewModel.updateName("Rutina nueva")
        viewModel.save(); runCurrent()
        assertTrue(viewModel.uiState.value is RoutineEditorUiState.Saved)
        assertFalse(viewModel.uiState.value.data.hasUnsavedChanges)
        assertEquals(1, repository.createRequests.size)
    }

    @Test
    fun existingEditorCapturesAndSendsEtagThenRefreshesIt() = runTest {
        val repository = FakeRoutineRepository()
        val viewModel = RoutineEditorViewModel(ROUTINE_ID, repository, FakeExerciseRepository(), SequentialIds())
        runCurrent()
        assertEquals("\"2\"", viewModel.uiState.value.data.etag?.headerValue)

        viewModel.updateDescription("cambio")
        viewModel.save(); runCurrent()
        assertEquals("\"2\"", repository.lastEtag?.headerValue)
        assertEquals("\"3\"", viewModel.uiState.value.data.etag?.headerValue)
    }

    @Test
    fun conflictDoesNotRetryOrOverwriteAndReloadWarnsViaDirtyState() = runTest {
        val repository = FakeRoutineRepository().apply {
            replaceHandler = { _, _ -> conflictFailure() }
        }
        val viewModel = RoutineEditorViewModel(ROUTINE_ID, repository, FakeExerciseRepository(), SequentialIds())
        runCurrent()
        viewModel.updateName("Cambio local")
        viewModel.save(); runCurrent()

        assertTrue(viewModel.uiState.value is RoutineEditorUiState.Conflict)
        assertTrue(viewModel.uiState.value.data.hasUnsavedChanges)
        assertEquals("Cambio local", viewModel.uiState.value.data.draft.name)
        assertEquals(1, repository.replaceRequests)

        viewModel.reloadServerVersion(); runCurrent()
        assertEquals("Rutina", viewModel.uiState.value.data.draft.name)
        assertFalse(viewModel.uiState.value.data.hasUnsavedChanges)
        assertEquals(2, repository.detailRequests)
    }

    @Test
    fun superserieEditsStayLocalAndSurviveEtagConflict() = runTest {
        val repository = FakeRoutineRepository().apply {
            replaceHandler = { _, _ -> conflictFailure() }
        }
        val viewModel = RoutineEditorViewModel(ROUTINE_ID, repository, FakeExerciseRepository(), SequentialIds())
        runCurrent()
        viewModel.addSelectedExercises(linkedSetOf(TEMPLATE_ID, SECOND_TEMPLATE_ID))
        runCurrent()
        val first = viewModel.uiState.value.data.draft.exercises.first()
        viewModel.groupWithAdjacent(first.localId, 1)

        val localGroup = viewModel.uiState.value.data.draft.exercises.first().supersetLocalId
        assertTrue(localGroup != null)
        assertEquals(0, repository.replaceRequests)
        viewModel.save()
        runCurrent()

        assertTrue(viewModel.uiState.value is RoutineEditorUiState.Conflict)
        assertEquals(
            listOf(localGroup, localGroup),
            viewModel.uiState.value.data.draft.exercises.map { it.supersetLocalId },
        )
        assertEquals(1, repository.replaceRequests)
    }

    @Test
    fun editorIntegratesPickerAddsOnlyUniqueTemplateAndSupportsReorder() = runTest {
        val viewModel = RoutineEditorViewModel(null, FakeRoutineRepository(), FakeExerciseRepository(), SequentialIds())
        viewModel.updateName("Rutina")
        viewModel.addSelectedExercises(linkedSetOf(TEMPLATE_ID, TEMPLATE_ID)); runCurrent()
        assertEquals(1, viewModel.uiState.value.data.draft.exercises.size)
        assertTrue(viewModel.uiState.value.data.hasUnsavedChanges)
        viewModel.addSet(viewModel.uiState.value.data.draft.exercises.single().localId)
        assertEquals(1, viewModel.uiState.value.data.draft.totalSets)
    }

    @Test
    fun routineAcceptsActiveCustomAndRejectsArchivedCustom() = runTest {
        val activeCustom = FakeExerciseRepository(
            template = FakeExerciseRepository.exerciseDetail(
                ExerciseTemplateSource.Custom,
                archived = false,
            )
        )
        val activeEditor = RoutineEditorViewModel(
            null,
            FakeRoutineRepository(),
            activeCustom,
            SequentialIds(),
        )
        activeEditor.addSelectedExercises(setOf(TEMPLATE_ID))
        runCurrent()
        assertEquals(1, activeEditor.uiState.value.data.draft.exercises.size)

        val archivedCustom = FakeExerciseRepository(
            template = FakeExerciseRepository.exerciseDetail(
                ExerciseTemplateSource.Custom,
                archived = true,
            )
        )
        val archivedEditor = RoutineEditorViewModel(
            null,
            FakeRoutineRepository(),
            archivedCustom,
            SequentialIds(),
        )
        archivedEditor.addSelectedExercises(setOf(TEMPLATE_ID))
        runCurrent()
        assertTrue(archivedEditor.uiState.value is RoutineEditorUiState.Error)
        assertTrue(archivedEditor.uiState.value.data.draft.exercises.isEmpty())
    }

    @Test
    fun maps404409AndNestedFieldErrors() {
        val nested = Json.parseToJsonElement(
            """[{"field":"exercises[0].sets[0].targetRpe","message":"invalid"}]"""
        )
        val error = NetworkFailure.HttpProblem(
            400,
            ProblemDetails(status = 400, errorCode = "INVALID_ROUTINE_SET", fieldErrors = nested),
            "correlation",
        ).toRoutineUiError()
        assertEquals("invalid", error.fieldErrors["exercises[0].sets[0].targetRpe"])
        assertEquals(RoutineUiErrorKind.Validation, error.kind)
        assertEquals(RoutineUiErrorKind.NotFound, NetworkFailure.HttpUnknown(404, null).toRoutineUiError().kind)
        assertEquals(RoutineUiErrorKind.Conflict, (conflictFailure().error).toRoutineUiError().kind)

        val draft = RoutineDraft(
            name = "Rutina",
            exercises = listOf(com.mar.gym.feature.routines.model.RoutineExerciseDraft(
                "exercise-local", TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
                sets = listOf(com.mar.gym.feature.routines.model.RoutineSetDraft("set-local", targetRpe = "8")),
            )),
        )
        val translated = translateFieldErrors(
            draft,
            mapOf("exercises[0].sets[0].targetRpe" to "invalid"),
        )
        assertEquals("invalid", translated["exercise.exercise-local.set.set-local.targetRpe"])
    }

    private class SequentialIds : LocalIdSource {
        private var next = 0
        override fun nextId() = "local-${++next}"
    }

    private class FakeExerciseRepository(
        private val template: ExerciseTemplateDetail = exerciseDetail(),
    ) : ExerciseTemplateRepository {
        override suspend fun getExerciseTemplates(query: String?, filters: ExerciseFilters, page: Int, size: Int, sort: ExerciseCatalogSort) =
            ExerciseRepositoryResult.Success(ExerciseTemplatePage(emptyList(), page, size, 0, 0, page == 0, true))
        override suspend fun getExerciseTemplate(exerciseTemplateId: String) = ExerciseRepositoryResult.Success(
            ExerciseTemplateDocument(
                template.copy(id = exerciseTemplateId),
                ExerciseTemplateEtag.fromVersion(template.version)!!,
            )
        )
        override suspend fun createCustomExercise(draft: CustomExerciseDraft) = networkExerciseFailure()
        override suspend fun replaceCustomExercise(draft: CustomExerciseDraft, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        override suspend fun archiveCustomExercise(exerciseTemplateId: String, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        override suspend fun restoreCustomExercise(exerciseTemplateId: String, etag: ExerciseTemplateEtag) = networkExerciseFailure()
        private fun networkExerciseFailure(): ExerciseRepositoryResult<ExerciseTemplateDocument> =
            ExerciseRepositoryResult.Failure(NetworkFailure.Network())

        companion object {
            fun exerciseDetail(
                source: ExerciseTemplateSource = ExerciseTemplateSource.Global,
                archived: Boolean = false,
            ) = ExerciseTemplateDetail(
                TEMPLATE_ID, "press", "Press", null, MuscleGroup.Chest, emptyList(), Equipment.Barbell,
                ExerciseType.WeightReps, MovementPattern.HorizontalPush,
                listOf(ExerciseInstruction(1, "Preparar")), source = source, archived = archived,
            )
        }
    }

    private data class ListRequest(val archived: Boolean, val query: String?, val page: Int)

    private class FakeRoutineRepository : RoutineRepository {
        val listRequests = mutableListOf<ListRequest>()
        val createRequests = mutableListOf<RoutineDraft>()
        var detailRequests = 0
        var replaceRequests = 0
        var lastEtag: RoutineEtag? = null
        var listHandler: suspend (ListRequest) -> RoutineRepositoryResult<RoutinePage> = {
            RoutineRepositoryResult.Success(page(archived = it.archived))
        }
        var replaceHandler: suspend (RoutineDraft, RoutineEtag) -> RoutineRepositoryResult<RoutineDocument> = { draft, _ ->
            RoutineRepositoryResult.Success(document(version = 3, name = draft.name, description = draft.description))
        }
        override suspend fun list(archived: Boolean, query: String?, page: Int, size: Int, sort: RoutineSort): RoutineRepositoryResult<RoutinePage> {
            val request = ListRequest(archived, query, page); listRequests += request; return listHandler(request)
        }
        override suspend fun detail(routineId: String): RoutineRepositoryResult<RoutineDocument> {
            detailRequests++; return RoutineRepositoryResult.Success(document())
        }
        override suspend fun create(draft: RoutineDraft): RoutineRepositoryResult<RoutineDocument> {
            createRequests += draft; return RoutineRepositoryResult.Success(document(version = 0, name = draft.name, description = draft.description))
        }
        override suspend fun replace(draft: RoutineDraft, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> {
            replaceRequests++; lastEtag = etag; return replaceHandler(draft, etag)
        }
        override suspend fun archive(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> {
            lastEtag = etag; return RoutineRepositoryResult.Success(document(archived = true))
        }
        override suspend fun restore(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> {
            lastEtag = etag; return RoutineRepositoryResult.Success(document(archived = false))
        }
        override suspend fun duplicate(routineId: String, etag: RoutineEtag, name: String?): RoutineRepositoryResult<RoutineDocument> {
            lastEtag = etag; return RoutineRepositoryResult.Success(document(id = COPY_ID, version = 0))
        }
    }

    companion object {
        const val ROUTINE_ID = "91111111-1111-4111-8111-111111111111"
        const val COPY_ID = "92222222-2222-4222-8222-222222222222"
        const val TEMPLATE_ID = "93333333-3333-4333-8333-333333333333"
        const val SECOND_TEMPLATE_ID = "94444444-4444-4444-8444-444444444444"

        private fun summary(archived: Boolean = false) = RoutineSummary(
            ROUTINE_ID, "Rutina", null, 0, archived, Instant.EPOCH, Instant.EPOCH, 2,
        )
        private fun page(page: Int = 0, last: Boolean = true, content: List<RoutineSummary> = listOf(summary()), archived: Boolean = false) = RoutinePage(
            if (content == listOf(summary())) listOf(summary(archived)) else content,
            page, 20, content.size.toLong(), page + 1, page == 0, last,
        )
        private fun document(
            id: String = ROUTINE_ID,
            version: Long = 2,
            name: String = "Rutina",
            description: String = "",
            archived: Boolean = false,
        ) = RoutineDocument(
            RoutineDetail(id, name, description.takeIf(String::isNotBlank), archived, version, Instant.EPOCH, Instant.EPOCH, emptyList()),
            RoutineEtag.fromVersion(version)!!,
        )
        private fun conflictFailure() = RoutineRepositoryResult.Failure(NetworkFailure.HttpProblem(
            409, ProblemDetails(status = 409, errorCode = "ROUTINE_VERSION_CONFLICT"), null,
        ))
    }
}
