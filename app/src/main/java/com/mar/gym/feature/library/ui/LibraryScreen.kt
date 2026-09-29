package com.mar.gym.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mar.gym.feature.library.model.*
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant

@Composable
fun LibraryRoute(viewModel: LibraryViewModel, onBack: () -> Unit,
                 onOpenRoutine: (String) -> Unit, onEditRoutine: (String) -> Unit,
                 onStartRoutine: (String) -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val program by viewModel.program.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val serverProgram by viewModel.serverProgram.collectAsStateWithLifecycle()
    BackHandler(enabled = program !is ProgramUiState.List) {
        if (program is ProgramUiState.Editor) viewModel.cancelEditor() else viewModel.closeProgram()
    }
    LibraryScreen(library, program, message, busy, serverProgram != null, viewModel::loadServerProgram,
        onBack, viewModel::refresh, viewModel::clearMessage,
        viewModel::createFolder, viewModel::renameFolder, viewModel::deleteFolder, viewModel::reorderFolder,
        viewModel::moveRoutine, viewModel::reorderRoutine, viewModel::duplicateRoutine, viewModel::deleteRoutine,
        onOpenRoutine, onEditRoutine, onStartRoutine, viewModel::openProgram, viewModel::newProgram,
        viewModel::editProgram, viewModel::closeProgram, viewModel::cancelEditor, viewModel::name,
        viewModel::description, viewModel::addDay, viewModel::removeDay, viewModel::moveDay,
        viewModel::routine, viewModel::label, viewModel::saveProgram, viewModel::deleteProgram)
}

