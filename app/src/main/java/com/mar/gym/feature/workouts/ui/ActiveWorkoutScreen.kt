package com.mar.gym.feature.workouts.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.routines.ui.SheetAction
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutExerciseDraft
import com.mar.gym.feature.workouts.model.WorkoutSetDraft
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.elapsedWorkoutSeconds
import com.mar.gym.feature.workouts.model.formatPreviousPerformance
import com.mar.gym.feature.workouts.model.previousSetFor
import com.mar.gym.feature.workouts.rest.RestTimer
import com.mar.gym.ui.components.ExerciseNameLink
import com.mar.gym.ui.components.ExerciseThumbnail
import com.mar.gym.ui.components.LoadingState
import com.mar.gym.ui.components.MetricCell
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.RestTimePickerButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import com.mar.gym.ui.theme.CompletedRowAccentDark
import com.mar.gym.ui.theme.CompletedRowAccentLight
import com.mar.gym.ui.theme.CompletedRowContainerDark
import com.mar.gym.ui.theme.CompletedRowContainerLight
import com.mar.gym.ui.theme.SetDrop
import com.mar.gym.ui.theme.SetFailure
import com.mar.gym.ui.theme.SetWarmup
import com.mar.gym.ui.theme.InkDarkSurface
import com.mar.gym.ui.theme.InkDarkSurfaceVariant
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActiveWorkoutRoute(
    viewModel: ActiveWorkoutViewModel,
    onBack: () -> Unit,
    onOpenSaveWorkout: () -> Unit,
    onOpenPicker: (Set<String>) -> Unit,
    onOpenReplacementPicker: (String) -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val restTimer by viewModel.restTimer.collectAsState()
    RestTimerNotificationPermissionEffect(
        timerActive = restTimer != null,
        onPermissionGranted = viewModel::refreshRestTimerNotification,
    )
    ActiveWorkoutScreen(
        state = state,
        clock = viewModel.clock,
        restTimer = restTimer,
        onBack = onBack,
        onOpenExercise = onOpenExercise,
        onOpenPicker = {
            onOpenPicker(state.data.draft?.exercises?.map { it.exerciseTemplateId }?.toSet().orEmpty())
        },
        onOpenReplacementPicker = onOpenReplacementPicker,
        onStartEmpty = viewModel::startEmpty,
        onUpdateTitle = viewModel::updateTitle,
        onUpdateNotes = viewModel::updateNotes,
        onRemoveExercise = viewModel::removeExercise,
        onMoveExercise = viewModel::moveExercise,
        onReorderExercises = viewModel::reorderExercises,
        onGroupWithAdjacent = viewModel::groupWithAdjacent,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
        onDissolveSuperset = viewModel::dissolveSuperset,
        onUpdateExercise = viewModel::updateExercise,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::removeSet,
        onMoveSet = viewModel::moveSet,
        onUpdateSet = viewModel::updateSet,
        onSave = viewModel::save,
        onFinish = onOpenSaveWorkout,
        onDiscard = viewModel::discard,
        onReload = viewModel::reloadDiscardingLocalChanges,
        onRetry = viewModel::retry,
        onRetryPrevious = viewModel::retryPreviousPerformance,
        onAdjustRestTimer = viewModel::adjustRestTimerSeconds,
        onSkipRestTimer = viewModel::skipRestTimer,
        manualClockState = viewModel.manualClockState,
    )
}

