package com.mar.gym.feature.system

sealed interface SystemUiState {
    data object Initial : SystemUiState
    data object Loading : SystemUiState

    data class Success(
        val timestamp: String,
        val correlationId: String?,
    ) : SystemUiState

    data class Error(
        val message: String,
        val correlationId: String?,
    ) : SystemUiState
}
