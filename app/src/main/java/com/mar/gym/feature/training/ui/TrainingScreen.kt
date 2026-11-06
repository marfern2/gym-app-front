package com.mar.gym.feature.training.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.mar.gym.feature.library.model.LibraryResponse
import com.mar.gym.feature.library.model.LibraryRoutine
import com.mar.gym.feature.library.model.RoutineFolder
import com.mar.gym.feature.library.ui.LibraryUiState
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.feature.workouts.ui.ActiveWorkoutUiState
import com.mar.gym.ui.components.AppBottomSheet
import com.mar.gym.ui.components.AppButton
import com.mar.gym.ui.components.AppCard
import com.mar.gym.ui.components.AppDivider
import com.mar.gym.ui.components.AppIconButton
import com.mar.gym.ui.components.AppListRow
import com.mar.gym.ui.components.AppRowStateLayerAlpha
import com.mar.gym.ui.components.AppSectionHeader
import com.mar.gym.ui.components.AppTextButton
import com.mar.gym.ui.components.appPressedState
import com.mar.gym.ui.theme.AppComponentSize
import com.mar.gym.ui.theme.AppSpacing
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.primaryActionContainerColor
import com.mar.gym.ui.theme.primaryActionContentColor
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

private class DragPointerState(var position: Offset = Offset.Zero)

