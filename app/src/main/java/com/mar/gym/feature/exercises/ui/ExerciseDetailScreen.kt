package com.mar.gym.feature.exercises.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import com.mar.gym.R
import com.mar.gym.core.units.*
import com.mar.gym.feature.exercises.model.*
import com.mar.gym.feature.progress.model.*
import com.mar.gym.feature.progress.ui.ExerciseProgressUiState
import com.mar.gym.feature.progress.ui.ExerciseProgressViewModel
import com.mar.gym.ui.components.*
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseDetailRoute(
    exerciseTemplateId: String,
    viewModel: ExerciseDetailViewModel,
    progressViewModel: ExerciseProgressViewModel,
    imageLoader: ImageLoader,
    onOpenAttribution: (HttpsUrl) -> Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onShare: (String) -> Unit,
    preferences: UnitPreferences,
    historyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val progress by progressViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(exerciseTemplateId) { viewModel.load(exerciseTemplateId) }
    ExerciseDetailScreen(
        state = state, options = options, progress = progress, preferences = preferences,
        onBack = onBack, onRetry = viewModel::retry, onReload = viewModel::reload,
        onEdit = onEdit, onArchive = viewModel::archive, onRestore = viewModel::restore,
        onSelectTab = viewModel::selectTab, onSelectRange = viewModel::selectRange,
        onSelectMetric = viewModel::selectMetric, onSelectChartPoint = viewModel::selectChartPoint,
        onSaveNote = viewModel::saveNote,
        onUnitOverride = viewModel::setWeightUnitOverride,
        onLoadMore = progressViewModel::loadMore,
        onRetryHistory = progressViewModel::retryHistory,
        onRetryRecords = progressViewModel::retryRecords,
        onOpenWorkout = onOpenWorkout, onShare = onShare, historyListState = historyListState,
        mediaRenderer = { media, description, mediaModifier ->
            CoilExerciseMedia(media, description, imageLoader, mediaModifier)
        },
        onOpenAttribution = onOpenAttribution, modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    mediaRenderer: ExerciseMediaRenderer,
    onOpenAttribution: (HttpsUrl) -> Boolean,
    modifier: Modifier = Modifier,
    options: ExerciseDetailOptions = ExerciseDetailOptions(),
    progress: ExerciseProgressUiState = ExerciseProgressUiState(loading = false, recordsLoading = false),
    preferences: UnitPreferences = UnitPreferences(),
    onReload: () -> Unit = onRetry,
    onEdit: (String) -> Unit = {},
    onArchive: () -> Unit = {},
    onRestore: () -> Unit = {},
    onSelectTab: (ExerciseDetailTab) -> Unit = {},
    onSelectRange: (HistoryRange) -> Unit = {},
    onSelectMetric: (ExerciseMetric) -> Unit = {},
    onSelectChartPoint: (LocalDate) -> Unit = {},
    onSaveNote: (String?) -> Unit = {},
    onUnitOverride: (WeightUnit?) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetryHistory: () -> Unit = {},
    onRetryRecords: () -> Unit = {},
    onOpenWorkout: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    historyListState: LazyListState = rememberLazyListState(),
) {
    val detail = when (state) {
        is ExerciseDetailUiState.Content -> state.document.detail
        is ExerciseDetailUiState.Conflict -> state.document.detail
        is ExerciseDetailUiState.Error -> state.retainedDocument?.detail
        else -> null
    }
    var sheet by remember { mutableStateOf<String?>(null) }
    var noteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    val units = preferences.copy(weight = effectiveWeightUnit(preferences.weight, options.weightUnitOverride))
    val baseColors = MaterialTheme.colorScheme
    val dark = baseColors.background.luminance() < 0.5f
    val accent = if (dark) Color(0xFFB79AFF) else Color(0xFF6848BB)
    MaterialTheme(
        colorScheme = baseColors.copy(
            primary = accent,
            onPrimary = if (dark) Color(0xFF24133F) else Color.White,
            primaryContainer = if (dark) Color(0xFF34264F) else Color(0xFFE9DFFF),
            onPrimaryContainer = if (dark) Color(0xFFEBDDFF) else Color(0xFF2E175B),
        ),
    ) {
    Scaffold(modifier.fillMaxSize(), topBar = {
        Column {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        detail?.name ?: stringResource(R.string.exercise_detail_title),
                        modifier = Modifier.testTag("exercise-title"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("exercise-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver",
                            modifier = Modifier.size(22.dp))
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton({ onShare(detail.name) }, Modifier.testTag("exercise-share")) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir ejercicio",
                                modifier = Modifier.size(21.dp))
                        }
                        IconButton({ sheet = "menu" }, Modifier.testTag("exercise-menu")) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones",
                                modifier = Modifier.size(23.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
            if (detail != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactTabs = maxWidth < 360.dp
                TabRow(
                selectedTabIndex = options.tab.ordinal,
                modifier = Modifier.testTag("exercise-tabs"),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[options.tab.ordinal]),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                divider = { HorizontalDivider(thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)) },
            ) {
                ExerciseDetailTab.entries.forEach { tab ->
                    Tab(options.tab == tab, { onSelectTab(tab) },
                        Modifier.height(48.dp).testTag("exercise-tab-${tab.name}"),
                        content = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(when (tab) {
                                    ExerciseDetailTab.Summary -> "Resumen"
                                    ExerciseDetailTab.History -> "Historial"
                                    ExerciseDetailTab.Instructions -> "Indicaciones"
                                }, style = when {
                                    compactTabs && LocalDensity.current.fontScale > 1.2f ->
                                        MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                                    compactTabs || LocalDensity.current.fontScale > 1.2f ->
                                        MaterialTheme.typography.labelMedium
                                    else -> MaterialTheme.typography.labelLarge
                                }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
        }
    }) { padding ->
        when {
            detail != null -> Crossfade(
                targetState = options.tab,
                animationSpec = tween(150),
                label = "Sección del ejercicio",
            ) { activeTab ->
                when (activeTab) {
                    ExerciseDetailTab.Summary -> SummaryTab(detail, state, progress, options, units,
                        mediaRenderer, onSelectRange, onSelectMetric, onSelectChartPoint,
                        onOpenWorkout, onLoadMore, onRetryHistory, onRetryRecords,
                        onReload, Modifier.padding(padding))
                    ExerciseDetailTab.History -> HistoryTab(progress, units, historyListState,
                        onOpenWorkout, onLoadMore, onRetryHistory, Modifier.padding(padding))
                    ExerciseDetailTab.Instructions -> InstructionsTab(detail, options.note, mediaRenderer,
                        onOpenAttribution, { noteDraft = options.note.orEmpty(); noteEditor = true },
                        Modifier.padding(padding))
                }
            }
            state is ExerciseDetailUiState.Loading -> LoadingState(
                message = stringResource(R.string.exercise_detail_loading),
                modifier = Modifier.padding(padding))
            state is ExerciseDetailUiState.Error -> Message("No se pudo cargar el ejercicio.", onRetry, Modifier.padding(padding))
            else -> Message(stringResource(R.string.exercise_detail_not_found_title), null, Modifier.padding(padding))
        }
    }
    if (sheet != null && detail != null) ModalBottomSheet(
        onDismissRequest = { sheet = null },
        modifier = Modifier.testTag("exercise-detail-sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.48f),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)) {
            if (sheet == "menu") {
                SheetTitle("Opciones del ejercicio")
                SheetAction("Unidades de peso", "exercise-unit-action", Icons.Default.Straighten) { sheet = "units" }
                if (detail.source == ExerciseTemplateSource.Custom) {
                    if (!detail.archived) {
                        SheetAction(stringResource(R.string.exercise_edit), "exercise-edit", Icons.Default.Edit) {
                            sheet = null; onEdit(detail.id)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SheetAction(stringResource(R.string.exercise_archive), "exercise-archive",
                            Icons.Default.DeleteOutline, destructive = true) {
                            sheet = null; onArchive()
                        }
                    } else SheetAction(stringResource(R.string.exercise_restore), "exercise-restore",
                        Icons.Default.RestartAlt) {
                        sheet = null; onRestore()
                    }
                }
            } else {
                SheetTitle("Unidades de peso")
                listOf(null, WeightUnit.KG, WeightUnit.LB).forEach { unit ->
                    val label = when (unit) {
                        null -> "Usar valor predeterminado de la app"
                        WeightUnit.KG -> "kg"
                        WeightUnit.LB -> "lbs"
                    }
                    SheetAction(label, "exercise-unit-${unit?.name ?: "default"}",
                        Icons.Default.Straighten, selected = options.weightUnitOverride == unit) {
                        onUnitOverride(unit); sheet = null
                    }
                }
            }
        }
    }
    if (noteEditor) AlertDialog(
        onDismissRequest = { noteEditor = false },
        title = { Text("Nota del ejercicio") },
        text = { TextField(noteDraft, { noteDraft = it }, Modifier.fillMaxWidth().testTag("exercise-note-input"),
            placeholder = { Text("Añade una nota") }, minLines = 3) },
        confirmButton = { TextButton({ onSaveNote(noteDraft); noteEditor = false }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (options.note != null) TextButton({ onSaveNote(null); noteEditor = false }) { Text("Eliminar") }
                TextButton({ noteEditor = false }) { Text("Cancelar") }
            }
        },
    )
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(title, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)
        .semantics { heading() }, style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SheetAction(
    label: String,
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
        .semantics { this.selected = selected }
        .clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp),
            tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = color)
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SummaryTab(
    detail: ExerciseTemplateDetail, state: ExerciseDetailUiState, progress: ExerciseProgressUiState,
    options: ExerciseDetailOptions, units: UnitPreferences, mediaRenderer: ExerciseMediaRenderer,
    onSelectRange: (HistoryRange) -> Unit, onSelectMetric: (ExerciseMetric) -> Unit,
    onSelectChartPoint: (LocalDate) -> Unit, onOpenWorkout: (String) -> Unit,
    onLoadMore: () -> Unit, onRetryHistory: () -> Unit, onRetryRecords: () -> Unit,
    onReload: () -> Unit, modifier: Modifier,
) {
    val metrics = remember(detail.exerciseType) { detail.exerciseType.availableMetrics() }
    val metric = options.metric?.takeIf { it in metrics } ?: metrics.first()
    val zone = remember { ZoneId.systemDefault() }
    val points = remember(progress.sessions, metric, options.range) {
        chartPoints(progress.sessions, metric, options.range, LocalDate.now(), zone)
    }
    val selectedPoint = selectedChartPoint(points, options.selectedChartDate)
    LazyColumn(modifier.fillMaxSize().testTag("exercise-summary"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Demonstration(detail, mediaRenderer) }
        item { ExerciseIdentity(detail) }
        if (state is ExerciseDetailUiState.Conflict) item {
            Text(stringResource(R.string.exercise_conflict_message), color = MaterialTheme.colorScheme.error)
            TextButton(onReload) { Text(stringResource(R.string.exercise_reload_server)) }
        }
        item { SectionDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("Progreso", Modifier.weight(1f))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton({ expanded = true }, Modifier.testTag("exercise-range"),
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)) {
                        Text(when (options.range) {
                            HistoryRange.ThreeMonths -> "3 meses"
                            HistoryRange.OneYear -> "1 año"
                            HistoryRange.AllTime -> "Todo"
                        }, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        listOf(HistoryRange.ThreeMonths to "Últimos 3 meses",
                            HistoryRange.OneYear to "Último año", HistoryRange.AllTime to "Todo")
                            .forEach { (range, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    onSelectRange(range); expanded = false
                                })
                            }
                    }
                }
            }
        }
        item {
            when {
                progress.loading && progress.sessions.isEmpty() -> InlineLoading("Cargando progreso",
                    Modifier.testTag("exercise-progress-loading"))
                progress.historyError != null && progress.sessions.isEmpty() ->
                    Message("No se pudo cargar el progreso.", onRetryHistory)
                points.isEmpty() -> CompactEmpty(
                    title = "No hay datos de progreso todavía",
                    subtitle = "Tus sesiones aparecerán aquí cuando registres este ejercicio.",
                    modifier = Modifier.testTag("exercise-progress-empty"),
                )
                else -> {
                    val selected = requireNotNull(selectedPoint)
                    val value = formatChartMetric(BigDecimal.valueOf(selected.value), metric, units)
                    val date = selected.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                    val workoutId = selected.workoutId
                    val summaryModifier = if (workoutId != null) Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onOpenWorkout(workoutId) }
                    else Modifier
                    Row(summaryModifier
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$value, $date" +
                                if (workoutId != null) ", abrir entrenamiento" else ""
                        }
                        .testTag("exercise-progress-selected")
                        .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(value,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(date,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (workoutId != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(5.dp))
                    TemporalChart(points, valueLabel = { formatMetric(BigDecimal.valueOf(it), metric, units) },
                        style = TemporalChartStyle.Line, integerTicks = metric in integerMetrics,
                        selectedPointIndex = points.indexOf(selected),
                        onPointSelected = { index -> onSelectChartPoint(points[index].date) },
                        modifier = Modifier.fillMaxWidth().testTag("exercise-progress-chart"))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                metrics.forEach { choice ->
                    FilterChip(choice == metric, { onSelectMetric(choice) },
                        label = { Text(choice.label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(36.dp).testTag("exercise-metric-${choice.name}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        if (progress.hasMore && progress.sessions.isNotEmpty()) item {
            Column {
                Text("Gráfico basado en sesiones cargadas.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onLoadMore, Modifier.testTag("exercise-summary-more")) { Text("Cargar más sesiones") }
            }
        }
        item { SectionDivider() }
        item { SectionHeading("Récords personales", icon = Icons.Default.EmojiEvents) }
        item {
            when {
                progress.recordsLoading -> InlineLoading("Cargando récords")
                progress.recordsError != null -> Message("No se pudieron cargar los récords.", onRetryRecords)
                else -> RecordRows(detail.exerciseType, progress.records, progress, units)
            }
        }
        if (detail.exerciseType in weightRepTypes && progress.records?.bestWeightsForReps?.isNotEmpty() == true) {
            item { SectionDivider() }
            item { SectionHeading("Récords de serie") }
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("REPS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("MEJOR MARCA", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                progress.records.bestWeightsForReps.sortedBy { it.reps }.take(20).forEach {
                    RecordRow(it.reps.toString(), UnitConverter.formatWeight(it.weightKg, units.weight))
                }
            }
        }
    }
}

private val weightRepTypes = setOf(ExerciseType.WeightReps, ExerciseType.WeightedBodyweight, ExerciseType.AssistedBodyweight)
private val integerMetrics = setOf(ExerciseMetric.TotalReps, ExerciseMetric.BestSetReps,
    ExerciseMetric.MaximumDuration, ExerciseMetric.TotalDuration)

@Composable
private fun RecordRows(type: ExerciseType, records: PersonalRecords?, progress: ExerciseProgressUiState, units: UnitPreferences) {
    if (records == null) { Text("Aún no hay récords."); return }
    var shown = false
    if (type in weightRepTypes || type == ExerciseType.WeightDistance) records.maximumWeightKg?.let {
        RecordRow("Mayor peso", UnitConverter.formatWeight(it, units.weight)); shown = true
    }
    if (type == ExerciseType.AssistedBodyweight) records.minimumAssistanceKg?.let {
        RecordRow("Menor asistencia", UnitConverter.formatWeight(it, units.weight)); shown = true
    }
    if (type == ExerciseType.WeightReps || type == ExerciseType.WeightedBodyweight) {
        records.bestWeightsForReps.mapNotNull {
            estimatedOneRepMax(ActualPerformanceSet(0, it.reps, it.weightKg, null, null, null))
        }.maxOrNull()?.let {
            RecordRow("Mejor 1RM estimado", UnitConverter.formatWeight(it, units.weight)); shown = true
        }
        if (!progress.hasMore && !progress.loading && progress.historyError == null) {
            progress.sessions.flatMap { it.sets }.filter { it.weightKg != null && it.reps != null }
                .maxByOrNull { it.weightKg!!.multiply(it.reps!!.toBigDecimal()) }?.let {
                    RecordRow("Mejor volumen de serie",
                        "${UnitConverter.formatWeight(it.weightKg!!, units.weight)} × ${it.reps}"); shown = true
                }
            progress.sessions.mapNotNull { it.metricValue(ExerciseMetric.SessionVolume) }.maxOrNull()?.let {
                RecordRow("Mejor volumen de sesión", UnitConverter.formatVolume(it, units.weight)); shown = true
            }
        }
    }
    if (type in weightRepTypes || type == ExerciseType.BodyweightReps) records.maximumReps?.let {
        RecordRow("Más repeticiones en serie", it.toString()); shown = true
    }
    if (type == ExerciseType.Duration || type == ExerciseType.DistanceDuration) records.maximumDurationSeconds?.let {
        RecordRow("Mayor duración", formatDuration(it)); shown = true
    }
    if (type == ExerciseType.DistanceDuration || type == ExerciseType.WeightDistance) records.maximumDistanceMeters?.let {
        RecordRow("Mayor distancia", UnitConverter.formatDistance(it, units.distance)); shown = true
    }
    if (!shown) Text("Aún no hay récords.")
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.42f), fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
}

@Composable
private fun HistoryTab(progress: ExerciseProgressUiState, units: UnitPreferences,
    listState: LazyListState, onOpenWorkout: (String) -> Unit, onLoadMore: () -> Unit,
    onRetry: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().testTag("exercise-history"), state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            progress.loading && progress.sessions.isEmpty() -> item { InlineLoading("Cargando historial") }
            progress.historyError != null && progress.sessions.isEmpty() ->
                item { Message("No se pudo cargar el historial.", onRetry) }
            progress.sessions.isEmpty() ->
                item { CompactEmpty("No hay historial todavía",
                    "Cuando registres este ejercicio aparecerá aquí.",
                    Modifier.testTag("exercise-history-empty"), Icons.Default.History) }
        }
        items(progress.sessions, key = ExercisePerformanceSession::workoutId) { session ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onOpenWorkout(session.workoutId) }
                .testTag("exercise-history-${session.workoutId}")
                .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.completedAt.atZone(ZoneId.systemDefault()).format(HISTORY_DATE),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(session.exerciseNameSnapshot,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text("SERIE", Modifier.width(54.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("RESULTADO", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                session.sets.forEach { set ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(set.position.toString(), Modifier.width(54.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatSet(set, units), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (progress.loadingMore) item { InlineLoading("Cargando más sesiones") }
        else if (progress.historyError != null && progress.sessions.isNotEmpty()) item {
            Message("No se pudo cargar más historial.", onRetry)
        }
        else if (progress.hasMore && progress.sessions.isNotEmpty()) item {
            TextButton(onLoadMore, Modifier.fillMaxWidth().testTag("exercise-history-more")) { Text("Cargar más") }
        }
    }
}

private val HISTORY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
private fun formatSet(set: ActualPerformanceSet, units: UnitPreferences): String = buildList {
    set.weightKg?.let { add(UnitConverter.formatWeight(it, units.weight)) }
    set.reps?.let { add("$it reps") }
    set.durationSeconds?.let { add(formatDuration(it)) }
    set.distanceMeters?.let { add(UnitConverter.formatDistance(it, units.distance)) }
}.joinToString(" × ").ifBlank { "—" }

@Composable
private fun InstructionsTab(detail: ExerciseTemplateDetail, note: String?,
    mediaRenderer: ExerciseMediaRenderer, onOpenAttribution: (HttpsUrl) -> Boolean,
    onEditNote: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().testTag("exercise-instructions"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Demonstration(detail, mediaRenderer) }
        item { Text(detail.name, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        if (detail.instructions.isNotEmpty()) {
            item { SectionHeading("Indicaciones") }
            items(detail.instructions.sortedBy(ExerciseInstruction::position)) {
                val accent = MaterialTheme.colorScheme.primary
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = accent,
                        fontWeight = FontWeight.Bold)) { append("${it.position}. ") }
                    append(it.text)
                }, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            }
        }
        detail.description?.takeIf(String::isNotBlank)?.let { description ->
            item { SectionDivider() }
            item { SectionHeading("Descripción") }
            item { Text(description, style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        detail.media.selectDemonstrationMedia()?.attribution?.let { attribution ->
            item { SectionDivider() }
            item { SectionHeading("Información / Fuente") }
            item { Text(attribution.text, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            attribution.url?.let { url -> item {
                var failed by remember { mutableStateOf(false) }
                TextButton({ failed = !onOpenAttribution(url) }) { Text("Abrir fuente") }
                if (failed) Text(stringResource(R.string.exercise_media_attribution_open_error),
                    color = MaterialTheme.colorScheme.error)
            } }
        }
        item { SectionDivider() }
        item { SectionHeading("Notas del ejercicio") }
        item {
            if (note == null) Text("Añade una nota para verla cada vez que consultes este ejercicio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Text(note, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp)
            }
        }
        item { TextButton(onEditNote, Modifier.testTag("exercise-note-action")) {
            Text(if (note == null) "+ Añadir nota" else "Editar nota")
        } }
    }
}

@Composable
private fun Demonstration(detail: ExerciseTemplateDetail, mediaRenderer: ExerciseMediaRenderer) {
    Text("Demostración", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(9.dp))
    val media = detail.media.selectDemonstrationMedia()
    Box(Modifier.fillMaxWidth().aspectRatio(1.82f).clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surface)
        .testTag("exercise-demo"), contentAlignment = Alignment.Center) {
        if (media == null) Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ImageNotSupported, null, Modifier.size(31.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
            Text(stringResource(R.string.exercise_media_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else mediaRenderer(media, stringResource(R.string.exercise_media_content_description, detail.name),
            Modifier.fillMaxSize())
    }
}

@Composable
private fun ExerciseIdentity(detail: ExerciseTemplateDetail) {
    val resources = LocalContext.current.resources
    val primary = stringResource(detail.primaryMuscleGroup.labelResource())
    val secondary = detail.secondaryMuscleGroups.joinToString { resources.getString(it.labelResource()) }
    val accent = MaterialTheme.colorScheme.primary
    Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        maxLines = 3, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(3.dp))
    Text(buildAnnotatedString {
        append("Primario  ")
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(primary)
        }
        if (secondary.isNotEmpty()) append("    ·    Secundario  $secondary")
    }, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(5.dp))
    Text("${stringResource(detail.equipment.labelResource())} · ${stringResource(detail.exerciseType.labelResource())}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (detail.archived) Text(stringResource(R.string.exercise_archived_badge), color = MaterialTheme.colorScheme.error)
}

private fun formatMetric(value: BigDecimal, metric: ExerciseMetric, units: UnitPreferences): String = when (metric) {
    ExerciseMetric.MaximumWeight, ExerciseMetric.EstimatedOneRepMax,
    ExerciseMetric.SessionVolume, ExerciseMetric.MinimumAssistance -> UnitConverter.formatWeight(value, units.weight)
    ExerciseMetric.MaximumDistance, ExerciseMetric.TotalDistance -> UnitConverter.formatDistance(value, units.distance)
    ExerciseMetric.MaximumDuration, ExerciseMetric.TotalDuration -> formatDuration(value.toInt())
    else -> value.stripTrailingZeros().toPlainString()
}

private fun formatChartMetric(value: BigDecimal, metric: ExerciseMetric, units: UnitPreferences): String {
    val formatted = formatMetric(value, metric, units)
    return if (metric == ExerciseMetric.TotalReps || metric == ExerciseMetric.BestSetReps) "$formatted reps"
        else formatted
}

private fun formatDuration(seconds: Int): String = if (seconds >= 3600)
    "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
else "%02d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun SectionHeading(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.semantics { heading() }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        thickness = 1.dp)
}

@Composable
private fun InlineLoading(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
        Text(label, Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactEmpty(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.History,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Message(message: String, retry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (retry != null) TextButton(retry) { Text("Reintentar") }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseDetailPreview() {
    GYmAppTheme {
        ExerciseDetailScreen(
            state = ExerciseDetailUiState.Content(ExerciseTemplateDocument(
                ExerciseTemplateDetail("preview", "press-banca", "Press de banca", "Ejercicio de empuje.",
                    MuscleGroup.Chest, listOf(MuscleGroup.Triceps), Equipment.Barbell,
                    ExerciseType.WeightReps, MovementPattern.HorizontalPush, emptyList()),
                requireNotNull(ExerciseTemplateEtag.fromVersion(0)))),
            onBack = {}, onRetry = {}, mediaRenderer = { _, _, _ -> }, onOpenAttribution = { false })
    }
}