@Composable
fun LibraryScreen(
    library: LibraryUiState, program: ProgramUiState, message: String?, busy: Boolean,
    serverVersionAvailable: Boolean, onLoadServerVersion: () -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onClearMessage: () -> Unit,
    onCreateFolder: (String) -> Unit, onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit, onReorderFolder: (String, Int) -> Unit,
    onMoveRoutine: (String, String?) -> Unit, onReorderRoutine: (String, Int) -> Unit,
    onDuplicateRoutine: (String) -> Unit, onDeleteRoutine: (String) -> Unit,
    onOpenRoutine: (String) -> Unit, onEditRoutine: (String) -> Unit, onStartRoutine: (String) -> Unit,
    onOpenProgram: (String) -> Unit, onCreateProgram: () -> Unit, onEditProgram: (String) -> Unit,
    onCloseProgram: () -> Unit, onCancelEditor: () -> Unit,
    onName: (String) -> Unit, onDescription: (String) -> Unit, onAddDay: (String) -> Unit,
    onRemoveDay: (Int) -> Unit, onMoveDay: (Int, Int) -> Unit,
    onChangeRoutine: (Int, String) -> Unit, onLabel: (Int, String) -> Unit,
    onSaveProgram: () -> Unit, onDeleteProgram: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var folderDialog by remember { mutableStateOf<RoutineFolder?>(null) }
    var createFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<RoutineFolder?>(null) }
    var moveRoutine by remember { mutableStateOf<LibraryRoutine?>(null) }
    var deleteRoutine by remember { mutableStateOf<LibraryRoutine?>(null) }
    var deleteProgram by remember { mutableStateOf<String?>(null) }
    var selectDay by remember { mutableStateOf<Int?>(null) }
    val content = (library as? LibraryUiState.Content)?.library
    Scaffold(topBar = {
        AppTopBar(title = when (program) {
            is ProgramUiState.Detail -> "Programa"
            is ProgramUiState.Editor -> if (program.id == null) "Crear programa" else "Editar programa"
            else -> "Biblioteca"
        }, onBack = if (program is ProgramUiState.List) onBack else {
            if (program is ProgramUiState.Editor) onCancelEditor else onCloseProgram
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (message != null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClearMessage) { Text("Cerrar") }
                }
            }
            if (serverVersionAvailable) TextButton(onClick = onLoadServerVersion) { Text("Cargar versión del servidor") }
            if (library is LibraryUiState.Error && program !is ProgramUiState.List) {
                Text(library.message, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh) { Text("Reintentar biblioteca") }
            }
            when (program) {
                is ProgramUiState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                is ProgramUiState.Detail -> ProgramDetailScreen(program.program, busy,
                    onStartRoutine, { onEditProgram(program.program.id) }, { deleteProgram = program.program.id })
                is ProgramUiState.Editor -> ProgramEditorScreen(program, content, busy, onName, onDescription,
                    onRemoveDay, onMoveDay, onLabel,
                    { selectDay = it }, onSaveProgram)
                is ProgramUiState.List -> {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Programas") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Mis rutinas") })
                    }
                    when (library) {
                        is LibraryUiState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                        is LibraryUiState.Error -> Column(Modifier.padding(16.dp)) {
                            Text(library.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Reintentar") }
                        }
                        is LibraryUiState.Content -> if (tab == 0) ProgramsListScreen(content!!,
                            onOpenProgram, onEditProgram, { deleteProgram = it }, onCreateProgram)
                        else RoutinesLibraryScreen(content!!, busy, onCreate = { createFolder = true },
                            onRename = { folderDialog = it }, onDeleteFolder = { deleteFolder = it },
                            onMoveFolder = onReorderFolder, onMoveRoutine = { moveRoutine = it },
                            onReorderRoutine = onReorderRoutine, onOpenRoutine = onOpenRoutine,
                            onEditRoutine = onEditRoutine, onStartRoutine = onStartRoutine,
                            onDuplicateRoutine = onDuplicateRoutine, onDeleteRoutine = { deleteRoutine = it })
                    }
                }
            }
        }
    }
    if (createFolder || folderDialog != null) {
        val folder = folderDialog
        NameDialog(title = if (folder == null) "Crear carpeta" else "Renombrar carpeta", initial = folder?.name.orEmpty(),
            onDismiss = { createFolder = false; folderDialog = null }, onSave = { name ->
                if (folder == null) onCreateFolder(name) else onRenameFolder(folder.id, name)
                createFolder = false; folderDialog = null
            })
    }
    deleteFolder?.let { folder -> ConfirmDialog("Borrar carpeta", "Las rutinas se moverán a la biblioteca principal.",
        onDismiss = { deleteFolder = null }, onConfirm = { onDeleteFolder(folder.id); deleteFolder = null }) }
    deleteRoutine?.let { routine -> ConfirmDialog("Borrar rutina", "¿Quieres borrar ${routine.name}?",
        onDismiss = { deleteRoutine = null }, onConfirm = { onDeleteRoutine(routine.id); deleteRoutine = null }) }
    deleteProgram?.let { id -> ConfirmDialog("Eliminar programa", "El programa se eliminará. Sus rutinas seguirán en la biblioteca.",
        onDismiss = { deleteProgram = null }, onConfirm = { onDeleteProgram(id); deleteProgram = null }) }
    moveRoutine?.let { routine -> if (content != null) RoutinePickerDialog(
        title = "Mover a carpeta", folders = content.folders, routines = null,
        onDismiss = { moveRoutine = null }, onSelectFolder = { id -> onMoveRoutine(routine.id, id); moveRoutine = null },
        onSelectRoutine = {}) }
    selectDay?.let { index -> if (content != null) RoutinePickerDialog(
        title = "Seleccionar rutina", folders = content.folders, routines = content.routines.filterNot { it.archived },
        onDismiss = { selectDay = null }, onSelectFolder = {},
        onSelectRoutine = { id ->
            if (index == -1) onAddDay(id) else onChangeRoutine(index, id)
            selectDay = null
        }) }
}

@Composable private fun ProgramsListScreen(data: LibraryResponse, onOpen: (String) -> Unit,
    onEdit: (String) -> Unit, onDelete: (String) -> Unit, onCreate: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Button(onClick = onCreate) { Text("Crear programa") } }
        if (data.programs.isEmpty()) item { Text("Todavía no tienes programas") }
        items(data.programs, key = { it.id }) { program ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(program.id) }.testTag("program-${program.id}")) {
                Column(Modifier.padding(12.dp)) {
                    Text(program.name, style = MaterialTheme.typography.titleMedium)
                    program.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text("${program.daysCount} días", style = MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick = { onOpen(program.id) }) { Text("Abrir") }
                        TextButton(onClick = { onEdit(program.id) }) { Text("Editar") }
                        TextButton(onClick = { onDelete(program.id) }) { Text("Eliminar") } }
                }
            }
        }
    }
}