private val TrainingSpatialSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private const val TrainingContentTypeHeader = "training-header"
private const val TrainingContentTypeAction = "training-action"
private const val TrainingContentTypeContainer = "training-container"
private const val TrainingContentTypeRoutine = "training-routine"
private const val TrainingContentTypeState = "training-state"
@Composable
fun TrainingScreen(
    activeWorkout: ActiveWorkoutUiState,
    library: LibraryUiState,
    message: String?,
    busy: Boolean,
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
    val activeRoutineCount = data?.routines?.count { !it.archived }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val targets = remember { mutableMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<DragItem?>(null) }
    val dragPointer = remember { DragPointerState() }
    var overlayTopY by remember { mutableFloatStateOf(0f) }
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
    var chooseRoutineDestination by remember { mutableStateOf(false) }
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
                    destination == "root" -> destinationBounds.bottom + with(density) { AppSpacing.sm.toPx() }
                    destination?.startsWith("folder:") == true -> {
                        val folderId = destination.removePrefix("folder:")
                        if (expanded[folderId] ?: false) destinationBounds.bottom + with(density) { AppSpacing.sm.toPx() }
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
                Animatable(overlayTopY).animateTo(targetTop, animationSpec = TrainingSpatialSpring) {
                    overlayTopY = value
                }
                settled = true
            }
        }
    }

    fun dragModifier(id: String, isFolder: Boolean, enabled: Boolean): Modifier = Modifier
        .then(if (dragging?.id == id) Modifier.graphicsLayer { alpha = 0f } else Modifier)
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
                    dragPointer.position = Offset(bounds.center.x, bounds.top + start.y)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { change, delta ->
                    change.consume()
                    if (dragging?.id != id) return@detectDragGesturesAfterLongPress
                    val verticalDelta = trainingVerticalDragOffset(Offset.Zero, delta)
                    overlayTopY += verticalDelta.y
                    dragPointer.position = dragPointer.position.copy(
                        y = dragPointer.position.y + verticalDelta.y,
                    )
                    if (scrollJob?.isActive != true) {
                        val scroll = when {
                            dragPointer.position.y < listBounds.top + AppComponentSize.listRowMinHeight.toPx() ->
                                -AppSpacing.xl.toPx()
                            dragPointer.position.y > listBounds.bottom - AppComponentSize.listRowMinHeight.toPx() ->
                                AppSpacing.xl.toPx()
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
                    if (centres.size == otherIds.size) {
                        previewIndex = trainingPreviewIndex(centres, dragPointer.position.y)
                    }
                    hover = targets.entries.firstOrNull { (key, rect) ->
                        dragPointer.position.y in rect.top..rect.bottom &&
                            key != (if (isFolder) "folder:$id" else "routine:$id") &&
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
            .onPlaced { listBounds = it.rootRect() },
        contentPadding = PaddingValues(
            start = AppSpacing.lg,
            top = AppSpacing.sm,
            end = AppSpacing.lg,
            bottom = AppSpacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        item(key = "training-header", contentType = TrainingContentTypeHeader) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = AppComponentSize.buttonHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Entrenamiento", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.weight(1f))
                AppIconButton(onClick = { onRetryWorkout(); onRetryLibrary() },
                    modifier = Modifier.size(AppComponentSize.touchTarget)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar entrenamiento y rutinas",
                        modifier = Modifier.size(AppComponentSize.iconMedium))
                }
            }
        }
        if (activeWorkout !is ActiveWorkoutUiState.Active) {
            item(key = "training-quick-start", contentType = TrainingContentTypeAction) {
                when (activeWorkout) {
                    is ActiveWorkoutUiState.NoActiveWorkout -> TrainingQuickStartRow(
                        onClick = onStartEmpty,
                        modifier = Modifier.testTag("training-start-empty"),
                    )
                    is ActiveWorkoutUiState.Loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(AppComponentSize.iconMedium),
                            strokeWidth = AppComponentSize.focusIndicator,
                        )
                        Text("Cargando entrenamiento…", Modifier.padding(start = AppSpacing.md))
                    }
                    is ActiveWorkoutUiState.Error -> Column {
                        Text(
                            "No se pudo cargar el entrenamiento.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        AppTextButton(onClick = onRetryWorkout) { Text("Reintentar") }
                    }
                    else -> Unit
                }
            }
        }
        item(key = "training-library-header", contentType = TrainingContentTypeHeader) {
            AppSectionHeader(
                title = activeRoutineCount?.let { "Rutinas ($it)" } ?: "Rutinas",
                modifier = Modifier.padding(top = AppSpacing.sm).semantics { heading() },
                actionContent = {
                    AppTextButton(
                        onClick = {
                            val folders = data?.folders.orEmpty()
                            if (folders.isEmpty()) onCreateRoutine(null) else chooseRoutineDestination = true
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .heightIn(min = AppComponentSize.touchTarget)
                            .testTag("training-new-routine"),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier
                                .size(AppComponentSize.iconSmall)
                                .testTag("training-new-routine-icon"),
                        )
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("Nueva", style = MaterialTheme.typography.labelLarge)
                    }
                    AppIconButton(
                        onClick = { onClearMessage(); folderName = ""; createFolder = true },
                        enabled = !busy,
                        modifier = Modifier
                            .size(AppComponentSize.touchTarget)
                            .testTag("training-create-folder"),
                    ) {
                        Icon(
                            Icons.Filled.CreateNewFolder,
                            contentDescription = "Crear carpeta",
                            modifier = Modifier.size(AppComponentSize.iconMedium),
                        )
                    }
                }
            )
        }
        item(key = "training-explore", contentType = TrainingContentTypeAction) {
            AppTextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .heightIn(min = AppComponentSize.touchTarget)
                    .testTag("training-explore")
                    .semantics { stateDescription = "No disponible. Próximamente" },
                contentPadding = PaddingValues(horizontal = AppSpacing.xs),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(AppComponentSize.iconSmall),
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Explorar rutinas · Próximamente", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (message != null) item(key = "training-message", contentType = TrainingContentTypeState) {
            AppListRow(
                title = message,
                destructive = true,
                titleStyle = MaterialTheme.typography.bodyMedium,
                trailingContent = {
                    AppTextButton(onClick = onClearMessage) { Text("Cerrar") }
                },
            )
        }
        when (library) {
            is LibraryUiState.Loading -> item(
                key = "training-library-loading",
                contentType = TrainingContentTypeState,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(AppComponentSize.iconMedium),
                        strokeWidth = AppComponentSize.focusIndicator,
                    )
                    Text("Cargando rutinas…", Modifier.padding(start = AppSpacing.md))
                }
            }
            is LibraryUiState.Error -> item(
                key = "training-library-error",
                contentType = TrainingContentTypeState,
            ) {
                Text(library.message, color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = onRetryLibrary) { Text("Reintentar") }
            }
            is LibraryUiState.Content -> {
                val folders = data!!.folders.sortedBy { it.position }
                val active = data.routines.filterNot { it.archived }
                val routinesByFolder = active
                    .groupBy { it.folderId }
                    .mapValues { (_, routines) -> routines.sortedBy { it.position } }
                val folderIndices = folders.mapIndexed { index, folder -> folder.id to index }.toMap()
                val root = routinesByFolder[null].orEmpty()
                val rootExpanded = expanded["root"] ?: true
                item(key = "root", contentType = TrainingContentTypeContainer) {
                    DisposableEffect(Unit) { onDispose { targets.remove("root") } }
                    TrainingContainerHeader(
                        title = "Mis rutinas",
                        count = root.size,
                        expanded = rootExpanded,
                        targeted = hover == "root" && dragging != null,
                        dragging = false,
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        chevronTestTag = "training-routines-chevron",
                        modifier = Modifier
                            .animateItem()
                            .onPlaced { targets["root"] = it.rootRect() }
                            .testTag("training-routines-header"),
                        onToggle = { expanded["root"] = !rootExpanded },
                    )
                }
                if (rootExpanded) {
                    if (root.isEmpty()) item(
                        key = "root-empty",
                        contentType = TrainingContentTypeState,
                    ) {
                        EmptyTrainingContainerRow(
                            message = if (active.isEmpty()) "Aún no tienes rutinas" else "Sin rutinas",
                            actionLabel = if (active.isEmpty()) "Crear primera" else "Añadir",
                            onAction = { onCreateRoutine(null) },
                            modifier = Modifier.animateItem().padding(start = AppSpacing.md),
                        )
                    }
                    val shownRoot = if (dragging?.folder == false && dragging?.containerId == null &&
                        previewIndex != null) {
                        val byId = root.associateBy { it.id }
                        trainingPreviewOrder(root.map { it.id }, dragging!!.id, previewIndex!!)
                            .mapNotNull(byId::get)
                    } else root
                    items(
                        items = shownRoot,
                        key = { "routine-${it.id}" },
                        contentType = { TrainingContentTypeRoutine },
                    ) { routine ->
                        DisposableEffect(routine.id) { onDispose { targets.remove("routine:${routine.id}") } }
                        TrainingRoutineCard(
                            routine = routine,
                            busy = busy,
                            targeted = hover == "routine:${routine.id}" && dragging != null,
                            modifier = Modifier
                                .animateItem()
                                .padding(start = AppSpacing.md)
                                .then(dragModifier(routine.id, false,
                                    canDragTrainingItem(routine.id, false, data)))
                                .onPlaced { targets["routine:${routine.id}"] = it.rootRect() },
                            onOpen = { onOpenRoutine(routine.id) }, onStart = { onStartRoutine(routine.id) },
                            onEdit = { onEditRoutine(routine.id) }, onDuplicate = { onDuplicateRoutine(routine.id) },
                            onShare = { onShareRoutine(routine.id) },
                            onMove = { moveRoutine = routine }, onDelete = { deleteRoutine = routine },
                            onUp = { onPlaceRoutine(routine.id, null, routine.position - 1) },
                            onDown = { onPlaceRoutine(routine.id, null, routine.position + 1) },
                            canUp = routine != root.first(), canDown = routine != root.last(),
                        )
                    }
                }
                val shownFolders = if (dragging?.folder == true && previewIndex != null) {
                    val byId = folders.associateBy { it.id }
                    trainingPreviewOrder(folders.map { it.id }, dragging!!.id, previewIndex!!)
                        .mapNotNull(byId::get)
                } else folders
                shownFolders.forEach { folder ->
                    val index = folderIndices.getValue(folder.id)
                    val isExpanded = expanded[folder.id] ?: false
                    val children = routinesByFolder[folder.id].orEmpty()
                    item(key = "folder-${folder.id}", contentType = TrainingContentTypeContainer) {
                        DisposableEffect(folder.id) { onDispose { targets.remove("folder:${folder.id}") } }
                        FolderHeader(folder, children.size, isExpanded, busy,
                            hover == "folder:${folder.id}" && dragging != null,
                            dragging = false,
                            modifier = Modifier
                                .animateItem()
                                .then(dragModifier(folder.id, true,
                                    canDragTrainingItem(folder.id, true, data)))
                                .onPlaced { targets["folder:${folder.id}"] = it.rootRect() },
                            onToggle = { expanded[folder.id] = !isExpanded },
                            onRename = { onClearMessage(); folderName = folder.name; renameFolder = folder }, onDelete = { deleteFolder = folder },
                            onAddRoutine = { onCreateRoutine(folder.id) },
                            onUp = { onReorderFolder(folder.id, index) },
                            onDown = { onReorderFolder(folder.id, index + 2) },
                            canUp = index > 0, canDown = index < folders.lastIndex)
                    }
                    if (isExpanded) {
                        val shownChildren = if (dragging?.folder == false &&
                            dragging?.containerId == folder.id && previewIndex != null) {
                            val byId = children.associateBy { it.id }
                            trainingPreviewOrder(children.map { it.id }, dragging!!.id, previewIndex!!)
                                .mapNotNull(byId::get)
                        } else children
                        if (children.isEmpty()) item(
                            key = "folder-empty-${folder.id}",
                            contentType = TrainingContentTypeState,
                        ) {
                            EmptyTrainingContainerRow(
                                message = "Carpeta vacía",
                                actionLabel = "Añadir",
                                onAction = { onCreateRoutine(folder.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = AppSpacing.md)
                                    .testTag("training-add-${folder.id}"),
                            )
                        }
                        items(
                            items = shownChildren,
                            key = { "routine-${it.id}" },
                            contentType = { TrainingContentTypeRoutine },
                        ) { routine ->
                            DisposableEffect(routine.id) { onDispose { targets.remove("routine:${routine.id}") } }
                            TrainingRoutineCard(routine, busy, hover == "routine:${routine.id}" && dragging != null,
                                modifier = Modifier
                                    .animateItem()
                                    .padding(start = AppSpacing.md)
                                    .then(dragModifier(routine.id, false,
                                        canDragTrainingItem(routine.id, false, data)))
                                    .onPlaced { targets["routine:${routine.id}"] = it.rootRect() },
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
            }
        }
    }
    dragging?.let { selected ->
        val routine = data?.routines?.find { it.id == selected.id }
        val folder = data?.folders?.find { it.id == selected.id }
        Box(Modifier.zIndex(TrainingDraggedItemZIndex).fillMaxWidth()
            .offset { IntOffset(0, (overlayTopY - listBounds.top).roundToInt()) }
            .padding(horizontal = AppSpacing.lg)
            .shadow(AppSpacing.md, if (selected.folder) MaterialTheme.shapes.small else MaterialTheme.shapes.medium)
            .graphicsLayer { alpha = if (released) 1f else 0.97f }
            .clearAndSetSemantics { stateDescription = "Arrastrando" }
            .testTag("training-drag-overlay")) {
            if (selected.folder && folder != null) {
                val folderCount = data?.routines?.count { !it.archived && it.folderId == folder.id }
                    ?: folder.routineCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                FolderHeader(folder, folderCount, expanded[folder.id] ?: false, true, false, dragging = true,
                    modifier = Modifier, onToggle = {}, onRename = {}, onAddRoutine = {},
                    onDelete = {}, onUp = {}, onDown = {}, canUp = false, canDown = false)
            } else if (routine != null) {
                TrainingRoutineCard(routine, false, false, dragging = true,
                    modifier = Modifier.padding(start = AppSpacing.md),
                    onOpen = {}, onStart = {}, onEdit = {}, onDuplicate = {}, onShare = {},
                    onMove = {}, onDelete = {}, onUp = {}, onDown = {}, canUp = false, canDown = false)
            }
        }
    }
    } }
    if (chooseRoutineDestination) {
        RoutineDestinationSheet(
            title = "Elegir carpeta",
            folders = data?.folders.orEmpty().sortedBy { it.position },
            onDismiss = { chooseRoutineDestination = false },
            onDestinationSelected = { folderId ->
                chooseRoutineDestination = false
                onCreateRoutine(folderId)
            },
        )
    }
    moveRoutine?.let { routine ->
        RoutineDestinationSheet(
            title = "Mover ${routine.name}",
            folders = data?.folders.orEmpty().sortedBy { it.position },
            onDismiss = { moveRoutine = null },
            onDestinationSelected = { folderId ->
                moveRoutine = null
                onMoveRoutine(routine.id, folderId)
            },
        )
    }
    if (createFolder || renameFolder != null) {
        val folder = renameFolder
        AlertDialog(onDismissRequest = { if (!busy) { createFolder = false; renameFolder = null } },
            title = { Text(if (folder == null) "Crear nueva carpeta" else "Renombrar carpeta") },
            text = { Column {
                OutlinedTextField(folderName, { folderName = it },
                    label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (folderSaving) CircularProgressIndicator(
                    Modifier.padding(top = AppSpacing.sm).size(AppComponentSize.iconMedium),
                    strokeWidth = AppComponentSize.focusIndicator,
                )
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { AppTextButton(onClick = {
                if (folder == null) { folderSaving = true; onCreateFolder(folderName.trim()) }
                else { onRenameFolder(folder.id, folderName.trim()); renameFolder = null; folderName = "" }
            }, enabled = !busy && !folderSaving && folderName.trim().length in 2..100) { Text("Guardar") } },
            dismissButton = { AppTextButton(onClick = { createFolder = false; renameFolder = null; folderName = "" }, enabled = !busy) { Text("Cancelar") } })
    }
    deleteFolder?.let { folder -> AlertDialog(onDismissRequest = { deleteFolder = null },
        title = { Text("Borrar carpeta") }, text = { Text("Sus rutinas pasarán a Mis rutinas.") },
        confirmButton = { AppTextButton(onClick = { onDeleteFolder(folder.id); deleteFolder = null }) { Text("Borrar") } },
        dismissButton = { AppTextButton(onClick = { deleteFolder = null }) { Text("Cancelar") } }) }
    deleteRoutine?.let { routine -> AlertDialog(onDismissRequest = { deleteRoutine = null },
        title = { Text("Borrar rutina") }, text = { Text("¿Quieres borrar ${routine.name}?") },
        confirmButton = { AppTextButton(onClick = { onDeleteRoutine(routine.id); deleteRoutine = null }) { Text("Eliminar") } },
        dismissButton = { AppTextButton(onClick = { deleteRoutine = null }) { Text("Cancelar") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDestinationSheet(
    title: String,
    folders: List<RoutineFolder>,
    onDismiss: () -> Unit,
    onDestinationSelected: (String?) -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("routine-destination-sheet"),
        title = title,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.testTag("routine-destination-handle"),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = AppComponentSize.sheetContentMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            val destinations = listOf(null to "Mis rutinas") + folders.map { it.id to it.name }
            destinations.forEachIndexed { index, (folderId, name) ->
                RoutineDestinationRow(
                    name = name,
                    isFolder = folderId != null,
                    testTag = folderId?.let { "routine-destination-$it" }
                        ?: "routine-destination-root",
                    onClick = { onDestinationSelected(folderId) },
                )
                if (index < destinations.lastIndex) {
                    AppDivider(modifier = Modifier.padding(horizontal = AppSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun RoutineDestinationRow(
    name: String,
    isFolder: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    AppListRow(
        title = name,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        leadingContent = {
            Icon(
                imageVector = if (isFolder) Icons.Filled.Folder else Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                modifier = Modifier.size(AppComponentSize.iconMedium),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

private fun androidx.compose.ui.layout.LayoutCoordinates.rootRect(): Rect {
    val topLeft = positionInRoot()
    return Rect(topLeft, topLeft + Offset(size.width.toFloat(), size.height.toFloat()))
}

@Composable
private fun TrainingQuickStartRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListRow(
        title = "Entrenamiento vacío",
        supportingText = "Empezar sin una rutina",
        onClick = onClick,
        modifier = modifier,
        titleStyle = MaterialTheme.typography.titleMedium,
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppComponentSize.iconMedium),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppComponentSize.iconSmall),
            )
        },
    )
}

@Composable
private fun EmptyTrainingContainerRow(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListRow(
        title = message,
        modifier = modifier,
        titleStyle = MaterialTheme.typography.bodyMedium,
        contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        trailingContent = {
            AppTextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = AppComponentSize.touchTarget),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun TrainingContainerHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    targeted: Boolean,
    dragging: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    chevronTestTag: String,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = TrainingSpatialSpring,
        label = "training-container-chevron",
    )
    val containerColor = when {
        targeted -> MaterialTheme.colorScheme.primaryContainer
        dragging -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val contentColor = if (targeted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val outlineModifier = if (targeted || dragging) {
        Modifier.border(
            width = AppComponentSize.focusIndicator,
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppComponentSize.listRowMinHeight)
            .background(containerColor, MaterialTheme.shapes.small)
            .then(outlineModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = AppComponentSize.listRowMinHeight)
                .appPressedState(
                    interaction,
                    shape = MaterialTheme.shapes.small,
                    maxOverlayAlpha = AppRowStateLayerAlpha,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .semantics {
                    heading()
                    stateDescription = if (expanded) {
                        "Expandida, $count rutinas"
                    } else {
                        "Contraída, $count rutinas"
                    }
                }
                .padding(start = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(AppComponentSize.iconMedium)
                    .graphicsLayer { rotationZ = chevronRotation }
                    .testTag(chevronTestTag),
                tint = if (targeted || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = AppSpacing.sm)
                    .size(AppComponentSize.iconMedium),
                tint = if (targeted || dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppSpacing.sm)
                    .testTag("training-routines-title"),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (targeted) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.sm),
        )
        if (targeted) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                contentDescription = "Soltar en $title",
                tint = contentColor,
                modifier = Modifier.size(AppComponentSize.iconMedium),
            )
        }
        trailingContent?.invoke()
    }
}

@Composable private fun FolderHeader(folder: RoutineFolder, routineCount: Int, expanded: Boolean,
    busy: Boolean, targeted: Boolean,
    dragging: Boolean, modifier: Modifier, onToggle: () -> Unit, onRename: () -> Unit,
    onAddRoutine: () -> Unit, onDelete: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, canUp: Boolean, canDown: Boolean) {
    var menu by remember { mutableStateOf(false) }
    TrainingContainerHeader(
        title = folder.name,
        count = routineCount,
        expanded = expanded,
        targeted = targeted,
        dragging = dragging,
        icon = Icons.Filled.Folder,
        chevronTestTag = "training-folder-chevron-${folder.id}",
        modifier = modifier.testTag("training-folder-${folder.id}"),
        onToggle = onToggle,
        trailingContent = {
            AppIconButton(onClick = { menu = true }, enabled = !busy && !dragging,
                modifier = Modifier.size(AppComponentSize.touchTarget)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de carpeta ${folder.name}",
                    modifier = Modifier.size(AppComponentSize.iconMedium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
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
    dragging: Boolean = false,
    modifier: Modifier, onOpen: () -> Unit, onStart: () -> Unit, onEdit: () -> Unit,
    onDuplicate: () -> Unit, onShare: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit,
    onUp: () -> Unit, onDown: () -> Unit, canUp: Boolean, canDown: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val emphasized = targeted || dragging
    val cardContentColor = if (targeted) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    AppCard(
        modifier = modifier.fillMaxWidth().testTag("training-routine-${routine.id}")
        .semantics {
            if (targeted) stateDescription = "Destino de inserción"
        },
        onClick = onOpen,
        enabled = !busy && !dragging,
        colors = CardDefaults.cardColors(
            containerColor = if (targeted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else if (dragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = cardContentColor,
        ),
        border = BorderStroke(
            width = if (emphasized) AppComponentSize.focusIndicator else AppComponentSize.border,
            color = if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    routine.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(description, Modifier.padding(top = AppSpacing.xs, end = AppSpacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targeted) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                AppIconButton(onClick = { menu = true }, enabled = !busy && !dragging) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de ${routine.name}",
                        modifier = Modifier.size(AppComponentSize.iconMedium),
                        tint = if (targeted) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${routine.exerciseCount} ejercicios",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (targeted) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                AppButton(
                    onClick = onStart,
                    enabled = !busy && !dragging,
                    modifier = Modifier
                        .heightIn(min = AppComponentSize.touchTarget)
                        .testTag("training-start-${routine.id}"),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryActionContainerColor(),
                        contentColor = primaryActionContentColor(),
                    ),
                    contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(AppComponentSize.iconSmall),
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text("Empezar", style = MaterialTheme.typography.labelLarge)
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
    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(tag),
        title = title,
        titleModifier = Modifier.testTag("$tag-title").semantics { heading() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = AppComponentSize.sheetContentMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@Composable private fun TrainingSheetDivider() {
    AppDivider(
        Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable private fun TrainingSheetDestructiveDivider() {
    Spacer(Modifier.height(AppSpacing.sm))
    TrainingSheetDivider()
    Spacer(Modifier.height(AppSpacing.xs))
}

@Composable private fun TrainingSheetAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    AppListRow(
        title = label,
        onClick = onClick,
        enabled = enabled,
        destructive = destructive,
        modifier = Modifier
            .testTag("training-action-$label")
            .then(
                if (destructive) {
                    Modifier.semantics { stateDescription = "Acción destructiva" }
                } else {
                    Modifier
                },
            ),
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(AppComponentSize.iconMedium),
                tint = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}

@Preview(
    showBackground = true,
    name = "Training · empty · dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable private fun TrainingPreview() { GYmAppTheme(darkTheme = true) {
    TrainingPreviewContent(ActiveWorkoutUiState.NoActiveWorkout(),
        LibraryUiState.Content(LibraryResponse(emptyList(), emptyList(), emptyList())))
} }

@Preview(
    showBackground = true,
    name = "Training · content · dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable private fun TrainingActivePreview() { GYmAppTheme(darkTheme = true) {
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

@Preview(
    showBackground = true,
    name = "Training · narrow · font 1.5",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 320,
    heightDp = 780,
    fontScale = 1.5f,
)
@Composable
private fun TrainingNarrowLargeFontPreview() {
    GYmAppTheme(darkTheme = true) {
        val now = Instant.now()
        TrainingPreviewContent(
            ActiveWorkoutUiState.NoActiveWorkout(),
            LibraryUiState.Content(
                LibraryResponse(
                    folders = listOf(
                        RoutineFolder(
                            "folder-long",
                            "Preparación de fuerza para tren inferior",
                            1,
                            1,
                            now,
                            now,
                        ),
                    ),
                    routines = listOf(
                        LibraryRoutine(
                            "routine-long",
                            "folder-long",
                            0,
                            "Hipertrofia avanzada de cuádriceps y cadena posterior",
                            "Sentadilla, prensa y accesorios de control técnico",
                            false,
                            7,
                            RoutineEtag.fromVersion(1)!!,
                            now,
                        ),
                    ),
                    programs = emptyList(),
                ),
            ),
        )
    }
}

@Composable private fun TrainingPreviewContent(activeWorkout: ActiveWorkoutUiState, library: LibraryUiState) {
    TrainingScreen(
        activeWorkout = activeWorkout,
        library = library,
        message = null, busy = false,
        onStartEmpty = {}, onRetryWorkout = {},
        onOpenRoutine = {}, onStartRoutine = {}, onEditRoutine = {},
        onDuplicateRoutine = {}, onShareRoutine = {}, onDeleteRoutine = {}, onCreateRoutine = {},
        onCreateFolder = {}, onRenameFolder = { _, _ -> }, onDeleteFolder = {},
        onMoveRoutine = { _, _ -> }, onPlaceRoutine = { _, _, _ -> },
        onReorderFolder = { _, _ -> }, onRetryLibrary = {},
        onClearMessage = {},
    )
}
