package com.mar.gym.feature.routines.ui

import androidx.lifecycle.ViewModelProvider
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.*
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedRoutineViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val shareId = "87777777-7777-4777-8777-777777777777"
    private val routineId = "11111111-1111-4111-8111-111111111111"

    @Test fun loadingDoubleTapAndSavedRoutineId() = runTest {
        val fake = FakeRepository()
        val vm = SharedRoutineViewModel(shareId, fake)
        assertEquals(SharedRoutineUiState.Loading, vm.uiState.value)
        runCurrent()
        val pending = CompletableDeferred<RoutineRepositoryResult<RoutineDocument>>()
        fake.importResult = { pending.await() }
        var refreshed = 0
        vm.importShared { refreshed++ }
        vm.importShared()
        assertTrue((vm.uiState.value as SharedRoutineUiState.Content).importing)
        runCurrent()
        assertEquals(1, fake.importCalls)
        pending.complete(RoutineRepositoryResult.Success(document()))
        runCurrent()
        val state = vm.uiState.value as SharedRoutineUiState.Content
        assertEquals(routineId, state.importedRoutineId)
        assertFalse(state.importing)
        assertEquals(1, refreshed)
        vm.importShared()
        runCurrent()
        assertEquals(1, fake.importCalls)
    }

    @Test fun conflictAndMissingShareShowSafeMessages() = runTest {
        val fake = FakeRepository()
        val vm = SharedRoutineViewModel(shareId, fake)
        runCurrent()
        fake.importResult = { RoutineRepositoryResult.Failure(NetworkFailure.HttpProblem(
            409, ProblemDetails(errorCode = "CUSTOM_EXERCISE_NAME_CONFLICT"), null)) }
        vm.importShared(); runCurrent()
        assertTrue((vm.uiState.value as SharedRoutineUiState.Content).importError!!.contains("conflicto"))
        fake.importResult = { RoutineRepositoryResult.Failure(NetworkFailure.HttpProblem(
            404, ProblemDetails(errorCode = "ROUTINE_NOT_FOUND"), null)) }
        vm.importShared(); runCurrent()
        assertTrue((vm.uiState.value as SharedRoutineUiState.Content).importError!!.contains("no está disponible"))
    }

    @Test fun logoutCancelsImportAndNewUserHasNoPreviousResult() = runTest {
        val scope = UserSessionViewModelScope()
        scope.activate("a")
        val fake = FakeRepository()
        val pending = CompletableDeferred<RoutineRepositoryResult<RoutineDocument>>()
        fake.importResult = { pending.await() }
        val a = ViewModelProvider(scope, SharedRoutineViewModelFactory(shareId, fake))
            .get(SharedRoutineViewModel::class.java)
        runCurrent()
        a.importShared(); runCurrent()
        scope.clearSession()
        scope.activate("b")
        val b = ViewModelProvider(scope, SharedRoutineViewModelFactory(shareId, fake))
            .get(SharedRoutineViewModel::class.java)
        runCurrent()
        pending.complete(RoutineRepositoryResult.Success(document()))
        runCurrent()
        assertEquals(null, (b.uiState.value as SharedRoutineUiState.Content).importedRoutineId)
        assertFalse(scope.isActive("a"))
        assertTrue(scope.isActive("b"))
    }

    private fun document() = RoutineDocument(RoutineDetail(routineId, "Importada", null,
        false, 0, Instant.EPOCH, Instant.EPOCH, emptyList()), RoutineEtag.fromVersion(0)!!)

    private inner class FakeRepository : RoutineRepository {
        var importCalls = 0
        var importResult: suspend () -> RoutineRepositoryResult<RoutineDocument> = {
            RoutineRepositoryResult.Success(document())
        }
        override suspend fun importShared(shareId: String): RoutineRepositoryResult<RoutineDocument> {
            importCalls++
            return importResult()
        }
        override suspend fun sharedDetail(shareId: String) = RoutineRepositoryResult.Success(
            SharedRoutine(shareId, "https://example.test/r/$shareId", "Compartida", null,
                Instant.EPOCH, emptyList()))
        override suspend fun list(archived: Boolean, query: String?, page: Int, size: Int,
                                  sort: RoutineSort): RoutineRepositoryResult<RoutinePage> = error("unused")
        override suspend fun detail(routineId: String): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun create(draft: RoutineDraft): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun replace(draft: RoutineDraft, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun archive(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun restore(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun duplicate(routineId: String, etag: RoutineEtag, name: String?): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun delete(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<Unit> = error("unused")
    }
}
