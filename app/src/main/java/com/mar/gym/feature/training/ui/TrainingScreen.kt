package com.mar.gym.feature.training.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.mar.gym.feature.library.model.LibraryResponse
import com.mar.gym.feature.library.model.LibraryRoutine
import com.mar.gym.feature.library.model.RoutineFolder
import com.mar.gym.feature.library.ui.LibraryUiState
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.workouts.ui.ActiveWorkoutCard
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Clock
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class DragItem(
    val id: String,
    val folder: Boolean,
    val containerId: String?,
    val originalIndex: Int,
    val touchOffsetY: Float,
    val initialCentres: List<Float>,
    val scrollIndex: Int,
    val scrollOffset: Int,
)
private val TrainingText = Color(0xFFF4F1F7)
private val TrainingMuted = Color(0xFFB8B2C0)
private val RoutineSurface = Color(0xFF1C1B21)
private val RoutinePurple = Color(0xFF492473)

@Composable
fun TrainingScreen(
    activeWorkout: ActiveWorkoutUiState,
    library: LibraryUiState,
    message: String?,
    busy: Boolean,
    clock: Clock,
    onContinueWorkout: () -> Unit,
    onStartEmpty: () -> Unit,
    onRetryWorkout: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit,
    onEditRoutine: (String) -> Unit,
    onDuplicateRoutine: (String) -> Unit,
    onShareRoutine: (String) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onCreateRoutine: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveRoutine: (String, String?) -> Unit,
    onPlaceRoutine: (String, String?, Int?) -> Unit,
    onReorderFolder: (String, Int) -> Unit,
    onRetryLibrary: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = (library as? LibraryUiState.Content)?.library
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<DragItem?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var overlayTopY by remember { mutableStateOf(0f) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var released by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var hover by remember { mutableStateOf<String?>(null) }
    var createFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderSaving by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<RoutineFolder?>(null) }
    var deleteFolder by remember { mutableStateOf<RoutineFolder?>(null) }
    var deleteRoutine by remember { mutableStateOf<LibraryRoutine?>(null) }
    var moveRoutine by remember { mutableStateOf<LibraryRoutine?>(null) }
    LaunchedEffect(busy, message, folderSaving) {
        if (folderSaving && !busy) {
            folderSaving = false
            if (message == null) { createFolder = false; folderName = "" }
        }
    }

    LaunchedEffect(released, settled, busy) {
        if (released && settled && !busy) {
            dragging = null
            previewIndex = null
            hover = null
            released = false
            settled = false
        }
    }

    fun finishDrag() {
        val source = dragging
        val destination = hover
        var landingTop: Float? = null
        if (source != null && data != null && !busy) {
            val action = if (source.folder) {
                val folders = data.folders.sortedBy { it.position }
                previewIndex?.takeIf { it != source.originalIndex }?.let { index ->
                    TrainingDropAction.ReorderFolder(source.id, folders[index].position)
                }
            } else {
                val targetAction = trainingDropAction(source.id, false, destination, data)
                val crossContainer = (targetAction as? TrainingDropAction.PlaceRoutine)
                    ?.takeIf { it.folderId != source.containerId }
                crossContainer ?: previewIndex?.takeIf { it != source.originalIndex }?.let { index ->
                    TrainingDropAction.PlaceRoutine(source.id, source.containerId, index)
                }
            }
            when (action) {
                is TrainingDropAction.PlaceRoutine -> onPlaceRoutine(action.id, action.folderId, action.position)
                is TrainingDropAction.ReorderFolder -> onReorderFolder(action.id, action.position)
                null -> Unit
            }
            if (action is TrainingDropAction.PlaceRoutine && action.folderId != source.containerId) {
                val destinationBounds = destination?.let(targets::get)
                landingTop = when {
                    destinationBounds == null -> null
                    destination == "root" -> destinationBounds.bottom + with(density) { 8.dp.toPx() }
                    destination?.startsWith("folder:") == true -> {
                        val folderId = destination.removePrefix("folder:")
                        if (expanded[folderId] ?: true) destinationBounds.bottom + with(density) { 8.dp.toPx() }
                        else destinationBounds.top
                    }
                    else -> destinationBounds.top
                }
            }
            if (action != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (source != null) {
            released = true
            settled = false
            val targetTop = landingTop ?: targets[if (source.folder) "folder:${source.id}" else "routine:${source.id}"]?.top
                ?: overlayTopY
            scope.launch {
                Animatable(overlayTopY).animateTo(targetTop, animationSpec = tween(180)) {
                    overlayTopY = value
                }
                settled = true
            }
        }
    }

    fun dragModifier(id: String, isFolder: Boolean, enabled: Boolean): Modifier = Modifier
        .graphicsLayer {
            alpha = if (dragging?.id == id) 0f else 1f
        }
        .pointerInput(id, busy, enabled, released) {
            if (enabled && !busy && !released) detectDragGesturesAfterLongPress(
                onDragStart = { start ->
                    val currentData = data ?: return@detectDragGesturesAfterLongPress
                    val bounds = targets[if (isFolder) "folder:$id" else "routine:$id"]
                        ?: return@detectDragGesturesAfterLongPress
                    val routine = currentData.routines.find { it.id == id }
                    val siblings = if (isFolder) currentData.folders.sortedBy { it.position }.map { it.id }
                        else currentData.routines.filter { !it.archived && it.folderId == routine?.folderId }
                            .sortedBy { it.position }.map { it.id }
                    val prefix = if (isFolder) "folder:" else "routine:"
                    val initialCentres = siblings.filterNot { it == id }
                        .mapNotNull { targets["$prefix$it"]?.center?.y }
                    dragging = DragItem(id, isFolder, routine?.folderId,
                        siblings.indexOf(id), start.y, initialCentres,
                        listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    previewIndex = siblings.indexOf(id)
                    released = false
                    settled = false
                    overlayTopY = bounds.top
                    dragPoint = Offset(bounds.center.x, bounds.top + start.y)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { change, delta ->
                    change.consume()
                    if (dragging?.id != id) return@detectDragGesturesAfterLongPress
                    val verticalDelta = trainingVerticalDragOffset(Offset.Zero, delta)
                    overlayTopY += verticalDelta.y
                    dragPoint = dragPoint.copy(y = dragPoint.y + verticalDelta.y)
                    if (scrollJob?.isActive != true) {
                        val scroll = when {
                            dragPoint.y < listBounds.top + 64.dp.toPx() -> -24.dp.toPx()
                            dragPoint.y > listBounds.bottom - 64.dp.toPx() -> 24.dp.toPx()
                            else -> 0f
                        }
                        if (scroll != 0f) scrollJob = scope.launch { listState.scrollBy(scroll) }
                    }
                    val currentData = data ?: return@detectDragGesturesAfterLongPress
                    val routine = currentData.routines.find { it.id == id }
                    val otherIds = if (isFolder) currentData.folders.sortedBy { it.position }
                        .map { it.id }.filterNot { it == id }
                    else currentData.routines.filter { !it.archived && it.folderId == routine?.folderId }
                        .sortedBy { it.position }.map { it.id }.filterNot { it == id }
                    val prefix = if (isFolder) "folder:" else "routine:"
                    val source = dragging ?: return@detectDragGesturesAfterLongPress
                    val centres = if (source.initialCentres.size == otherIds.size &&
                        source.scrollIndex == listState.firstVisibleItemIndex &&
                        source.scrollOffset == listState.firstVisibleItemScrollOffset) source.initialCentres
                    else otherIds.mapNotNull { targets["$prefix$it"]?.center?.y }
                    if (centres.size == otherIds.size) previewIndex = trainingPreviewIndex(centres, dragPoint.y)
                    hover = targets.entries.firstOrNull { (key, rect) ->
                        dragPoint.y in rect.top..rect.bottom && key != (if (isFolder) "folder:$id" else "routine:$id") &&
                            (!isFolder || key.startsWith("folder:"))
                    }?.key
                },
                onDragEnd = ::finishDrag,
                onDragCancel = {
                    dragging = null; hover = null; previewIndex = null; released = false
                },
            )
        }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { listBounds = it.rootRect() },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Entrenamiento", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onRetryWorkout(); onRetryLibrary() },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar entrenamiento y rutinas",
                        modifier = Modifier.size(21.dp))
                }
            }
        }
        item {
            when (activeWorkout) {
                is ActiveWorkoutUiState.Active -> ActiveWorkoutCard(activeWorkout, clock, onContinueWorkout)
                is ActiveWorkoutUiState.NoActiveWorkout -> Button(
                    onClick = onStartEmpty,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("training-start-empty")
                        .semantics { contentDescription = "+ Empezar Entrenamiento Vacío" },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoutineSurface,
                        contentColor = TrainingText,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                        Text("Empezar Entrenamiento Vacío", Modifier.padding(start = 7.dp),
                            style = MaterialTheme.typography.labelLarge, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                is ActiveWorkoutUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Cargando entrenamiento…", Modifier.padding(start = 10.dp))
                }
                is ActiveWorkoutUiState.Error -> Column {
                    Text("No se pudo cargar el entrenamiento.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryWorkout) { Text("Reintentar") }
                }
                else -> Unit
            }
        }
        item {
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Rutinas", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = { onClearMessage(); folderName = ""; createFolder = true }, enabled = !busy,
                    modifier = Modifier.size(48.dp).testTag("training-create-folder")) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Crear carpeta",
                        modifier = Modifier.size(22.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCreateRoutine(null) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("training-new-routine"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoutineSurface,
                        contentColor = TrainingText)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Icono de rutina", modifier = Modifier.size(19.dp)
                            .testTag("training-new-routine-icon"))
                        Text("Nueva rutina", Modifier.padding(start = 7.dp),
                            style = MaterialTheme.typography.labelLarge, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(onClick = {}, enabled = false,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("training-explore"),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = TrainingMuted.copy(alpha = 0.6f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoutineSurface)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(19.dp))
                        Text("Explorar", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        if (message != null) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onClearMessage) { Text("Cerrar") }
            }
        }
        when (library) {
            is LibraryUiState.Loading -> item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Cargando rutinas…", Modifier.padding(start = 10.dp))
                }
            }
            is LibraryUiState.Error -> item {
                Text(library.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryLibrary) { Text("Reintentar") }
            }
            is LibraryUiState.Content -> {
                val folders = data!!.folders.sortedBy { it.position }
                val active = data.routines.filterNot { it.archived }
                val shownFolders = if (dragging?.folder == true && previewIndex != null) {
                    val byId = folders.associateBy { it.id }
                    trainingPreviewOrder(folders.map { it.id }, dragging!!.id, previewIndex!!)
                        .mapNotNull(byId::get)
                } else folders
                shownFolders.forEach { folder ->
                    val index = folders.indexOfFirst { it.id == folder.id }
                    val isExpanded = expanded[folder.id] ?: true
                    item(key = "folder-${folder.id}") {
                        DisposableEffect(folder.id) { onDispose { targets.remove("folder:${folder.id}") } }
                        FolderHeader(folder, isExpanded, busy, hover == "folder:${folder.id}" && dragging != null,
                            modifier = Modifier
                                .animateItem()
                                .then(dragModifier(folder.id, true,
                                    canDragTrainingItem(folder.id, true, data)))
                                .onGloballyPositioned { targets["folder:${folder.id}"] = it.rootRect() },
                            onToggle = { expanded[folder.id] = !isExpanded },
                            onRename = { onClearMessage(); folderName = folder.name; renameFolder = folder }, onDelete = { deleteFolder = folder },
                            onAddRoutine = { onCreateRoutine(folder.id) },
                            onUp = { onReorderFolder(folder.id, index) },
                            onDown = { onReorderFolder(folder.id, index + 2) },
                            canUp = index > 0, canDown = index < folders.lastIndex)
                    }
                    if (isExpanded) {
                        val children = active.filter { it.folderId == folder.id }.sortedBy { it.position }
                        val shownChildren = if (dragging?.folder == false &&
                            dragging?.containerId == folder.id && previewIndex != null) {
                            val byId = children.associateBy { it.id }
                            trainingPreviewOrder(children.map { it.id }, dragging!!.id, previewIndex!!)
                                .mapNotNull(byId::get)
                        } else children
                        items(shownChildren, key = { "routine-${it.id}" }) { routine ->
                            DisposableEffect(routine.id) { onDispose { targets.remove("routine:${routine.id}") } }
                            TrainingRoutineCard(routine, busy, hover == "routine:${routine.id}" && dragging != null,
                                modifier = Modifier.animateItem().then(dragModifier(routine.id, false,
                                    canDragTrainingItem(routine.id, false, data)))
                                    .onGloballyPositioned { targets["routine:${routine.id}"] = it.rootRect() },
                                onOpen = { onOpenRoutine(routine.id) }, onStart = { onStartRoutine(routine.id) },
                                onEdit = { onEditRoutine(routine.id) }, onDuplicate = { onDuplicateRoutine(routine.id) },
                                onShare = { onShareRoutine(routine.id) },
                                onMove = { moveRoutine = routine }, onDelete = { deleteRoutine = routine },
                                onUp = { onPlaceRoutine(routine.id, folder.id, routine.position - 1) },
                                onDown = { onPlaceRoutine(routine.id, folder.id, routine.position + 1) },
                                canUp = routine != children.first(), canDown = routine != children.last())
                        }
                    }
                }
                val root = active.filter { it.folderId == null }.sortedBy { it.position }
                val rootExpanded = expanded["root"] ?: true
                item(key = "root") {
                    DisposableEffect(Unit) { onDispose { targets.remove("root") } }
                    LibrarySectionHeader("Mis rutinas (${root.size})", rootExpanded,
                        hover == "root" && dragging != null,
                        Modifier.animateItem().onGloballyPositioned { targets["root"] = it.rootRect() }
                            .testTag("training-routines-header")) { expanded["root"] = !rootExpanded }
                }
                if (rootExpanded) {
                    if (root.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (active.isEmpty()) "Aún no tienes rutinas" else "No hay rutinas en Mis rutinas",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (active.isEmpty()) TextButton(onClick = { onCreateRoutine(null) }) { Text("Crear primera rutina") }
                        }
                    }
                    val shownRoot = if (dragging?.folder == false && dragging?.containerId == null &&
                        previewIndex != null) {
                        val byId = root.associateBy { it.id }
                        trainingPreviewOrder(root.map { it.id }, dragging!!.id, previewIndex!!)
                            .mapNotNull(byId::get)
                    } else root
                    items(shownRoot, key = { "routine-${it.id}" }) { routine ->
                        DisposableEffect(routine.id) { onDispose { targets.remove("routine:${routine.id}") } }
                        TrainingRoutineCard(routine, busy, hover == "routine:${routine.id}" && dragging != null,
                            modifier = Modifier.animateItem().then(dragModifier(routine.id, false,
                                canDragTrainingItem(routine.id, false, data)))
                                .onGloballyPositioned { targets["routine:${routine.id}"] = it.rootRect() },
                            onOpen = { onOpenRoutine(routine.id) }, onStart = { onStartRoutine(routine.id) },
                            onEdit = { onEditRoutine(routine.id) }, onDuplicate = { onDuplicateRoutine(routine.id) },
                            onShare = { onShareRoutine(routine.id) },
                            onMove = { moveRoutine = routine }, onDelete = { deleteRoutine = routine },
                            onUp = { onPlaceRoutine(routine.id, null, routine.position - 1) },
                            onDown = { onPlaceRoutine(routine.id, null, routine.position + 1) },
                            canUp = routine != root.first(), canDown = routine != root.last())
                    }
                }
            }
        }
    }
    dragging?.let { selected ->
        val routine = data?.routines?.find { it.id == selected.id }
        val folder = data?.folders?.find { it.id == selected.id }
        Box(Modifier.zIndex(TrainingDraggedItemZIndex).fillMaxWidth()
            .offset { IntOffset(0, (overlayTopY - listBounds.top).roundToInt()) }
            .padding(horizontal = 18.dp)
            .shadow(10.dp, RoundedCornerShape(if (selected.folder) 10.dp else 12.dp))
            .graphicsLayer { alpha = if (released) 1f else 0.97f }
            .clearAndSetSemantics { stateDescription = "Arrastrando" }
            .testTag("training-drag-overlay")) {
            if (selected.folder && folder != null) {
                FolderHeader(folder, expanded[folder.id] ?: true, true, false,
                    modifier = Modifier, onToggle = {}, onRename = {}, onAddRoutine = {},
                    onDelete = {}, onUp = {}, onDown = {}, canUp = false, canDown = false)
            } else if (routine != null) {
                TrainingRoutineCard(routine, false, false, modifier = Modifier,
                    onOpen = {}, onStart = {}, onEdit = {}, onDuplicate = {}, onShare = {},
                    onMove = {}, onDelete = {}, onUp = {}, onDown = {}, canUp = false, canDown = false)
            }
        }
    }
    } }
    if (createFolder || renameFolder != null) {
        val folder = renameFolder
        AlertDialog(onDismissRequest = { if (!busy) { createFolder = false; renameFolder = null } },
            title = { Text(if (folder == null) "Crear nueva carpeta" else "Renombrar carpeta") },
            text = { Column {
                OutlinedTextField(folderName, { folderName = it },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (folderSaving) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(20.dp), strokeWidth = 2.dp)
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(onClick = {
                if (folder == null) { folderSaving = true; onCreateFolder(folderName.trim()) }
                else { onRenameFolder(folder.id, folderName.trim()); renameFolder = null; folderName = "" }
            }, enabled = !busy && !folderSaving && folderName.trim().length in 2..100) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { createFolder = false; renameFolder = null; folderName = "" }, enabled = !busy) { Text("Cancelar") } })
    }
    deleteFolder?.let { folder -> AlertDialog(onDismissRequest = { deleteFolder = null },
        title = { Text("Borrar carpeta") }, text = { Text("Sus rutinas pasarán a Mis rutinas.") },
        confirmButton = { TextButton(onClick = { onDeleteFolder(folder.id); deleteFolder = null }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { deleteFolder = null }) { Text("Cancelar") } }) }
    deleteRoutine?.let { routine -> AlertDialog(onDismissRequest = { deleteRoutine = null },
        title = { Text("Borrar rutina") }, text = { Text("¿Quieres borrar ${routine.name}?") },
        confirmButton = { TextButton(onClick = { onDeleteRoutine(routine.id); deleteRoutine = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleteRoutine = null }) { Text("Cancelar") } }) }
    moveRoutine?.let { routine -> AlertDialog(onDismissRequest = { moveRoutine = null }, title = { Text("Mover a carpeta") },
        text = { Column {
            TextButton(onClick = { onMoveRoutine(routine.id, null); moveRoutine = null }) { Text("Mis rutinas") }
            data?.folders?.sortedBy { it.position }?.forEach { folder ->
                TextButton(onClick = { onMoveRoutine(routine.id, folder.id); moveRoutine = null }) { Text(folder.name) }
            }
        } }, confirmButton = { TextButton(onClick = { moveRoutine = null }) { Text("Cancelar") } }) }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.rootRect(): Rect {
    val topLeft = positionInRoot()
    return Rect(topLeft, topLeft + Offset(size.width.toFloat(), size.height.toFloat()))
}

