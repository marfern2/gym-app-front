package com.mar.gym.feature.measurements.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.profile.ui.label as rangeLabel
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.EmptyState
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SecondaryButton
import com.mar.gym.ui.components.TemporalChart
import com.mar.gym.ui.components.TemporalChartPoint
import com.mar.gym.ui.components.TemporalChartStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MeasurementRoute(viewModel: MeasurementViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    MeasurementScreen(
        state, onBack, viewModel::selectFilter, viewModel::selectRange, viewModel::retryList,
        viewModel::openCreate, viewModel::openEdit, viewModel::delete, viewModel::dismissForm,
        viewModel::updateType, viewModel::updateValue, viewModel::updateMeasuredAt,
        viewModel::save, viewModel::reloadEditingKeepingDraft,
    )
}

@Composable
fun MeasurementScreen(
    state: MeasurementUiState,
    onBack: () -> Unit,
    onFilter: (BodyMeasurementType) -> Unit,
    onRange: (HistoryRange) -> Unit,
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
    Scaffold(topBar = {
        AppTopBar("Medidas", onBack = onBack, actions = {
            IconButton(onClick = onCreate, modifier = Modifier.testTag("measurement_add")) {
                Icon(Icons.Default.Add, contentDescription = "Registrar medida")
            }
        })
    }) { padding ->
        when {
            state.loading && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            state.listError != null && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No se pudo cargar el historial.", color = MaterialTheme.colorScheme.error)
                SecondaryButton("Reintentar", onRetry)
            }
            state.items.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("measurement_empty"),
                icon = Icons.Default.MonitorWeight,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "No hay medidas registradas",
                message = "Registra tu primera medida para empezar el historial.",
                actionLabel = "Añadir la primera",
                onAction = onCreate,
            )
            else -> MeasurementContent(
                state = state,
                modifier = Modifier.padding(padding),
                onFilter = onFilter,
                onRange = onRange,
                onEdit = onEdit,
                onDelete = { confirmDelete = it },
            )
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
private fun MeasurementContent(
    state: MeasurementUiState,
    modifier: Modifier,
    onFilter: (BodyMeasurementType) -> Unit,
    onRange: (HistoryRange) -> Unit,
    onEdit: (BodyMeasurement) -> Unit,
    onDelete: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val latest = state.items.first()
    val today = LocalDate.now(zone)
    val start = state.selectedRange.startDate(today)
    val chartItems = state.items.filter { item ->
        val date = item.measuredAt.atZone(zone).toLocalDate()
        start == null || date >= start
    }.sortedBy(BodyMeasurement::measuredAt)
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("measurement_history"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${latest.value.stripTrailingZeros().toPlainString()} ${latest.unit.symbol}",
                    Modifier.weight(1f).testTag("measurement_latest_value"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                RangeMenu(state.selectedRange, onRange)
            }
            Text(
                "Último registro · ${DATE.format(latest.measuredAt.atZone(zone))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chartItems.isNotEmpty()) item {
            TemporalChart(
                points = chartItems.map { TemporalChartPoint(it.measuredAt.atZone(zone).toLocalDate(), it.value.toFloat()) },
                valueLabel = { "${it.decimal()} ${latest.unit.symbol}" },
                style = TemporalChartStyle.Line,
                modifier = Modifier.testTag("measurement_chart"),
            )
        } else item {
            Text(
                "No hay registros en este rango.",
                Modifier.padding(vertical = 32.dp).testTag("measurement_range_empty"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { MetricMenu(state.filter, onFilter) }
        item {
            Text(
                "Historial de ${state.filter.metricLabel().lowercase(Locale.forLanguageTag("es-ES"))}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(state.items, key = BodyMeasurement::id) { item ->
            MeasurementHistoryRow(item, onEdit = { onEdit(item) }, onDelete = { onDelete(item.id) })
        }
        if (state.loadingMore) item { CircularProgressIndicator() }
    }
}

@Composable
private fun MeasurementHistoryRow(item: BodyMeasurement, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).testTag("measurement_${item.id}")) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DATE.format(item.measuredAt.atZone(ZoneId.systemDefault())), Modifier.weight(1f))
            Text("${item.value.stripTrailingZeros().toPlainString()} ${item.unit.symbol}", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RangeMenu(selected: HistoryRange, onSelected: (HistoryRange) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("measurement_range")) { Text(selected.rangeLabel()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HistoryRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = { Text(range.rangeLabel()) },
                    onClick = { expanded = false; onSelected(range) },
                    modifier = Modifier.testTag("measurement_range_${range.name}"),
                )
            }
        }
    }
}

@Composable
private fun MetricMenu(selected: BodyMeasurementType, onSelected: (BodyMeasurementType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        SecondaryButton(selected.metricLabel(), { expanded = true }, Modifier.fillMaxWidth().testTag("measurement_metric"))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BodyMeasurementType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.metricLabel()) }, onClick = { expanded = false; onSelected(type) })
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
                    local.hour, local.minute, true,
                ).show()
            },
            local.year, local.monthValue - 1, local.dayOfMonth,
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }
    AlertDialog(
        modifier = Modifier.testTag("measurement_form"),
        onDismissRequest = onDismiss,
        title = { Text("Medición corporal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricMenu(draft.type, onTypeChange)
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
                if (hasError) Text("No se pudo guardar la medida.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = !saving) { Text(if (saving) "Guardando…" else "Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

private fun Float.decimal(): String = toBigDecimal().stripTrailingZeros().toPlainString()
internal fun BodyMeasurementType.metricLabel() = when (this) {
    BodyMeasurementType.BodyWeight -> "Peso corporal"
    BodyMeasurementType.BodyFatPercentage -> "Grasa corporal"
    BodyMeasurementType.Chest -> "Pecho"
    BodyMeasurementType.Waist -> "Cintura"
    BodyMeasurementType.Hips -> "Cadera"
    BodyMeasurementType.LeftBiceps -> "Bíceps izquierdo"
    BodyMeasurementType.RightBiceps -> "Bíceps derecho"
    BodyMeasurementType.LeftThigh -> "Muslo izquierdo"
    BodyMeasurementType.RightThigh -> "Muslo derecho"
    BodyMeasurementType.LeftCalf -> "Gemelo izquierdo"
    BodyMeasurementType.RightCalf -> "Gemelo derecho"
    BodyMeasurementType.Neck -> "Cuello"
}
private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
