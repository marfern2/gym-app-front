package com.mar.gym.feature.library.ui

import androidx.lifecycle.ViewModelProvider
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.library.data.LibraryRepository
import com.mar.gym.feature.library.data.LibraryResult
import com.mar.gym.feature.library.model.*
import com.mar.gym.feature.routines.data.RoutineRepository
import com.mar.gym.feature.routines.data.RoutineRepositoryResult
import com.mar.gym.feature.routines.model.*
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun loadsContentEmptyErrorAndFolderMutationsRefreshServerState() = runTest {
        val library = FakeLibraryRepository()
        val vm = LibraryViewModel(library, FakeRoutines())
        runCurrent()
        assertTrue(vm.library.value is LibraryUiState.Content)
        assertTrue((vm.library.value as LibraryUiState.Content).library.folders.isEmpty())

        vm.createFolder("Fuerza"); runCurrent()
        assertEquals("Fuerza", (vm.library.value as LibraryUiState.Content).library.folders.single().name)
        vm.renameFolder(FOLDER, "Casa"); runCurrent()
        assertEquals("Casa", library.data.folders.single().name)
        vm.reorderFolder(FOLDER, 1); runCurrent()
        assertEquals(1, library.folderPosition)

        vm.moveRoutine(ROUTINE, FOLDER); runCurrent()
        assertEquals(FOLDER, library.data.routines.single().folderId)
        vm.reorderRoutine(ROUTINE, 0); runCurrent()
        assertEquals(0, library.routinePosition)
        vm.moveRoutine(ROUTINE, null); runCurrent()
        assertNull(library.data.routines.single().folderId)

        vm.deleteFolder(FOLDER); runCurrent()
        assertTrue(library.data.folders.isEmpty())
        assertEquals(ROUTINE, library.data.routines.single().id)

        library.failure = NetworkFailure.Network()
        vm.refresh(); runCurrent()
        assertTrue(vm.library.value is LibraryUiState.Error)
    }

    @Test fun programEditorSupportsRepeatedDaysLabelsReorderingAndServerConflict() = runTest {
        val library = FakeLibraryRepository()
        val vm = LibraryViewModel(library, FakeRoutines())
        runCurrent()
        vm.newProgram()
        vm.name("Plan de fuerza")
        vm.addDay(ROUTINE)
        vm.addDay(ROUTINE)
        vm.label(1, "Pesado")
        assertEquals(listOf(ROUTINE, ROUTINE), (vm.program.value as ProgramUiState.Editor).days.map { it.routineId })
        vm.moveDay(1, -1)
        assertEquals("Pesado", (vm.program.value as ProgramUiState.Editor).days.first().label)
        vm.removeDay(1)
        vm.addDay(ROUTINE)
        vm.saveProgram(); runCurrent()
        assertEquals("Pesado", library.lastInput!!.days.first().label)
        assertEquals(2, (vm.program.value as ProgramUiState.Detail).program.days.size)
        assertEquals(0L, (vm.program.value as ProgramUiState.Detail).program.etag.version)

        vm.editProgram()
        vm.description("Tres semanas")
        vm.saveProgram(); runCurrent()
        assertEquals(0L, library.lastUpdateEtag!!.version)
        assertEquals(1L, (vm.program.value as ProgramUiState.Detail).program.etag.version)

        vm.editProgram()
        vm.name("Mi borrador")
        library.conflict = true
        vm.saveProgram(); runCurrent()
        assertEquals("Mi borrador", (vm.program.value as ProgramUiState.Editor).name)
        assertNotNull(vm.serverProgram.value)
        assertTrue(vm.message.value!!.contains("servidor"))
        vm.loadServerProgram()
        assertEquals("Plan de fuerza", (vm.program.value as ProgramUiState.Editor).name)
        assertNull(vm.serverProgram.value)

        vm.cancelEditor(); runCurrent()
        vm.deleteProgram(PROGRAM); runCurrent()
        assertTrue((vm.library.value as LibraryUiState.Content).library.programs.isEmpty())
        assertEquals(ROUTINE, (vm.library.value as LibraryUiState.Content).library.routines.single().id)
    }

    @Test fun duplicateStaysInFolderAndRoutineInProgramConflictDoesNotRemoveIt() = runTest {
        val library = FakeLibraryRepository()
        library.data = library.data.copy(folders = listOf(folder()), routines = listOf(routine(folderId = FOLDER)))
        val routines = FakeRoutines(library)
        val vm = LibraryViewModel(library, routines)
        runCurrent()
        vm.duplicateRoutine(ROUTINE); runCurrent()
        assertEquals(FOLDER, (vm.library.value as LibraryUiState.Content).library.routines.last().folderId)
        assertTrue((vm.library.value as LibraryUiState.Content).library.programs.isEmpty())

        routines.deleteConflict = true
        vm.deleteRoutine(ROUTINE); runCurrent()
        assertEquals("Esta rutina forma parte de uno o más programas.", vm.message.value)
        assertEquals(2, (vm.library.value as LibraryUiState.Content).library.routines.size)
    }

    @Test fun sessionScopeDropsLibraryStateAcrossLogout() = runTest {
        val library = FakeLibraryRepository()
        val scope = UserSessionViewModelScope()
        scope.activate("A")
        library.data = library.data.copy(folders = listOf(folder()))
        val a = ViewModelProvider(scope, LibraryViewModelFactory(library, FakeRoutines()))[LibraryViewModel::class.java]
        runCurrent()
        assertEquals(1, (a.library.value as LibraryUiState.Content).library.folders.size)
        scope.clearSession()
        library.data = library.data.copy(folders = emptyList())
        scope.activate("B")
        val b = ViewModelProvider(scope, LibraryViewModelFactory(library, FakeRoutines()))[LibraryViewModel::class.java]
        runCurrent()
        assertNotSame(a, b)
        assertTrue((b.library.value as LibraryUiState.Content).library.folders.isEmpty())
    }

    private class FakeLibraryRepository : LibraryRepository {
        var data = LibraryResponse(emptyList(), listOf(routine()), emptyList())
        var failure: NetworkFailure? = null
        var conflict = false
        var folderPosition = -1
        var routinePosition = -1
        var lastInput: ProgramInput? = null
        var lastUpdateEtag: RoutineEtag? = null
        var detail = program(version = 0)
        override suspend fun library(): LibraryResult<LibraryResponse> = failure?.let { LibraryResult.Failure(it) } ?: LibraryResult.Success(data)
        override suspend fun createFolder(name: String): LibraryResult<RoutineFolder> {
            val item = folder().copy(name = name)
            data = data.copy(folders = data.folders + item)
            return LibraryResult.Success(item)
        }
        override suspend fun renameFolder(id: String, name: String): LibraryResult<RoutineFolder> {
            val item = data.folders.single { it.id == id }.copy(name = name)
            data = data.copy(folders = listOf(item))
            return LibraryResult.Success(item)
        }
        override suspend fun reorderFolder(id: String, position: Int): LibraryResult<RoutineFolder> {
            folderPosition = position
            return LibraryResult.Success(data.folders.single())
        }
        override suspend fun deleteFolder(id: String): LibraryResult<Unit> {
            data = data.copy(folders = data.folders.filterNot { it.id == id },
                routines = data.routines.map { if (it.folderId == id) it.copy(folderId = null) else it })
            return LibraryResult.Success(Unit)
        }
        override suspend fun moveRoutine(routine: LibraryRoutine, folderId: String?): LibraryResult<LibraryRoutine> {
            val item = routine.copy(folderId = folderId, etag = RoutineEtag.fromVersion(routine.etag.version + 1)!!)
            data = data.copy(routines = data.routines.map { if (it.id == item.id) item else it })
            return LibraryResult.Success(item)
        }
        override suspend fun reorderRoutine(routine: LibraryRoutine, position: Int): LibraryResult<LibraryRoutine> {
            routinePosition = position
            return LibraryResult.Success(routine)
        }
        override suspend fun program(id: String) = LibraryResult.Success(detail)
        override suspend fun createProgram(input: ProgramInput): LibraryResult<ProgramDetail> {
            lastInput = input
            detail = program(0, input)
            data = data.copy(programs = listOf(summary(0)))
            return LibraryResult.Success(detail)
        }
        override suspend fun updateProgram(id: String, etag: RoutineEtag, input: ProgramInput): LibraryResult<ProgramDetail> {
            lastInput = input; lastUpdateEtag = etag
            if (conflict) return LibraryResult.Failure(NetworkFailure.HttpProblem(409, ProblemDetails(errorCode = "LIBRARY_VERSION_CONFLICT"), null))
            detail = program(etag.version.toInt() + 1, input)
            data = data.copy(programs = listOf(summary(detail.etag.version)))
            return LibraryResult.Success(detail)
        }
        override suspend fun deleteProgram(id: String, etag: RoutineEtag): LibraryResult<Unit> {
            data = data.copy(programs = emptyList())
            return LibraryResult.Success(Unit)
        }
    }

    private class FakeRoutines(private val library: FakeLibraryRepository? = null) : RoutineRepository {
        var deleteConflict = false
        override suspend fun list(archived: Boolean, query: String?, page: Int, size: Int, sort: RoutineSort): RoutineRepositoryResult<RoutinePage> = error("unused")
        override suspend fun detail(routineId: String): RoutineRepositoryResult<RoutineDocument> =
            RoutineRepositoryResult.Success(RoutineDocument(RoutineDetail(routineId, "A", null, false, 0, NOW, NOW, emptyList()), RoutineEtag.fromVersion(0)!!))
        override suspend fun create(draft: RoutineDraft): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun replace(draft: RoutineDraft, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun archive(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun restore(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<RoutineDocument> = error("unused")
        override suspend fun duplicate(routineId: String, etag: RoutineEtag, name: String?): RoutineRepositoryResult<RoutineDocument> {
            library?.let { it.data = it.data.copy(routines = it.data.routines + routine(id = DUPLICATE, folderId = it.data.routines.first().folderId)) }
            return detail(DUPLICATE)
        }
        override suspend fun delete(routineId: String, etag: RoutineEtag): RoutineRepositoryResult<Unit> =
            if (deleteConflict) RoutineRepositoryResult.Failure(NetworkFailure.HttpProblem(409,
                ProblemDetails(errorCode = "ROUTINE_IN_PROGRAM"), null)) else RoutineRepositoryResult.Success(Unit)
    }

    private companion object {
        const val FOLDER = "00000000-0000-0000-0000-000000000001"
        const val ROUTINE = "00000000-0000-0000-0000-000000000002"
        const val PROGRAM = "00000000-0000-0000-0000-000000000003"
        const val DUPLICATE = "00000000-0000-0000-0000-000000000004"
        val NOW: Instant = Instant.parse("2026-09-29T10:00:00Z")
        fun folder() = RoutineFolder(FOLDER, "Fuerza", 1, 1, NOW, NOW)
        fun routine(id: String = ROUTINE, folderId: String? = null) = LibraryRoutine(id, folderId, 0,
            "A", null, false, 1, RoutineEtag.fromVersion(0)!!, NOW)
        fun summary(version: Long) = ProgramSummary(PROGRAM, "Plan de fuerza", null, 2, NOW, version)
        fun program(version: Int, input: ProgramInput = ProgramInput("Plan de fuerza", null, emptyList())) = ProgramDetail(
            PROGRAM, input.name, input.description, RoutineEtag.fromVersion(version.toLong())!!, NOW, NOW,
            input.days.mapIndexed { i, day -> ProgramDay("00000000-0000-0000-0000-00000000001${i + 1}", i + 1,
                day.label, day.routineId, "A", false) })
    }
}