@Composable private fun RoutinesLibraryScreen(data: LibraryResponse, busy: Boolean, onCreate: () -> Unit,
    onRename: (RoutineFolder) -> Unit, onDeleteFolder: (RoutineFolder) -> Unit,
    onMoveFolder: (String, Int) -> Unit, onMoveRoutine: (LibraryRoutine) -> Unit,
    onReorderRoutine: (String, Int) -> Unit, onOpenRoutine: (String) -> Unit,
    onEditRoutine: (String) -> Unit, onStartRoutine: (String) -> Unit,
    onDuplicateRoutine: (String) -> Unit, onDeleteRoutine: (LibraryRoutine) -> Unit) {
    val folders = data.folders.sortedBy { it.position }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = onCreate, enabled = !busy) { Text("Crear carpeta") } }
        item { Text("Biblioteca principal", style = MaterialTheme.typography.titleMedium) }
        val root = data.routines.filter { it.folderId == null }.sortedBy { it.position }
        items(root, key = { it.id }) { routine -> RoutineLibraryRow(routine, root.size, busy, onMoveRoutine,
            onReorderRoutine, onOpenRoutine, onEditRoutine, onStartRoutine, onDuplicateRoutine, onDeleteRoutine) }
        folders.forEachIndexed { index, folder ->
            val expanded = expandedFolders[folder.id] ?: true
            item(key = "folder-${folder.id}") {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
                    TextButton(onClick = { expandedFolders[folder.id] = !expanded }) { Text("${if (expanded) "▾" else "▸"} ${folder.name} (${folder.routineCount})") }
                    Row {
                        TextButton(onClick = { onRename(folder) }, enabled = !busy) { Text("Renombrar") }
                        TextButton(onClick = { onDeleteFolder(folder) }, enabled = !busy) { Text("Borrar") }
                        TextButton(onClick = { onMoveFolder(folder.id, index) }, enabled = !busy && index > 0,
                            modifier = Modifier.semantics { contentDescription = "Subir carpeta ${folder.name}" }) { Text("↑") }
                        TextButton(onClick = { onMoveFolder(folder.id, index + 2) }, enabled = !busy && index < folders.lastIndex,
                            modifier = Modifier.semantics { contentDescription = "Bajar carpeta ${folder.name}" }) { Text("↓") }
                    }
                } }
            }
            if (expanded) {
                val children = data.routines.filter { it.folderId == folder.id }.sortedBy { it.position }
                items(children, key = { it.id }) { routine -> RoutineLibraryRow(routine, children.size, busy, onMoveRoutine,
                    onReorderRoutine, onOpenRoutine, onEditRoutine, onStartRoutine, onDuplicateRoutine, onDeleteRoutine) }
            }
        }
    }
}

@Composable private fun RoutineLibraryRow(routine: LibraryRoutine, count: Int, busy: Boolean,
    onMove: (LibraryRoutine) -> Unit, onReorder: (String, Int) -> Unit, onOpen: (String) -> Unit,
    onEdit: (String) -> Unit, onStart: (String) -> Unit, onDuplicate: (String) -> Unit,
    onDelete: (LibraryRoutine) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("library-routine-${routine.id}")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onOpen(routine.id) }) {
                Text(routine.name, style = MaterialTheme.typography.bodyLarge)
                Text("${routine.exerciseCount} ejercicios${if (routine.archived) " · Archivada" else ""}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onStart(routine.id) }, enabled = !routine.archived) { Text("Iniciar") }
            Box { TextButton(onClick = { menu = true }, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Opciones de ${routine.name}" }) { Text("⋮") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; onOpen(routine.id) })
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit(routine.id) })
                    DropdownMenuItem(text = { Text("Mover a carpeta") }, onClick = { menu = false; onMove(routine) })
                    DropdownMenuItem(text = { Text("Subir") }, enabled = routine.position > 0,
                        onClick = { menu = false; onReorder(routine.id, routine.position - 1) })
                    DropdownMenuItem(text = { Text("Bajar") }, enabled = routine.position < count - 1,
                        onClick = { menu = false; onReorder(routine.id, routine.position + 1) })
                    DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; onDuplicate(routine.id) })
                    DropdownMenuItem(text = { Text("Borrar") }, onClick = { menu = false; onDelete(routine) })
                }
            }
        }
    }
}

@Composable internal fun ProgramDetailScreen(detail: ProgramDetail, busy: Boolean,
    onStart: (String) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(detail.name, style = MaterialTheme.typography.headlineSmall) }
        detail.description?.let { item { Text(it) } }
        item { Row { TextButton(onClick = onEdit, enabled = !busy) { Text("Editar") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("Eliminar") } } }
        items(detail.days, key = { it.id }) { day -> Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.label?.takeIf { it.isNotBlank() } ?: "Día ${day.position}", style = MaterialTheme.typography.titleMedium)
                    Text(day.routineName)
                }
                TextButton(onClick = { onStart(day.routineId) }, enabled = !day.routineArchived) { Text("Iniciar") }
            }
        } }
    }
}

