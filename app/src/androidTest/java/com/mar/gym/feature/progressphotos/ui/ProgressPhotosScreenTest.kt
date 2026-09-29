package com.mar.gym.feature.progressphotos.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mar.gym.AppContainer
import com.mar.gym.core.units.UnitPreferences
import com.mar.gym.core.units.WeightUnit
import com.mar.gym.feature.progressphotos.data.ProgressPhoto
import com.mar.gym.feature.progressphotos.data.ProgressPhotoMedia
import com.mar.gym.feature.progressphotos.data.ProgressPhotoDraft
import com.mar.gym.feature.progressphotos.data.ProgressPhotoPage
import com.mar.gym.feature.progressphotos.data.ProgressPhotoRepository
import com.mar.gym.feature.progressphotos.data.ProgressPhotoResult
import com.mar.gym.ui.theme.GYmAppTheme
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressPhotosScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun loadingStateIsVisible() {
        setList(ProgressPhotosUiState())
        composeRule.onNodeWithTag("progress_photo_loading").assertIsDisplayed()
    }

    @Test fun emptyStateInvitesAddingPhoto() {
        var added = false
        setList(ProgressPhotosUiState(loading = false, hasMore = false), onAdd = { added = true })
        composeRule.onNodeWithText("Todavía no tienes fotos de progreso").assertIsDisplayed()
        composeRule.onNodeWithText("Añade fotos para seguir visualmente tu evolución.").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir foto").performClick()
        composeRule.runOnIdle { assertTrue(added) }
    }

    @Test fun errorStateRetries() {
        var retried = false
        setList(ProgressPhotosUiState(loading = false, listError = "Error de red"), onRetry = { retried = true })
        composeRule.onNodeWithTag("progress_photo_error").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test fun listOpensDetailAndContainsNoSocialActions() {
        var opened: String? = null
        setList(ProgressPhotosUiState(loading = false, items = listOf(photo()), hasMore = false),
            onOpenDetail = { opened = it })
        composeRule.onNodeWithText("45.4 kg").assertIsDisplayed()
        composeRule.onNodeWithText("nota privada").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_photo_$ID").performClick()
        composeRule.runOnIdle { assertEquals(ID, opened) }
        composeRule.onNodeWithText("Compartir").assertDoesNotExist()
        composeRule.onNodeWithText("Me gusta").assertDoesNotExist()
        composeRule.onNodeWithText("Comentarios").assertDoesNotExist()
    }

    @Test fun detailSupportsFullscreenDeleteConfirmationAndHotUnitChange() {
        var unit by mutableStateOf(WeightUnit.KG)
        var deleted = false
        AppContainer.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent { GYmAppTheme {
            ProgressPhotoDetailScreen(
                ProgressPhotosUiState(loading = false, detail = photo()),
                UnitPreferences(weight = unit), {}, {}, { deleted = true },
            )
        } }
        composeRule.onNodeWithText("45.4 kg").assertIsDisplayed()
        composeRule.runOnIdle { unit = WeightUnit.LB }
        composeRule.onNodeWithText("100 lb").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_photo_detail_image").performClick()
        composeRule.onNodeWithTag("progress_photo_fullscreen").assertIsDisplayed()
        composeRule.onNodeWithText("Volver").performClick()
        composeRule.onNodeWithTag("progress_photo_fullscreen").assertDoesNotExist()
        composeRule.onNodeWithTag("progress_photo_delete").performClick()
        composeRule.onNodeWithText("Esta foto se eliminará definitivamente.").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.runOnIdle { assertEquals(false, deleted) }
        composeRule.onNodeWithTag("progress_photo_delete").performClick()
        composeRule.onNodeWithTag("progress_photo_confirm_delete").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
        composeRule.onNodeWithText("Compartir").assertDoesNotExist()
    }

    @Test fun listShowsPoundsAfterPreferenceChange() {
        var unit by mutableStateOf(WeightUnit.KG)
        val state = ProgressPhotosUiState(loading = false, items = listOf(photo()), hasMore = false)
        AppContainer.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent { GYmAppTheme {
            ProgressPhotosScreen(state, UnitPreferences(weight = unit), {}, {}, {}, {}, {})
        } }
        composeRule.onNodeWithText("45.4 kg").assertIsDisplayed()
        composeRule.runOnIdle { unit = WeightUnit.LB }
        composeRule.onNodeWithText("100 lb").assertIsDisplayed()
    }

    @Test fun shortFirstPageRequestsMoreOnce() {
        var requests = 0
        AppContainer.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent { GYmAppTheme {
            ProgressPhotosScreen(
                ProgressPhotosUiState(loading = false, items = listOf(photo()), hasMore = true),
                UnitPreferences(), {}, {}, {}, { requests++ }, {},
            )
        } }
        composeRule.waitUntil(5_000) { requests > 0 }
        composeRule.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun detailErrorOffersRetry() {
        var retried = false
        composeRule.setContent { GYmAppTheme {
            ProgressPhotoDetailScreen(
                ProgressPhotosUiState(loading = false, detailError = "Foto no disponible"),
                UnitPreferences(), {}, { retried = true }, {},
            )
        } }
        composeRule.onNodeWithText("Foto no disponible").assertIsDisplayed()
        composeRule.onNodeWithText("Reintentar").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test fun selectedImageOpensMetadataBeforeAnyUpload() {
        var uploads = 0
        val repository = object : ProgressPhotoRepository {
            override suspend fun list(page: Int, size: Int) =
                ProgressPhotoResult.Success(ProgressPhotoPage(emptyList(), page, true))
            override suspend fun detail(id: String): ProgressPhotoResult<ProgressPhoto> =
                ProgressPhotoResult.Error("No disponible")
            override suspend fun upload(draft: ProgressPhotoDraft, now: Instant): ProgressPhotoResult<ProgressPhoto> {
                uploads++
                return ProgressPhotoResult.Error("No disponible")
            }
            override suspend fun delete(id: String): ProgressPhotoResult<Unit> =
                ProgressPhotoResult.Error("No disponible")
        }
        val viewModel = ProgressPhotosViewModel(repository,
            Clock.fixed(Instant.parse("2026-01-02T10:00:00Z"), ZoneOffset.UTC))
        viewModel.selectImage(Uri.parse("content://picker/selected"), WeightUnit.LB)
        composeRule.setContent { GYmAppTheme {
            ProgressPhotosRoute(viewModel, UnitPreferences(weight = WeightUnit.LB), {}, {})
        } }
        composeRule.onNodeWithTag("progress_photo_form").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_photo_taken_at").assertIsDisplayed()
        composeRule.onNodeWithText("Peso (lb, opcional)").assertIsDisplayed()
        composeRule.onNodeWithTag("progress_photo_note").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, uploads) }
    }

    private fun setList(state: ProgressPhotosUiState, onAdd: () -> Unit = {},
        onRetry: () -> Unit = {}, onOpenDetail: (String) -> Unit = {}) {
        AppContainer.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        composeRule.setContent { GYmAppTheme {
            ProgressPhotosScreen(state, UnitPreferences(), {}, onAdd, onOpenDetail, {}, onRetry)
        } }
    }

    private fun photo() = ProgressPhoto(ID, Instant.parse("2026-01-01T10:15:00Z"),
        BigDecimal("45.359"), "nota privada", Instant.parse("2026-01-01T10:16:00Z"),
        ProgressPhotoMedia(MEDIA_ID, "https://example.test/media/$MEDIA_ID", 100, 200))

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val MEDIA_ID = "00000000-0000-4000-8000-000000000002"
    }
}