@Composable private fun LibrarySectionHeader(title: String, expanded: Boolean, targeted: Boolean,
    modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp)
        .background(if (targeted) RoutinePurple.copy(alpha = 0.26f) else Color.Transparent)
        .graphicsLayer { alpha = if (pressed) 0.7f else 1f }
        .clickable(interactionSource = interaction, indication = ripple(color = Color.White), onClick = onToggle)
        .semantics { stateDescription = if (expanded) "Desplegada" else "Contraída" },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            contentDescription = if (expanded) "Contraer $title" else "Desplegar $title",
            modifier = Modifier.size(22.dp).testTag("training-routines-chevron"), tint = TrainingMuted)
        Text(title, Modifier.weight(1f).padding(start = 9.dp).testTag("training-routines-title"),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun FolderHeader(folder: RoutineFolder, expanded: Boolean, busy: Boolean, targeted: Boolean,
    modifier: Modifier, onToggle: () -> Unit, onRename: () -> Unit, onAddRoutine: () -> Unit, onDelete: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, canUp: Boolean, canDown: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("training-folder-${folder.id}")
        .background(if (targeted) RoutinePurple.copy(alpha = 0.26f) else Color.Transparent)
        .semantics { stateDescription = if (expanded) "Desplegada. Mantén pulsado para ordenar" else "Contraída. Mantén pulsado para ordenar" },
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 52.dp)
            .graphicsLayer { alpha = if (pressed) 0.7f else 1f }
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White), onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Contraer ${folder.name}" else "Desplegar ${folder.name}",
                modifier = Modifier.size(22.dp).testTag("training-folder-chevron-${folder.id}"), tint = TrainingMuted)
            Text(buildAnnotatedString {
                append(folder.name)
                withStyle(SpanStyle(color = TrainingMuted)) { append(" (${folder.routineCount})") }
            }, Modifier.weight(1f).padding(start = 9.dp), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de carpeta ${folder.name}",
                modifier = Modifier.size(20.dp), tint = TrainingMuted)
        }
    }
    if (menu) TrainingActionsSheet(folder.name, "training-folder-sheet", { menu = false }) {
        TrainingSheetAction("Renombrar carpeta", Icons.Filled.Edit, { menu = false; onRename() })
        TrainingSheetAction("Agregar nueva rutina", Icons.Filled.Add, { menu = false; onAddRoutine() })
        TrainingSheetDivider()
        TrainingSheetAction("Subir", Icons.Filled.ArrowUpward, { menu = false; onUp() }, enabled = canUp)
        TrainingSheetAction("Bajar", Icons.Filled.ArrowDownward, { menu = false; onDown() }, enabled = canDown)
        TrainingSheetDestructiveDivider()
        TrainingSheetAction("Eliminar carpeta", Icons.Filled.DeleteOutline, { menu = false; onDelete() }, destructive = true)
    }
}

