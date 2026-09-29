package com.mar.gym.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.library.data.LibraryRepository
import com.mar.gym.feature.library.data.LibraryResult
import com.mar.gym.feature.library.model.*
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Content(val library: LibraryResponse) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface ProgramUiState {
    data object List : ProgramUiState
    data object Loading : ProgramUiState
    data class Detail(val program: ProgramDetail) : ProgramUiState
    data class Editor(val id: String?, val etag: com.mar.gym.feature.routines.model.RoutineEtag?,
                      val name: String, val description: String, val days: kotlin.collections.List<ProgramDayInput>) : ProgramUiState
}

class LibraryViewModel(private val repository: LibraryRepository, private val routines: RoutineRepository) : ViewModel() {
    private val _library = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()
    private val _program = MutableStateFlow<ProgramUiState>(ProgramUiState.List)
    val program: StateFlow<ProgramUiState> = _program.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _serverProgram = MutableStateFlow<ProgramDetail?>(null)
    val serverProgram: StateFlow<ProgramDetail?> = _serverProgram.asStateFlow()

    init { refresh() }

    fun clearMessage() { _message.value = null }
    fun refresh() { viewModelScope.launch { loadLibrary() } }
    private suspend fun loadLibrary() {
        _library.value = LibraryUiState.Loading
        _library.value = when (val result = repository.library()) {
            is LibraryResult.Success -> LibraryUiState.Content(result.value)
            is LibraryResult.Failure -> LibraryUiState.Error(result.error.safeMessage())
        }
    }