@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    clock: Clock,
    onBack: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenReplacementPicker: (String) -> Unit = {},
    onStartEmpty: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onReorderExercises: (List<String>) -> Unit = { _ -> },
    onGroupWithAdjacent: (String, Int) -> Unit = { _, _ -> },
    onRemoveFromSuperset: (String) -> Unit = {},
    onDissolveSuperset: (String) -> Unit = {},
    onUpdateExercise: (String, (WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    onSave: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onReload: () -> Unit,
    onRetry: () -> Unit,
    onRetryPrevious: () -> Unit = {},
    onOpenExercise: (String) -> Unit = {},
    restTimer: RestTimer? = null,
    onAdjustRestTimer: (Int) -> Unit = {},
    onSkipRestTimer: () -> Unit = {},
    manualClockState: ManualWorkoutClockState? = null,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var reorderOpen by remember { mutableStateOf(false) }
    var clockSheetOpen by remember { mutableStateOf(false) }
    val clockState = manualClockState ?: remember(clock) { ManualWorkoutClockState(clock) }
    val requestBack = { if (state.data.hasUnsavedChanges) confirmExit = true else onBack() }
    val editorEnabled = state is ActiveWorkoutUiState.Active && !state.data.addingExercises
    BackHandler(onBack = requestBack)
    Scaffold(
        topBar = {
            Column {
                ActiveWorkoutHeader(
                    onBack = requestBack,
                    onOpenClock = { clockSheetOpen = true },
                    onFinish = onFinish,
                    actionsEnabled = editorEnabled,
                )
                state.data.draft?.let { draft ->
                    WorkoutProgressBar(
                        progress = draft.completedSetsProgress,
                        modifier = Modifier.testTag("workout_progress"),
                    )
                }
            }
        },
        bottomBar = {
            restTimer?.let { timer ->
                ActiveRestTimerDock(timer, clock, onAdjustRestTimer, onSkipRestTimer)
            }
        },
        containerColor = Color.Black,
        contentColor = Color.White,
    ) { padding ->
        when (state) {
            is ActiveWorkoutUiState.Loading -> LoadingState(
                message = stringResource(R.string.workout_loading),
                modifier = Modifier.padding(padding),
            )
            is ActiveWorkoutUiState.NoActiveWorkout -> NoActiveWorkout(
                onStart = onStartEmpty,
                modifier = Modifier.padding(padding),
            )
            is ActiveWorkoutUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WorkoutError(state.error)
                PrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.data.draft?.let { draft ->
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = false,
                        onOpenPicker, onOpenReplacementPicker,
                        onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        { reorderOpen = true },
                        onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmDiscard = true },
                        onRetryPrevious,
                        onOpenExercise,
                    )
                }
            }
            else -> {
                val draft = state.data.draft
                if (draft == null) LoadingState(
                    message = stringResource(R.string.workout_loading),
                    modifier = Modifier.padding(padding),
                ) else Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state) {
                        is ActiveWorkoutUiState.Saving -> OperationStatus(R.string.workout_saving)
                        is ActiveWorkoutUiState.Completing -> OperationStatus(R.string.workout_completing)
                        is ActiveWorkoutUiState.Discarding -> OperationStatus(R.string.workout_discarding)
                        is ActiveWorkoutUiState.Conflict -> {
                            Text(
                                stringResource(R.string.workout_conflict_title),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
                            )
                            Text(
                                stringResource(R.string.workout_conflict_message),
                                modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
                            )
                            if (state.data.hasUnsavedChanges) {
                                Text(
                                    stringResource(R.string.workout_conflict_dirty_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
                                )
                            }
                            PrimaryButton(
                                text = stringResource(R.string.workout_reload_server),
                                onClick = onReload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = WorkoutContentHorizontalPadding),
                            )
                        }
                        else -> Unit
                    }
                    WorkoutEditorContent(
                        draft, state.data, clock, enabled = editorEnabled,
                        onOpenPicker, onOpenReplacementPicker,
                        onUpdateTitle, onUpdateNotes, onRemoveExercise, onMoveExercise,
                        { reorderOpen = true },
                        onGroupWithAdjacent, onRemoveFromSuperset, onDissolveSuperset,
                        onUpdateExercise, onAddSet, onRemoveSet, onMoveSet, onUpdateSet,
                        onSave, { confirmDiscard = true },
                        onRetryPrevious,
                        onOpenExercise,
                    )
                }
            }
        }
    }
    if (confirmDiscard) ConfirmDialog(
        title = R.string.workout_discard_confirm_title,
        message = R.string.workout_discard_confirm_message,
        action = R.string.workout_discard,
        onDismiss = { confirmDiscard = false },
        onConfirm = { confirmDiscard = false; onDiscard() },
    )
    if (confirmExit) ConfirmDialog(
        title = R.string.workout_exit_confirm_title,
        message = R.string.workout_exit_confirm_message,
        action = R.string.workout_exit_without_saving,
        onDismiss = { confirmExit = false },
        onConfirm = onBack,
    )
    val reorderDraft = state.data.draft
    if (reorderOpen && reorderDraft != null) {
        WorkoutReorderExercisesDialog(
            exercises = reorderDraft.exercises,
            onClose = { reorderOpen = false },
            onApply = onReorderExercises,
        )
    }
    if (clockSheetOpen) {
        ManualWorkoutClockSheet(
            state = clockState,
            onDismiss = { clockSheetOpen = false },
        )
    }
}

