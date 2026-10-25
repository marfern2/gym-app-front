package com.mar.gym.feature.profile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.Manifest
import android.os.Build
import com.mar.gym.feature.push.PushNotifications
import com.mar.gym.feature.push.shouldRequestNotificationPermission
import com.mar.gym.feature.push.ui.PushPreferencesUiState
import com.mar.gym.feature.push.ui.PushPreferencesViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mar.gym.core.units.DistanceUnit
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.ui.components.AppTopBar
import com.mar.gym.ui.components.SecondaryButton
import java.time.LocalDate

@Composable
fun ProfileSettingsRoute(
    viewModel: ProfileViewModel,
    transferViewModel: UserDataTransferViewModel,
    pushViewModel: PushPreferencesViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenBlockedUsers: () -> Unit = {},
    onImported: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationPermissionGranted by remember { mutableStateOf(PushNotifications.canShow(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = PushNotifications.canShow(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionGranted = granted
        permissionDenied = !granted
        if (granted) pushViewModel.setEnabled(true)
    }
    val pushState by pushViewModel.uiState.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val transferState by transferViewModel.state.collectAsState()
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { transferViewModel.destinationSelected(it?.toString()) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        transferViewModel.importSelected(it?.toString())
    }
    LaunchedEffect(transferState) {
        if (transferState == UserDataTransferState.AwaitingDestination) {
            createDocument.launch("gym-export-${LocalDate.now()}.json")
        }
    }
    val profile = state.profile?.value
    ProfileSettingsScreen(
        pushState = pushState,
        notificationPermissionGranted = notificationPermissionGranted,
        permissionDenied = permissionDenied,
        onRetryPush = pushViewModel::refresh,
        onPushEnabledChange = { enabled ->
            if (!enabled) pushViewModel.setEnabled(false)
            else if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, PushNotifications.canShow(context))) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else pushViewModel.setEnabled(true)
        },
        preferences = UnitPreferences(
            profile?.preferredWeightUnit ?: WeightUnit.KG,
            profile?.preferredDistanceUnit ?: DistanceUnit.KM,
        ),
        saving = state.saving,
        saveError = state.profileError != null,
        onPreferencesChange = { viewModel.saveUnitPreferences(it.weight, it.distance) },
        onBack = onBack,
        onLogout = onLogout,
        onOpenBlockedUsers = onOpenBlockedUsers,
        transferState = transferState,
        onExport = transferViewModel::export,
        onImport = {
            transferViewModel.beginImportSelection()
            openDocument.launch(arrayOf("application/json"))
        },
        onConfirmImport = { transferViewModel.confirmImport(onImported) },
        onDismissTransfer = transferViewModel::dismiss,
    )
}

@Composable
fun ProfileSettingsScreen(
    preferences: UnitPreferences,
    saving: Boolean,
    saveError: Boolean,
    onPreferencesChange: (UnitPreferences) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    pushState: PushPreferencesUiState = PushPreferencesUiState.Loading,
    notificationPermissionGranted: Boolean = true,
    permissionDenied: Boolean = false,
    onPushEnabledChange: (Boolean) -> Unit = {},
    onRetryPush: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {},
    transferState: UserDataTransferState = UserDataTransferState.Idle,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onDismissTransfer: () -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Ajustes", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Unidades", style = MaterialTheme.typography.titleLarge)
            UnitChoice(
                title = "Peso",
                options = listOf(WeightUnit.KG to "Kilogramos", WeightUnit.LB to "Libras"),
                selected = preferences.weight,
                enabled = !saving,
                tag = "weight_unit",
                onSelected = { onPreferencesChange(preferences.copy(weight = it)) },
            )
            UnitChoice(
                title = "Distancia",
                options = listOf(DistanceUnit.KM to "Kilómetros", DistanceUnit.MI to "Millas"),
                selected = preferences.distance,
                enabled = !saving,
                tag = "distance_unit",
                onSelected = { onPreferencesChange(preferences.copy(distance = it)) },
            )
            Text(
                "Las unidades cambian cómo se muestran e introducen los valores. Tus datos guardados no se modifican.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (saving) Text("Guardando unidades…", style = MaterialTheme.typography.bodySmall)
            if (saveError) Text("No se pudieron guardar las unidades.", color = MaterialTheme.colorScheme.error)
            Text("Notificaciones", style = MaterialTheme.typography.titleLarge)
            when (pushState) {
                PushPreferencesUiState.Loading -> Text("Cargando preferencia de push…")
                PushPreferencesUiState.Error -> TextButton(onClick = onRetryPush) {
                    Text("No se pudo cargar la preferencia. Reintentar")
                }
                is PushPreferencesUiState.Content -> {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable(enabled = !pushState.saving) {
                            onPushEnabledChange(!pushState.enabled)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Notificaciones push")
                        Switch(
                            checked = pushState.enabled,
                            onCheckedChange = onPushEnabledChange,
                            enabled = !pushState.saving,
                            modifier = Modifier.testTag("push_notifications")
                                .semantics { contentDescription = "Notificaciones push" },
                        )
                    }
                    if (pushState.saving) Text("Guardando preferencia…")
                    if (pushState.error) Text("No se pudo guardar la preferencia.", color = MaterialTheme.colorScheme.error)
                    if (pushState.enabled && !notificationPermissionGranted || permissionDenied) Text(
                        "Android tiene las notificaciones desactivadas. Puedes verlas dentro de la app; habilítalas en los ajustes del sistema.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text("Datos", style = MaterialTheme.typography.titleLarge)
            Text(
                "Puedes guardar una copia de tus datos fitness o importar una exportación compatible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val transferBusy = transferState == UserDataTransferState.LoadingExport ||
                transferState == UserDataTransferState.AwaitingDestination ||
                transferState == UserDataTransferState.AwaitingSource ||
                transferState == UserDataTransferState.SavingExport ||
                transferState == UserDataTransferState.ReadingImport ||
                transferState == UserDataTransferState.Importing ||
                transferState == UserDataTransferState.ConfirmImport
            SecondaryButton("Exportar datos", onExport,
                Modifier.fillMaxWidth().testTag("export_data"), enabled = !transferBusy)
            SecondaryButton("Importar datos", onImport,
                Modifier.fillMaxWidth().testTag("import_data"), enabled = !transferBusy)
            when (transferState) {
                UserDataTransferState.LoadingExport -> Text("Preparando exportación…")
                UserDataTransferState.AwaitingDestination -> Text("Selecciona dónde guardar el archivo…")
                UserDataTransferState.SavingExport -> Text("Guardando exportación…")
                UserDataTransferState.ReadingImport -> Text("Leyendo archivo…")
                UserDataTransferState.Importing -> Text("Importando datos…")
                UserDataTransferState.ExportSaved -> Text("Exportación guardada", color = MaterialTheme.colorScheme.primary)
                is UserDataTransferState.Error -> Text(transferState.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            SecondaryButton(
                "Usuarios bloqueados",
                onOpenBlockedUsers,
                Modifier.fillMaxWidth(),
            )
            SecondaryButton("Cerrar sesión", { confirmLogout = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Cerrar sesión") },
        text = { Text("¿Quieres cerrar la sesión en este dispositivo?") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
    )
    if (transferState == UserDataTransferState.ConfirmImport) AlertDialog(
        onDismissRequest = onDismissTransfer,
        title = { Text("Importar datos") },
        text = { Text("La importación es aditiva. No borrará tus datos actuales.") },
        confirmButton = { TextButton(onClick = onConfirmImport) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismissTransfer) { Text("Cancelar") } },
    )
    if (transferState is UserDataTransferState.ImportCompleted) AlertDialog(
        onDismissRequest = onDismissTransfer,
        title = { Text("Importación completada") },
        text = {
            val summary = transferState.summary
            Text(buildList {
                if (summary.customExercisesCreated > 0) add(countLabel(summary.customExercisesCreated, "ejercicio", "ejercicios"))
                if (summary.foldersCreated > 0) add(countLabel(summary.foldersCreated, "carpeta", "carpetas"))
                if (summary.routinesCreated > 0) add(countLabel(summary.routinesCreated, "rutina", "rutinas"))
                if (summary.programsCreated > 0) add(countLabel(summary.programsCreated, "programa", "programas"))
                if (summary.measurementsCreated > 0) add(countLabel(summary.measurementsCreated, "medida", "medidas"))
            }.ifEmpty { listOf("No se crearon nuevos datos") }.joinToString("\n"))
        },
        confirmButton = { TextButton(onClick = onDismissTransfer) { Text("Aceptar") } },
    )
}

private fun countLabel(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"

@Composable
private fun <T> UnitChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    tag: String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (value, label) ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled) { onSelected(value) }
                    .testTag("${tag}_${value.toString().lowercase()}")
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == value, onClick = { onSelected(value) }, enabled = enabled)
                Text(label)
            }
        }
    }
}
