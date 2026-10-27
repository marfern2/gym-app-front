package com.mar.gym.feature.exercises.ui

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseInstruction
import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaAttribution
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType
import com.mar.gym.feature.exercises.model.ExerciseTemplateDetail
import com.mar.gym.feature.exercises.model.ExerciseTemplateDocument
import com.mar.gym.feature.exercises.model.ExerciseTemplateEtag
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.HttpsUrl
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup
import com.mar.gym.feature.progress.model.ActualPerformanceSet
import com.mar.gym.feature.progress.model.BestWeightForReps
import com.mar.gym.feature.progress.model.ExercisePerformanceSession
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.ui.ExerciseProgressUiState
import com.mar.gym.ui.theme.GYmAppTheme
import java.io.FileOutputStream
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

/** On-device screenshots for visual review; uses only local fixtures. */
class ExerciseDetailVisualQaTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun captureRequestedStates() {
        var detail by mutableStateOf(fixtureDetail())
        var progress by mutableStateOf(fixtureProgress())
        var options by mutableStateOf(ExerciseDetailOptions(range = HistoryRange.AllTime))
        var largeFont by mutableStateOf(false)
        var narrow by mutableStateOf(false)
        composeRule.setContent {
            GYmAppTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(
                    density.density, if (largeFont) 1.4f else density.fontScale,
                )) {
                    ExerciseDetailScreen(
                        state = ExerciseDetailUiState.Content(ExerciseTemplateDocument(
                            detail, requireNotNull(ExerciseTemplateEtag.fromVersion(detail.version)))),
                        options = options,
                        progress = progress,
                        onBack = {},
                        onRetry = {},
                        onSelectTab = { options = options.copy(tab = it) },
                        onSelectRange = { options = options.copy(range = it) },
                        onSelectMetric = { options = options.copy(metric = it) },
                        onUnitOverride = { options = options.copy(weightUnitOverride = it) },
                        onSaveNote = { options = options.copy(note = it) },
                        modifier = if (narrow) Modifier.size(width = 320.dp, height = 780.dp) else Modifier,
                        mediaRenderer = { _, _, modifier ->
                            Box(modifier.background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FitnessCenter, null, Modifier.size(54.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onOpenAttribution = { true },
                    )
                }
            }
        }

        capture("01-summary-history")
        composeRule.onNodeWithTag("exercise-summary").performScrollToIndex(9)
        composeRule.onNodeWithText("Récords personales").assertIsDisplayed()
        capture("08-summary-records")
        composeRule.onNodeWithTag("exercise-summary").performScrollToIndex(0)
        composeRule.runOnIdle { progress = ExerciseProgressUiState(loading = false, recordsLoading = false, hasMore = false) }
        capture("02-summary-empty")
        composeRule.runOnIdle { progress = fixtureProgress(); options = options.copy(tab = ExerciseDetailTab.History) }
        capture("03-history")
        composeRule.runOnIdle {
            options = options.copy(tab = ExerciseDetailTab.Instructions,
                note = "Mantener la espalda estable y revisar la posición del asiento.")
        }
        capture("04-instructions")
        composeRule.onNodeWithTag("exercise-menu").performClick()
        composeRule.onNodeWithTag("exercise-unit-action").performClick()
        capture("05-unit-sheet")
        composeRule.onNodeWithTag("exercise-unit-default").performClick()
        composeRule.runOnIdle {
            detail = fixtureDetail().copy(source = ExerciseTemplateSource.Custom,
                media = emptyList(), instructions = emptyList(), description = null)
            progress = ExerciseProgressUiState(loading = false, recordsLoading = false, hasMore = false)
            options = ExerciseDetailOptions()
        }
        capture("06-custom")
        composeRule.runOnIdle {
            detail = fixtureDetail().copy(name = "Press de pecho unilateral en máquina con agarre neutro y recorrido controlado")
            progress = fixtureProgress()
            largeFont = true
        }
        capture("07-long-name-large-font")
        composeRule.runOnIdle { narrow = true }
        capture("09-narrow-large-font")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(250)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/exercise-detail-ux-final")
            }
            val uri = requireNotNull(context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } else {
            val directory = File(requireNotNull(context.getExternalFilesDir(null)), "exercise-detail-qa")
            check(directory.exists() || directory.mkdirs())
            FileOutputStream(File(directory, "$name.png")).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    private fun fixtureDetail() = ExerciseTemplateDetail(
        id = "visual-press", slug = "press-pecho-maquina", name = "Press de pecho en máquina",
        description = "Movimiento de empuje para trabajar el pecho con una trayectoria guiada.",
        primaryMuscleGroup = MuscleGroup.Chest,
        secondaryMuscleGroups = listOf(MuscleGroup.Shoulders, MuscleGroup.Triceps),
        equipment = Equipment.Machine, exerciseType = ExerciseType.WeightReps,
        movementPattern = MovementPattern.HorizontalPush,
        instructions = listOf(
            ExerciseInstruction(1, "Ajusta el asiento a una altura cómoda."),
            ExerciseInstruction(2, "Apoya la espalda y sujeta las empuñaduras."),
            ExerciseInstruction(3, "Empuja de forma controlada y vuelve sin perder la postura."),
        ),
        media = listOf(ExerciseMedia(ExerciseMediaType.Image, ExerciseMediaRole.Demonstration,
            requireNotNull(HttpsUrl.parse("https://example.test/fixture.png")), 960, 540,
            ExerciseMediaAttribution("Imagen de prueba local", HttpsUrl.parse("https://example.test")))),
    )

    private fun fixtureProgress(): ExerciseProgressUiState {
        fun session(id: String, date: String, first: Int, second: Int) =
            ExercisePerformanceSession(id, Instant.parse(date), "Press de pecho en máquina",
                ExerciseType.WeightReps, listOf(
                    ActualPerformanceSet(1, 8, first.toBigDecimal(), null, null, null),
                    ActualPerformanceSet(2, 10, second.toBigDecimal(), null, null, null),
                ))
        return ExerciseProgressUiState(
            loading = false, recordsLoading = false, hasMore = false,
            sessions = listOf(
                session("workout-3", "2026-10-27T11:30:00Z", 95, 80),
                session("workout-2", "2026-09-15T12:00:00Z", 85, 75),
                session("workout-1", "2026-08-01T12:00:00Z", 70, 60),
            ),
            records = PersonalRecords("visual-press", 95.toBigDecimal(), 12, null, null, null,
                listOf(BestWeightForReps(6, 95.toBigDecimal()),
                    BestWeightForReps(8, 90.toBigDecimal()),
                    BestWeightForReps(10, 80.toBigDecimal()))),
        )
    }
}