    private fun mutate(block: suspend () -> LibraryResult<*>) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val result = block()
            if (result is LibraryResult.Failure) _message.value = result.error.safeMessage()
            loadLibrary()
            _busy.value = false
        }
    }

    fun createFolder(name: String) = mutate { repository.createFolder(name) }
    fun renameFolder(id: String, name: String) = mutate { repository.renameFolder(id, name) }
    fun deleteFolder(id: String) = mutate { repository.deleteFolder(id) }
    fun reorderFolder(id: String, position: Int) = mutate { repository.reorderFolder(id, position) }

    fun moveRoutine(routineId: String, folderId: String?) {
        val item = (_library.value as? LibraryUiState.Content)?.library?.routines?.find { it.id == routineId } ?: return
        mutate { repository.moveRoutine(item, folderId) }
    }

    fun reorderRoutine(routineId: String, position: Int) {
        val item = (_library.value as? LibraryUiState.Content)?.library?.routines?.find { it.id == routineId } ?: return
        mutate { repository.reorderRoutine(item, position) }
    }

    fun duplicateRoutine(id: String) {
        val item = (_library.value as? LibraryUiState.Content)?.library?.routines?.find { it.id == id } ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = routines.duplicate(id, item.etag)) {
                is RoutineRepositoryResult.Failure -> _message.value = result.error.safeMessage()
                is RoutineRepositoryResult.Success -> Unit
            }
            loadLibrary()
            _busy.value = false
        }
    }

    fun deleteRoutine(id: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val detail = routines.detail(id)
            val result = when (detail) {
                is RoutineRepositoryResult.Failure -> RoutineRepositoryResult.Failure(detail.error)
                is RoutineRepositoryResult.Success -> routines.delete(id, detail.value.etag)
            }
            when (result) {
                is RoutineRepositoryResult.Success -> loadLibrary()
                is RoutineRepositoryResult.Failure -> {
                    _message.value = if (result.error is NetworkFailure.HttpProblem &&
                        result.error.problem.errorCode == "ROUTINE_IN_PROGRAM")
                        "Esta rutina forma parte de uno o más programas." else result.error.safeMessage()
                    if (result.error.isConflict()) loadLibrary()
                }
            }
            _busy.value = false
        }
    }

    fun closeProgram() { _program.value = ProgramUiState.List; _message.value = null; _serverProgram.value = null; refresh() }
    fun openProgram(id: String) {
        _program.value = ProgramUiState.Loading
        viewModelScope.launch {
            when (val result = repository.program(id)) {
                is LibraryResult.Success -> _program.value = ProgramUiState.Detail(result.value)
                is LibraryResult.Failure -> {
                    _program.value = ProgramUiState.List
                    _message.value = result.error.safeMessage()
                    refresh()
                }
            }
        }
    }

    fun newProgram() { _message.value = null; _serverProgram.value = null; _program.value = ProgramUiState.Editor(null, null, "", "", emptyList()) }
    fun editProgram() {
        val detail = (_program.value as? ProgramUiState.Detail)?.program ?: return
        _program.value = ProgramUiState.Editor(detail.id, detail.etag, detail.name,
            detail.description.orEmpty(), detail.days.map { ProgramDayInput(it.routineId, it.label) })
    }
    fun editProgram(id: String) {
        _program.value = ProgramUiState.Loading
        viewModelScope.launch {
            when (val result = repository.program(id)) {
                is LibraryResult.Success -> {
                    val detail = result.value
                    _program.value = ProgramUiState.Editor(detail.id, detail.etag, detail.name,
                        detail.description.orEmpty(), detail.days.map { ProgramDayInput(it.routineId, it.label) })
                }
                is LibraryResult.Failure -> { _program.value = ProgramUiState.List; _message.value = result.error.safeMessage() }
            }
        }
    }
    fun cancelEditor() {
        val id = (_program.value as? ProgramUiState.Editor)?.id
        if (id == null) closeProgram() else openProgram(id)
    }
    fun loadServerProgram() {
        val detail = _serverProgram.value ?: return
        _serverProgram.value = null
        _message.value = null
        _program.value = ProgramUiState.Editor(detail.id, detail.etag, detail.name,
            detail.description.orEmpty(), detail.days.map { ProgramDayInput(it.routineId, it.label) })
    }
    private fun edit(transform: ProgramUiState.Editor.() -> ProgramUiState.Editor) {
        val current = _program.value as? ProgramUiState.Editor ?: return
        _program.value = current.transform()
    }
    fun name(value: String) = edit { copy(name = value) }
    fun description(value: String) = edit { copy(description = value) }
    fun addDay(routineId: String) = edit { copy(days = days + ProgramDayInput(routineId)) }
    fun removeDay(index: Int) = edit { copy(days = days.filterIndexed { i, _ -> i != index }) }
    fun label(index: Int, value: String) = edit { copy(days = days.mapIndexed { i, day -> if (i == index) day.copy(label = value) else day }) }
    fun routine(index: Int, routineId: String) = edit { copy(days = days.mapIndexed { i, day -> if (i == index) day.copy(routineId = routineId) else day }) }
    fun moveDay(index: Int, offset: Int) = edit {
        val target = index + offset
        if (index !in days.indices || target !in days.indices) this else copy(days = days.toMutableList().apply {
            add(target, removeAt(index))
        })
    }

    fun saveProgram() {
        val editor = _program.value as? ProgramUiState.Editor ?: return
        if (editor.name.trim().length !in 2..100) { _message.value = "El nombre debe tener entre 2 y 100 caracteres."; return }
        if (editor.description.trim().length > 2000 || editor.days.size > 100 ||
            editor.days.any { (it.label?.trim()?.length ?: 0) > 100 }) {
            _message.value = "La descripción, los días o una etiqueta superan el límite permitido."
            return
        }
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            val input = ProgramInput(editor.name, editor.description, editor.days)
            val result = if (editor.id == null) repository.createProgram(input)
                else repository.updateProgram(editor.id, editor.etag ?: return@launch, input)
            when (result) {
                is LibraryResult.Success -> { _serverProgram.value = null; _program.value = ProgramUiState.Detail(result.value); loadLibrary() }
                is LibraryResult.Failure -> {
                    _message.value = result.error.safeMessage()
                    if (result.error.isConflict() && editor.id != null) {
                        val latest = repository.program(editor.id)
                        if (latest is LibraryResult.Success) _serverProgram.value = latest.value
                        loadLibrary()
                    }
                }
            }
            _busy.value = false
        }
    }

    fun deleteProgram(id: String) {
        val summary = (_library.value as? LibraryUiState.Content)?.library?.programs?.find { it.id == id }
        val detail = (_program.value as? ProgramUiState.Detail)?.program?.takeIf { it.id == id }
        val etag = detail?.etag ?: summary?.version?.let(com.mar.gym.feature.routines.model.RoutineEtag::fromVersion) ?: return
        mutate { repository.deleteProgram(id, etag) }
        _program.value = ProgramUiState.List
    }
}

internal fun NetworkFailure.isConflict() = when (this) {
    is NetworkFailure.HttpProblem -> statusCode == 409 || statusCode == 412
    is NetworkFailure.HttpUnknown -> statusCode == 409 || statusCode == 412
    else -> false
}

internal fun NetworkFailure.safeMessage(): String = when {
    isConflict() -> "Hay cambios en el servidor. Se ha recargado la biblioteca; revisa antes de intentarlo de nuevo."
    this is NetworkFailure.Network || this is NetworkFailure.Timeout -> "No se pudo conectar. Inténtalo de nuevo."
    this is NetworkFailure.HttpProblem && statusCode == 401 -> "La sesión ha caducado."
    this is NetworkFailure.HttpProblem && statusCode == 404 -> "El elemento ya no existe."
    else -> "No se pudo completar la operación. Inténtalo de nuevo."
}

class LibraryViewModelFactory(private val repository: LibraryRepository, private val routines: RoutineRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        LibraryViewModel(repository, routines) as T
}
