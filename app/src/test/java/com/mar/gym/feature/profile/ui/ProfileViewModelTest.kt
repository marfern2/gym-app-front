package com.mar.gym.feature.profile.ui

import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.profile.data.ProfileRepository
import com.mar.gym.feature.profile.data.ProfileResult
import com.mar.gym.feature.profile.model.PrivateProfile
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfilePrivacy
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.feature.routines.model.SetType
import com.mar.gym.feature.system.MainDispatcherRule
import com.mar.gym.feature.social.data.SocialRepository
import com.mar.gym.feature.social.data.SocialResult
import com.mar.gym.feature.social.model.PublicProfile
import com.mar.gym.feature.social.model.SocialProfilePage
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutDocument
import com.mar.gym.feature.workouts.model.WorkoutDraft
import com.mar.gym.feature.workouts.model.WorkoutEtag
import com.mar.gym.feature.workouts.model.WorkoutExercise
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import com.mar.gym.feature.workouts.model.WorkoutHistoryPage
import com.mar.gym.feature.workouts.model.WorkoutSet
import com.mar.gym.feature.workouts.model.WorkoutSetTargets
import com.mar.gym.feature.workouts.model.WorkoutStatus
import com.mar.gym.feature.workouts.model.WorkoutVisibility
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `profile loads the real username`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("mar.gym", viewModel.uiState.value.profile?.value?.username)
        assertFalse(viewModel.uiState.value.profileLoading)
    }

    @Test fun `duration volume and repetitions can be selected`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectActivityMetric(ProfileActivityMetric.Volume)
        assertEquals(ProfileActivityMetric.Volume, viewModel.uiState.value.selectedActivityMetric)
        viewModel.selectActivityMetric(ProfileActivityMetric.Repetitions)
        assertEquals(ProfileActivityMetric.Repetitions, viewModel.uiState.value.selectedActivityMetric)
        viewModel.selectActivityMetric(ProfileActivityMetric.Duration)
        assertEquals(ProfileActivityMetric.Duration, viewModel.uiState.value.selectedActivityMetric)
    }

    @Test fun `three months one year and all time reload real completed history`() = runTest {
        val workouts = FakeWorkoutRepository()
        val viewModel = viewModel(workouts = workouts)
        advanceUntilIdle()

        assertEquals(HistoryRange.ThreeMonths, viewModel.uiState.value.selectedActivityRange)
        viewModel.selectActivityRange(HistoryRange.OneYear)
        advanceUntilIdle()
        assertEquals(HistoryRange.OneYear, viewModel.uiState.value.selectedActivityRange)
        viewModel.selectActivityRange(HistoryRange.AllTime)
        advanceUntilIdle()
        assertEquals(HistoryRange.AllTime, viewModel.uiState.value.selectedActivityRange)
        assertEquals(3, workouts.historyCalls)
    }

    @Test fun `completed workouts populate profile and exact activity metrics`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val workouts = viewModel.uiState.value.workouts as ProfileSection.Content
        assertEquals(listOf("Entreno real"), workouts.value.map(WorkoutHistoryItem::title))
        val point = (viewModel.uiState.value.activity as ProfileSection.Content).value.single()
        assertEquals(3_600, point.durationSeconds)
        assertEquals(BigDecimal("300.0"), point.volumeKg)
        assertEquals(12, point.repetitions)
    }

    @Test fun `profile update keeps canonical server response`() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()
        viewModel.startEditing()
        viewModel.updateDisplayName("Local")
        profiles.updateResult = ProfileResult.Success(document(name = "Servidor", username = "server.name"))
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Servidor", viewModel.uiState.value.profile?.value?.displayName)
        assertFalse(viewModel.uiState.value.editing)
    }

    @Test fun `privacy loads and can be changed in the draft`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(ProfilePrivacy.Public, viewModel.uiState.value.profile?.value?.privacy)
        viewModel.startEditing()
        viewModel.updatePrivacy(ProfilePrivacy.Private)
        assertEquals(ProfilePrivacy.Private, viewModel.uiState.value.draft?.privacy)
    }

    @Test fun `default workout visibility changes without altering other profile fields`() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()
        viewModel.startEditing()

        viewModel.updateDefaultWorkoutVisibility(WorkoutVisibility.Public)
        viewModel.saveProfile()
        advanceUntilIdle()

        val saved = profiles.lastDraft!!
        assertEquals(WorkoutVisibility.Public, saved.defaultWorkoutVisibility)
        assertEquals("Mar", saved.displayName)
        assertEquals("mar.gym", saved.username)
        assertEquals(ProfilePrivacy.Public, saved.privacy)
        assertEquals(0L, profiles.lastCurrent?.etag?.version)
    }

    @Test fun `PUBLIC profile loads when optional social profile fails`() = runTest {
        val social = FakeSocialRepository().apply {
            profileResult = SocialResult.Failure(NetworkFailure.Network())
        }

        val viewModel = viewModel(socials = social)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Mar", state.profile?.value?.displayName)
        assertEquals("mar.gym", state.profile?.value?.username)
        assertNull(state.profileError)
        assertTrue(state.socialProfile is ProfileSection.Error)
    }

    @Test fun `social failure does not replace main profile or analytics state`() = runTest {
        val social = FakeSocialRepository().apply {
            profileResult = SocialResult.Failure(NetworkFailure.InvalidResponse())
        }

        val viewModel = viewModel(socials = social)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProfilePrivacy.Public, state.profile?.value?.privacy)
        assertNull(state.profileError)
        assertTrue(state.summary is ProfileSection.Content)
        assertTrue(state.activity is ProfileSection.Content)
    }

    @Test fun `real own profile failure exposes main profile error`() = runTest {
        val profiles = FakeProfileRepository().apply {
            getResult = ProfileResult.Failure(NetworkFailure.Network())
        }

        val viewModel = viewModel(profiles = profiles)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.profile)
        assertTrue(viewModel.uiState.value.profileError is NetworkFailure.Network)
        assertFalse(viewModel.uiState.value.profileLoading)
    }

    @Test fun `retry reloads own profile after its failure`() = runTest {
        val profiles = FakeProfileRepository().apply {
            getResult = ProfileResult.Failure(NetworkFailure.Network())
        }
        val viewModel = viewModel(profiles = profiles)
        advanceUntilIdle()
        profiles.getResult = ProfileResult.Success(document(name = "Recuperado"))

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, profiles.getCalls)
        assertEquals("Recuperado", viewModel.uiState.value.profile?.value?.displayName)
        assertNull(viewModel.uiState.value.profileError)
    }

    @Test fun `PRIVATE profile does not depend on public endpoint`() = runTest {
        val profiles = FakeProfileRepository().apply {
            getResult = ProfileResult.Success(document(privacy = ProfilePrivacy.Private))
        }
        val social = FakeSocialRepository().apply {
            profileResult = SocialResult.Failure(NetworkFailure.Network())
        }

        val viewModel = viewModel(profiles = profiles, socials = social)
        advanceUntilIdle()

        assertEquals(ProfilePrivacy.Private, viewModel.uiState.value.profile?.value?.privacy)
        assertEquals(0, social.profileCalls)
        assertNull(viewModel.uiState.value.socialProfile)
    }

    @Test fun `stale profile load cannot overwrite newer retry result`() = runTest {
        val profiles = RacingProfileRepository()
        val viewModel = viewModel(profiles = profiles)
        runCurrent()
        assertTrue(profiles.firstStarted.isCompleted)

        viewModel.refresh()
        runCurrent()
        assertEquals("Nuevo", viewModel.uiState.value.profile?.value?.displayName)

        profiles.releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals("Nuevo", viewModel.uiState.value.profile?.value?.displayName)
        assertNull(viewModel.uiState.value.profileError)
    }

    private fun viewModel(
        profiles: FakeProfileRepository = FakeProfileRepository(),
        workouts: FakeWorkoutRepository = FakeWorkoutRepository(),
        socials: FakeSocialRepository = FakeSocialRepository(),
    ) = ProfileViewModel(
        profiles,
        FakeAnalyticsRepository(),
        workouts,
        socials,
        TimeZoneProvider { "Europe/Madrid" },
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun viewModel(
        profiles: ProfileRepository,
        workouts: FakeWorkoutRepository = FakeWorkoutRepository(),
        socials: FakeSocialRepository = FakeSocialRepository(),
    ) = ProfileViewModel(
        profiles,
        FakeAnalyticsRepository(),
        workouts,
        socials,
        TimeZoneProvider { "Europe/Madrid" },
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private class FakeProfileRepository : ProfileRepository {
        var getCalls = 0
        var getResult: ProfileResult<PrivateProfileDocument> = ProfileResult.Success(document())
        var updateResult: ProfileResult<PrivateProfileDocument> = ProfileResult.Success(document())
        var lastDraft: PrivateProfileDraft? = null
        var lastCurrent: PrivateProfileDocument? = null
        override suspend fun getProfile(): ProfileResult<PrivateProfileDocument> {
            getCalls += 1
            return getResult
        }
        override suspend fun updateProfile(draft: PrivateProfileDraft, current: PrivateProfileDocument): ProfileResult<PrivateProfileDocument> {
            lastDraft = draft
            lastCurrent = current
            return updateResult
        }
    }

    private class RacingProfileRepository : ProfileRepository {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        private var getCalls = 0

        override suspend fun getProfile(): ProfileResult<PrivateProfileDocument> {
            getCalls += 1
            return if (getCalls == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                ProfileResult.Success(document(name = "Antiguo"))
            } else {
                ProfileResult.Success(document(name = "Nuevo"))
            }
        }

        override suspend fun updateProfile(
            draft: PrivateProfileDraft,
            current: PrivateProfileDocument,
        ): ProfileResult<PrivateProfileDocument> = ProfileResult.Failure(NetworkFailure.Network())
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        var historyCalls = 0
        private val detail = workoutDetail()
        override suspend fun getWorkoutHistory(page: Int, size: Int): WorkoutRepositoryResult<WorkoutHistoryPage> {
            historyCalls += 1
            return WorkoutRepositoryResult.Success(WorkoutHistoryPage(
                content = if (page == 0) listOf(historyItem()) else emptyList(),
                page = page, size = size, totalElements = 1, totalPages = 1, first = true, last = true,
            ))
        }
        override suspend fun getWorkout(workoutId: String) = WorkoutRepositoryResult.Success(
            WorkoutDocument(detail, WorkoutEtag.fromVersion(0)!!),
        )
        override suspend fun getActiveWorkout(): WorkoutRepositoryResult<WorkoutDocument> = fail()
        override suspend fun startWorkout(routineId: String?): WorkoutRepositoryResult<WorkoutDocument> = fail()
        override suspend fun updateWorkout(workoutId: String, draft: WorkoutDraft, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = fail()
        override suspend fun completeWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<WorkoutDocument> = fail()
        override suspend fun updateWorkoutVisibility(
            workoutId: String,
            visibility: WorkoutVisibility,
            etag: WorkoutEtag,
        ): WorkoutRepositoryResult<WorkoutDocument> = fail()
        override suspend fun discardWorkout(workoutId: String, etag: WorkoutEtag): WorkoutRepositoryResult<Unit> = fail()
        private fun <T> fail(): WorkoutRepositoryResult<T> = WorkoutRepositoryResult.Failure(NetworkFailure.Network())
    }

    private class FakeAnalyticsRepository : AnalyticsRepository {
        override suspend fun calendar(month: YearMonth, timezone: String) = AnalyticsResult.Success(
            TrainingCalendar(month.atDay(1), month.atEndOfMonth(), timezone, emptyList()),
        )
        override suspend fun summary(period: AnalyticsPeriod, timezone: String) = AnalyticsResult.Success(
            ProgressSummary(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), timezone, 1, 1, 3600, BigDecimal.TEN, 1, 3600),
        )
        override suspend fun muscleDistribution(period: AnalyticsPeriod, timezone: String) = AnalyticsResult.Success(
            MuscleDistribution(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), timezone, 0, emptyList()),
        )
        override suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int): AnalyticsResult<ExerciseHistoryPage> = fail()
        override suspend fun previousPerformance(exerciseTemplateIds: List<String>): AnalyticsResult<List<PreviousPerformanceItem>> = fail()
        override suspend fun personalRecords(exerciseTemplateId: String): AnalyticsResult<PersonalRecords> = fail()
        private fun <T> fail(): AnalyticsResult<T> = AnalyticsResult.Failure(NetworkFailure.Network())
    }

    private class FakeSocialRepository : SocialRepository {
        var profileCalls = 0
        var profileResult: SocialResult<PublicProfile> = SocialResult.Success(publicProfile())
        override suspend fun profile(username: String): SocialResult<PublicProfile> {
            profileCalls += 1
            return when (val result = profileResult) {
                is SocialResult.Failure -> result
                is SocialResult.Success -> SocialResult.Success(result.value.copy(username = username))
            }
        }
        override suspend fun search(query: String, page: Int, size: Int): SocialResult<SocialProfilePage> = fail()
        override suspend fun follow(username: String): SocialResult<Unit> = fail()
        override suspend fun unfollow(username: String): SocialResult<Unit> = fail()
        override suspend fun followers(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = fail()
        override suspend fun following(username: String, page: Int, size: Int): SocialResult<SocialProfilePage> = fail()
        private fun <T> fail(): SocialResult<T> = SocialResult.Failure(NetworkFailure.Network())
    }

    private companion object {
        const val PROFILE_ID = "00000000-0000-4000-8000-000000000001"
        const val WORKOUT_ID = "00000000-0000-4000-8000-000000000002"
        const val EXERCISE_ID = "00000000-0000-4000-8000-000000000003"
        const val TEMPLATE_ID = "00000000-0000-4000-8000-000000000004"
        const val SET_ID = "00000000-0000-4000-8000-000000000005"
        val NOW: Instant = Instant.parse("2026-08-09T10:00:00Z")
        fun document(
            name: String = "Mar",
            username: String? = "mar.gym",
            privacy: ProfilePrivacy = ProfilePrivacy.Public,
        ) = VersionedDocument(
            PrivateProfile(
                PROFILE_ID, name, username, Instant.EPOCH, NOW, 0, privacy,
            ), EntityTag.fromVersion(0)!!,
        )
        fun publicProfile() = PublicProfile(
            PROFILE_ID, "mar.gym", "Mar", null, 1, 2, 3, false, ProfilePrivacy.Public,
        )
        fun historyItem() = WorkoutHistoryItem(WORKOUT_ID, "Entreno real", NOW.minusSeconds(3_600), NOW, 3_600, 1, 1)
        fun workoutDetail() = WorkoutDetail(
            WORKOUT_ID, null, null, "Entreno real", null, WorkoutStatus.Completed,
            NOW.minusSeconds(3_600), NOW, 3_600, NOW.minusSeconds(3_600), NOW, 0,
            listOf(WorkoutExercise(
                EXERCISE_ID, TEMPLATE_ID, "Press", ExerciseType.WeightReps, Equipment.Barbell,
                1, null, 90,
                listOf(WorkoutSet(
                    SET_ID, 1, SetType.Normal,
                    WorkoutSetTargets(null, null, null, null, null, null),
                    true, 12, BigDecimal("25.0"), null, null, null,
                )),
            )),
        )
    }
}
