package com.mar.gym.feature.progressphotos.ui

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import com.mar.gym.feature.auth.ui.UserSessionViewModelScope
import com.mar.gym.feature.progressphotos.data.ProgressPhoto
import com.mar.gym.feature.progressphotos.data.ProgressPhotoDraft
import com.mar.gym.feature.progressphotos.data.ProgressPhotoMedia
import com.mar.gym.feature.progressphotos.data.ProgressPhotoPage
import com.mar.gym.feature.progressphotos.data.ProgressPhotoRepository
import com.mar.gym.feature.progressphotos.data.ProgressPhotoResult
import com.mar.gym.feature.system.MainDispatcherRule
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPhotosViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val clock = Clock.fixed(Instant.parse("2026-01-02T10:00:00Z"), ZoneOffset.UTC)

    @Test fun `initial loading pagination and dedupe`() = runTest {
        val repo = FakeRepository().apply {
            pages[0] = ProgressPhotoResult.Success(ProgressPhotoPage(listOf(photo(ID)), 0, false))
            pages[1] = ProgressPhotoResult.Success(ProgressPhotoPage(listOf(photo(ID), photo(ID_TWO)), 1, true))
        }
        val vm = ProgressPhotosViewModel(repo, clock)
        assertTrue(vm.uiState.value.loading)
        advanceUntilIdle()
        assertEquals(listOf(ID), vm.uiState.value.items.map { it.id })
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(ID, ID_TWO), vm.uiState.value.items.map { it.id })
        assertEquals(listOf(0, 1), repo.requestedPages)
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test fun `error retries same page and empty is explicit`() = runTest {
        val repo = FakeRepository().apply { pages[0] = ProgressPhotoResult.Error("Error de red") }
        val vm = ProgressPhotosViewModel(repo, clock)
        advanceUntilIdle()
        assertEquals("Error de red", vm.uiState.value.listError)
        repo.pages[0] = ProgressPhotoResult.Success(ProgressPhotoPage(emptyList(), 0, true))
        vm.retryList()
        advanceUntilIdle()
        assertNull(vm.uiState.value.listError)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun `detail and confirmed delete remove photo from local list`() = runTest {
        val repo = FakeRepository().apply {
            pages[0] = ProgressPhotoResult.Success(ProgressPhotoPage(listOf(photo(ID)), 0, true))
        }
        val vm = ProgressPhotosViewModel(repo, clock)
        advanceUntilIdle()
        vm.openDetail(ID)
        advanceUntilIdle()
        assertEquals(ID, vm.uiState.value.detail?.id)
        var returned = false
        vm.delete { returned = true }
        advanceUntilIdle()
        assertTrue(returned)
        assertEquals(listOf(ID), repo.deleted)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertNull(vm.uiState.value.detail)
    }

    @Test fun `user scope creates fresh photo state after logout and new login`() = runTest {
        val first = FakeRepository().apply {
            pages[0] = ProgressPhotoResult.Success(ProgressPhotoPage(listOf(photo(ID)), 0, true))
        }
        val second = FakeRepository().apply {
            pages[0] = ProgressPhotoResult.Success(ProgressPhotoPage(emptyList(), 0, true))
        }
        val scope = UserSessionViewModelScope()
        scope.activate("user-a")
        val a = ViewModelProvider(scope, ProgressPhotosViewModelFactory(first, clock))[ProgressPhotosViewModel::class.java]
        advanceUntilIdle()
        assertEquals(ID, a.uiState.value.items.single().id)
        scope.clearSession()
        scope.activate("user-b")
        val b = ViewModelProvider(scope, ProgressPhotosViewModelFactory(second, clock))[ProgressPhotosViewModel::class.java]
        advanceUntilIdle()
        assertNotSame(a, b)
        assertTrue(b.uiState.value.items.isEmpty())
        assertNull(b.uiState.value.detail)
        assertNull(b.uiState.value.draft)
        assertNull(b.uiState.value.formError)
    }

    private class FakeRepository : ProgressPhotoRepository {
        val pages = mutableMapOf<Int, ProgressPhotoResult<ProgressPhotoPage>>()
        val requestedPages = mutableListOf<Int>()
        val deleted = mutableListOf<String>()
        override suspend fun list(page: Int, size: Int): ProgressPhotoResult<ProgressPhotoPage> {
            requestedPages += page
            return pages[page] ?: ProgressPhotoResult.Success(ProgressPhotoPage(emptyList(), page, true))
        }
        override suspend fun detail(id: String) = ProgressPhotoResult.Success(photo(id))
        override suspend fun upload(draft: ProgressPhotoDraft, now: Instant): ProgressPhotoResult<ProgressPhoto> =
            ProgressPhotoResult.Error("not used")
        override suspend fun delete(id: String): ProgressPhotoResult<Unit> {
            deleted += id
            pages[0] = (pages[0] as? ProgressPhotoResult.Success)?.let {
                ProgressPhotoResult.Success(it.value.copy(content = it.value.content.filterNot { photo -> photo.id == id }))
            } ?: pages[0] ?: ProgressPhotoResult.Success(ProgressPhotoPage(emptyList(), 0, true))
            return ProgressPhotoResult.Success(Unit)
        }
    }

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        const val ID_TWO = "00000000-0000-4000-8000-000000000002"
        fun photo(id: String) = ProgressPhoto(id, Instant.parse("2026-01-01T10:00:00Z"),
            BigDecimal("45.359"), "nota", Instant.parse("2026-01-01T11:00:00Z"),
            ProgressPhotoMedia(id, "https://example.test/photo.jpg", 100, 200))
    }
}
