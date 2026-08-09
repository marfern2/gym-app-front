package com.mar.gym.feature.profile.ui

import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.ProblemDetails
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.feature.measurements.data.MeasurementRepository
import com.mar.gym.feature.measurements.data.MeasurementResult
import com.mar.gym.feature.measurements.model.BodyMeasurementDocument
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementPage
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.profile.data.ProfileRepository
import com.mar.gym.feature.profile.data.ProfileResult
import com.mar.gym.feature.profile.model.PrivateProfile
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.ExerciseHistoryPage
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.PersonalRecords
import com.mar.gym.feature.progress.model.PreviousPerformanceItem
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import com.mar.gym.feature.system.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `initial concurrent responses cannot restore completed sections to Loading`() = runTest {
        val profileResult = CompletableDeferred<ProfileResult<PrivateProfileDocument>>()
        val calendarResult = CompletableDeferred<AnalyticsResult<TrainingCalendar>>()
        val summaryResult = CompletableDeferred<AnalyticsResult<ProgressSummary>>()
        val distributionResult = CompletableDeferred<AnalyticsResult<MuscleDistribution>>()
        val latestResult = CompletableDeferred<MeasurementResult<List<com.mar.gym.feature.measurements.model.BodyMeasurement>>>()
        val profileRepository = FakeProfileRepository().apply { getProfileBlock = { profileResult.await() } }
        val analyticsRepository = FakeAnalyticsRepository().apply {
            calendarBlock = { month, timezone -> calendarResult.await() }
            summaryBlock = { _, _ -> summaryResult.await() }
            distributionBlock = { _, _ -> distributionResult.await() }
        }
        val measurementRepository = FakeMeasurementRepository().apply { latestBlock = { latestResult.await() } }
        val viewModel = viewModel(profileRepository, analyticsRepository, measurementRepository)
        runCurrent()

        profileResult.complete(ProfileResult.Success(document()))
        runCurrent()
        assertFalse(viewModel.uiState.value.profileLoading)

        calendarResult.complete(AnalyticsResult.Success(calendar(AUGUST)))
        runCurrent()
        assertFalse(viewModel.uiState.value.profileLoading)
        assertTrue(viewModel.uiState.value.calendar is ProfileSection.Content)

        summaryResult.complete(AnalyticsResult.Success(summary(1)))
        runCurrent()
        distributionResult.complete(AnalyticsResult.Success(distribution()))
        latestResult.complete(MeasurementResult.Success(emptyList()))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.profileLoading)
        assertTrue(viewModel.uiState.value.calendar is ProfileSection.Content)
        assertTrue(viewModel.uiState.value.summary is ProfileSection.Content)
        assertTrue(viewModel.uiState.value.distribution is ProfileSection.Empty)
        assertTrue(viewModel.uiState.value.latestMeasurements is ProfileSection.Empty)
    }

    @Test fun `profile loads automatically on creation and exits Loading with success`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(1, repository.getCalls)
        assertEquals("Mar", viewModel.uiState.value.profile?.value?.displayName)
        assertFalse(viewModel.uiState.value.profileLoading)
        assertEquals(null, viewModel.uiState.value.profileError)
    }

    @Test fun `profile initial error exits Loading without a manual event`() = runTest {
        val repository = FakeProfileRepository().apply {
            getResult = ProfileResult.Failure(NetworkFailure.Network())
        }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.getCalls)
        assertFalse(viewModel.uiState.value.profileLoading)
        assertTrue(viewModel.uiState.value.profileError is NetworkFailure.Network)
    }

    @Test fun `summary loads initial month automatically and exits Loading with success`() = runTest {
        val analytics = FakeAnalyticsRepository().apply {
            summaryBlock = { _, _ -> AnalyticsResult.Success(summary(3)) }
        }
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        assertEquals(listOf(AnalyticsPeriod.Month), analytics.summaryPeriods)
        assertTrue(viewModel.uiState.value.summary is ProfileSection.Content)
    }

    @Test fun `summary initial error exits Loading`() = runTest {
        val analytics = FakeAnalyticsRepository().apply {
            summaryBlock = { _, _ -> AnalyticsResult.Failure(NetworkFailure.Network()) }
        }
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        assertEquals(listOf(AnalyticsPeriod.Month), analytics.summaryPeriods)
        assertTrue(viewModel.uiState.value.summary is ProfileSection.Error)
    }

    @Test fun `summary period change still reloads selected period`() = runTest {
        val analytics = FakeAnalyticsRepository()
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.Week)
        advanceUntilIdle()

        assertEquals(listOf(AnalyticsPeriod.Month, AnalyticsPeriod.Week), analytics.summaryPeriods)
        assertEquals(AnalyticsPeriod.Week, viewModel.uiState.value.selectedPeriod)
        assertTrue(viewModel.uiState.value.summary is ProfileSection.Empty)
    }

    @Test fun `calendar loads initial month automatically and exits Loading with success`() = runTest {
        val analytics = FakeAnalyticsRepository().apply {
            calendarBlock = { month, _ -> AnalyticsResult.Success(calendar(month)) }
        }
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        assertEquals(listOf(AUGUST), analytics.calendarMonths)
        assertTrue(viewModel.uiState.value.calendar is ProfileSection.Content)
    }

    @Test fun `calendar initial error exits Loading`() = runTest {
        val analytics = FakeAnalyticsRepository().apply {
            calendarBlock = { _, _ -> AnalyticsResult.Failure(NetworkFailure.Network()) }
        }
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        assertEquals(listOf(AUGUST), analytics.calendarMonths)
        assertTrue(viewModel.uiState.value.calendar is ProfileSection.Error)
    }

    @Test fun `calendar month change still reloads selected month`() = runTest {
        val analytics = FakeAnalyticsRepository()
        val viewModel = viewModel(FakeProfileRepository(), analytics)
        advanceUntilIdle()

        viewModel.previousMonth()
        advanceUntilIdle()

        assertEquals(listOf(AUGUST, AUGUST.minusMonths(1)), analytics.calendarMonths)
        assertEquals(AUGUST.minusMonths(1), viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.calendar is ProfileSection.Empty)
    }

    @Test fun `update uses loaded ETag and canonical response`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.startEditing()
        viewModel.updateDisplayName("Local")
        repository.updateResult = ProfileResult.Success(document(1, "Canonical", "canonical.name"))
        viewModel.saveProfile()
        advanceUntilIdle()
        assertEquals(0L, repository.lastUpdatedFrom?.etag?.version)
        assertEquals("Canonical", viewModel.uiState.value.profile?.value?.displayName)
        assertFalse(viewModel.uiState.value.editing)
    }

    @Test fun `ETag conflict and duplicate username preserve local edit`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.startEditing()
        viewModel.updateDisplayName("Unsaved")
        repository.updateResult = problem("PROFILE_VERSION_CONFLICT")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.conflict)
        assertEquals("Unsaved", viewModel.uiState.value.draft?.displayName)

        repository.updateResult = problem("USERNAME_UNAVAILABLE")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.usernameUnavailable)
        assertEquals("Unsaved", viewModel.uiState.value.draft?.displayName)
    }

    private fun viewModel(
        repository: FakeProfileRepository,
        analyticsRepository: FakeAnalyticsRepository = FakeAnalyticsRepository(),
        measurementRepository: FakeMeasurementRepository = FakeMeasurementRepository(),
    ) = ProfileViewModel(
        repository, analyticsRepository, measurementRepository, TimeZoneProvider { "Europe/Madrid" },
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private class FakeProfileRepository : ProfileRepository {
        var getResult: ProfileResult<PrivateProfileDocument> = ProfileResult.Success(document())
        var getProfileBlock: suspend () -> ProfileResult<PrivateProfileDocument> = { getResult }
        var updateResult: ProfileResult<PrivateProfileDocument> = ProfileResult.Success(document(1))
        var lastUpdatedFrom: PrivateProfileDocument? = null
        var getCalls = 0
        override suspend fun getProfile(): ProfileResult<PrivateProfileDocument> {
            getCalls += 1
            return getProfileBlock()
        }
        override suspend fun updateProfile(draft: PrivateProfileDraft, current: PrivateProfileDocument): ProfileResult<PrivateProfileDocument> {
            lastUpdatedFrom = current
            return updateResult
        }
    }

    private class FakeAnalyticsRepository : AnalyticsRepository {
        val calendarMonths = mutableListOf<YearMonth>()
        val summaryPeriods = mutableListOf<AnalyticsPeriod>()
        var calendarBlock: suspend (YearMonth, String) -> AnalyticsResult<TrainingCalendar> = { month, timezone ->
            AnalyticsResult.Success(TrainingCalendar(month.atDay(1), month.atEndOfMonth(), timezone, emptyList()))
        }
        var summaryBlock: suspend (AnalyticsPeriod, String) -> AnalyticsResult<ProgressSummary> = { _, timezone ->
            AnalyticsResult.Success(summary(timezone = timezone))
        }
        var distributionBlock: suspend (AnalyticsPeriod, String) -> AnalyticsResult<MuscleDistribution> = { _, timezone ->
            AnalyticsResult.Success(distribution(timezone))
        }
        override suspend fun calendar(month: YearMonth, timezone: String): AnalyticsResult<TrainingCalendar> {
            calendarMonths += month
            return calendarBlock(month, timezone)
        }
        override suspend fun summary(period: AnalyticsPeriod, timezone: String): AnalyticsResult<ProgressSummary> {
            summaryPeriods += period
            return summaryBlock(period, timezone)
        }
        override suspend fun muscleDistribution(
            period: AnalyticsPeriod,
            timezone: String,
        ): AnalyticsResult<MuscleDistribution> = distributionBlock(period, timezone)
        override suspend fun exerciseHistory(exerciseTemplateId: String, page: Int, size: Int): AnalyticsResult<ExerciseHistoryPage> = fail()
        override suspend fun previousPerformance(exerciseTemplateIds: List<String>): AnalyticsResult<List<PreviousPerformanceItem>> = fail()
        override suspend fun personalRecords(exerciseTemplateId: String): AnalyticsResult<PersonalRecords> = fail()
        private fun <T> fail(): AnalyticsResult<T> = AnalyticsResult.Failure(NetworkFailure.Network())
    }

    private class FakeMeasurementRepository : MeasurementRepository {
        var latestBlock: suspend () -> MeasurementResult<List<com.mar.gym.feature.measurements.model.BodyMeasurement>> = {
            MeasurementResult.Success(emptyList())
        }
        override suspend fun latest() = latestBlock()
        override suspend fun create(draft: BodyMeasurementDraft, now: Instant): MeasurementResult<BodyMeasurementDocument> = fail()
        override suspend fun list(type: BodyMeasurementType?, page: Int, size: Int): MeasurementResult<BodyMeasurementPage> = fail()
        override suspend fun detail(id: String): MeasurementResult<BodyMeasurementDocument> = fail()
        override suspend fun update(current: BodyMeasurementDocument, draft: BodyMeasurementDraft, now: Instant): MeasurementResult<BodyMeasurementDocument> = fail()
        override suspend fun delete(id: String): MeasurementResult<Unit> = fail()
        private fun <T> fail(): MeasurementResult<T> = MeasurementResult.Failure(NetworkFailure.Network())
    }

    private companion object {
        const val ID = "00000000-0000-4000-8000-000000000001"
        val NOW: Instant = Instant.parse("2026-08-09T10:00:00Z")
        val AUGUST: YearMonth = YearMonth.of(2026, 8)
        fun document(version: Long = 0, name: String = "Mar", username: String? = null) = VersionedDocument(
            PrivateProfile(ID, name, username, Instant.EPOCH, NOW, version), EntityTag.fromVersion(version)!!,
        )
        fun calendar(month: YearMonth) = TrainingCalendar(
            month.atDay(1), month.atEndOfMonth(), "Europe/Madrid",
            listOf(com.mar.gym.feature.progress.model.TrainingCalendarDay(month.atDay(2), 1, 3, 3_600)),
        )
        fun summary(workouts: Long = 0, timezone: String = "Europe/Madrid") = ProgressSummary(
            LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), timezone, workouts, 0, 0,
            java.math.BigDecimal.ZERO, 0, 0,
        )
        fun distribution(timezone: String = "Europe/Madrid") = MuscleDistribution(
            LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), timezone, 0, emptyList(),
        )
        fun problem(code: String): ProfileResult.Failure = ProfileResult.Failure(
            NetworkFailure.HttpProblem(409, ProblemDetails(status = 409, errorCode = code), null),
        )
    }
}