@Composable private fun TrainingRoutineCard(routine: LibraryRoutine, busy: Boolean, targeted: Boolean,
    modifier: Modifier, onOpen: () -> Unit, onStart: () -> Unit, onEdit: () -> Unit,
    onDuplicate: () -> Unit, onShare: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, canUp: Boolean, canDown: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(modifier.fillMaxWidth().testTag("training-routine-${routine.id}")
        .graphicsLayer { alpha = if (pressed) 0.88f else 1f }
        .clickable(interactionSource = interaction, indication = ripple(color = Color.White),
            enabled = !busy, onClick = onOpen)
        .semantics {
            contentDescription = "Abrir ${routine.name}"
            stateDescription = "Mantén pulsado para mover. También puedes usar Opciones."
        },
        color = if (targeted) Color(0xFF282033) else RoutineSurface,
        contentColor = TrainingText,
        shape = RoundedCornerShape(12.dp), tonalElevation = if (targeted) 2.dp else 0.dp) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("${routine.exerciseCount} ejercicios")
                        }
                        routine.description?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    }, Modifier.padding(top = 3.dp, end = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TrainingMuted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { menu = true }, enabled = !busy) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de ${routine.name}",
                        modifier = Modifier.size(20.dp), tint = TrainingMuted)
                }
            }
            Button(onClick = onStart, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 6.dp).heightIn(min = 50.dp)
                    .testTag("training-start-${routine.id}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoutinePurple,
                    contentColor = Color.White, disabledContainerColor = RoutinePurple.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f))) {
                Text("Empezar rutina", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (menu) TrainingActionsSheet(routine.name, "training-routine-sheet", { menu = false }) {
        TrainingSheetAction("Compartir rutina", Icons.Filled.Share, { menu = false; onShare() })
        TrainingSheetAction("Duplicar rutina", Icons.Filled.ContentCopy, { menu = false; onDuplicate() })
        TrainingSheetDivider()
        TrainingSheetAction("Abrir", Icons.AutoMirrored.Filled.OpenInNew, { menu = false; onOpen() })
        TrainingSheetAction("Editar rutina", Icons.Filled.Edit, { menu = false; onEdit() })
        TrainingSheetAction("Mover a carpeta", Icons.AutoMirrored.Filled.DriveFileMove, { menu = false; onMove() })
        TrainingSheetDivider()
        TrainingSheetAction("Subir", Icons.Filled.ArrowUpward, { menu = false; onUp() }, enabled = canUp)
        TrainingSheetAction("Bajar", Icons.Filled.ArrowDownward, { menu = false; onDown() }, enabled = canDown)
        TrainingSheetDestructiveDivider()
        TrainingSheetAction("Eliminar rutina", Icons.Filled.DeleteOutline, { menu = false; onDelete() }, destructive = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TrainingActionsSheet(title: String, tag: String, onDismiss: () -> Unit,
    content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.48f),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
            .padding(bottom = 22.dp)) {
            Text(title, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)
                .testTag("$tag-title").semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable private fun TrainingSheetDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable private fun TrainingSheetDestructiveDivider() {
    Spacer(Modifier.height(8.dp))
    TrainingSheetDivider()
    Spacer(Modifier.height(3.dp))
}

@Composable private fun TrainingSheetAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp)
        .testTag("training-action-$label")
        .then(if (destructive) Modifier.semantics { stateDescription = "Acción destructiva" } else Modifier)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp),
            tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Preview(showBackground = true)
@Composable private fun TrainingPreview() { GYmAppTheme {
    TrainingPreviewContent(ActiveWorkoutUiState.NoActiveWorkout(),
        LibraryUiState.Content(LibraryResponse(emptyList(), emptyList(), emptyList())))
} }

@Preview(showBackground = true, name = "Entrenamiento activo con carpeta")
@Composable private fun TrainingActivePreview() { GYmAppTheme {
    val now = Instant.now()
    TrainingPreviewContent(
        ActiveWorkoutUiState.Active(com.mar.gym.feature.workouts.ui.ActiveWorkoutData(
            draft = com.mar.gym.feature.workouts.model.WorkoutDraft(workoutId = "w", title = "Fuerza superior"),
            startedAt = now.minusSeconds(600),
        )),
        LibraryUiState.Content(LibraryResponse(
            folders = listOf(RoutineFolder("f", "Pata", 1, 1, now, now)),
            routines = listOf(LibraryRoutine("r", "f", 0, "Pierna", "Sentadilla y prensa", false, 2,
                RoutineEtag.fromVersion(1)!!, now)),
            programs = emptyList(),
        )),
    )
} }

@Composable private fun TrainingPreviewContent(activeWorkout: ActiveWorkoutUiState, library: LibraryUiState) {
    TrainingScreen(
        activeWorkout = activeWorkout,
        library = library,
        message = null, busy = false, clock = Clock.systemUTC(),
        onContinueWorkout = {}, onStartEmpty = {}, onRetryWorkout = {},
        onOpenRoutine = {}, onStartRoutine = {}, onEditRoutine = {},
        onDuplicateRoutine = {}, onShareRoutine = {}, onDeleteRoutine = {}, onCreateRoutine = {},
        onCreateFolder = {}, onRenameFolder = { _, _ -> }, onDeleteFolder = {},
        onMoveRoutine = { _, _ -> }, onPlaceRoutine = { _, _, _ -> },
        onReorderFolder = { _, _ -> }, onRetryLibrary = {},
        onClearMessage = {},
    )
}
