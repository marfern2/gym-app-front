package com.mar.gym.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.profile.data.ProfileRepository
import com.mar.gym.feature.profile.data.ProfileResult
import com.mar.gym.feature.profile.model.PrivateProfileDocument
import com.mar.gym.feature.profile.model.PrivateProfileDraft
import com.mar.gym.feature.profile.model.ProfileActivityMetric
import com.mar.gym.feature.profile.model.ProfileActivityPoint
import com.mar.gym.feature.profile.model.validate
import com.mar.gym.feature.profile.model.workoutActivityPoints
import com.mar.gym.feature.progress.data.AnalyticsRepository
import com.mar.gym.feature.progress.data.AnalyticsResult
import com.mar.gym.feature.progress.data.TimeZoneProvider
import com.mar.gym.feature.progress.model.AnalyticsPeriod
import com.mar.gym.feature.progress.model.HistoryRange
import com.mar.gym.feature.progress.model.MuscleDistribution
import com.mar.gym.feature.progress.model.ProgressSummary
import com.mar.gym.feature.workouts.data.WorkoutRepository
import com.mar.gym.feature.workouts.data.WorkoutRepositoryResult
import com.mar.gym.feature.workouts.model.WorkoutDetail
import com.mar.gym.feature.workouts.model.WorkoutHistoryItem
import java.time.Clock
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
    val selectedActivityMetric: ProfileActivityMetric = ProfileActivityMetric.Duration,
    val selectedActivityRange: HistoryRange = HistoryRange.ThreeMonths,
    val activity: ProfileSection<List<ProfileActivityPoint>> = ProfileSection.Loading,
    val workouts: ProfileSection<List<WorkoutHistoryItem>> = ProfileSection.Loading,
    val selectedStatsPeriod: AnalyticsPeriod = AnalyticsPeriod.Month,
    val summary: ProfileSection<ProgressSummary> = ProfileSection.Loading,
    val distribution: ProfileSection<MuscleDistribution> = ProfileSection.Loading,
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModel() {
    private val zone = ZoneId.of(timeZoneProvider.zoneId())
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private var activityJob: Job? = null
    private var analyticsJob: Job? = null
    private val workoutDetails = mutableMapOf<String, WorkoutDetail>()

    init { refresh() }

    fun refresh() {
        loadProfile()
        loadWorkoutsAndActivity()
        loadAnalytics()
    }

    fun selectActivityMetric(metric: ProfileActivityMetric) {
        _uiState.update { it.copy(selectedActivityMetric = metric) }
    }

    fun selectActivityRange(range: HistoryRange) {
        if (range == _uiState.value.selectedActivityRange) return
        _uiState.update { it.copy(selectedActivityRange = range) }
        loadWorkoutsAndActivity()
    }

    fun selectStatsPeriod(period: AnalyticsPeriod) {
        if (period == _uiState.value.selectedStatsPeriod) return
        _uiState.update { it.copy(selectedStatsPeriod = period) }
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

    private fun loadWorkoutsAndActivity() {
        activityJob?.cancel()
        val range = _uiState.value.selectedActivityRange
        _uiState.update { it.copy(activity = ProfileSection.Loading, workouts = ProfileSection.Loading) }
        activityJob = viewModelScope.launch {
            val today = clock.instant().atZone(zone).toLocalDate()
            val cutoff = range.startDate(today)
            val history = mutableListOf<WorkoutHistoryItem>()
            var pageIndex = 0
            var finished = false
            while (!finished) {
                when (val result = workoutRepository.getWorkoutHistory(pageIndex, HISTORY_PAGE_SIZE)) {
                    is WorkoutRepositoryResult.Failure -> {
                        val error = ProfileSection.Error(result.error)
                        _uiState.update {
                            it.copy(
                                workouts = if (history.isEmpty()) error else section(history.take(RECENT_WORKOUT_COUNT)),
                                activity = error,
                            )
                        }
                        return@launch
                    }
                    is WorkoutRepositoryResult.Success -> {
                        val page = result.value
                        history += page.content
                        if (pageIndex == 0) {
                            _uiState.update { it.copy(workouts = section(history.take(RECENT_WORKOUT_COUNT))) }
                        }
                        val oldest = page.content.lastOrNull()?.completedAt?.atZone(zone)?.toLocalDate()
                        finished = page.last || page.content.isEmpty() || (cutoff != null && oldest != null && oldest < cutoff)
                        pageIndex += 1
                    }
                }
            }
            _uiState.update { it.copy(workouts = section(history.take(RECENT_WORKOUT_COUNT))) }
            val relevant = history.filter { item ->
                val date = item.completedAt.atZone(zone).toLocalDate()
                date <= today && (cutoff == null || date >= cutoff)
            }
            val details = mutableListOf<WorkoutDetail>()
            for (item in relevant) {
                val detail = workoutDetails[item.id] ?: when (val result = workoutRepository.getWorkout(item.id)) {
                    is WorkoutRepositoryResult.Failure -> {
                        _uiState.update { it.copy(activity = ProfileSection.Error(result.error)) }
                        return@launch
                    }
                    is WorkoutRepositoryResult.Success -> result.value.detail.also { workoutDetails[item.id] = it }
                }
                details += detail
            }
            val points = workoutActivityPoints(details, zone, range, today)
            _uiState.update { it.copy(activity = section(points)) }
        }
    }

    private fun loadAnalytics() {
        analyticsJob?.cancel()
        val period = _uiState.value.selectedStatsPeriod
        _uiState.update { it.copy(summary = ProfileSection.Loading, distribution = ProfileSection.Loading) }
        analyticsJob = viewModelScope.launch {
            val summary = analyticsRepository.summary(period, timeZoneProvider.zoneId())
            _uiState.update { state -> state.copy(summary = when (summary) {
                is AnalyticsResult.Failure -> ProfileSection.Error(summary.error)
                is AnalyticsResult.Success -> if (summary.value.workoutCount == 0L) ProfileSection.Empty(summary.value)
                else ProfileSection.Content(summary.value)
            }) }
            val distribution = analyticsRepository.muscleDistribution(period, timeZoneProvider.zoneId())
            _uiState.update { state -> state.copy(distribution = when (distribution) {
                is AnalyticsResult.Failure -> ProfileSection.Error(distribution.error)
                is AnalyticsResult.Success -> if (distribution.value.items.isEmpty()) ProfileSection.Empty(distribution.value)
                else ProfileSection.Content(distribution.value)
            }) }
        }
    }

    private fun <T> section(value: List<T>): ProfileSection<List<T>> =
        if (value.isEmpty()) ProfileSection.Empty(value) else ProfileSection.Content(value)

    private companion object {
        const val HISTORY_PAGE_SIZE = 100
        const val RECENT_WORKOUT_COUNT = 20
    }
}

class ProfileViewModelFactory(
    private val profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeZoneProvider: TimeZoneProvider,
    private val clock: Clock,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(profileRepository, analyticsRepository, workoutRepository, timeZoneProvider, clock) as T
    }
}
