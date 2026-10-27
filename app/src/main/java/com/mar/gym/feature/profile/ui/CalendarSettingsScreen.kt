package com.mar.gym.feature.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mar.gym.ui.components.AppTopBar
import java.time.DayOfWeek

private val options = listOf(
    DayOfWeek.SUNDAY to "Domingo", DayOfWeek.MONDAY to "Lunes",
    DayOfWeek.TUESDAY to "Martes", DayOfWeek.WEDNESDAY to "Miércoles",
    DayOfWeek.THURSDAY to "Jueves", DayOfWeek.FRIDAY to "Viernes",
    DayOfWeek.SATURDAY to "Sábado",
)

@Composable
fun CalendarSettingsRoute(viewModel: ProfileCalendarViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    CalendarSettingsScreen(state.firstDayOfWeek, viewModel::setFirstDayOfWeek, onBack)
}

@Composable
fun CalendarSettingsScreen(
    firstDayOfWeek: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Primer Día de la Semana", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("calendar_settings")) {
            Text("Esto cambiará el primer día de la semana del calendario, así como tus gráficos en el perfil.",
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                options.forEach { (day, label) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onSelect(day) }
                        .semantics { selected = firstDayOfWeek == day }
                        .testTag("calendar_first_day_${day.name}")
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (firstDayOfWeek == day) FontWeight.SemiBold else FontWeight.Normal)
                        if (firstDayOfWeek == day) Icon(Icons.Default.Check, contentDescription = "Seleccionado",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
