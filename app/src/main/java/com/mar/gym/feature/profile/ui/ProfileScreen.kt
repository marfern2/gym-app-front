package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.exercises.ui.labelResource
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.ui.components.PrimaryButton
import com.mar.gym.ui.components.SectionHeader
import com.mar.gym.ui.components.SecondaryButton
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onOpenMeasurements: () -> Unit,
    onOpenExercises: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    ProfileScreen(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectPeriod = viewModel::selectPeriod,
        onEditProfile = viewModel::startEditing,
        onCancelEdit = viewModel::cancelEditing,
        onDisplayNameChange = viewModel::updateDisplayName,
        onUsernameChange = viewModel::updateUsername,
        onSaveProfile = viewModel::saveProfile,
        onReloadProfile = viewModel::reloadProfileKeepingDraft,
        onRetry = viewModel::refresh,
        onOpenMeasurements = onOpenMeasurements,
        onOpenExercises = onOpenExercises,
        onLogout = onLogout,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    onEditProfile: () -> Unit,
    onCancelEdit: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onReloadProfile: () -> Unit,
    onRetry: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenExercises: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            when {
                state.profileLoading && state.profile == null -> CenterLoading("Cargando perfil…")
                state.profileError != null && state.profile == null -> ErrorCard("No se pudo cargar el perfil.", onRetry)
                state.profile != null -> {
                    val profile = state.profile.value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(profile.displayName.trim().firstOrNull()?.uppercase() ?: "G", style = MaterialTheme.typography.headlineLarge) }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(profile.displayName.ifBlank { "Sin nombre" }, style = MaterialTheme.typography.titleLarge)
                            profile.username?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(onClick = onEditProfile) { Text("Editar") }
                    }
                }
            }
        }
        item {
            SectionHeader("Resumen de progreso")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(period.label()) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SummarySection(state.summary, onRetry)
        }
        item {
            SectionHeader("Distribución muscular")
            DistributionSection(state.distribution, onRetry)
        }
        item {
            SectionHeader("Calendario de entrenamiento")
            CalendarSection(
                month = state.month,
                minMonth = state.minMonth,
                maxMonth = state.maxMonth,
                section = state.calendar,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onRetry = onRetry,
            )
        }
        item {
            SectionHeader("Últimas medidas")
            LatestMeasurements(state.latestMeasurements, onRetry)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Ver historial y registrar medida", onOpenMeasurements, Modifier.fillMaxWidth())
        }
        item {
            SectionHeader("Progreso por ejercicio y PRs")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Abre un ejercicio para consultar sus sesiones y récords personales.")
                    SecondaryButton("Abrir ejercicios", onOpenExercises, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            SecondaryButton("Cerrar sesión", { confirmLogout = true }, Modifier.fillMaxWidth())
        }
    }

    if (state.editing && state.draft != null) {
        AlertDialog(
            modifier = Modifier.testTag("profile_editor"),
            onDismissRequest = onCancelEdit,
            title = { Text("Editar perfil privado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.draft.displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Nombre") },
                        isError = state.fieldErrors.containsKey("displayName"),
                        supportingText = state.fieldErrors["displayName"]?.let { { Text(it) } },
                    )
                    OutlinedTextField(
                        value = state.draft.username,
                        onValueChange = onUsernameChange,
                        label = { Text("Username (opcional)") },
                        isError = state.fieldErrors.containsKey("username") || state.usernameUnavailable,
                        supportingText = {
                            Text(
                                when {
                                    state.usernameUnavailable -> "Ese username ya está en uso."
                                    state.fieldErrors["username"] != null -> state.fieldErrors.getValue("username")
                                    else -> "3–30; letras, números, punto y guion bajo."
                                }
                            )
                        },
                    )
                    if (state.conflict) {
                        Text("El perfil cambió en otro cliente. Tu edición se conserva.", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onReloadProfile) { Text("Recargar versión del servidor") }
                    }
                    if (state.profileError != null) Text("No se pudo guardar el perfil.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = onSaveProfile, enabled = !state.saving) { Text(if (state.saving) "Guardando…" else "Guardar") } },
            dismissButton = { TextButton(onClick = onCancelEdit, enabled = !state.saving) { Text("Cancelar") } },
        )
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Cerrar sesión") },
        text = { Text("¿Quieres cerrar la sesión en este dispositivo?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun SummarySection(section: ProfileSection<ProgressSummary>, retry: () -> Unit) = when (section) {
    ProfileSection.Loading -> CenterLoading("Cargando resumen…")
    is ProfileSection.Error -> ErrorCard("No se pudo cargar el resumen.", retry)
    is ProfileSection.Empty -> Card(Modifier.fillMaxWidth().testTag("summary_empty")) { Text("No hay entrenamientos en este periodo.", Modifier.padding(16.dp)) }
    is ProfileSection.Content -> SummaryCard(section.value)
}

@Composable
private fun SummaryCard(summary: ProgressSummary) {
    Card(Modifier.fillMaxWidth().testTag("summary_data")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricRow("Entrenamientos", summary.workoutCount.toString())
            MetricRow("Series completadas", summary.completedSetCount.toString())
            MetricRow("Duración", formatSeconds(summary.totalDurationSeconds))
            MetricRow("Volumen", "${summary.totalVolumeKg.stripTrailingZeros().toPlainString()} kg·rep")
            MetricRow("Días activos", summary.activeDays.toString())
            MetricRow("Duración media", formatSeconds(summary.averageWorkoutDurationSeconds))
        }
    }
}

@Composable
private fun DistributionSection(section: ProfileSection<MuscleDistribution>, retry: () -> Unit) = when (section) {
    ProfileSection.Loading -> CenterLoading("Cargando distribución…")
    is ProfileSection.Error -> ErrorCard("No se pudo cargar la distribución.", retry)
    is ProfileSection.Empty -> Card(Modifier.fillMaxWidth()) { Text("No hay series completadas en este periodo.", Modifier.padding(16.dp)) }
    is ProfileSection.Content -> Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            section.value.items.forEach { item ->
                val percent = if (section.value.totalCompletedSetCount == 0L) "0"
                else item.completedSetCount.toBigDecimal().multiply(100.toBigDecimal())
                    .divide(section.value.totalCompletedSetCount.toBigDecimal(), 1, RoundingMode.HALF_UP).toPlainString()
                MetricRow(stringResource(item.muscleGroup.labelResource()), "${item.completedSetCount} · $percent %")
            }
        }
    }
}

