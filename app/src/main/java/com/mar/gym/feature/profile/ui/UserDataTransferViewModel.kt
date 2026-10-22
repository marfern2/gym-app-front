package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.feature.profile.data.UserDataDocumentStore
import com.mar.gym.feature.profile.data.UserDataFileTooLarge
import com.mar.gym.feature.profile.data.UserDataImportSummary
import com.mar.gym.feature.profile.data.UserDataTransferRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface UserDataTransferState {
    data object Idle : UserDataTransferState
    data object LoadingExport : UserDataTransferState
    data object AwaitingDestination : UserDataTransferState
    data object SavingExport : UserDataTransferState
    data object ExportSaved : UserDataTransferState
    data object AwaitingSource : UserDataTransferState
    data object ReadingImport : UserDataTransferState
    data object ConfirmImport : UserDataTransferState
    data object Importing : UserDataTransferState
    data class ImportCompleted(val summary: UserDataImportSummary) : UserDataTransferState
    data class Error(val message: String) : UserDataTransferState
}

class UserDataTransferViewModel(
    private val repository: UserDataTransferRepository,
    private val documents: UserDataDocumentStore,
) : ViewModel() {
    private val _state = MutableStateFlow<UserDataTransferState>(UserDataTransferState.Idle)
    val state = _state.asStateFlow()
    private var exportJson: String? = null
    private var importJson: String? = null

    fun export() {
        if (busy()) return
        exportJson = null
        _state.value = UserDataTransferState.LoadingExport
        viewModelScope.launch {
            when (val response = repository.export()) {
                is NetworkResponse.Success -> {
                    exportJson = response.value
                    _state.value = UserDataTransferState.AwaitingDestination
                }
                is NetworkResponse.Failure -> _state.value = UserDataTransferState.Error(
                    response.error.safeTransferError(false))
            }
        }
    }

    fun destinationSelected(documentUri: String?) {
        if (_state.value != UserDataTransferState.AwaitingDestination) return
        val json = exportJson ?: return
        if (documentUri == null) {
            exportJson = null
            _state.value = UserDataTransferState.Idle
            return
        }
        _state.value = UserDataTransferState.SavingExport
        viewModelScope.launch {
            try {
                documents.write(documentUri, json)
                _state.value = UserDataTransferState.ExportSaved
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = UserDataTransferState.Error("No se pudo guardar la exportación.")
            } finally {
                exportJson = null
            }
        }
    }

    fun beginImportSelection() {
        if (busy()) return
        _state.value = UserDataTransferState.AwaitingSource
    }

    fun importSelected(documentUri: String?) {
        if (_state.value != UserDataTransferState.AwaitingSource) return
        if (documentUri == null) {
            _state.value = UserDataTransferState.Idle
            return
        }
        importJson = null
        _state.value = UserDataTransferState.ReadingImport
        viewModelScope.launch {
            try {
                val json = documents.read(documentUri)
                Json.parseToJsonElement(json)
                importJson = json
                _state.value = UserDataTransferState.ConfirmImport
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: UserDataFileTooLarge) {
                _state.value = UserDataTransferState.Error("El archivo es demasiado grande.")
            } catch (_: Exception) {
                _state.value = UserDataTransferState.Error("El archivo no es una exportación válida.")
            }
        }
    }

    fun confirmImport(onImported: () -> Unit = {}) {
        if (_state.value != UserDataTransferState.ConfirmImport) return
        val json = importJson ?: return
        _state.value = UserDataTransferState.Importing
        viewModelScope.launch {
            try {
                when (val response = repository.import(json)) {
                    is NetworkResponse.Success -> {
                        _state.value = UserDataTransferState.ImportCompleted(response.value)
                        onImported()
                    }
                    is NetworkResponse.Failure -> _state.value = UserDataTransferState.Error(
                        response.error.safeTransferError(true))
                }
            } finally {
                importJson = null
            }
        }
    }

    fun dismiss() {
        if (_state.value == UserDataTransferState.ConfirmImport ||
            _state.value is UserDataTransferState.ImportCompleted ||
            _state.value is UserDataTransferState.Error ||
            _state.value == UserDataTransferState.ExportSaved) {
            importJson = null
            _state.value = UserDataTransferState.Idle
        }
    }

    private fun busy() = _state.value in listOf(
        UserDataTransferState.LoadingExport, UserDataTransferState.AwaitingDestination,
        UserDataTransferState.AwaitingSource,
        UserDataTransferState.SavingExport, UserDataTransferState.ReadingImport,
        UserDataTransferState.ConfirmImport, UserDataTransferState.Importing,
    )

    override fun onCleared() {
        exportJson = null
        importJson = null
        super.onCleared()
    }
}

internal fun NetworkFailure.safeTransferError(importing: Boolean): String {
    if (importing && this is NetworkFailure.HttpProblem) return when (problem.errorCode) {
        "UNSUPPORTED_SCHEMA_VERSION" -> "Esta exportación usa una versión no compatible."
        "INVALID_REFERENCE", "INVALID_EXERCISE", "CUSTOM_EXERCISE_NAME_CONFLICT" ->
            "El archivo contiene datos incompatibles o incompletos."
        "MALFORMED_IMPORT" -> "El archivo no es una exportación válida."
        "PAYLOAD_TOO_LARGE" -> "El archivo es demasiado grande."
        else -> "No se pudo importar el archivo. Inténtalo de nuevo."
    }
    if (importing && this is NetworkFailure.HttpUnknown && statusCode == 413) {
        return "El archivo es demasiado grande."
    }
    return when (this) {
        is NetworkFailure.Network, is NetworkFailure.Timeout -> "No hay conexión. Inténtalo de nuevo."
        else -> if (importing) "No se pudo importar el archivo. Inténtalo de nuevo."
                else "No se pudo exportar tus datos. Inténtalo de nuevo."
    }
}

class UserDataTransferViewModelFactory(
    private val repository: UserDataTransferRepository,
    private val documents: UserDataDocumentStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(UserDataTransferViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return UserDataTransferViewModel(repository, documents) as T
    }
}