@Composable private fun ProgramEditorScreen(editor: ProgramUiState.Editor, library: LibraryResponse?, busy: Boolean,
    onName: (String) -> Unit, onDescription: (String) -> Unit,
    onRemoveDay: (Int) -> Unit, onMoveDay: (Int, Int) -> Unit,
    onLabel: (Int, String) -> Unit, onPick: (Int) -> Unit, onSave: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(editor.name, onName, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(editor.description, onDescription, label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth()) }
        item { Text("Días", style = MaterialTheme.typography.titleMedium) }
        items(editor.days.size) { index ->
            val day = editor.days[index]
            val routineName = library?.routines?.find { it.id == day.routineId }?.name ?: "Rutina"
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("Día ${index + 1}")
                OutlinedTextField(day.label.orEmpty(), { onLabel(index, it) }, label = { Text("Etiqueta (opcional)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onPick(index) }) { Text(routineName) }
                Row {
                    TextButton(onClick = { onMoveDay(index, -1) }, enabled = index > 0,
                        modifier = Modifier.semantics { contentDescription = "Subir día ${index + 1}" }) { Text("↑") }
                    TextButton(onClick = { onMoveDay(index, 1) }, enabled = index < editor.days.lastIndex,
                        modifier = Modifier.semantics { contentDescription = "Bajar día ${index + 1}" }) { Text("↓") }
                    TextButton(onClick = { onRemoveDay(index) }) { Text("Quitar") }
                }
            } }
        }
        item { Button(onClick = { onPick(-1) }, enabled = editor.days.size < 100 && library?.routines?.any { !it.archived } == true) { Text("Añadir día") } }
        item { Button(onClick = onSave, enabled = !busy && editor.name.trim().length in 2..100) { Text("Guardar programa") } }
    }
}

@Composable private fun RoutinePickerDialog(title: String, folders: List<RoutineFolder>, routines: List<LibraryRoutine>?,
    onDismiss: () -> Unit, onSelectFolder: (String?) -> Unit, onSelectRoutine: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            if (routines == null) {
                item { TextButton(onClick = { onSelectFolder(null) }) { Text("Biblioteca principal") } }
                items(folders.sortedBy { it.position }) { folder ->
                    TextButton(onClick = { onSelectFolder(folder.id) }) { Text(folder.name) }
                }
            } else {
                if (routines.isEmpty()) item { Text("No hay rutinas disponibles") }
                item { Text("Biblioteca principal", style = MaterialTheme.typography.titleSmall) }
                items(routines.filter { it.folderId == null }.sortedBy { it.position }) { item ->
                    RoutinePickerRow(item, onSelectRoutine)
                }
                folders.sortedBy { it.position }.forEach { folder ->
                    item { Text(folder.name, style = MaterialTheme.typography.titleSmall) }
                    items(routines.filter { it.folderId == folder.id }.sortedBy { it.position }) { item ->
                        RoutinePickerRow(item, onSelectRoutine)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable private fun RoutinePickerRow(item: LibraryRoutine, onSelect: (String) -> Unit) {
    TextButton(onClick = { onSelect(item.id) }) { Text("${item.name} · ${item.exerciseCount} ejercicios") }
}

@Composable private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.trim().length in 2..100) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Preview(showBackground = true, name = "Programas vacíos")
@Composable private fun ProgramsEmptyPreview() { GYmAppTheme {
    ProgramsListScreen(LibraryResponse(emptyList(), emptyList(), emptyList()), {}, {}, {}, {})
} }

@Preview(showBackground = true, name = "Detalle de programa")
@Composable private fun ProgramDetailPreview() { GYmAppTheme {
    ProgramDetailScreen(ProgramDetail("p", "Plan de fuerza", "Tres días por semana",
        com.mar.gym.feature.routines.model.RoutineEtag.fromVersion(0)!!, Instant.EPOCH, Instant.EPOCH,
        listOf(ProgramDay("d", 1, "Día A", "r", "Fuerza superior", false))), false, {}, {}, {})
} }

@Preview(showBackground = true, name = "Editor de programa")
@Composable private fun ProgramEditorPreview() { GYmAppTheme {
    ProgramEditorScreen(ProgramUiState.Editor(null, null, "Plan de fuerza", "", listOf(ProgramDayInput("r", "Día A"))),
        null, false, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
} }
