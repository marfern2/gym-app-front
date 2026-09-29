package com.mar.gym.feature.progressphotos.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mar.gym.core.units.UnitConverter
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.feature.progressphotos.data.ProgressPhoto
import com.mar.gym.feature.progressphotos.data.ProgressPhotoDraft
import com.mar.gym.feature.social.ui.MediaFullscreenDialog
import com.mar.gym.feature.social.ui.SocialMediaImage
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun ProgressPhotosRoute(
    viewModel: ProgressPhotosViewModel, preferences: UnitPreferences,
    onBack: () -> Unit, onOpenDetail: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(preferences.weight) { viewModel.updateUnit(preferences.weight) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.selectImage(it, preferences.weight) }
    }
    ProgressPhotosScreen(state, preferences, onBack, { picker.launch("image/*") },
        onOpenDetail, viewModel::loadMore, viewModel::retryList)
    state.draft?.let { draft -> ProgressPhotoForm(draft, state.saving, state.formError,
        viewModel::dismissForm, viewModel::updateTakenAt, viewModel::updateWeight,
        viewModel::updateNote, viewModel::save) }
}

@Composable
fun ProgressPhotosScreen(
    state: ProgressPhotosUiState, preferences: UnitPreferences,
    onBack: () -> Unit, onAdd: () -> Unit, onOpenDetail: (String) -> Unit,
    onLoadMore: () -> Unit, onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.items.size, state.hasMore, state.listError, state.loadingMore) {
        if (state.items.isNotEmpty() && state.hasMore && state.listError == null && !state.loadingMore) {
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                visible >= listState.layoutInfo.totalItemsCount - 2
            }.distinctUntilChanged().collect { if (it) onLoadMore() }
        }
    }
    Scaffold(topBar = { AppTopBar("Fotos de progreso", onBack = onBack, actions = {
        TextButton(onClick = onAdd, modifier = Modifier.testTag("progress_photo_add")) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Añadir")
        }
    }) }) { padding ->
        when {
            state.loading && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).testTag("progress_photo_loading"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.listError != null && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag("progress_photo_error"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.listError, color = MaterialTheme.colorScheme.error)
                SecondaryButton("Reintentar", onRetry)
            }
            state.items.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("progress_photo_empty"),
                icon = Icons.Default.PhotoLibrary,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Todavía no tienes fotos de progreso",
                message = "Añade fotos para seguir visualmente tu evolución.",
                actionLabel = "Añadir foto",
                onAction = onAdd,
            )
            else -> LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize().padding(padding)
                    .testTag("progress_photo_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = ProgressPhoto::id) { photo ->
                    ProgressPhotoCard(photo, preferences, { onOpenDetail(photo.id) })
                }
                if (state.loadingMore) item("loading_more") { CircularProgressIndicator(Modifier.padding(16.dp)) }
                state.listError?.let { error -> item("load_more_error") {
                    Column(Modifier.padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        SecondaryButton("Reintentar", onRetry)
                    }
                } }
            }
        }
    }
}

@Composable
private fun ProgressPhotoCard(photo: ProgressPhoto, preferences: UnitPreferences, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick)
        .testTag("progress_photo_${photo.id}")) {
        Column {
            SocialMediaImage(photo.media.url, "Foto de progreso", Modifier.fillMaxWidth().height(240.dp))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(dateFormat.format(photo.takenAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.titleMedium)
                photo.weightKg?.let { Text(UnitConverter.formatWeight(it, preferences.weight)) }
                photo.note?.takeIf(String::isNotBlank)?.let {
                    Text(it, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProgressPhotoForm(
    draft: ProgressPhotoDraft, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onTakenAt: (Instant) -> Unit, onWeight: (String) -> Unit,
    onNote: (String) -> Unit, onSave: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val local = draft.takenAt.atZone(zone)
    AlertDialog(
        modifier = Modifier.testTag("progress_photo_form"),
        onDismissRequest = onDismiss,
        title = { Text("Añadir foto de progreso") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    val date = LocalDate.of(year, month + 1, day)
                    TimePickerDialog(context, { _, hour, minute ->
                        onTakenAt(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant())
                    }, local.hour, local.minute, true).show()
                }, local.year, local.monthValue - 1, local.dayOfMonth)
                    .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
            }, modifier = Modifier.testTag("progress_photo_taken_at")) {
                Text("Fecha: ${dateTimeFormat.format(local)}")
            }
            OutlinedTextField(draft.weightInput, onWeight,
                label = { Text("Peso (${draft.weightState.displayUnit.symbol}, opcional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("progress_photo_weight"))
            OutlinedTextField(draft.note, onNote, label = { Text("Nota (opcional)") },
                supportingText = { Text("${draft.note.length}/1000") },
                modifier = Modifier.fillMaxWidth().testTag("progress_photo_note"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = onSave, enabled = !saving,
            modifier = Modifier.testTag("progress_photo_save")) {
            Text(if (saving) "Guardando…" else "Guardar")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
fun ProgressPhotoDetailRoute(
    viewModel: ProgressPhotosViewModel, photoId: String, preferences: UnitPreferences,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(photoId) { viewModel.openDetail(photoId) }
    ProgressPhotoDetailScreen(state, preferences, onBack, { viewModel.openDetail(photoId) },
        { viewModel.delete(onBack) })
}

@Composable
fun ProgressPhotoDetailScreen(
    state: ProgressPhotosUiState, preferences: UnitPreferences,
    onBack: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Foto de progreso", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("progress_photo_detail"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.detailLoading && state.detail == null) CircularProgressIndicator(Modifier.padding(24.dp))
            state.detailError?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                SecondaryButton("Reintentar", onRetry)
            }
            state.detail?.let { photo ->
                SocialMediaImage(photo.media.url, "Foto de progreso", Modifier.fillMaxWidth().height(360.dp)
                    .clickable { fullscreen = true }.testTag("progress_photo_detail_image"), ContentScale.Fit)
                Text(dateTimeFormat.format(photo.takenAt.atZone(ZoneId.systemDefault())), Modifier.padding(horizontal = 16.dp))
                photo.weightKg?.let { Text(UnitConverter.formatWeight(it, preferences.weight),
                    Modifier.padding(horizontal = 16.dp).testTag("progress_photo_detail_weight")) }
                photo.note?.takeIf(String::isNotBlank)?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
                state.deleteError?.let { Text(it, Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { confirmDelete = true }, enabled = !state.deleting,
                    modifier = Modifier.testTag("progress_photo_delete")) { Text("Eliminar") }
                if (state.deleting) CircularProgressIndicator()
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminar foto") },
        text = { Text("Esta foto se eliminará definitivamente.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() },
            modifier = Modifier.testTag("progress_photo_confirm_delete")) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    if (fullscreen) state.detail?.let {
        MediaFullscreenDialog(it.media.url, { fullscreen = false }, "progress_photo_fullscreen")
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressPhotosEmptyPreview() = GYmAppTheme {
    ProgressPhotosScreen(ProgressPhotosUiState(loading = false, hasMore = false),
        UnitPreferences(), {}, {}, {}, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun ProgressPhotosLoadingPreview() = GYmAppTheme {
    ProgressPhotosScreen(ProgressPhotosUiState(), UnitPreferences(), {}, {}, {}, {}, {})
}

@Preview(showBackground = true)
@Composable
private fun ProgressPhotosErrorPreview() = GYmAppTheme {
    ProgressPhotosScreen(ProgressPhotosUiState(loading = false, listError = "Error de red"),
        UnitPreferences(), {}, {}, {}, {}, {})
}
