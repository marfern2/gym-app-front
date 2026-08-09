package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.measurements.data.MeasurementRepository
import com.mar.gym.feature.measurements.data.MeasurementResult
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.profile.data.ProfileRepository
import com.mar.gym.feature.profile.data.ProfileResult
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import com.mar.gym.feature.profile.model.validate
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.progress.model.TrainingCalendar
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileSection<out T> {
    data object Loading : ProfileSection<Nothing>
    data class Empty<T>(val value: T) : ProfileSection<T>
    data class Content<T>(val value: T) : ProfileSection<T>
    data class Error(val error: NetworkFailure) : ProfileSection<Nothing>
}

data class ProfileUiState(
    val profile: PrivateProfileDocument? = null,
    val profileLoading: Boolean = true,
    val profileError: NetworkFailure? = null,
    val editing: Boolean = false,
    val draft: PrivateProfileDraft? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val conflict: Boolean = false,
    val usernameUnavailable: Boolean = false,
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.Month,
    val month: YearMonth,
    val minMonth: YearMonth,
    val maxMonth: YearMonth,
    val calendar: ProfileSection<TrainingCalendar> = ProfileSection.Loading,
    val summary: ProfileSection<ProgressSummary> = ProfileSection.Loading,
    val distribution: ProfileSection<MuscleDistribution> = ProfileSection.Loading,
    val latestMeasurements: ProfileSection<List<BodyMeasurement>> = ProfileSection.Loading,
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val measurementRepository: MeasurementRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModel() {
    private val zone = ZoneId.of(timeZoneProvider.zoneId())
    private val currentMonth = YearMonth.from(clock.instant().atZone(zone))
    private val _uiState = MutableStateFlow(
        ProfileUiState(
            month = currentMonth,
            minMonth = currentMonth.minusYears(10),
            maxMonth = currentMonth,
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var calendarJob: Job? = null
    private var analyticsJob: Job? = null

    init { refresh() }

    fun refresh() {
        loadProfile()
        loadCalendar()
        loadAnalytics()
        loadLatest()
    }

    fun previousMonth() = changeMonth(_uiState.value.month.minusMonths(1))
    fun nextMonth() = changeMonth(_uiState.value.month.plusMonths(1))

    private fun changeMonth(month: YearMonth) {
        if (month !in _uiState.value.minMonth.._uiState.value.maxMonth) return
        _uiState.update { it.copy(month = month) }
        loadCalendar()
    }

    fun selectPeriod(period: AnalyticsPeriod) {
        if (period == _uiState.value.selectedPeriod) return
        _uiState.update { it.copy(selectedPeriod = period) }
        loadAnalytics()
    }

    fun startEditing() {
        val document = _uiState.value.profile ?: return
        _uiState.update { state -> state.copy(
            editing = true,
            draft = PrivateProfileDraft.from(document.value),
            fieldErrors = emptyMap(),
            conflict = false,
            usernameUnavailable = false,
        ) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editing = false, draft = null, fieldErrors = emptyMap()) }
    }

    fun updateDisplayName(value: String) = updateDraft { copy(displayName = value) }
    fun updateUsername(value: String) = updateDraft { copy(username = value) }

    private fun updateDraft(transform: PrivateProfileDraft.() -> PrivateProfileDraft) {
        val draft = _uiState.value.draft ?: return
        _uiState.update { state -> state.copy(
            draft = draft.transform(), fieldErrors = emptyMap(), conflict = false, usernameUnavailable = false,
        ) }
    }

    fun saveProfile() {
        val state = _uiState.value
        val current = state.profile ?: return
        val draft = state.draft ?: return
        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }
        _uiState.update {
            it.copy(saving = true, profileError = null, conflict = false, usernameUnavailable = false)
        }
        viewModelScope.launch {
            when (val result = profileRepository.updateProfile(draft, current)) {
                is ProfileResult.Success -> _uiState.update {
                    it.copy(
                        profile = result.value,
                        profileLoading = false,
                        saving = false,
                        editing = false,
                        draft = null,
                    )
                }
                is ProfileResult.Failure -> {
                    val code = (result.error as? NetworkFailure.HttpProblem)?.problem?.errorCode
                    _uiState.update {
                        it.copy(
                            saving = false,
                            profileError = if (code in setOf(
                                    "PROFILE_VERSION_CONFLICT", "USERNAME_UNAVAILABLE"
                                )
                            ) null else result.error,
                            conflict = code == "PROFILE_VERSION_CONFLICT",
                            usernameUnavailable = code == "USERNAME_UNAVAILABLE",
                        )
                    }
                }
            }
        }
    }

    fun reloadProfileKeepingDraft() = loadProfile(keepDraft = true)

    private fun loadProfile(keepDraft: Boolean = false) {
        _uiState.update { it.copy(profileLoading = true, profileError = null) }
        viewModelScope.launch {
            when (val result = profileRepository.getProfile()) {
                is ProfileResult.Success -> _uiState.update {
                    it.copy(
                        profile = result.value,
                        profileLoading = false,
                        conflict = false,
                        draft = if (keepDraft) it.draft else it.draft,
                    )
                }
                is ProfileResult.Failure -> _uiState.update {
                    it.copy(profileLoading = false, profileError = result.error)
                }
            }
        }
    }

    private fun loadCalendar() {
        calendarJob?.cancel()
        val month = _uiState.value.month
        _uiState.update { it.copy(calendar = ProfileSection.Loading) }
        calendarJob = viewModelScope.launch {
            val section = when (val result = analyticsRepository.calendar(month, timeZoneProvider.zoneId())) {
                is AnalyticsResult.Failure -> ProfileSection.Error(result.error)
                is AnalyticsResult.Success -> if (result.value.days.isEmpty()) ProfileSection.Empty(result.value)
                else ProfileSection.Content(result.value)
            }
            _uiState.update { it.copy(calendar = section) }
        }
    }

    private fun loadAnalytics() {
        analyticsJob?.cancel()
        val period = _uiState.value.selectedPeriod
        _uiState.update {
            it.copy(summary = ProfileSection.Loading, distribution = ProfileSection.Loading)
        }
        analyticsJob = viewModelScope.launch {
            val summary = analyticsRepository.summary(period, timeZoneProvider.zoneId())
            val summarySection = when (summary) {
                is AnalyticsResult.Failure -> ProfileSection.Error(summary.error)
                is AnalyticsResult.Success -> if (summary.value.workoutCount == 0L) ProfileSection.Empty(summary.value)
                else ProfileSection.Content(summary.value)
            }
            _uiState.update { it.copy(summary = summarySection) }

            val distribution = analyticsRepository.muscleDistribution(period, timeZoneProvider.zoneId())
            val distributionSection = when (distribution) {
                is AnalyticsResult.Failure -> ProfileSection.Error(distribution.error)
                is AnalyticsResult.Success -> if (distribution.value.items.isEmpty()) ProfileSection.Empty(distribution.value)
                else ProfileSection.Content(distribution.value)
            }
            _uiState.update { it.copy(distribution = distributionSection) }
        }
    }

    private fun loadLatest() {
        _uiState.update { it.copy(latestMeasurements = ProfileSection.Loading) }
        viewModelScope.launch {
            val section = when (val result = measurementRepository.latest()) {
                is MeasurementResult.Failure -> ProfileSection.Error(result.error)
                is MeasurementResult.Success -> if (result.value.isEmpty()) ProfileSection.Empty(result.value)
                else ProfileSection.Content(result.value)
            }
            _uiState.update { it.copy(latestMeasurements = section) }
        }
    }
}

class ProfileViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val measurementRepository: MeasurementRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(profileRepository, analyticsRepository, measurementRepository, timeZoneProvider, clock) as T
    }
}
