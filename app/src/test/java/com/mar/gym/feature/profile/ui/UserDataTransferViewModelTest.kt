package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModelProvider
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.profile.data.UserDataDocumentStore
import com.mar.gym.feature.profile.data.UserDataImportSummary
import com.mar.gym.feature.profile.data.UserDataFileTooLarge
import com.mar.gym.feature.profile.data.UserDataTransferRepository
import com.mar.gym.feature.system.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataTransferViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun exportLoadingDestinationCancellationAndNetworkError() = runTest {
        val fake = FakeTransfer()
        val vm = UserDataTransferViewModel(fake, FakeDocuments())
        val pending = CompletableDeferred<NetworkResponse<String>>()
        fake.exportResult = { pending.await() }
        vm.export()
        assertEquals(UserDataTransferState.LoadingExport, vm.state.value)
        vm.export(); runCurrent()
        assertEquals(1, fake.exportCalls)
        pending.complete(NetworkResponse.Success("{ \"schemaVersion\":1 }", null))
        runCurrent()
        assertEquals(UserDataTransferState.AwaitingDestination, vm.state.value)
        vm.destinationSelected(null)
        assertEquals(UserDataTransferState.Idle, vm.state.value)
        fake.exportResult = { NetworkResponse.Failure(NetworkFailure.Network()) }
        vm.export(); runCurrent()
        assertTrue((vm.state.value as UserDataTransferState.Error).message.contains("conexión"))
    }

    @Test fun importPickerCancellationAndErrorMappings() {
        val vm = UserDataTransferViewModel(FakeTransfer(), FakeDocuments())
        vm.beginImportSelection()
        assertEquals(UserDataTransferState.AwaitingSource, vm.state.value)
        vm.importSelected(null)
        assertEquals(UserDataTransferState.Idle, vm.state.value)
        val cases = mapOf(
            "UNSUPPORTED_SCHEMA_VERSION" to "versión no compatible",
            "INVALID_REFERENCE" to "incompatibles o incompletos",
            "MALFORMED_IMPORT" to "no es una exportación válida",
            "PAYLOAD_TOO_LARGE" to "demasiado grande",
        )
        cases.forEach { (code, text) ->
            val failure = NetworkFailure.HttpProblem(400, ProblemDetails(errorCode = code), null)
            assertTrue(failure.safeTransferError(true).contains(text))
        }
        assertTrue(NetworkFailure.Network().safeTransferError(true).contains("conexión"))
    }

    @Test fun exportWritesOriginalJsonAndShowsSuccess() = runTest {
        val json = "{ \"schemaVersion\":1,\n \"routines\":[] }"
        val fake = FakeTransfer().apply { exportResult = { NetworkResponse.Success(json, null) } }
        val documents = FakeDocuments()
        val vm = UserDataTransferViewModel(fake, documents)
        vm.export(); runCurrent()
        vm.destinationSelected("content://test/export.json"); runCurrent()
        assertEquals(json, documents.written)
        assertEquals("content://test/export.json", documents.writtenUri)
        assertEquals(UserDataTransferState.ExportSaved, vm.state.value)
    }

    @Test fun importRequiresConfirmationPostsExactJsonAndShowsSummary() = runTest {
        val json = "{ \"schemaVersion\":1,\n \"routines\": [] }"
        val fake = FakeTransfer()
        val documents = FakeDocuments().apply { contents = json }
        val vm = UserDataTransferViewModel(fake, documents)
        vm.beginImportSelection()
        vm.importSelected("content://test/import.json")
        assertEquals(UserDataTransferState.ReadingImport, vm.state.value)
        runCurrent()
        assertEquals(UserDataTransferState.ConfirmImport, vm.state.value)
        assertEquals(null, fake.importedJson)
        var refreshed = 0
        vm.confirmImport { refreshed++ }
        assertEquals(UserDataTransferState.Importing, vm.state.value)
        vm.confirmImport { refreshed++ }
        runCurrent()
        assertEquals(json, fake.importedJson)
        assertEquals(1, fake.importCalls)
        assertEquals(1, refreshed)
        val summary = (vm.state.value as UserDataTransferState.ImportCompleted).summary
        assertEquals(8, summary.routinesCreated)
        assertEquals(4, summary.measurementsCreated)
    }

    @Test fun malformedAndOversizedDocumentStopBeforePost() = runTest {
        val fake = FakeTransfer()
        val documents = FakeDocuments().apply { contents = "not JSON" }
        val vm = UserDataTransferViewModel(fake, documents)
        vm.beginImportSelection(); vm.importSelected("content://test/bad.json"); runCurrent()
        assertTrue((vm.state.value as UserDataTransferState.Error).message.contains("no es una exportación válida"))
        assertEquals(0, fake.importCalls)
        documents.readError = UserDataFileTooLarge()
        vm.beginImportSelection(); vm.importSelected("content://test/large.json"); runCurrent()
        assertTrue((vm.state.value as UserDataTransferState.Error).message.contains("demasiado grande"))
        assertEquals(0, fake.importCalls)
    }

    @Test fun backendValidationAndNetworkErrorsAreSafe() = runTest {
        val fake = FakeTransfer()
        val documents = FakeDocuments().apply { contents = "{\"schemaVersion\":1}" }
        val vm = UserDataTransferViewModel(fake, documents)
        listOf(
            "UNSUPPORTED_SCHEMA_VERSION" to "versión no compatible",
            "INVALID_REFERENCE" to "incompatibles o incompletos",
            "MALFORMED_IMPORT" to "no es una exportación válida",
            "PAYLOAD_TOO_LARGE" to "demasiado grande",
        ).forEach { (code, expected) ->
            fake.importResult = { NetworkResponse.Failure(NetworkFailure.HttpProblem(
                400, ProblemDetails(errorCode = code), null)) }
            vm.beginImportSelection(); vm.importSelected("content://test/import.json"); runCurrent()
            vm.confirmImport(); runCurrent()
            assertTrue((vm.state.value as UserDataTransferState.Error).message.contains(expected))
        }
        fake.importResult = { NetworkResponse.Failure(NetworkFailure.Network()) }
        vm.beginImportSelection(); vm.importSelected("content://test/import.json"); runCurrent()
        vm.confirmImport(); runCurrent()
        assertTrue((vm.state.value as UserDataTransferState.Error).message.contains("conexión"))
    }

    @Test fun logoutThenDifferentUserCannotReceiveOldExport() = runTest {
        val scope = UserSessionViewModelScope()
        val fake = FakeTransfer()
        val pending = CompletableDeferred<NetworkResponse<String>>()
        fake.exportResult = { pending.await() }
        scope.activate("a")
        val a = ViewModelProvider(scope, UserDataTransferViewModelFactory(fake, FakeDocuments()))
            .get(UserDataTransferViewModel::class.java)
        a.export(); runCurrent()
        scope.clearSession()
        scope.activate("b")
        val b = ViewModelProvider(scope, UserDataTransferViewModelFactory(fake, FakeDocuments()))
            .get(UserDataTransferViewModel::class.java)
        pending.complete(NetworkResponse.Success("{\"schemaVersion\":1}", null))
        runCurrent()
        assertEquals(UserDataTransferState.Idle, b.state.value)
        b.importSelected(null)
        assertEquals(UserDataTransferState.Idle, b.state.value)
    }

    @Test fun logoutThenDifferentUserCannotReceiveOldImportSummary() = runTest {
        val scope = UserSessionViewModelScope()
        val fake = FakeTransfer()
        val pending = CompletableDeferred<NetworkResponse<UserDataImportSummary>>()
        fake.importResult = { pending.await() }
        scope.activate("a")
        val documents = FakeDocuments().apply { contents = "{\"schemaVersion\":1}" }
        val a = ViewModelProvider(scope, UserDataTransferViewModelFactory(fake, documents))
            .get(UserDataTransferViewModel::class.java)
        a.beginImportSelection(); a.importSelected("content://test/import.json"); runCurrent()
        var refreshed = 0
        a.confirmImport { refreshed++ }; runCurrent()
        scope.clearSession()
        scope.activate("b")
        val b = ViewModelProvider(scope, UserDataTransferViewModelFactory(fake, documents))
            .get(UserDataTransferViewModel::class.java)
        pending.complete(NetworkResponse.Success(UserDataImportSummary(1, 1, 1, 1, 1), null))
        runCurrent()
        assertEquals(UserDataTransferState.Idle, b.state.value)
        assertEquals(0, refreshed)
    }

    private class FakeTransfer : UserDataTransferRepository {
        var exportCalls = 0
        var importCalls = 0
        var importedJson: String? = null
        var exportResult: suspend () -> NetworkResponse<String> = {
            NetworkResponse.Success("{}", null)
        }
        override suspend fun export(): NetworkResponse<String> {
            exportCalls++
            return exportResult()
        }
        var importResult: suspend () -> NetworkResponse<UserDataImportSummary> = {
            NetworkResponse.Success(UserDataImportSummary(3, 2, 8, 1, 4), null)
        }
        override suspend fun import(json: String): NetworkResponse<UserDataImportSummary> {
            importCalls++
            importedJson = json
            return importResult()
        }
    }

    private class FakeDocuments : UserDataDocumentStore {
        var contents = "{}"
        var readError: Exception? = null
        var written: String? = null
        var writtenUri: String? = null
        override suspend fun read(documentUri: String): String {
            readError?.let { throw it }
            return contents
        }
        override suspend fun write(documentUri: String, json: String) {
            writtenUri = documentUri
            written = json
        }
    }
}