@Composable
private fun CalendarSection(
    month: YearMonth,
    minMonth: YearMonth,
    maxMonth: YearMonth,
    section: ProfileSection<TrainingCalendar>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("calendar")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious, enabled = month > minMonth) { Text("‹") }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onNext, enabled = month < maxMonth) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), fontWeight = FontWeight.Bold) } }
            when (section) {
                ProfileSection.Loading -> CenterLoading("Cargando calendario…")
                is ProfileSection.Error -> ErrorCard("No se pudo cargar el calendario.", onRetry, "calendar_error")
                is ProfileSection.Empty -> CalendarGrid(month, section.value)
                is ProfileSection.Content -> CalendarGrid(month, section.value)
            }
        }
    }
}

@Composable
private fun CalendarGrid(month: YearMonth, calendar: TrainingCalendar) {
    val values = calendar.days.associateBy { it.date.dayOfMonth }
    val cells = List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { it }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { null }).forEach { day ->
                val data = day?.let(values::get)
                Column(
                    Modifier.weight(1f).height(52.dp).padding(2.dp)
                        .background(if (data != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
                    if (data != null) Text("${data.workoutCount}×", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (calendar.days.isEmpty()) Text("Sin entrenamientos este mes.", Modifier.testTag("calendar_empty"))
}

@Composable
private fun LatestMeasurements(section: ProfileSection<List<BodyMeasurement>>, retry: () -> Unit) = when (section) {
    ProfileSection.Loading -> CenterLoading("Cargando medidas…")
    is ProfileSection.Error -> ErrorCard("No se pudieron cargar las medidas.", retry)
    is ProfileSection.Empty -> Card(Modifier.fillMaxWidth().testTag("latest_measurements_empty")) { Text("Todavía no hay medidas registradas.", Modifier.padding(16.dp)) }
    is ProfileSection.Content -> Card(Modifier.fillMaxWidth().testTag("latest_measurements_data")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            section.value.forEach { MetricRow(it.type.label(), "${it.value.stripTrailingZeros().toPlainString()} ${it.unit.symbol}") }
        }
    }
}

@Composable private fun MetricRow(label: String, value: String) = Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(value, fontWeight = FontWeight.SemiBold) }
@Composable private fun CenterLoading(text: String) = Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(20.dp)); Text(text, Modifier.padding(start = 8.dp)) }
@Composable private fun ErrorCard(message: String, retry: () -> Unit, tag: String = "section_error") = Card(Modifier.fillMaxWidth().testTag(tag)) { Column(Modifier.padding(16.dp)) { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Reintentar") } } }

private fun AnalyticsPeriod.label() = when (this) { AnalyticsPeriod.Week -> "Semana"; AnalyticsPeriod.Month -> "Mes"; AnalyticsPeriod.Year -> "Año" }
internal fun BodyMeasurementType.label() = when (this) {
    BodyMeasurementType.BodyWeight -> "Peso"
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
private fun formatSeconds(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