@Composable
private fun RestTimerNotificationPermissionEffect(
    timerActive: Boolean,
    onPermissionGranted: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onPermissionGranted()
    }
    LaunchedEffect(timerActive) {
        if (
            timerActive &&
            !requested &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requested = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ActiveRestTimerDock(
    timer: RestTimer,
    clock: Clock,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    var refresh by remember(timer.deadline) { mutableLongStateOf(0L) }
    val remainingMillis = remember(timer.deadline, clock, refresh) {
        timer.remainingMillis(clock.instant())
    }
    val progress = timerProgress(
        remainingMillis = remainingMillis,
        configuredMillis = timer.configuredDurationSeconds * 1_000L,
    )
    LaunchedEffect(timer.deadline, clock) {
        while (timer.remainingMillis(clock.instant()) > 0L) {
            delay(250L)
            refresh += 1L
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(InkDarkSurface)
            .navigationBarsPadding()
            .testTag("active_rest_timer"),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .testTag("active_rest_timer_progress"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RestTimerAdjustmentButton(
                text = stringResource(R.string.rest_timer_minus_fifteen_compact),
                onClick = { onAdjust(-15) },
                modifier = Modifier.testTag("active_rest_timer_minus"),
            )
            Text(
                text = formatRestTimer(remainingMillis),
                modifier = Modifier
                    .weight(1f)
                    .testTag("active_rest_timer_remaining"),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            RestTimerAdjustmentButton(
                text = stringResource(R.string.rest_timer_plus_fifteen_compact),
                onClick = { onAdjust(15) },
                modifier = Modifier.testTag("active_rest_timer_plus"),
            )
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("active_rest_timer_skip"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(stringResource(R.string.rest_timer_skip), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RestTimerAdjustmentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .width(60.dp)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = InkDarkSurfaceVariant,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutHeader(
    onBack: () -> Unit,
    onOpenClock: () -> Unit,
    onFinish: () -> Unit,
    actionsEnabled: Boolean,
) {
    TopAppBar(
        modifier = Modifier.testTag("active_workout_header"),
        title = {
            Text(
                text = stringResource(R.string.workout_header_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.routine_back),
                    tint = Color.White,
                )
            }
        },
        actions = {
            IconButton(
                onClick = onOpenClock,
                enabled = actionsEnabled,
                modifier = Modifier.testTag("manual_clock_open"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = stringResource(R.string.workout_clock_open),
                    tint = Color.White,
                )
            }
            Button(
                onClick = onFinish,
                enabled = actionsEnabled,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(40.dp)
                    .testTag("complete_workout"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    disabledContentColor = Color.White.copy(alpha = 0.65f),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    text = stringResource(R.string.workout_finish),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ActiveWorkoutHeaderBackground,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualWorkoutClockSheet(
    state: ManualWorkoutClockState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ManualClockBackground,
        contentColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("manual_clock_sheet"),
        ) {
            Text(
                text = stringResource(R.string.workout_clock_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.65f))
            Row(Modifier.fillMaxWidth()) {
                ManualClockTab(
                    text = stringResource(R.string.workout_clock_timer),
                    selected = pagerState.currentPage == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                )
                ManualClockTab(
                    text = stringResource(R.string.workout_clock_stopwatch),
                    selected = pagerState.currentPage == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .testTag("manual_clock_pager"),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ManualTimerPage(state)
                    else -> ManualStopwatchPage(state)
                }
            }
        }
    }
}

@Composable
private fun ManualClockTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(bottom = 12.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun ManualTimerPage(state: ManualWorkoutClockState) {
    val revision by state.revision.collectAsState()
    var refresh by remember { mutableLongStateOf(0L) }
    val snapshot = remember(revision, refresh) { state.snapshot() }
    LaunchedEffect(snapshot.timerRunning) {
        if (snapshot.timerRunning) {
            while (state.snapshot().timerRunning) {
                delay(100L)
                refresh += 1L
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .testTag("manual_timer_ring"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { timerProgress(snapshot.timerRemainingMillis, snapshot.timerConfiguredMillis) },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 6.dp,
            )
            Text(
                text = formatTimer(snapshot.timerRemainingMillis),
                modifier = Modifier.testTag("manual_timer_value"),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerAdjustmentButton(
                text = stringResource(R.string.workout_clock_minus_fifteen),
                testTag = "manual_timer_minus",
                onClick = { state.adjustTimerSeconds(-15L) },
            )
            TimerAdjustmentButton(
                text = stringResource(R.string.workout_clock_plus_fifteen),
                testTag = "manual_timer_plus",
                onClick = { state.adjustTimerSeconds(15L) },
            )
        }
        ClockActionButton(
            text = stringResource(
                if (snapshot.timerRunning) {
                    R.string.workout_clock_cancel
                } else {
                    R.string.workout_clock_start
                },
            ),
            onClick = if (snapshot.timerRunning) state::cancelTimer else state::startTimer,
            blue = !snapshot.timerRunning,
            enabled = if (snapshot.timerRunning) true else snapshot.timerRemainingMillis > 0L,
            modifier = Modifier.testTag("manual_timer_start"),
        )
    }
}

@Composable
private fun TimerAdjustmentButton(
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ManualStopwatchPage(state: ManualWorkoutClockState) {
    val revision by state.revision.collectAsState()
    var refresh by remember { mutableLongStateOf(0L) }
    val snapshot = remember(revision, refresh) { state.snapshot() }
    LaunchedEffect(snapshot.stopwatchStatus) {
        if (snapshot.stopwatchStatus == StopwatchStatus.Running) {
            while (state.snapshot().stopwatchStatus == StopwatchStatus.Running) {
                delay(16L)
                refresh += 1L
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatStopwatch(snapshot.stopwatchElapsedMillis),
            modifier = Modifier.testTag("manual_stopwatch_value"),
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
        )
        when (snapshot.stopwatchStatus) {
            StopwatchStatus.Initial -> ClockActionButton(
                text = stringResource(R.string.workout_clock_start),
                onClick = state::startStopwatch,
                blue = true,
                modifier = Modifier.testTag("manual_stopwatch_start"),
            )
            StopwatchStatus.Running -> ClockActionButton(
                text = stringResource(R.string.workout_clock_stop),
                onClick = state::stopStopwatch,
                blue = false,
                modifier = Modifier.testTag("manual_stopwatch_stop"),
            )
            StopwatchStatus.Stopped -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClockActionButton(
                    text = stringResource(R.string.workout_clock_reset),
                    onClick = state::resetStopwatch,
                    blue = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_stopwatch_reset"),
                )
                ClockActionButton(
                    text = stringResource(R.string.workout_clock_start),
                    onClick = state::startStopwatch,
                    blue = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_stopwatch_resume"),
                )
            }
        }
    }
}

@Composable
private fun ClockActionButton(
    text: String,
    onClick: () -> Unit,
    blue: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (blue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = Color.White,
            disabledContainerColor = if (blue) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            disabledContentColor = Color.White.copy(alpha = 0.55f),
        ),
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

internal fun formatTimer(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1_000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal fun formatRestTimer(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal fun timerProgress(remainingMillis: Long, configuredMillis: Long): Float =
    if (configuredMillis > 0L) {
        (remainingMillis.toFloat() / configuredMillis).coerceIn(0f, 1f)
    } else 0f

internal fun formatStopwatch(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    return "%02d:%02d.%03d".format(totalSeconds / 60L, totalSeconds % 60L, safe % 1_000L)
}

internal val ActiveWorkoutHeaderBackground = Color(0xFF242424)
private val ActiveWorkoutProgressTrack = Color(0xFF333333)
private val ManualClockBackground = Color(0xFF121212)

@Composable
private fun WorkoutProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(ActiveWorkoutProgressTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun WorkoutEditorContent(
    draft: WorkoutDraft,
    data: ActiveWorkoutData,
    clock: Clock,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onOpenReplacementPicker: (String) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onRemoveExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onReorder: () -> Unit,
    onGroupWithAdjacent: (String, Int) -> Unit,
    onRemoveFromSuperset: (String) -> Unit,
    onDissolveSuperset: (String) -> Unit,
    onUpdateExercise: (String, (WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: (String) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onUpdateSet: (String, String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRetryPrevious: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WorkoutContentHorizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.startedAt?.let { WorkoutElapsed(it, clock) }
        if (data.hasUnsavedChanges) {
            Text(
                text = stringResource(R.string.workout_unsaved_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (data.previousPerformanceLoading) {
        Text(
            "Cargando rendimiento anterior…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
        )
    }
    if (data.previousPerformanceError != null) {
        Row(
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "No se pudo cargar ANTERIOR.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetryPrevious) { Text(stringResource(R.string.retry)) }
        }
    }
    if (draft.exercises.isEmpty()) {
        Text(
            stringResource(R.string.workout_no_exercises),
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
        )
    }
    draft.exercises.forEachIndexed { index, exercise ->
        WorkoutExerciseEditor(
            exercise, index, draft.exercises.size, data.fieldErrors, enabled,
            previousSupersetLocalId = draft.exercises.getOrNull(index - 1)?.supersetLocalId,
            nextSupersetLocalId = draft.exercises.getOrNull(index + 1)?.supersetLocalId,
            supersetOrdinal = draft.supersetOrdinal(exercise.localId),
            onOpenExercise = { onOpenExercise(exercise.exerciseTemplateId) },
            onReorder = onReorder,
            onReplace = { onOpenReplacementPicker(exercise.localId) },
            onRemove = { onRemoveExercise(exercise.localId) },
            onGroupWithAdjacent = { onGroupWithAdjacent(exercise.localId, it) },
            onRemoveFromSuperset = { onRemoveFromSuperset(exercise.localId) },
            onDissolveSuperset = { onDissolveSuperset(exercise.localId) },
            onUpdate = { transform -> onUpdateExercise(exercise.localId, transform) },
            onAddSet = { onAddSet(exercise.localId) },
            onRemoveSet = { onRemoveSet(exercise.localId, it) },
            onUpdateSet = { setId, transform -> onUpdateSet(exercise.localId, setId, transform) },
            previousValue = { setId ->
                formatPreviousPerformance(
                    exercise.exerciseTypeSnapshot,
                    previousSetFor(draft, data.previousPerformance, exercise.localId, setId),
                )
            },
        )
    }
    PrimaryButton(
        text = stringResource(R.string.workout_add_exercises),
        onClick = onOpenPicker,
        enabled = enabled && draft.exercises.size < WorkoutDraft.MAX_EXERCISES,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WorkoutContentHorizontalPadding),
    )
    if (data.addingExercises) OperationStatus(R.string.workout_adding_exercises)
    if (data.hasUnsavedChanges) {
        PrimaryButton(
            text = stringResource(R.string.workout_save),
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutContentHorizontalPadding)
                .testTag("save_workout"),
        )
    }
    SecondaryButton(
        text = stringResource(R.string.workout_discard),
        onClick = onDiscard,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WorkoutContentHorizontalPadding),
    )
    Spacer(Modifier.height(24.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutExerciseEditor(
    exercise: WorkoutExerciseDraft,
    index: Int,
    count: Int,
    errors: Map<String, String>,
    enabled: Boolean,
    previousSupersetLocalId: String?,
    nextSupersetLocalId: String?,
    supersetOrdinal: Int?,
    onOpenExercise: () -> Unit,
    onReorder: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onGroupWithAdjacent: (Int) -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    onUpdate: ((WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (String) -> Unit,
    onUpdateSet: (String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previousValue: (String) -> String,
) {
    val prefix = "exercise.${exercise.localId}"
    var showActions by remember { mutableStateOf(false) }
    var showSupersetActions by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("workout_exercise_${exercise.localId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutContentHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ExerciseThumbnail()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseNameLink(
                            name = exercise.exerciseNameSnapshot,
                            onClick = onOpenExercise,
                            onLongClick = onReorder,
                        )
                        supersetOrdinal?.let { ordinal ->
                            Text(
                                stringResource(R.string.superset_badge, ordinal),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .testTag("workout_superset_${exercise.localId}"),
                            )
                        }
                    }
                    Text(
                        text = stringResource(exercise.exerciseTypeSnapshot.labelResource()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showActions = true },
                    enabled = enabled,
                    modifier = Modifier.testTag("workout_exercise_menu_${exercise.localId}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(
                            R.string.workout_exercise_menu,
                            exercise.exerciseNameSnapshot,
                        ),
                    )
                }
            }
            WorkoutTextField(
                value = exercise.notes,
                onValueChange = { value -> onUpdate { it.copy(notes = value) } },
                label = R.string.workout_exercise_notes_hint,
                error = errors["$prefix.notes"],
                enabled = enabled,
                singleLine = false,
            )
            RestTimePickerButton(
                restSeconds = exercise.restSeconds,
                onConfirm = { value -> onUpdate { it.copy(restSeconds = value) } },
                enabled = enabled,
                testTag = "workout_rest_${exercise.localId}",
                errorMessage = errors["$prefix.restSeconds"]?.let { workoutValidationMessage(it) },
                ghost = true,
            )
        }
        if (exercise.sets.isNotEmpty()) {
            WorkoutSetTable(
                exercise = exercise,
                prefix = prefix,
                errors = errors,
                enabled = enabled,
                onRemoveSet = onRemoveSet,
                onUpdateSet = onUpdateSet,
                previousValue = previousValue,
            )
        }
        TextButton(
            onClick = onAddSet,
            enabled = enabled && exercise.sets.size < 20,
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
        ) {
            Text(stringResource(R.string.workout_add_set))
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
        )
    }
    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            modifier = Modifier.testTag("workout_exercise_actions_${exercise.localId}"),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.workout_exercise_action_reorder),
                    enabled = enabled,
                    modifier = Modifier.testTag("workout_exercise_reorder_action_${exercise.localId}"),
                    onClick = { showActions = false; onReorder() },
                )
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.workout_exercise_action_replace),
                    enabled = enabled,
                    modifier = Modifier.testTag("workout_exercise_replace_action_${exercise.localId}"),
                    onClick = { showActions = false; onReplace() },
                )
                WorkoutExerciseSheetAction(
                    text = stringResource(
                        if (exercise.supersetLocalId == null) {
                            R.string.workout_exercise_action_add_superset
                        } else {
                            R.string.workout_exercise_action_edit_superset
                        },
                    ),
                    enabled = enabled && (count > 1 || exercise.supersetLocalId != null),
                    modifier = Modifier.testTag("workout_exercise_superset_action_${exercise.localId}"),
                    onClick = { showActions = false; showSupersetActions = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                WorkoutExerciseSheetAction(
                    text = stringResource(R.string.routine_remove_exercise),
                    enabled = enabled,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("workout_exercise_delete_action_${exercise.localId}"),
                    onClick = { showActions = false; onRemove() },
                )
            }
        }
    }
    if (showSupersetActions) {
        ModalBottomSheet(
            onDismissRequest = { showSupersetActions = false },
            modifier = Modifier.testTag("workout_exercise_superset_actions_${exercise.localId}"),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (index > 0 && (exercise.supersetLocalId == null || previousSupersetLocalId == null)) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_group_previous),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_group_previous_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onGroupWithAdjacent(-1) },
                    )
                }
                if (index < count - 1 && (exercise.supersetLocalId == null || nextSupersetLocalId == null)) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_group_next),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_group_next_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onGroupWithAdjacent(1) },
                    )
                }
                if (exercise.supersetLocalId != null) {
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_remove_member),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_superset_remove_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onRemoveFromSuperset() },
                    )
                    WorkoutExerciseSheetAction(
                        text = stringResource(R.string.superset_dissolve),
                        enabled = enabled,
                        modifier = Modifier.testTag("workout_superset_dissolve_${exercise.localId}"),
                        onClick = { showSupersetActions = false; onDissolveSuperset() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseSheetAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private data class MetricColumn(
    val header: String,
    val value: (WorkoutSetDraft) -> String,
    val update: (WorkoutSetDraft, String) -> WorkoutSetDraft,
    val errorKey: (String) -> String,
    val keyboardType: KeyboardType,
)

@Composable
private fun WorkoutExerciseDraft.metricColumns(): List<MetricColumn> = buildList {
    val type = exerciseTypeSnapshot
    if (type.supportsWeight()) add(
        MetricColumn(
            header = stringResource(
                when (type) {
                    ExerciseType.WeightedBodyweight -> R.string.workout_metric_lastre
                    ExerciseType.AssistedBodyweight -> R.string.workout_metric_asistencia
                    else -> R.string.workout_metric_kg
                }
            ),
            value = { it.weight },
            update = { set, v -> set.copy(weight = v) },
            errorKey = { p -> "$p.weight" },
            keyboardType = KeyboardType.Decimal,
        )
    )
    if (type.supportsRepetitions()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_reps),
            value = { it.reps },
            update = { set, v -> set.copy(reps = v) },
            errorKey = { p -> "$p.reps" },
            keyboardType = KeyboardType.Number,
        )
    )
    if (type.supportsDuration()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_time),
            value = { it.durationSeconds },
            update = { set, v -> set.copy(durationSeconds = v) },
            errorKey = { p -> "$p.durationSeconds" },
            keyboardType = KeyboardType.Number,
        )
    )
    if (type.supportsDistance()) add(
        MetricColumn(
            header = stringResource(R.string.workout_metric_distance),
            value = { it.distanceMeters },
            update = { set, v -> set.copy(distanceMeters = v) },
            errorKey = { p -> "$p.distanceMeters" },
            keyboardType = KeyboardType.Decimal,
        )
    )
}

@Composable
private fun WorkoutSetTable(
    exercise: WorkoutExerciseDraft,
    prefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemoveSet: (String) -> Unit,
    onUpdateSet: (String, (WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previousValue: (String) -> String,
) {
    val columns = exercise.metricColumns()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutSetRowContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TableHeaderCell(
                stringResource(R.string.workout_series_header),
                modifier = Modifier.width(SetTypeColumnWidth),
            )
            TableHeaderCell(stringResource(R.string.workout_previous_header), modifier = Modifier.weight(1.15f))
            columns.forEach { column ->
                TableHeaderCell(column.header, modifier = Modifier.weight(1f))
            }
            TableHeaderCell(
                "✓",
                modifier = Modifier
                    .width(48.dp)
                    .testTag("set_complete_header"),
            )
        }
        exercise.sets.forEachIndexed { setIndex, set ->
            WorkoutSetRow(
                set = set,
                index = setIndex,
                columns = columns,
                prefix = "$prefix.set.${set.localId}",
                errors = errors,
                enabled = enabled,
                onRemove = { onRemoveSet(set.localId) },
                onUpdate = { transform -> onUpdateSet(set.localId, transform) },
                previous = previousValue(set.localId),
            )
        }
    }
}

@Composable
private fun WorkoutSetRow(
    set: WorkoutSetDraft,
    index: Int,
    columns: List<MetricColumn>,
    prefix: String,
    errors: Map<String, String>,
    enabled: Boolean,
    onRemove: () -> Unit,
    onUpdate: ((WorkoutSetDraft) -> WorkoutSetDraft) -> Unit,
    previous: String,
) {
    val rowBackground by animateColorAsState(
        targetValue = if (set.completed) completedRowContainer() else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "completed_row_background",
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("set_row_background_${set.localId}")
                .background(rowBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WorkoutSetRowContentPadding, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SetTypeCell(
                    type = set.setType,
                    index = index,
                    enabled = enabled,
                    onSelect = { value -> onUpdate { it.copy(setType = value) } },
                    onRemove = onRemove,
                    modifier = Modifier.width(SetTypeColumnWidth),
                )
                Box(
                    modifier = Modifier.weight(1.15f).padding(horizontal = 2.dp).testTag("previous_${set.localId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = previous,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                columns.forEach { column ->
                    MetricCell(
                        value = column.value(set),
                        onValueChange = { value -> onUpdate { column.update(it, value) } },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        keyboardType = column.keyboardType,
                        enabled = enabled,
                        isError = errors.containsKey(column.errorKey(prefix)),
                        contentDescription = "${column.header} ${index + 1}",
                        testTag = "${column.errorKey(prefix)}_${set.localId}",
                        ghost = true,
                    )
                }
                SetCompleteToggle(
                    completed = set.completed,
                    enabled = enabled,
                    onToggle = { value -> onUpdate { it.copy(completed = value) } },
                    testTag = "set_complete_tick_${set.localId}",
                    toggleTag = "set_complete_${set.localId}",
                )
            }
        }
        errors["$prefix.completed"]?.let {
            Text(
                text = stringResource(R.string.workout_error_completed_metrics),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
        )
    }
}

@Composable
private fun completedRowContainer(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) CompletedRowContainerDark else CompletedRowContainerLight
}

@Composable
private fun completedRowAccent(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) CompletedRowAccentDark else CompletedRowAccentLight
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val WorkoutContentHorizontalPadding = 16.dp
private val WorkoutSetRowContentPadding = WorkoutContentHorizontalPadding + 4.dp
private val SetTypeColumnWidth = 38.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTypeCell(
    type: SetType,
    index: Int,
    enabled: Boolean,
    onSelect: (SetType) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    val color = when (type) {
        SetType.Normal -> Color.White
        SetType.Warmup -> SetWarmup
        SetType.Failure -> SetFailure
        SetType.Drop -> SetDrop
    }
    val label = when (type) {
        SetType.Normal -> (index + 1).toString()
        SetType.Warmup -> "W"
        SetType.Failure -> "F"
        SetType.Drop -> "D"
    }
    val description = stringResource(
        R.string.workout_set_cell_description,
        index + 1,
        stringResource(type.cellLabelResource()),
    )
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled) { showSheet = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                SheetAction(
                    text = stringResource(R.string.workout_set_normal),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Normal) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_warmup),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Warmup) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_failure),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Failure) },
                )
                SheetAction(
                    text = stringResource(R.string.workout_set_drop),
                    enabled = enabled,
                    onClick = { showSheet = false; onSelect(SetType.Drop) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SheetAction(
                    text = stringResource(R.string.workout_set_delete),
                    enabled = enabled,
                    onClick = { showSheet = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun SetCompleteToggle(
    completed: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    testTag: String? = null,
    toggleTag: String? = null,
) {
    val description = stringResource(
        if (completed) R.string.workout_uncheck_set else R.string.workout_check_set
    )
    val accent = completedRowAccent()
    val ringColor = if (completed) {
        accent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val checkColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(if (toggleTag != null) Modifier.testTag(toggleTag) else Modifier)
            .semantics { contentDescription = description }
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onToggle(!completed) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (completed) accent else Color.Transparent)
                .border(2.dp, ringColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = completed,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                    tint = checkColor,
                )
            }
        }
    }
}

private fun SetType.cellLabelResource() = when (this) {
    SetType.Normal -> R.string.workout_set_type_normal
    SetType.Warmup -> R.string.workout_set_type_warmup
    SetType.Failure -> R.string.workout_set_type_failure
    SetType.Drop -> R.string.workout_set_type_drop
}

@Composable
private fun WorkoutTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .onFocusChanged { focused = it.isFocused }
                .padding(vertical = 10.dp),
            singleLine = singleLine,
            textStyle = style.copy(
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            ),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty() && !focused) {
                        Text(
                            text = stringResource(label),
                            style = style.copy(color = Color.White.copy(alpha = 0.45f)),
                        )
                    }
                    innerTextField()
                }
            },
        )
        error?.let {
            Text(
                text = workoutValidationMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun workoutValidationMessage(code: String): String = stringResource(
    when (code) {
        "workout_error_title_length" -> R.string.workout_error_title_length
        "workout_error_workout_notes_length" -> R.string.workout_error_workout_notes_length
        "workout_error_exercise_notes_length" -> R.string.workout_error_exercise_notes_length
        "workout_error_rest_range" -> R.string.workout_error_rest_range
        "workout_error_incompatible_metric" -> R.string.workout_error_incompatible_metric
        "workout_error_completed_metrics" -> R.string.workout_error_completed_metrics
        "workout_error_invalid_superset" -> R.string.workout_error_invalid_superset
        else -> R.string.workout_error_number_range
    },
)

@Composable
private fun WorkoutElapsed(startedAt: Instant, clock: Clock) {
    var elapsed by remember(startedAt, clock) { mutableLongStateOf(elapsedWorkoutSeconds(startedAt, clock)) }
    LaunchedEffect(startedAt, clock) {
        while (true) {
            elapsed = elapsedWorkoutSeconds(startedAt, clock)
            delay(1_000)
        }
    }
    Text(stringResource(R.string.workout_elapsed, formatDuration(elapsed)), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NoActiveWorkout(onStart: () -> Unit, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(stringResource(R.string.workout_no_active_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.workout_no_active_message))
    PrimaryButton(
        text = stringResource(R.string.workout_start_empty),
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OperationStatus(text: Int) = Row(
    modifier = Modifier.padding(horizontal = WorkoutContentHorizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    CircularProgressIndicator()
    Text(stringResource(text))
}

@Composable
private fun WorkoutError(error: WorkoutUiError) {
    Text(stringResource(R.string.workout_error_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
    Text(stringResource(error.messageResource()))
    error.correlationId?.let { Text(stringResource(R.string.correlation_id, it)) }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    message: Int,
    action: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = { Text(stringResource(message)) },
    confirmButton = { Button(onClick = onConfirm) { Text(stringResource(action)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.routine_cancel)) } },
)

internal fun targetSummary(set: WorkoutSetDraft): String = buildList {
    val target = set.targets
    if (target.targetWeight != null) add("${target.targetWeight.stripTrailingZeros().toPlainString()} kg")
    if (target.targetRepsMin != null || target.targetRepsMax != null) {
        add(when {
            target.targetRepsMin != null && target.targetRepsMax != null && target.targetRepsMin != target.targetRepsMax ->
                "${target.targetRepsMin}–${target.targetRepsMax} reps"
            else -> "${target.targetRepsMin ?: target.targetRepsMax} reps"
        })
    }
    if (target.targetDurationSeconds != null) add("${target.targetDurationSeconds} s")
    if (target.targetDistanceMeters != null) add("${target.targetDistanceMeters.stripTrailingZeros().toPlainString()} m")
    if (target.targetRpe != null) add("RPE ${target.targetRpe.stripTrailingZeros().toPlainString()}")
}.joinToString(" · ").ifBlank { "Sin objetivo" }

internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(safe / 3_600, (safe % 3_600) / 60, safe % 60)
}

internal fun WorkoutUiError.messageResource() = when (kind) {
    WorkoutUiErrorKind.Network -> R.string.workout_error_network
    WorkoutUiErrorKind.Timeout -> R.string.workout_error_timeout
    WorkoutUiErrorKind.Unauthorized -> R.string.workout_error_unauthorized
    WorkoutUiErrorKind.NotFound -> R.string.workout_error_not_found
    WorkoutUiErrorKind.ActiveAlreadyExists -> R.string.workout_error_active_exists
    WorkoutUiErrorKind.RoutineArchived -> R.string.workout_error_routine_archived
    WorkoutUiErrorKind.Validation -> R.string.workout_error_validation
    WorkoutUiErrorKind.Conflict -> R.string.workout_conflict_message
    WorkoutUiErrorKind.AlreadyCompleted -> R.string.workout_error_completed
    WorkoutUiErrorKind.InvalidResponse -> R.string.workout_error_invalid_response
    WorkoutUiErrorKind.Server -> R.string.workout_error_server
    WorkoutUiErrorKind.Unknown -> R.string.workout_error_unknown
}

private fun ExerciseType.supportsRepetitions() = this in setOf(
    ExerciseType.WeightReps, ExerciseType.BodyweightReps,
    ExerciseType.WeightedBodyweight, ExerciseType.AssistedBodyweight,
)
private fun ExerciseType.supportsWeight() = this in setOf(
    ExerciseType.WeightReps, ExerciseType.WeightedBodyweight,
    ExerciseType.AssistedBodyweight, ExerciseType.WeightDistance,
)
private fun ExerciseType.supportsDuration() = this in setOf(ExerciseType.Duration, ExerciseType.DistanceDuration)
private fun ExerciseType.supportsDistance() = this in setOf(ExerciseType.DistanceDuration, ExerciseType.WeightDistance)

@Preview(showBackground = true)
@Composable
private fun NoActiveWorkoutPreview() {
    GYmAppTheme {
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState.NoActiveWorkout(), clock = Clock.systemUTC(),
            onBack = {}, onOpenPicker = {}, onStartEmpty = {},
            onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {}, onMoveExercise = { _, _ -> },
            onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
            onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> }, onSave = {}, onFinish = {},
            onDiscard = {}, onReload = {}, onRetry = {},
        )
    }
}

@Preview(showBackground = true, name = "Editor activo")
@Composable
private fun ActiveEditorPreview() {
    GYmAppTheme {
        val set = WorkoutSetDraft(
            localId = "set", serverId = "server-set",
            targets = WorkoutSetTargets(8, 10, BigDecimal("80.000"), null, null, null),
        )
        ActiveWorkoutScreen(
            state = ActiveWorkoutUiState.Active(
                ActiveWorkoutData(
                    draft = WorkoutDraft(
                        workoutId = "workout", title = "Fuerza superior",
                        exercises = listOf(WorkoutExerciseDraft(
                            "exercise", "server-exercise", "template", "Press de banca",
                            ExerciseType.WeightReps, com.mar.gym.feature.exercises.model.Equipment.Barbell,
                            sets = listOf(set),
                        )),
                    ),
                    startedAt = Instant.now().minusSeconds(600),
                )
            ),
            clock = Clock.systemUTC(), onBack = {}, onOpenPicker = {}, onStartEmpty = {},
            onUpdateTitle = {}, onUpdateNotes = {}, onRemoveExercise = {}, onMoveExercise = { _, _ -> },
            onUpdateExercise = { _, _ -> }, onAddSet = {}, onRemoveSet = { _, _ -> },
            onMoveSet = { _, _, _ -> }, onUpdateSet = { _, _, _ -> }, onSave = {}, onFinish = {},
            onDiscard = {}, onReload = {}, onRetry = {},
        )
    }
}
