package com.mar.gym.feature.exercises.ui

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.exercises.data.ExerciseRepositoryResult
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomExerciseEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createsCustomWithSupportedFieldsOnly() = runTest {
        val repository = FakeExerciseTemplateRepository()
        val viewModel = CustomExerciseEditorViewModel(null, repository)

        viewModel.updateName("Press propio")
        viewModel.updateExerciseType(ExerciseType.WeightedBodyweight)
        viewModel.updatePrimaryMuscleGroup(MuscleGroup.Chest)
        viewModel.toggleSecondaryMuscleGroup(MuscleGroup.Triceps)
        viewModel.updateEquipment(Equipment.Dumbbell)
        viewModel.updateMovementPattern(MovementPattern.HorizontalPush)
        viewModel.updateInstructions("Preparar\nEmpujar")
        viewModel.save()
        runCurrent()

        val request = repository.createRequests.single()
        assertEquals(null, request.exerciseTemplateId)
        assertEquals("Press propio", request.name)
        assertEquals(ExerciseType.WeightedBodyweight, request.exerciseType)
        assertEquals(setOf(MuscleGroup.Triceps), request.secondaryMuscleGroups)
        assertEquals(listOf("Preparar", "Empujar"), request.instructions)
    }

    @Test
    fun editLoadsAndSendsCapturedEtag() = runTest {
        val repository = FakeExerciseTemplateRepository(
            detailHandler = {
                ExerciseRepositoryResult.Success(
                    document(detail(source = ExerciseTemplateSource.Custom, version = 4))
                )
            },
        )
        val viewModel = CustomExerciseEditorViewModel(EXERCISE_ID, repository)
        runCurrent()

        viewModel.updateName("Nombre cambiado")
        viewModel.save()
        runCurrent()

        val request = repository.replaceRequests.single()
        assertEquals("Nombre cambiado", request.first.name)
        assertEquals(4L, request.second.version)
    }

    @Test
    fun conflictKeepsUserDraftAndNeverRetries() = runTest {
        val repository = FakeExerciseTemplateRepository(
            detailHandler = {
                ExerciseRepositoryResult.Success(
                    document(detail(source = ExerciseTemplateSource.Custom, version = 2))
                )
            },
            replaceHandler = { _, _ -> conflictFailure() },
        )
        val viewModel = CustomExerciseEditorViewModel(EXERCISE_ID, repository)
        runCurrent()
        viewModel.updateName("Mi cambio sin guardar")

        viewModel.save()
        runCurrent()

        val state = viewModel.uiState.value as CustomExerciseEditorUiState.Conflict
        assertEquals("Mi cambio sin guardar", state.data.draft.name)
        assertTrue(state.data.hasUnsavedChanges)
        assertEquals(1, repository.replaceRequests.size)
    }

    @Test
    fun backendValidationErrorsRemainAttachedToDraft() = runTest {
        val repository = FakeExerciseTemplateRepository(
            createHandler = {
                ExerciseRepositoryResult.Failure(
                    NetworkFailure.HttpProblem(
                        400,
                        ProblemDetails(
                            errorCode = "VALIDATION_FAILED",
                            fieldErrors = Json.parseToJsonElement(
                                """[{"field":"name","message":"Nombre ya utilizado"}]"""
                            ),
                        ),
                        "correlation",
                    )
                )
            },
        )
        val viewModel = CustomExerciseEditorViewModel(null, repository)
        viewModel.updateName("Nombre válido")

        viewModel.save()
        runCurrent()

        val state = viewModel.uiState.value as CustomExerciseEditorUiState.Editing
        assertEquals("Nombre válido", state.data.draft.name)
        assertEquals("Nombre ya utilizado", state.data.fieldErrors["name"])
    }

    @Test
    fun duplicateNameUsesSpecificBackendFeedbackAndKeepsDraft() = runTest {
        val repository = FakeExerciseTemplateRepository(
            createHandler = {
                ExerciseRepositoryResult.Failure(
                    NetworkFailure.HttpProblem(
                        409,
                        ProblemDetails(errorCode = "EXERCISE_TEMPLATE_NAME_CONFLICT"),
                        null,
                    )
                )
            },
        )
        val viewModel = CustomExerciseEditorViewModel(null, repository)
        viewModel.updateName("Nombre repetido")

        viewModel.save()
        runCurrent()

        val state = viewModel.uiState.value as CustomExerciseEditorUiState.Error
        assertEquals(ExerciseUiErrorKind.NameConflict, state.error.kind)
        assertEquals("Nombre repetido", state.data.draft.name)
    }

    @Test
    fun archivedOrGlobalDetailCannotEnterEditor() = runTest {
        val globalRepository = FakeExerciseTemplateRepository()
        val global = CustomExerciseEditorViewModel(EXERCISE_ID, globalRepository)
        runCurrent()
        assertEquals(
            ExerciseUiErrorKind.Forbidden,
            (global.uiState.value as CustomExerciseEditorUiState.Error).error.kind,
        )

        val archivedRepository = FakeExerciseTemplateRepository(
            detailHandler = {
                ExerciseRepositoryResult.Success(
                    document(
                        detail(
                            source = ExerciseTemplateSource.Custom,
                            archived = true,
                        )
                    )
                )
            },
        )
        val archived = CustomExerciseEditorViewModel(EXERCISE_ID, archivedRepository)
        runCurrent()
        assertEquals(
            ExerciseUiErrorKind.Forbidden,
            (archived.uiState.value as CustomExerciseEditorUiState.Error).error.kind,
        )
    }

    private fun conflictFailure() = ExerciseRepositoryResult.Failure(
        NetworkFailure.HttpProblem(
            409,
            ProblemDetails(errorCode = "EXERCISE_TEMPLATE_VERSION_CONFLICT"),
            null,
        )
    )
}
