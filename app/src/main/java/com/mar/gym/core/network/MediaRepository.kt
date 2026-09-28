package com.mar.gym.core.network

import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface MediaResult {
    data object Success : MediaResult
    data class Uploaded(val id: String, val url: String, val width: Int, val height: Int) : MediaResult
    data class Error(val message: String) : MediaResult
}

interface MediaOperations {
    suspend fun uploadAvatar(uri: Uri): MediaResult
    suspend fun deleteAvatar(): MediaResult
    suspend fun uploadWorkoutImage(workoutId: String, uri: Uri): MediaResult
    suspend fun deleteWorkoutImage(workoutId: String, imageId: String): MediaResult
}

class MediaRepository(private val api: MediaApi, private val parts: MediaPartFactory) : MediaOperations {
    override suspend fun uploadAvatar(uri: Uri): MediaResult = when (val part = withContext(Dispatchers.IO) {
        parts.from(uri, 5_000_000)
    }) {
        is SelectedImageResult.Ready -> executeNetworkRequest { api.avatar(part.part) }.toUploadResult()
        else -> part.error()
    }

    override suspend fun deleteAvatar(): MediaResult = executeNetworkUnitRequest { api.deleteAvatar() }.toMediaResult()

    override suspend fun uploadWorkoutImage(workoutId: String, uri: Uri): MediaResult {
        if (!workoutId.isUuid()) return MediaResult.Error("Imagen inválida")
        return when (val part = withContext(Dispatchers.IO) { parts.from(uri, 10_000_000) }) {
            is SelectedImageResult.Ready -> executeNetworkRequest {
                api.workoutImage(workoutId, part.part)
            }.toUploadResult()
            else -> part.error()
        }
    }

    override suspend fun deleteWorkoutImage(workoutId: String, imageId: String): MediaResult {
        if (!workoutId.isUuid() || !imageId.isUuid()) return MediaResult.Error("Imagen inválida")
        return executeNetworkUnitRequest { api.deleteWorkoutImage(workoutId, imageId) }.toMediaResult()
    }

    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
}

private fun NetworkResponse<UploadedMediaDto>.toUploadResult(): MediaResult = when (this) {
    is NetworkResponse.Failure -> toMediaResult()
    is NetworkResponse.Success -> value.let { asset ->
        val url = mediaUrl(asset.url)
        if (runCatching { UUID.fromString(asset.id) }.isFailure || url == null ||
            asset.width <= 0 || asset.height <= 0 || asset.sizeBytes <= 0
        ) MediaResult.Error("Imagen inválida")
        else MediaResult.Uploaded(asset.id, url, asset.width, asset.height)
    }
}

private fun SelectedImageResult.error(): MediaResult.Error = MediaResult.Error(when (this) {
    SelectedImageResult.TooLarge -> "Imagen demasiado grande"
    SelectedImageResult.Unsupported -> "Formato no soportado"
    else -> "Imagen inválida"
})

internal fun NetworkResponse<*>.toMediaResult(): MediaResult = when (this) {
    is NetworkResponse.Success -> MediaResult.Success
    is NetworkResponse.Failure -> MediaResult.Error(when (val failure = error) {
        is NetworkFailure.HttpProblem -> if (failure.statusCode == 404) "Contenido no disponible"
            else if (failure.statusCode == 413) "Imagen demasiado grande"
            else if (failure.statusCode == 415) "Formato no soportado"
            else if (failure.statusCode == 400 || failure.statusCode == 422) "Imagen inválida"
            else "No se pudo guardar la imagen"
        is NetworkFailure.HttpUnknown -> if (failure.statusCode == 404) "Contenido no disponible"
            else "No se pudo guardar la imagen"
        is NetworkFailure.Network, is NetworkFailure.Timeout -> "Error de red. Inténtalo de nuevo"
        else -> "No se pudo guardar la imagen"
    })
}
