package com.mar.gym.feature.measurements.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.profile.ui.label
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MeasurementRoute(viewModel: MeasurementViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    MeasurementScreen(
        state, onBack, viewModel::selectFilter, viewModel::loadMore, viewModel::retryList,
        viewModel::openCreate, viewModel::openEdit, viewModel::delete, viewModel::dismissForm,
        viewModel::updateType, viewModel::updateValue, viewModel::updateMeasuredAt,
        viewModel::save, viewModel::reloadEditingKeepingDraft,
    )
}

@Composable
fun MeasurementScreen(
    state: MeasurementUiState,
    onBack: () -> Unit,
    onFilter: (BodyMeasurementType?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (BodyMeasurement) -> Unit,
    onDelete: (String) -> Unit,
    onDismissForm: () -> Unit,
    onTypeChange: (BodyMeasurementType) -> Unit,
    onValueChange: (String) -> Unit,
    onMeasuredAtChange: (java.time.Instant) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AppTopBar("Medidas corporales", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
                .testTag("measurement_history"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeMenu(state.filter, "Todos los tipos", onFilter, Modifier.weight(1f))
                PrimaryButton("Registrar", onCreate)
            }
            when {
                state.loading -> CircularProgressIndicator()
                state.listError != null -> Column {
                    Text("No se pudo cargar el historial.", color = MaterialTheme.colorScheme.error)
                    SecondaryButton("Reintentar", onRetry)
                }
                state.items.isEmpty() -> Text("No hay medidas para este filtro.", Modifier.testTag("measurement_empty"))
                else -> {
                    state.items.forEach { item -> MeasurementCard(item, { onEdit(item) }, { confirmDelete = item.id }, state.deletingId == item.id) }
                    if (state.loadingMore) CircularProgressIndicator()
                    else if (state.hasMore) PrimaryButton("Cargar más", onLoadMore, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (state.formVisible && state.draft != null) MeasurementForm(
        draft = state.draft,
        fieldErrors = state.fieldErrors,
        saving = state.saving,
        conflict = state.conflict,
        hasError = state.formError != null,
        onDismiss = onDismissForm,
        onTypeChange = onTypeChange,
        onValueChange = onValueChange,
        onMeasuredAtChange = onMeasuredAtChange,
        onSave = onSave,
        onReload = onReload,
    )
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminar medida") },
            text = { Text("Esta medida se eliminará definitivamente.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(id) }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MeasurementCard(item: BodyMeasurement, edit: () -> Unit, delete: () -> Unit, deleting: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(item.type.label(), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${item.value.stripTrailingZeros().toPlainString()} ${item.unit.symbol}", fontWeight = FontWeight.Bold)
            }
            Text(DATE_TIME.format(item.measuredAt.atZone(ZoneId.systemDefault())))
            Row {
                TextButton(onClick = edit, enabled = !deleting) { Text("Editar") }
                TextButton(onClick = delete, enabled = !deleting) { Text(if (deleting) "Eliminando…" else "Eliminar") }
            }
        }
    }
}

@Composable
private fun MeasurementForm(
    draft: BodyMeasurementDraft,
    fieldErrors: Map<String, String>,
    saving: Boolean,
    conflict: Boolean,
    hasError: Boolean,
    onDismiss: () -> Unit,
    onTypeChange: (BodyMeasurementType) -> Unit,
    onValueChange: (String) -> Unit,
    onMeasuredAtChange: (java.time.Instant) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val local = draft.measuredAt.atZone(zone)
    val pickDateTime = {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onMeasuredAtChange(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant())
                    },
                    local.hour,
                    local.minute,
                    true,
                ).show()
            },
            local.year,
            local.monthValue - 1,
            local.dayOfMonth,
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }
    AlertDialog(
        modifier = Modifier.testTag("measurement_form"),
        onDismissRequest = onDismiss,
        title = { Text("Medición corporal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeMenu(draft.type, draft.type.label(), { it?.let(onTypeChange) })
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = onValueChange,
                    label = { Text("Valor (${draft.type.unit.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = fieldErrors.containsKey("value"),
                    supportingText = fieldErrors["value"]?.let { { Text(it) } },
                    modifier = Modifier.testTag("measurement_value"),
                )
                TextButton(onClick = pickDateTime, modifier = Modifier.testTag("measurement_datetime")) {
                    Text("Medido: ${DATE_TIME.format(local)}")
                }
                fieldErrors["measuredAt"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (conflict) {
                    Text("La medida cambió en otro cliente. Tu edición se conserva.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onReload) { Text("Recargar versión del servidor") }
                }
                if (hasError) Text("El backend rechazó la operación. Revisa los datos o inténtalo de nuevo.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !saving) { Text(if (saving) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@Composable
private fun TypeMenu(
    selected: BodyMeasurementType?,
    label: String,
    onSelected: (BodyMeasurementType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        SecondaryButton(label, { expanded = true }, Modifier.fillMaxWidth())
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (selected == null) DropdownMenuItem(text = { Text("Todos") }, onClick = { expanded = false; onSelected(null) })
            else DropdownMenuItem(text = { Text("Todos") }, onClick = { expanded = false; onSelected(null) })
            BodyMeasurementType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.label()) }, onClick = { expanded = false; onSelected(type) })
            }
        }
    }
}

private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
