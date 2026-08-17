package com.mar.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mar.gym.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

const val REST_SECONDS_MIN = 0
const val REST_SECONDS_MAX = 3_600
const val REST_SECONDS_STEP = 5

val restSecondsOptions: List<Int> =
    (REST_SECONDS_MIN..REST_SECONDS_MAX step REST_SECONDS_STEP).toList()

fun formatRestSeconds(seconds: Int): String = when {
    seconds == 0 -> "Sin descanso"
    seconds < 60 -> "$seconds s"
    else -> "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

fun nearestRestSeconds(seconds: Int): Int =
    ((seconds.coerceIn(REST_SECONDS_MIN, REST_SECONDS_MAX).toFloat() / REST_SECONDS_STEP)
        .roundToInt() * REST_SECONDS_STEP)
        .coerceIn(REST_SECONDS_MIN, REST_SECONDS_MAX)

@Composable
fun RestTimePickerButton(
    restSeconds: String,
    onConfirm: (String) -> Unit,
    enabled: Boolean,
    testTag: String,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val currentSeconds = restSeconds.toIntOrNull()
        ?.coerceIn(REST_SECONDS_MIN, REST_SECONDS_MAX)
        ?: REST_SECONDS_MIN
    var showPicker by remember { mutableStateOf(false) }
    val formatted = formatRestSeconds(currentSeconds)
    val buttonText = if (currentSeconds == 0) {
        formatted
    } else {
        stringResource(R.string.rest_time_button, formatted)
    }
    val buttonDescription = stringResource(R.string.rest_time_configure, formatted)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(testTag)
                .semantics {
                    contentDescription = buttonDescription
                },
        ) {
            Icon(imageVector = Icons.Outlined.Timer, contentDescription = null)
            Text(
                text = buttonText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showPicker) {
        RestTimePickerBottomSheet(
            currentSeconds = currentSeconds,
            onDismiss = { showPicker = false },
            onConfirm = { selectedSeconds ->
                onConfirm(selectedSeconds.toString())
                showPicker = false
            },
            testTag = testTag,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimePickerBottomSheet(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    testTag: String = "rest_picker",
) {
    var pendingSeconds by remember(currentSeconds) {
        mutableIntStateOf(nearestRestSeconds(currentSeconds))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("${testTag}_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.rest_time_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            RestTimeWheel(
                selectedSeconds = pendingSeconds,
                onSelectionChanged = { pendingSeconds = it },
                testTag = testTag,
            )
            Button(
                onClick = { onConfirm(pendingSeconds) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${testTag}_confirm"),
            ) {
                Text(stringResource(R.string.rest_time_done))
            }
        }
    }
}

@Composable
fun RestTimeWheel(
    selectedSeconds: Int,
    onSelectionChanged: (Int) -> Unit,
    testTag: String = "rest_picker",
) {
    val initialSeconds = nearestRestSeconds(selectedSeconds)
    val initialIndex = initialSeconds / REST_SECONDS_STEP
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val itemHeight = 56.dp

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .map { layoutInfo ->
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }?.index
            }
            .distinctUntilChanged()
            .collect { index ->
                index?.let { onSelectionChanged(restSecondsOptions[it]) }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 3)
                .testTag("${testTag}_wheel"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                count = restSecondsOptions.size,
                key = { restSecondsOptions[it] },
            ) { index ->
                val seconds = restSecondsOptions[index]
                val isSelected = seconds == selectedSeconds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            onSelectionChanged(seconds)
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                        }
                        .testTag("${testTag}_value_$seconds")
                        .semantics {
                            selected = isSelected
                            role = Role.RadioButton
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatRestSeconds(seconds),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = if (isSelected) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
